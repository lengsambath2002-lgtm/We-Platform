'use client';

import type { ReactNode } from 'react';

import { TooltipProvider } from '@/components/ui/tooltip';
import type { OrgContext } from '@/lib/api/types';

import { BottomNav } from './bottom-nav';
import { Header } from './header';
import { Sidebar } from './sidebar';
import { useCollapsed } from './use-collapsed';

export function AppShell({ ctx, children }: { ctx: OrgContext; children: ReactNode }) {
  const [collapsed, setCollapsed] = useCollapsed();
  return (
    <TooltipProvider delayDuration={200}>
      <div className="flex min-h-dvh">
        <Sidebar ctx={ctx} collapsed={collapsed} setCollapsed={setCollapsed} />
        <div className="flex min-w-0 flex-1 flex-col">
          <Header ctx={ctx} />
          <main id="main" className="mx-auto w-full max-w-[1440px] flex-1 px-4 pb-24 pt-6 sm:px-6 lg:pb-10">{children}</main>
        </div>
      </div>
      <BottomNav ctx={ctx} />
    </TooltipProvider>
  );
}
