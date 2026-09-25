/**
 * The client half of Cognito's USER_SRP_AUTH (Secure Remote Password, SRP-6a).
 *
 * The password never leaves the browser: sign-in sends a random public value
 * A, Cognito answers with its own public value B plus a salt, and the client
 * proves it knows the password by signing Cognito's secret block with a key
 * only the right password can derive. Neither side can learn the password
 * from what crosses the wire, not even a server that records it.
 *
 * Byte-for-byte the algorithm of AWS's amazon-cognito-identity-js
 * (AuthenticationHelper + CognitoUser), without its dependencies: BigInt for
 * the 3072-bit arithmetic, Web Crypto for SHA-256 / HMAC. srp.test.ts pins it
 * to values produced by that library.
 */

/** RFC 5054's 3072-bit group, the one Cognito uses. */
const N = BigInt(
  '0x' +
    'FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1' +
    '29024E088A67CC74020BBEA63B139B22514A08798E3404DD' +
    'EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245' +
    'E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED' +
    'EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D' +
    'C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F' +
    '83655D23DCA3AD961C62F356208552BB9ED529077096966D' +
    '670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B' +
    'E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9' +
    'DE2BCBF6955817183995497CEA956AE515D2261898FA0510' +
    '15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64' +
    'ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7' +
    'ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B' +
    'F12FFA06D98A0864D87602733EC86A64521F2B18177B200C' +
    'BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31' +
    '43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF',
);
const G = 2n;
const encoder = new TextEncoder();

/** The client's secret a and public A = g^a mod N for one sign-in attempt. */
export interface SrpClient {
  smallA: bigint;
  largeA: bigint;
}

/** What Cognito's PASSWORD_VERIFIER challenge sends back. */
export interface SrpChallenge {
  /** USER_ID_FOR_SRP: the user's internal id, which Cognito signs with (not the email). */
  userId: string;
  saltHex: string;
  serverBHex: string;
  secretBlock: string;
}

/** Starts an attempt. {@code smallA} is only passed by tests. */
export function newSrpClient(smallA?: bigint): SrpClient {
  const a = smallA ?? randomBigInt(128) % N;
  const A = modPow(G, a, N);
  if (A % N === 0n) throw new Error('Invalid SRP value; please try again.');
  return { smallA: a, largeA: A };
}

/** The SRP_A auth parameter. */
export function srpA(client: SrpClient): string {
  return client.largeA.toString(16);
}

/**
 * The PASSWORD_CLAIM_SIGNATURE for Cognito's challenge (plus the timestamp
 * it was computed for, which must be sent alongside it).
 */
export async function passwordClaim(
  client: SrpClient,
  poolId: string,
  password: string,
  challenge: SrpChallenge,
  now: Date = new Date(),
): Promise<{ timestamp: string; signature: string }> {
  const poolName = poolId.split('_')[1];
  const hkdf = await passwordAuthenticationKey(client, poolName, challenge, password);
  const timestamp = cognitoTimestamp(now);
  const message = concat(
    encoder.encode(poolName),
    encoder.encode(challenge.userId),
    base64ToBytes(challenge.secretBlock),
    encoder.encode(timestamp),
  );
  const signature = await hmac(hkdf, message);
  return { timestamp, signature: bytesToBase64(signature) };
}

/** HKDF key both sides derive from the shared SRP secret. Exported for tests. */
export async function passwordAuthenticationKey(
  client: SrpClient,
  poolName: string,
  challenge: SrpChallenge,
  password: string,
): Promise<Uint8Array> {
  const B = BigInt('0x' + challenge.serverBHex);
  if (B % N === 0n) throw new Error('Invalid SRP value from Cognito.');
  const u = BigInt('0x' + (await hexHash(padHex(client.largeA) + padHex(B))));
  if (u === 0n) throw new Error('Invalid SRP value from Cognito.');

  const k = BigInt('0x' + (await hexHash(padHex(N) + padHex(G))));
  const userPasswordHash = await hash(encoder.encode(`${poolName}${challenge.userId}:${password}`));
  const x = BigInt('0x' + (await hexHash(padHex(BigInt('0x' + challenge.saltHex)) + userPasswordHash)));

  const base = mod(B - k * modPow(G, x, N), N);
  const S = modPow(base, client.smallA + u * x, N);

  // HKDF-SHA256, info "Caldera Derived Key", first 16 bytes.
  const prk = await hmac(hexToBytes(padHex(u)), hexToBytes(padHex(S)));
  const okm = await hmac(prk, concat(encoder.encode('Caldera Derived Key'), new Uint8Array([1])));
  return okm.slice(0, 16);
}

/** "Sat Sep 5 09:03:07 UTC 2026": day not zero-padded, the rest is. */
export function cognitoTimestamp(date: Date): string {
  const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const two = (n: number) => String(n).padStart(2, '0');
  return (
    `${days[date.getUTCDay()]} ${months[date.getUTCMonth()]} ${date.getUTCDate()} ` +
    `${two(date.getUTCHours())}:${two(date.getUTCMinutes())}:${two(date.getUTCSeconds())} UTC ${date.getUTCFullYear()}`
  );
}

// ---------------- arithmetic + encoding ----------------

function mod(n: bigint, m: bigint): bigint {
  const r = n % m;
  return r < 0n ? r + m : r;
}

function modPow(base: bigint, exponent: bigint, m: bigint): bigint {
  let result = 1n;
  let b = mod(base, m);
  let e = exponent;
  while (e > 0n) {
    if (e & 1n) result = (result * b) % m;
    b = (b * b) % m;
    e >>= 1n;
  }
  return result;
}

/** Even-length hex, with a leading 00 when the top bit is set (a positive two's-complement encoding). */
export function padHex(n: bigint): string {
  let hex = n.toString(16);
  if (hex.length % 2 !== 0) hex = '0' + hex;
  if (/^[89a-f]/i.test(hex)) hex = '00' + hex;
  return hex;
}

function randomBigInt(bytes: number): bigint {
  const buf = crypto.getRandomValues(new Uint8Array(bytes));
  return BigInt('0x' + bytesToHex(buf));
}

async function hash(data: Uint8Array): Promise<string> {
  return bytesToHex(new Uint8Array(await crypto.subtle.digest('SHA-256', data as BufferSource)));
}

function hexHash(hex: string): Promise<string> {
  return hash(hexToBytes(hex));
}

async function hmac(key: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
  const k = await crypto.subtle.importKey('raw', key as BufferSource, { name: 'HMAC', hash: 'SHA-256' }, false, [
    'sign',
  ]);
  return new Uint8Array(await crypto.subtle.sign('HMAC', k, data as BufferSource));
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let offset = 0;
  for (const p of parts) {
    out.set(p, offset);
    offset += p.length;
  }
  return out;
}

function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}

export function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

function base64ToBytes(b64: string): Uint8Array {
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

function bytesToBase64(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes));
}
