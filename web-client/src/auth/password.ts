/**
 * The user pool's password policy (infra/cloudformation/03-cognito.yaml),
 * as rules the sign-up and reset forms check live - so a password Cognito
 * would reject never gets sent. Mirrored in the desktop client.
 */
export interface PasswordRule {
  label: string;
  met: (password: string) => boolean;
}

export const PASSWORD_RULES: PasswordRule[] = [
  { label: 'At least 12 characters', met: (p) => p.length >= 12 },
  { label: 'An uppercase letter (A-Z)', met: (p) => /[A-Z]/.test(p) },
  { label: 'A lowercase letter (a-z)', met: (p) => /[a-z]/.test(p) },
  { label: 'A number (0-9)', met: (p) => /[0-9]/.test(p) },
];

export function meetsPasswordRules(password: string): boolean {
  return PASSWORD_RULES.every((rule) => rule.met(password));
}
