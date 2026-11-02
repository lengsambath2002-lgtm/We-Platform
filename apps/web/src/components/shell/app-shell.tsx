'use client';

import { Fragment, type ReactNode } from 'react';

import { VerifyEmailNotice } from '@/components/account/verify-email-notice';
import { TooltipProvider } from '@/components/ui/tooltip';
import type { OrgContext } from '@/lib/api/types';

import { BillingBanner } from './billing-banner';
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
          <main id="main" className="mx-auto w-full max-w-[1440px] flex-1 px-4 pb-24 pt-6 sm:px-6 lg:pb-10 print:max-w-none print:p-0">
            {/* Keys: the banners appear and disappear above the page, which must keep its state. */}
            <VerifyEmailNotice key="verify-email" />
            <BillingBanner key="billing" />
            <Fragment key="page">{children}</Fragment>
          </main>
        </div>
      </div>
      <BottomNav ctx={ctx} />
    </TooltipProvider>
  );
}
