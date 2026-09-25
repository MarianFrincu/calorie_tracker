import { afterEach, describe, expect, it, vi } from 'vitest';

import { CognitoError, friendlyCognitoMessage, login } from './cognito';

const POOL = 'eu-central-1_AbCdEf123';
const PASSWORD = 'Correct-Horse-9!';

function reply(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

describe('Cognito SRP sign-in', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('proves the password without ever sending it', async () => {
    const sent: { target: string; body: Record<string, unknown> }[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      expect(url).toBe('https://cognito-idp.eu-central-1.amazonaws.com/');
      const target = String((init.headers as Record<string, string>)['X-Amz-Target']).split('.')[1];
      sent.push({ target, body: JSON.parse(String(init.body)) });
      if (target === 'InitiateAuth') {
        return reply({
          ChallengeName: 'PASSWORD_VERIFIER',
          Session: 'session-1',
          ChallengeParameters: {
            USER_ID_FOR_SRP: 'user-uuid',
            SALT: 'a3f1c0de5b7e9a2c4d6f8e1b3a5c7d90',
            SRP_B: 'c0ffee'.repeat(64),
            SECRET_BLOCK: btoa('secret block'),
          },
        });
      }
      return reply({ AuthenticationResult: { AccessToken: 'access-1', RefreshToken: 'refresh-1' } });
    }));

    const tokens = await login(POOL, 'client-1', 'me@example.com', PASSWORD);

    expect(tokens).toEqual({ accessToken: 'access-1', refreshToken: 'refresh-1' });
    expect(sent.map((s) => s.target)).toEqual(['InitiateAuth', 'RespondToAuthChallenge']);
    expect(sent[0].body).toMatchObject({
      AuthFlow: 'USER_SRP_AUTH',
      ClientId: 'client-1',
      AuthParameters: { USERNAME: 'me@example.com', SRP_A: expect.stringMatching(/^[0-9a-f]{700,}$/) },
    });
    const responses = sent[1].body.ChallengeResponses as Record<string, string>;
    expect(sent[1].body).toMatchObject({ ChallengeName: 'PASSWORD_VERIFIER', ClientId: 'client-1', Session: 'session-1' });
    expect(responses.USERNAME).toBe('user-uuid');
    expect(responses.PASSWORD_CLAIM_SECRET_BLOCK).toBe(btoa('secret block'));
    expect(responses.TIMESTAMP).toMatch(/^[A-Z][a-z]{2} [A-Z][a-z]{2} \d{1,2} \d{2}:\d{2}:\d{2} UTC \d{4}$/);
    expect(atob(responses.PASSWORD_CLAIM_SIGNATURE)).toHaveLength(32);
    expect(JSON.stringify(sent)).not.toContain(PASSWORD);
  });

  it('surfaces a wrong password as Cognito words it', async () => {
    vi.stubGlobal('fetch', vi.fn(async (_url: string, init: RequestInit) =>
      String((init.headers as Record<string, string>)['X-Amz-Target']).endsWith('InitiateAuth')
        ? reply({ ChallengeName: 'PASSWORD_VERIFIER', ChallengeParameters: {
            USER_ID_FOR_SRP: 'u', SALT: '0f', SRP_B: 'ab'.repeat(300), SECRET_BLOCK: btoa('s') } })
        : reply({ __type: 'NotAuthorizedException', message: 'Incorrect username or password.' }, 400)));

    await expect(login(POOL, 'c', 'me@example.com', 'wrong')).rejects.toThrow('Incorrect email or password.');
  });

  it('never shows raw codes or HTTP statuses', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      reply({ __type: 'com.amazonaws#UserNotConfirmedException', message: 'User is not confirmed.' }, 400)));
    const err = await login(POOL, 'c', 'me@example.com', 'x').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(CognitoError);
    expect((err as CognitoError).type).toBe('UserNotConfirmedException');
    expect((err as CognitoError).message).toBe("Your email isn't confirmed yet. Enter the code we emailed you.");
    expect((err as CognitoError).message).not.toMatch(/\d{3}|Exception|InitiateAuth/);

    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
    await expect(login(POOL, 'c', 'a', 'b')).rejects.toThrow("Can't reach the sign-in service");
  });

  it('turns every Cognito error into a plain sentence', () => {
    expect(friendlyCognitoMessage('UsernameExistsException', 'User already exists')).toMatch(/already exists/);
    expect(friendlyCognitoMessage('CodeMismatchException', 'Invalid verification code provided')).toMatch(/code isn't right/);
    expect(friendlyCognitoMessage('NotAuthorizedException', 'Password attempts exceeded')).toMatch(/Too many failed attempts/);
    expect(friendlyCognitoMessage('SomethingNewException', 'weird')).toBe('Something went wrong. Please try again.');
  });

  it('refuses a malformed pool id', async () => {
    await expect(login('evil.com/x_1', 'c', 'a', 'b')).rejects.toThrow('Invalid Cognito user pool id');
  });
});
