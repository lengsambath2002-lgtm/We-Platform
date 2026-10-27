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

Tests: `cd apps/api && ./gradlew test` (uses `business_platform_test`, wiped each run).

Existing systems (WeGroupware, VirtualBiz) are **not modified**; they will be integrated later only
through their current APIs.
