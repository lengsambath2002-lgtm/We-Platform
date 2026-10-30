'use client';

import Link from 'next/link';

import { useT } from '@/i18n/provider';
import { daysUntil, useSubscription } from '@/lib/api/billing';
import { useOrg } from '@/lib/org-context';

/** For people who handle billing: the trial's last week, or a late payment. */
export function BillingBanner() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const allowed = can('billing.manage');
  const { data } = useSubscription(slug, allowed);
  if (!allowed || !data) return null;
  const days = daysUntil(data.trialEndsAt);
  const trialEnding = data.status === 'TRIALING' && days <= 7;
  if (!trialEnding && data.status !== 'PAST_DUE') return null;
  return (
    <div role="status" className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
      <span className="flex-1">{trialEnding ? t('billing.bannerTrial', { count: days }) : t('billing.bannerPastDue')}</span>
      <Link href={`/${slug}/admin/billing`} className="font-medium underline">{t('billing.bannerAction')}</Link>
    </div>
  );
}
