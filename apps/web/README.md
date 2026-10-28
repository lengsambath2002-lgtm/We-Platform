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

## Languages (English and Khmer)

- Text lives in `src/i18n/messages/<area>.ts`, one `en` and one `km` object per area; `km` is typed from `en`, so a missing Khmer line fails `pnpm typecheck`.
- In components: `const { t } = useT(); t('tasks.newTask')`; outside React: `tr(...)`. Placeholders `{name}`; English plurals use a `<key>_one` variant with `{ count }`.
- Dates go through `lib/format.ts` (`fmt`, `day`, `ago`, `dueLabel`) so they follow the language.
- The language is the account's (`/me` locale) once signed in, otherwise the saved or browser language; the switch is on sign-in, in the user menu and on the account page. Every API request sends `Accept-Language`, and the API answers its error messages in that language.

Permissions shown in the UI come from the server, which re-checks every request.
