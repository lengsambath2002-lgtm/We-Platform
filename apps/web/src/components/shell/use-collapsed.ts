'use client';

import { useCallback, useSyncExternalStore } from 'react';

const KEY = 'bp-sidebar-collapsed';
const EVENT = 'bp-sidebar';

function read() {
  try {
    return localStorage.getItem(KEY) === '1';
  } catch {
    return false;
  }
}

/** The sidebar's collapsed state, remembered on this device. */
export function useCollapsed(): [boolean, (v: boolean) => void] {
  const value = useSyncExternalStore(
    (cb) => {
      window.addEventListener(EVENT, cb);
      return () => window.removeEventListener(EVENT, cb);
    },
    read,
    () => false,
  );
  const set = useCallback((v: boolean) => {
    try {
      localStorage.setItem(KEY, v ? '1' : '0');
    } catch {
      /* storage blocked: keep it for this page only */
    }
    window.dispatchEvent(new Event(EVENT));
  }, []);
  return [value, set];
}
