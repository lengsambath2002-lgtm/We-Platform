# Business Platform (working name)

One multi-industry business management platform: shared core (identity, organisations, roles,
projects & tasks, approvals, calendar, notifications) and industry modules (General Business,
Education, Retail & Commerce, HR & Workforce, Service & Operations) that each organisation switches on.

| Path | What |
|---|---|
| `apps/api` | Spring Boot 3, Java 21, PostgreSQL, Flyway, REST + OpenAPI (`/api/docs/ui`) |
| `apps/web` | Next.js (App Router, TypeScript), Tailwind, shadcn/ui, Lucide, TanStack Query, React Hook Form + Zod |
| `docs/` | 01 discovery · 02 architecture · 03 design system |

## Run locally

```sh
# database (once): a role and two databases on your PostgreSQL
createuser bizplat -P   # password bizplat
createdb -O bizplat business_platform && createdb -O bizplat business_platform_test

cd apps/api && ./gradlew bootRun        # http://localhost:8280 (DATABASE_URL to change)
cd apps/web && pnpm install && pnpm dev # http://localhost:3300
```

Tests: `cd apps/api && ./gradlew test` (uses `business_platform_test`, wiped each run;
`TEST_DATABASE_URL` to change) · `cd apps/web && pnpm typecheck && pnpm lint && pnpm test:e2e`
(Playwright, with the API and web app running).

## Run with Docker

```sh
cp .env.example .env          # set DATABASE_PASSWORD and PLATFORM_JWT_SECRET (openssl rand -base64 48)
docker compose up --build     # web http://localhost:3300 · API docs http://localhost:8280/api/docs/ui
```

Images: `apps/api/Dockerfile` (JRE, non-root, uploads in the `/data/files` volume) and
`apps/web/Dockerfile` (Next.js standalone, non-root; `API_URL` is a build argument because the
`/api` rewrite is compiled in).

**Production**: set `PLATFORM_ENV=production`. The API then refuses to start unless
`PLATFORM_JWT_SECRET` is your own, `PLATFORM_COOKIE_SECURE=true` and `PLATFORM_APP_URL` is `https://…`.

**Email** (invitations, "forgot password", "password changed"): set `PLATFORM_MAIL_HOST`,
`PLATFORM_MAIL_PORT` (default 587, or 465 with `ssl`), `PLATFORM_MAIL_USERNAME`,
`PLATFORM_MAIL_PASSWORD`, `PLATFORM_MAIL_FROM` (e.g. `Business Platform <no-reply@your-domain>`) and
`PLATFORM_MAIL_SECURITY` (`starttls` default, `ssl` or `none`). Emails go out in the background after
the request commits, in the recipient's language; a failure is logged and never fails the request.
Without a host, emails are only logged (with their link only when `PLATFORM_ENV=local`); production
then starts with a warning, since invitation links can still be copied but password resets can't
reach anyone.

## CI

`.github/workflows/ci.yml` runs on every push to `main` and every pull request: API tests against
PostgreSQL, web typecheck + lint + build, the Playwright journeys against the built API and web app,
and a build of both Docker images (not pushed).

Existing systems (WeGroupware, VirtualBiz) are **not modified**; they will be integrated later only
through their current APIs.
