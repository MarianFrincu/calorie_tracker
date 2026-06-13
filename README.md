# Calorie Tracker

A microservices calorie-tracking application: **JavaFX desktop client →
Spring Cloud Gateway → (ai-service · backend-core) → PostgreSQL**, with
Eureka discovery, AWS Cognito security, an AWS Bedrock AI feature,
Docker/Compose for local dev, and CloudFormation for AWS deployment.

The desktop client is a real product: per-day diary with 4 meals + water,
custom ingredients + recipes, AI-assisted parsing with library
cross-checking, calorie/macro objectives with historical snapshots, weight
tracking with smooth-curve charts, weekly/monthly reports, food comparison,
advanced rule-based food search, and a Cognito sign-in/register flow for
cloud mode.

## Repository layout

| Path | What |
|---|---|
| [eureka-server/](eureka-server/) | Service registry (port 8761) |
| [api-gateway/](api-gateway/) | Spring Cloud Gateway (port 8080) — single public entry, JWT at the edge |
| [backend-core/](backend-core/) | CRUD + business logic (port 8081) → PostgreSQL |
| [ai-service/](ai-service/) | Stateless AI parser (port 8082) → optional AWS Bedrock |
| [desktop-client/](desktop-client/) | JavaFX client (REST only, never touches the DB) |
| [docker-compose.yml](docker-compose.yml) + [override](docker-compose.override.yml) | Local orchestration |
| [infra/cloudformation/](infra/cloudformation/) | 5 CloudFormation stacks (foundation, database, cognito, app, ci/cd) |
| [infra/buildspec.yml](infra/buildspec.yml) | Reference CodeBuild buildspec (mirrored inline in `05-cicd.yaml`) |

## Tech

Java 21 · Spring Boot 3.4.1 · Spring Cloud 2024.0.0 · Spring AI 1.0.0 (Bedrock
Converse) · PostgreSQL 16 · Flyway · JavaFX 23.0.1 · Docker / Compose · AWS
(ECS Fargate, RDS, Cognito, ALB, Secrets Manager, ECR, CloudFormation,
CodePipeline + CodeBuild for CI/CD).

> Builds run inside Docker (Maven JDK 21 → JRE 21), so your host JDK version
> doesn't matter for the backend. For the desktop client itself, you need
> JDK 21 (anything newer breaks JavaFX 23 on Linux at the moment).

## Run locally (60 seconds from clone)

```bash
docker compose up --build -d
```

That starts five containers — postgres, eureka-server, ai-service,
backend-core, api-gateway — wired up via healthchecks so the boot order is
correct.

- Eureka dashboard: <http://localhost:8761>
- Gateway: <http://localhost:8080> (e.g. `curl http://localhost:8080/api/profile`)

Run the desktop client in a separate terminal:

```bash
mvn -f desktop-client/pom.xml javafx:run
```

In the default override mode, the backend runs under the `dev` Spring profile
(security off) so the client doesn't need a login. The first call lazily
creates a single shared user `dev-user`, and all your data persists in
Postgres until you `docker compose down -v`.

## Three security modes

The same code runs under three profiles. The only difference is how the
gateway/backend-core/ai-service validate JWTs.

| Profile | Behavior | When to use |
|---|---|---|
| `dev` | auth OFF | local desktop development (compose default via override) |
| `localjwt` | validate JWT vs baked-in RSA key | test the security layer offline — `docker compose -f docker-compose.yml up` |
| `cognito` | validate JWT vs AWS Cognito (issuer-uri) | cloud (set `COGNITO_ISSUER_URI`) |

Roles come from the Cognito `cognito:groups` claim (`USER` / `ADMIN`);
machine-to-machine tokens authorize via OAuth2 scopes
(`foods.read` → ROLE_USER, `foods.write` → ROLE_ADMIN).

## Features at a glance

- **Day view**: per-day diary across Breakfast / Lunch / Dinner / Snack, with
  per-meal kcal + macros + the right-side TODAY card (kcal target + 4 macro
  bars), water tracking with one-tap +/- chips.
- **Move / Copy** any diary row to another meal or another date via the `⋯`
  menu — historical days never re-mutate.
- **Foods & Recipes**: search your library (or the public seed set); fuzzy
  fallback so "zucini" still finds "Zucchini"; build recipes with a live
  macros total + `kcal ≈ 4·P + 4·C + 9·F ± 10` consistency check.
- **AI tab**: free-text → structured nutrition. *"2 eggs and toast with
  butter"* → individual rows with calories + macros + **Add all to diary**;
  or *"chicken 200g and rice 150g"* → an editable recipe blueprint you can
  save with one click. Each parsed item is cross-checked against the
  database and shows a coloured badge: *"in app library"* (blue), *"in my
  library"* (green), or *"not in any library — AI estimate"* (amber) with
  a one-click **Save to my library** button.
- **Explore tab**:
  - **Compare** — pick 2-4 foods/recipes side-by-side, table + grouped bar
    chart per macro/calories.
  - **Advanced search** — rule-based food filter; rules combine via AND/OR
    groups that nest up to 4 deep.
- **Profile**: body stats + live preview of BMR (Mifflin-St Jeor) and TDEE
  (BMR × activity multiplier). The weight field is linked to the Weight tab
  in both directions.
- **Objective**: read-only summary + modal editor (±10/15/20/25/30%
  intensity, three macro presets BALANCED / MAINTAIN_MUSCLE / KETOGENIC).
  Saving snapshots today's objective so yesterday stays unchanged.
- **Weight**: 1-decimal precision, table + smooth monotone-cubic chart over
  time; saving today's weight also patches `profile.weightKg`.
- **Reports**: monotone-cubic AreaChart per metric (calories, protein, carbs,
  fat, fiber, water) for the current calendar week or month. Day-specific
  historical targets are drawn as a dashed red curve that flexes to match
  the value in effect on each day.
- **Login / Register / Logout**: TabPane in cloud mode — Sign-in
  (USER_PASSWORD_AUTH), Register (SignUp + ConfirmSignUp via email code),
  and a Sign-out button.

## AI feature

`POST /api/ai/parse` and `POST /api/ai/parse-recipe` (served by
**ai-service**) turn free-text into structured macros. Provider is
swappable via the `CALORIETRACKER_AI_PROVIDER` env var:

- `mock` (default, offline, deterministic — for local + CI)
- `bedrock` (Spring AI + AWS Bedrock Converse, `bedrock` profile)

Persisting an AI-built recipe goes through **backend-core**
(`POST /api/recipes/from-ai`) because only that service owns the DB —
ai-service is intentionally stateless.