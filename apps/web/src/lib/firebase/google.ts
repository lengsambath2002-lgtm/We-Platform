// Sign-in with Google through Firebase Authentication (docs/ops/firebase.md). Firebase only proves who signed in:
// the ID token goes to POST /api/auth/firebase, which returns a normal Business Platform session.

const config = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

/** Shown only when the web app has the Firebase config. */
export const googleEnabled = () => !!(config.apiKey && config.authDomain && config.projectId);

/** A Firebase error code (`auth/popup-closed-by-user` …), or null. */
export function firebaseCode(e: unknown): string | null {
  const code = (e as { code?: unknown } | null)?.code;
  return typeof code === 'string' && code.startsWith('auth/') ? code : null;
}

/** Google's account chooser in a popup; the Firebase ID token. The Firebase session ends at once (ours takes over). */
export async function googleIdToken(locale: string): Promise<string> {
  const [{ getApps, initializeApp }, m] = await Promise.all([import('firebase/app'), import('firebase/auth')]);
  const app = getApps()[0] ?? initializeApp(config);
  const auth = m.getAuth(app);
  auth.languageCode = locale === 'km' ? 'km' : 'en';
  const provider = new m.GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  const cred = await m.signInWithPopup(auth, provider);
  const token = await cred.user.getIdToken();
  await m.signOut(auth).catch(() => undefined);
  return token;
}
