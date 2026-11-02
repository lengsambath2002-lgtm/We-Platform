'use client';

import type { ReactNode } from 'react';

import { SettingsFrame } from '@/components/shell/settings-frame';

/** Every administration page sits in the settings area. */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return <SettingsFrame>{children}</SettingsFrame>;
}
