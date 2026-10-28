import type { ReactNode } from 'react';

import { LanguageSwitch } from '@/components/shell/language-switch';

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative grid min-h-dvh place-items-center bg-background px-4 py-14">
      <LanguageSwitch className="absolute right-4 top-4" />
      <div className="w-full max-w-sm">
        <div className="mb-8 flex items-center justify-center gap-2">
          <span className="grid size-8 place-items-center rounded-md bg-primary text-sm font-bold text-primary-foreground">BP</span>
          <span className="text-base font-semibold">Business Platform</span>
        </div>
        {children}
      </div>
    </div>
  );
}
