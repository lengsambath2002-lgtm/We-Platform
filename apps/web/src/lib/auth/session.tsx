'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { toLocale } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { http, refreshSession, setAccessToken, setSignedOutHandler } from '@/lib/api/client';
import type { Session, User } from '@/lib/api/types';

interface SessionState {
  user: User | null;
  ready: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (name: string, email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  setUser: (u: User) => void;
  /** Adopts a session the API just issued (e.g. after resetting the password). */
  startSession: (s: Session) => void;
}

const Ctx = createContext<SessionState | null>(null);

/** Restores the session from the refresh cookie on load; keeps the user in memory. */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const { setLocale } = useT();

  // The account's language wins once signed in.
  useEffect(() => { if (user) setLocale(toLocale(user.locale)); }, [user, setLocale]);

  useEffect(() => {
    setSignedOutHandler(() => {
      setAccessToken(null);
      setUser(null);
    });
    refreshSession()
      .then(async (token) => {
        if (token) setUser(await http.get<User>('/me'));
      })
      .catch(() => setUser(null))
      .finally(() => setReady(true));
  }, []);

  const start = useCallback((s: Session) => {
    setAccessToken(s.accessToken);
    setUser(s.user);
  }, []);

  const value = useMemo<SessionState>(
    () => ({
      user,
      ready,
      setUser,
      startSession: start,
      signIn: async (email, password) => start(await http.post<Session>('/auth/sign-in', { email, password })),
      signUp: async (name, email, password) =>
        start(await http.post<Session>('/auth/sign-up', { name, email, password })),
      signOut: async () => {
        await http.post('/auth/sign-out').catch(() => undefined);
        setAccessToken(null);
        setUser(null);
      },
    }),
    [user, ready, start],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession() {
  const s = useContext(Ctx);
  if (!s) throw new Error('useSession outside SessionProvider');
  return s;
}
