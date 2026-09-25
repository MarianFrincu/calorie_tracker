# Calorie Tracker — web client

The main client: React 19 + TypeScript, built with Vite and served by nginx.
It has the same features and look as the JavaFX desktop client, and talks
only to the API gateway.

## Run

- **With the whole app:** `make up` from the repo root, then open
  http://localhost:8088.
- **With hot reload (Node 20+):** keep `make up` running for the backend,
  then:

  ```bash
  npm install
  npm run dev            # http://localhost:5173, /api proxied to localhost:8080
  ```

| Command | |
|---|---|
| `npm run dev` / `npm run preview` | dev server / serve the built bundle |
| `npm run build` | type-check, then build to `dist/` |
| `npm run lint` | type-check only |
| `npm test` | unit tests (Vitest); they also run in the Docker build |
| `npm run e2e` | Playwright journeys; from the repo root use `make e2e` |

`make e2e` walks every feature on desktop and phone sizes, and fails on any
console error or failed API call. Locally it adds a few diary entries for
today.

To run it against a deployment:

```bash
E2E_BASE_URL=https://<id>.cloudfront.net E2E_EMAIL=... E2E_PASSWORD=... make e2e
```

`E2E_ALLOW_DELETE=1` also runs the account-deletion journey.

## How it's built

- **Data:** TanStack Query for server data. `src/api/types.ts` mirrors the
  Java DTOs.
- **Routing:** React Router gives one URL per screen, so `/reports` is
  linkable. On phones the sidebar becomes a drawer.
- **Styling:** plain CSS (`src/styles/app.css`) ported from the desktop
  client. Charts use Recharts.
- **One origin:** the API is always `/api` on the same host. nginx proxies it
  locally, and the load balancer routes it on AWS. There is no CORS anywhere,
  and the CSP only allows `'self'` plus Cognito.
- **Configuration:** set at build time (`VITE_AUTH_MODE`,
  `VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_CLIENT_ID`; see
  [.env.example](.env.example)). These values are public by design; never put
  a secret in them.

```
src/
  api/          client.ts (fetch, auth, timeouts), cognito.ts + srp.ts (sign-in), session.ts, types.ts
  auth/         AuthContext, LoginView, PasswordField (show/hide + rules)
  components/   Modal, NumberInput, DataTable, Toast, ...
  dialogs/      add food, move/copy, food editor, recipe builder, objective editor
  lib/          nutrition.ts (mirrors the backend maths), format.ts
  views/        one per screen, plus day/, ai/, explore/
```

## Security notes

- **Sign-in** uses SRP ([src/api/srp.ts](src/api/srp.ts)), so the password
  never leaves the browser. It is tested against values from AWS's own
  `amazon-cognito-identity-js`.
- **Tokens:**
  - The access token (1 hour) is kept in memory and mirrored to
    `sessionStorage`, so a page refresh keeps you signed in. That copy is per
    tab and disappears with it.
  - The refresh token (30 days) stays in memory only, and sign-out revokes it.
- **Script injection:** a script running on this page could still read the
  access token. The strict CSP (no inline scripts, no `eval`) and React's
  escaping are what prevent that. Keeping tokens in a server-side session
  behind an httpOnly cookie would remove the risk, but needs an extra backend
  component.
- **Source maps** are not shipped (`VITE_SOURCEMAP=true npm run build` makes
  them for local debugging).
