# Business Platform — web

Next.js 15 (App Router) + TypeScript, Tailwind v4, shadcn/ui, TanStack Query, React Hook Form + Zod.

```bash
pnpm install
pnpm dev            # http://localhost:3300 — /api/* is proxied to API_URL (default http://localhost:8280)
pnpm typecheck
pnpm lint
pnpm test:e2e       # Playwright journeys; start the API (apps/api: ./gradlew bootRun) and `pnpm dev` first
```

- `src/app/[org]/…` — pages inside an organisation (overview, my tasks, projects, tasks, approvals, calendar, teams, admin, `m/[module]` for modules not built yet).
- `src/lib/api` — fetch client (access token in memory, one refresh retry), response types, query hooks and mutations.
- `src/components` — shell (sidebar, header, bottom nav), shared states (loading / empty / error / no access), and feature components.

The UI is English for now; Khmer screens are still to be translated. Permissions shown in the UI come from the server, which re-checks every request.
