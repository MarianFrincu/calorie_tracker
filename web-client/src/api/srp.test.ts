import { describe, expect, it } from 'vitest';

import { bytesToHex, cognitoTimestamp, newSrpClient, passwordAuthenticationKey, passwordClaim, srpA } from './srp';

/**
 * Produced by AWS's amazon-cognito-identity-js 6.3.20 (AuthenticationHelper
 * and CognitoUser's signature code) from these fixed inputs - so passing
 * means Cognito computes the same values. The second case covers the other
 * padding branch (odd-length salt) and a non-ASCII password. The same
 * vectors pin the desktop client (CognitoSrpTest).
 */
const VECTORS = [
  {
    poolId: "eu-central-1_AbCdEf123",
    userId: "8f2c4d5e-1a2b-4c3d-9e8f-0a1b2c3d4e5f",
    password: "Correct-Horse-9!",
    saltHex: "a3f1c0de5b7e9a2c4d6f8e1b3a5c7d90",
    secretBlock: "Zml4ZWQgc2VjcmV0IGJsb2NrIGJ5dGVzIGZvciB0aGUgdGVzdCB2ZWN0b3I=",
    smallAHex: "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
    largeAHex: "f043a4fd8b21cb2abf88a8ebe60bf8c0b8c3b3f93da04f29bc5b789964d10296153713f0ca5745c592731018e16e756ea1f6ca4c76b7eacdd3738436da07cab66153ffad0962e75c9a69ed14ea943e79494910919090f25826d14bb2e8573a5c4effe93c66339f764330a45c8f1dc177b7fbc69c60948232be68614f240c81f2ed634256cbfe081b322d9c83d4c6104b040808723fafd57d3d0dacc9a766ec822b74a6e61b4a80ee7af9c02b885f46652194ab1d3174c41973e5caac488fa4a50b6ad8f53932a5ba5fca29e86f1f61d320bc401ff700964ffa08c0f8edba8904249eedf11b60e0af23e7067bbc71b8af6d64d7f2faea893dfbb971b556c2c352ef15514135901370ef25e5c7d81393e0f5e6541f1496ca4edd0292ae0ff6b0a88ced3776404b0595624fb424fc9624c017dde6a30575ee174348bca1bb7da7a738c76512409b6f18d94f4837628e06a8d127d6e40764846526c27895242fce72b8a8930847a66665006c19f250c3b7c0a550e5ee02da14e9714418ba3c08dec5",
    serverBHex: "e8ecbe6e85366bc7934820681e58455d0eac7ee50b39a0a1be26e9b78742c7a0fa96de61b5b71b73589c2dc5ee617cd094f4161054898528e906cadb8bef6fcb8ad722296b949522935a9f41841c054cc0113034f6a01f5a8357e7956a79e203cabcbe6e18bb02692ef6072f3163d5c1c8f5703e8022d2ce243886bfe6ddf7cbbd4cbde7c54600f7c557c3637f3a4b366501a0afccd61c032ccc772ba4a0e539237a5869094d6a3fbeccdfa771de15f8e8dc43e0424c21cefdbcae6f69edc2a03eb299ba2c1256bb114feae4fae0a689f385920d413dd760b6c4720db6fe0c48ffdac692ae93b3d2ae816553ff9a5e7c22a60a8c8d77eddd0cdbf865d4907794b557a22989f4387acd16b4446d2f7de1ebf0e1d995d57aa254fb662086cf1531758818c7e4f6ec73ebc11e2a3fa71c16d201c8bf3be69c8c5f88b9d8010e9007a76b07ca422bc522b426a0cae8b3469ada9b17fe2c32afb63737e739d80f97f44372573045c787e76e4f260912926c3875892813d90917cfbf11d44623758594",
    hkdfHex: "84c93cedaba24ab9ba47650230018a32",
    timestamp: "Sat Sep 5 09:03:07 UTC 2026",
    signature: "qa1J/DM1JRwh2pgIf3UwX9U0TWzgSW1l7+HPf7GfMo4=",
  },
  {
    poolId: "us-east-1_Zz9",
    userId: "0a1b2c3d-0000-4000-8000-123456789abc",
    password: "p\u00e4ssw\u00f6rd-\u03a9mega 12",
    saltHex: "0f1e2d3c4b5a69788796a5b4c3d2e1f0",
    secretBlock: "Zml4ZWQgc2VjcmV0IGJsb2NrIGJ5dGVzIGZvciB0aGUgdGVzdCB2ZWN0b3I=",
    smallAHex: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    largeAHex: "54b8e4da8991abf64544e5e11b2bd8c7a17bf13cdc3b757a276c8d475af798afd3666395ae0a291861ac9f3181e263185ab8006e236972ff0ac89ff60a824bf8c4e19c07106f6f62b7a129f56e73477cd23ce582c6a23d19c5cb2fbf29f32978fcf0743650a1572c349444cdc3229d5b29c60fdb9f3dfc0ecbd532302115b7aab5016382590f811a5b7caa46ec650538e1377de9c35a4203edc9b004658d1147caa08516730d40c80409b0bab60fd885ae184434764cc759d8b7905a7c1deff646e62540614d40378604b21e170159c1931f362f8003e66b5d81cd0b5996850c0735512ed5c8913864e819e7520b25d6df825b2372b56398ccb46a2f29e31760d68e468d841518a783046b56c28cfc12d789a725469640ef0db5b557cbe9117f97d808df3f32c9610e5ef7822593e5ee1289b7f1993ba4406d84926ca459cbcb00d77369492e9363a7ac6191652c8d18d1c94422fd0e579bcfff161b945072743a4934e650373ecc40acebb84ff209b7c8ba5d5b7cd4a786ba1174323284223",
    serverBHex: "ad6d951ce528f1f391c735fd5dea3d509c7ee15979152372eca077da88aa9ed9309e9c8326ccc8cc0c695b2854c9460dbbfbcd925965b189aaf456c2f1df37cd0ccd9ac8945855987730aa42739534b348f83b3591aaa9fd9d55cad40b19beedaa641afe1b46fab4099b6b17f6d4d4dd22f6e9a8a995bfcebf3a54862c954abe27c268a8413b1187d01a546da450ab85aea96ce76e0ef6644d6e8eb98594832e08ac6ef6198fb810c2a4a849f0609c0c3bef1fd59caf0b8af8af2e4f39f0a19ec366c36d5ee39bcd16e6979772b29c48f011b94b363ef7ba5b0381d4ac92bc26b026a4cf748b0c0ec00e6b326b4bbf5b317d1f3e35c6f2288d0f5228569a973c0d24b2eddbdc06f469559ac2081df184d7b51b988f07a40b9e230adc9ad402abd2d27f3879ac4055cef2988b3e28855b89d437424b8957fd0de3b5d0bf192c8e6a8d531da2964b07f079ebd7f2d7b4aef205e11fa03c31ee1b9425af7e22ae6faf473be28ee9ff67b684dfd10807e6054fe2f199479bda867cf9d513393ec655",
    hkdfHex: "1dac09ccf458289a763be7cddd980cdc",
    timestamp: "Sat Sep 5 09:03:07 UTC 2026",
    signature: "T1pekF4Y83DTMsnxnz76xE71chmladjJOqm3XmIfkJs=",
  },
];

describe('Cognito SRP', () => {
  it.each(VECTORS)('matches the AWS library for $poolId', async (v) => {
    const client = newSrpClient(BigInt('0x' + v.smallAHex));
    expect(srpA(client)).toBe(v.largeAHex);

    const challenge = { userId: v.userId, saltHex: v.saltHex, serverBHex: v.serverBHex, secretBlock: v.secretBlock };
    const key = await passwordAuthenticationKey(client, v.poolId.split('_')[1], challenge, v.password);
    expect(bytesToHex(key)).toBe(v.hkdfHex);

    const claim = await passwordClaim(client, v.poolId, v.password, challenge, new Date('2026-09-05T09:03:07Z'));
    expect(claim).toEqual({ timestamp: v.timestamp, signature: v.signature });
  });

  it('formats timestamps like Cognito expects', () => {
    expect(cognitoTimestamp(new Date('2026-12-25T23:04:05Z'))).toBe('Fri Dec 25 23:04:05 UTC 2026');
    expect(cognitoTimestamp(new Date('2027-01-01T00:00:00Z'))).toBe('Fri Jan 1 00:00:00 UTC 2027');
  });

  it('uses a fresh random secret for every attempt', () => {
    expect(srpA(newSrpClient())).not.toBe(srpA(newSrpClient()));
  });
});
