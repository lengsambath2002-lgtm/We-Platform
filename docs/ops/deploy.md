# Deploy: API on Render, web app on Vercel

The same setup as VirtualBiz. The API and its PostgreSQL run on Render (Singapore, `render.yaml`); the web app runs
on Vercel and forwards `/api/*` to the API, so the sign-in cookie stays on the web app's own address.

## 1. Render: API and database

1. render.com → **New → Blueprint** → connect GitHub → `lengsambath2002-lgtm/We-Platform` → `render.yaml`.
2. Render asks for the `sync: false` values:
   - `PLATFORM_APP_URL`: the web app's address, e.g. `https://we-platform.vercel.app` (pick the Vercel project name
     now and use it in step 2; it must start with `https://`, or the API refuses to start in production).
   - `PLATFORM_SUPER_ADMINS`: your email.
   - Mail: `smtp.gmail.com`, `587`, the Gmail address, its app password, `Business Platform <that address>`;
     `PLATFORM_MAIL_ONLY_TO` empty to email everyone.
3. Create. The first build takes ~10 minutes. The API is at `https://business-platform-api.onrender.com` (Render shows
   the exact address if the name was taken); `…/actuator/health` answers `{"status":"UP"}`.

## 2. Vercel: web app

1. vercel.com → **Add New → Project** → import `We-Platform`.
2. **Root Directory**: `apps/web`. Framework: Next.js (found by itself).
3. **Environment Variables** (Production and Preview):

   | Name | Value |
   | --- | --- |
   | `API_URL` | the Render address from step 1, without a trailing `/` |
   | `ENABLE_EXPERIMENTAL_COREPACK` | `1` (builds with the pnpm version in `package.json`) |
   | `NEXT_PUBLIC_FIREBASE_API_KEY`, `…_AUTH_DOMAIN`, `…_PROJECT_ID`, `…_APP_ID` | as in `docs/ops/firebase.md` |
   | `NEXT_PUBLIC_LEGAL_NAME`, `…_ADDRESS`, `…_EMAIL` | who runs the service (Terms and Privacy pages) |

4. Deploy. If the address Vercel gives differs from `PLATFORM_APP_URL`, change it on Render (Environment) and
   Render redeploys.

## 3. After the first deploy

- Firebase console → Authentication → Settings → **Authorized domains**: add the Vercel address, or Google sign-in
  fails there.
- Sign up with the super admin email, open the emailed link, and check **Platform** in the account menu.
- Restrict the Firebase API key to the Vercel address (Google Cloud → Credentials).

Every push to `main` redeploys both (Render and Vercel watch the repository).

## Limits of the free plans

Fine for a demo, not for customers' real data:

| Free plan | What happens | For production |
| --- | --- | --- |
| Render web service | Sleeps after 15 minutes idle; the next request waits ~1 minute | Starter plan or higher |
| Render disk | None: **uploaded files are lost** at every deploy or restart | A paid plan with a disk mounted at `/data/files` |
| Render PostgreSQL | **Expires 30 days after creation**, no backups | A paid database (daily backups, point-in-time recovery) |

The `backup` service of docker-compose (`docs/ops/backups.md`) doesn't run on Render; a paid Render database has its
own backups.
