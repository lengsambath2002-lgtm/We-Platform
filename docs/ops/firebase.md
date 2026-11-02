# Sign-in with Google (Firebase)

"Continue with Google" on the sign-in and sign-up pages uses Firebase Authentication, in the same Firebase project
as VirtualBiz (**`virtaulbiz`**), so people use one Google account for both apps.

Firebase only proves who signed in. The web app opens Google's account chooser (`signInWithPopup`), sends the
Firebase ID token to `POST /api/auth/firebase`, and gets a normal Business Platform session; the Firebase session is
ended at once. The API checks the token itself, as Firebase documents (Google's public keys, issuer
`https://securetoken.google.com/virtaulbiz`, audience `virtaulbiz`, expiry): no Admin SDK, no service-account file.

| Case | What happens |
| --- | --- |
| New email | A new account: Google's name and photo, email confirmed, Terms version recorded, no password until "Forgot password" |
| An account already uses that email | Linked on first use (`app_user.firebase_uid`); the password keeps working too |
| Two-step sign-in on | The code is still asked after Google |
| Email not confirmed by Google | Refused (`EMAIL_NOT_VERIFIED`) |
| The account is linked to another Google account | Refused (`CONFLICT`) |
| Disabled or deleted account | Refused, as with a password |

Passwords stay in Business Platform (lockout, two-step sign-in, email verification), not in Firebase.

This is not single sign-on between the two apps by itself: each keeps its own session. Business Platform is the
single sign-on provider for VirtualBiz through OpenID Connect ("Sign in with Business Platform", see the README).

## Set up

1. Firebase console → **Authentication → Sign-in method**: **Google** on (VirtualBiz already turned it on).
2. **Authentication → Settings → Authorized domains**: add every domain the web app runs on (`localhost` is there).
3. API: `PLATFORM_FIREBASE_PROJECT_ID=virtaulbiz`. Empty turns Google sign-in off (`NOT_FOUND`).
4. Web (compiled in; `apps/web/.env.local` locally, `FIREBASE_*` build args with docker-compose): from **Project
   settings → General → Your apps → Web app**:
   ```
   NEXT_PUBLIC_FIREBASE_API_KEY=…
   NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=virtaulbiz.firebaseapp.com
   NEXT_PUBLIC_FIREBASE_PROJECT_ID=virtaulbiz
   NEXT_PUBLIC_FIREBASE_APP_ID=…
   ```
   These values are public by design. Restrict the API key to your domains in Google Cloud → Credentials.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| No "Continue with Google" button | The web app was built without `NEXT_PUBLIC_FIREBASE_*` |
| "Google sign-in isn't available right now" | Google provider off, or this domain isn't in Authorized domains |
| "The browser blocked the Google window" | Pop-ups blocked for the site |
| `INVALID_TOKEN` from `/api/auth/firebase` | The web app and the API point at different Firebase projects |
