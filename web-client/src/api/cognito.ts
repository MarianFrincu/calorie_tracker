/**
 * Minimal Cognito client over its HTTPS API (no AWS SDK), same as the desktop
 * app's CognitoAuthService. Sign-in uses SRP (srp.ts), so the password never
 * leaves the browser; sign-up and reset send the new password over TLS.
 */

import { newSrpClient, passwordClaim, srpA } from './srp';

const REGION_PATTERN = /^[a-z0-9-]+$/;

interface CognitoErrorBody {
  __type?: string;
  message?: string;
}

/**
 * A Cognito failure with a sentence fit to show the user as-is (no codes, no
 * jargon). {@link type} is Cognito's error name, for code that reacts to a
 * specific case (e.g. UserNotConfirmedException → the confirm-code step).
 */
export class CognitoError extends Error {
  constructor(
    message: string,
    readonly type: string,
  ) {
    super(message);
    this.name = 'CognitoError';
  }
}

/** Cognito's error → what the user should read. */
export function friendlyCognitoMessage(type: string, message: string): string {
  switch (type) {
    case 'NotAuthorizedException':
      if (/attempts exceeded/i.test(message)) return 'Too many failed attempts. Wait a few minutes and try again.';
      if (/disabled/i.test(message)) return 'This account is disabled.';
      if (/refresh token|revoked|expired/i.test(message)) return 'Your session expired. Please sign in again.';
      return 'Incorrect email or password.';
    case 'UserNotFoundException':
      return 'Incorrect email or password.';
    case 'UserNotConfirmedException':
      return "Your email isn't confirmed yet. Enter the code we emailed you.";
    case 'UsernameExistsException':
      return 'An account with this email already exists. Sign in, or use "Forgot password?".';
    case 'InvalidPasswordException':
      return "The password doesn't meet the rules: at least 12 characters, with upper- and lowercase letters and a number.";
    case 'CodeMismatchException':
      return "That code isn't right. Check the email and try again.";
    case 'ExpiredCodeException':
      return 'That code has expired. Ask for a new one.';
    case 'LimitExceededException':
    case 'TooManyRequestsException':
    case 'TooManyFailedAttemptsException':
      return 'Too many attempts. Please wait a few minutes and try again.';
    case 'InvalidParameterException':
      return /email|username/i.test(message) ? 'Please enter a valid email address.' : "Some of the details aren't valid.";
    default:
      return 'Something went wrong. Please try again.';
  }
}

async function call(
  region: string,
  action: string,
  payload: Record<string, unknown>,
): Promise<unknown> {
  if (!REGION_PATTERN.test(region)) {
    // Only region-shaped values, so config can't point requests elsewhere.
    throw new Error(`Invalid AWS region: ${region}`);
  }

  let response: Response;
  try {
    response = await fetch(`https://cognito-idp.${region}.amazonaws.com/`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-amz-json-1.1',
        'X-Amz-Target': `AWSCognitoIdentityProviderService.${action}`,
      },
      body: JSON.stringify(payload),
      credentials: 'omit',
    });
  } catch {
    throw new CognitoError("Can't reach the sign-in service. Check your internet connection.", 'NetworkError');
  }

  const text = await response.text();
  if (!response.ok) {
    const { type, message } = parseError(text);
    throw new CognitoError(friendlyCognitoMessage(type, message), type);
  }
  return text ? (JSON.parse(text) as unknown) : {};
}

/** Cognito returns errors as {"__type":"...Exception","message":"..."}. */
function parseError(body: string): { type: string; message: string } {
  try {
    const parsed = JSON.parse(body) as CognitoErrorBody;
    // __type can be namespaced: "com.amazonaws...#NotAuthorizedException".
    return { type: (parsed.__type ?? '').split('#').pop() ?? '', message: parsed.message ?? '' };
  } catch {
    return { type: '', message: '' };
  }
}

interface InitiateAuthResponse {
  AuthenticationResult?: { AccessToken?: string; RefreshToken?: string; ExpiresIn?: number };
  ChallengeName?: string;
  ChallengeParameters?: Record<string, string>;
  Session?: string;
}

const POOL_ID_PATTERN = /^[a-z]{2}(-[a-z]+)+-\d+_[0-9A-Za-z]+$/;

export interface Tokens {
  /** Sent as the API Bearer credential. Short-lived (1 h in this pool). */
  accessToken: string;
  /** Long-lived; only ever used to mint new access tokens. Absent on refresh. */
  refreshToken?: string;
}

function tokensFrom(result: InitiateAuthResponse): Tokens {
  if (result.ChallengeName) {
    // e.g. NEW_PASSWORD_REQUIRED for an admin-created user, or MFA.
    throw new CognitoError(
      'This account needs an extra sign-in step the app doesn\'t support. Use "Forgot password?" to set a new password.',
      result.ChallengeName,
    );
  }
  const accessToken = result.AuthenticationResult?.AccessToken;
  if (!accessToken) throw new CognitoError('Sign-in failed. Please try again.', 'NoToken');
  return { accessToken, refreshToken: result.AuthenticationResult?.RefreshToken };
}

/** SRP sign-in: two round trips, and the password itself is never sent. */
export async function login(
  userPoolId: string,
  clientId: string,
  username: string,
  password: string,
): Promise<Tokens> {
  if (!POOL_ID_PATTERN.test(userPoolId)) throw new Error(`Invalid Cognito user pool id: ${userPoolId}`);
  const region = userPoolId.split('_')[0];
  const srp = newSrpClient();
  const started = (await call(region, 'InitiateAuth', {
    AuthFlow: 'USER_SRP_AUTH',
    ClientId: clientId,
    AuthParameters: { USERNAME: username, SRP_A: srpA(srp) },
  })) as InitiateAuthResponse;
  const params = started.ChallengeParameters;
  if (started.ChallengeName !== 'PASSWORD_VERIFIER' || !params) return tokensFrom(started);

  const claim = await passwordClaim(srp, userPoolId, password, {
    userId: params.USER_ID_FOR_SRP,
    saltHex: params.SALT,
    serverBHex: params.SRP_B,
    secretBlock: params.SECRET_BLOCK,
  });
  return tokensFrom(
    (await call(region, 'RespondToAuthChallenge', {
      ChallengeName: 'PASSWORD_VERIFIER',
      ClientId: clientId,
      Session: started.Session,
      ChallengeResponses: {
        USERNAME: params.USER_ID_FOR_SRP,
        PASSWORD_CLAIM_SECRET_BLOCK: params.SECRET_BLOCK,
        TIMESTAMP: claim.timestamp,
        PASSWORD_CLAIM_SIGNATURE: claim.signature,
      },
    })) as InitiateAuthResponse,
  );
}

/** Trades a refresh token for a fresh access token. */
export async function refresh(
  region: string,
  clientId: string,
  refreshToken: string,
): Promise<Tokens> {
  return tokensFrom(
    (await call(region, 'InitiateAuth', {
      AuthFlow: 'REFRESH_TOKEN_AUTH',
      ClientId: clientId,
      AuthParameters: { REFRESH_TOKEN: refreshToken },
    })) as InitiateAuthResponse,
  );
}

/**
 * Invalidates the refresh token and every access token minted from it, so a
 * token copied before sign-out stops working server-side too.
 */
export async function revoke(region: string, clientId: string, refreshToken: string): Promise<void> {
  await call(region, 'RevokeToken', { ClientId: clientId, Token: refreshToken });
}

/** Emails a reset code (Cognito never reveals whether the address exists). */
export async function forgotPassword(region: string, clientId: string, email: string): Promise<void> {
  await call(region, 'ForgotPassword', { ClientId: clientId, Username: email });
}

export async function confirmForgotPassword(
  region: string,
  clientId: string,
  email: string,
  code: string,
  newPassword: string,
): Promise<void> {
  await call(region, 'ConfirmForgotPassword', {
    ClientId: clientId,
    Username: email,
    ConfirmationCode: code,
    Password: newPassword,
  });
}

/** Deletes the signed-in user's Cognito identity. Authorised by the access token itself. */
export async function deleteUser(region: string, accessToken: string): Promise<void> {
  await call(region, 'DeleteUser', { AccessToken: accessToken });
}

export async function signUp(
  region: string,
  clientId: string,
  email: string,
  password: string,
): Promise<void> {
  await call(region, 'SignUp', {
    ClientId: clientId,
    Username: email,
    Password: password,
    UserAttributes: [{ Name: 'email', Value: email }],
  });
}

export async function confirmSignUp(
  region: string,
  clientId: string,
  email: string,
  code: string,
): Promise<void> {
  await call(region, 'ConfirmSignUp', {
    ClientId: clientId,
    Username: email,
    ConfirmationCode: code,
  });
}

export async function resendCode(
  region: string,
  clientId: string,
  email: string,
): Promise<void> {
  await call(region, 'ResendConfirmationCode', { ClientId: clientId, Username: email });
}
