'use client';

import { Check } from 'lucide-react';
import { toast } from 'sonner';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { moduleName } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { type Cycle, type Plan, billingApi, daysUntil, useSubscription } from '@/lib/api/billing';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const gb = (bytes: number) => `${(bytes / 1024 ** 3).toFixed(bytes < 1024 ** 3 ? 2 : 1)} GB`;

/** The organisation's plan, usage and the plans it can move to (Owners and the billing role change it). */
export default function BillingPage() {
  const { slug, can, modules } = useOrg();
  const { t, locale } = useT();
  const allowed = can('billing.view');
  const { data: s, error, isPending, refetch } = useSubscription(slug, allowed);
  const [cycle, setCycle] = useState<Cycle | null>(null);
  const change = useOrgMutation(slug, (v: { code: string; cycle: Cycle }) => billingApi.change(slug, v.code, v.cycle));
  const cancel = useOrgMutation(slug, () => billingApi.cancel(slug));
  if (!allowed) return <PermissionDenied />;
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={4} />;
  const c = cycle ?? s.billingCycle;
  const name = (p: Plan) => (locale === 'km' ? p.nameKm : p.nameEn);
  const modName = (key: string) => { const m = modules.find((x) => x.key === key); return m ? moduleName(m) : key; };
  const paid = (p: Plan) => p.priceMonthly > 0 || p.priceYearly > 0;
  const u = s.usage;

  return (
    <>
      <PageHeader title={t('billing.title')} subtitle={t('billing.subtitle')} />
      <section aria-labelledby="cur-h" className="mb-6 grid gap-4 rounded-lg border bg-card p-4 sm:p-6 md:grid-cols-[1fr_1fr]">
        <div>
          <p id="cur-h" className="text-xs text-muted-foreground">{t('billing.current')}</p>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-xl font-semibold">{name(s.plan)}
            <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium">{tr(`billing.status_${s.status}` as MessageKey)}</span></p>
          <p className="mt-1 text-sm text-muted-foreground">
            {s.status === 'TRIALING' ? `${t('billing.trialLeft', { count: daysUntil(s.trialEndsAt) })} · ${t('billing.trialHint')}`
              : s.status === 'COMPLIMENTARY' ? t('billing.complimentaryHint')
                : s.cancelAtPeriodEnd && s.currentPeriodEnd ? t('billing.endsOn', { date: day(s.currentPeriodEnd) })
                  : s.currentPeriodEnd ? t('billing.renews', { date: day(s.currentPeriodEnd) }) : ''}
          </p>
          {s.nextAmount > 0 && s.status !== 'TRIALING' && !s.cancelAtPeriodEnd && (
            <p className="mt-2 text-sm">{t('billing.next', { cycle: tr(`billing.cycle_${s.billingCycle}` as MessageKey), amount: usd(s.nextAmount), members: u.members })}</p>
          )}
          {s.canManage && s.status === 'ACTIVE' && paid(s.plan) && !s.cancelAtPeriodEnd && (
            <Button variant="ghost" size="sm" className="mt-2 px-0 text-destructive" onClick={() => {
              if (window.confirm(t('billing.cancelConfirm'))) cancel.mutateAsync(undefined).then(() => toast.success(t('billing.cancelled'))).catch((e) => toast.error(errorMessage(e)));
            }}>{t('billing.cancel')}</Button>
          )}
        </div>
        <div className="space-y-4">
          <div>
            <p className="mb-1 flex justify-between text-sm"><span>{t('billing.members')}</span>
              <span className="tabular text-muted-foreground">{u.maxMembers ? t('billing.membersOf', { used: u.members, max: u.maxMembers }) : t('billing.membersUnlimited', { used: u.members })}</span></p>
            {u.maxMembers && <Progress value={Math.min(100, (u.members / u.maxMembers) * 100)} aria-label={t('billing.members')} />}
            <p className="mt-1 text-xs text-muted-foreground">{t('billing.guestsFree')}</p>
          </div>
          <div>
            <p className="mb-1 flex justify-between text-sm"><span>{t('billing.storage')}</span>
              <span className="tabular text-muted-foreground">{t('billing.storageOf', { used: gb(u.storageBytes), max: gb(u.storageLimitBytes) })}</span></p>
            <Progress value={u.storageLimitBytes ? Math.min(100, (u.storageBytes / u.storageLimitBytes) * 100) : 100} aria-label={t('billing.storage')} />
          </div>
        </div>
      </section>

      <div className="mb-3 inline-flex rounded-md border p-0.5" role="group" aria-label={t('billing.yearly')}>
        {(['MONTHLY', 'YEARLY'] as Cycle[]).map((x) => (
          <button key={x} type="button" aria-pressed={c === x} onClick={() => setCycle(x)}
            className={cn('rounded px-3 py-1 text-sm', c === x ? 'bg-primary text-primary-foreground' : 'text-muted-foreground')}>{t(x === 'MONTHLY' ? 'billing.monthly' : 'billing.yearly')}</button>
        ))}
      </div>
      <ul className="grid gap-4 md:grid-cols-3">
        {s.plans.map((p) => {
          const mine = p.code === s.plan.code && s.status !== 'TRIALING';
          const price = c === 'YEARLY' ? p.priceYearly : p.priceMonthly;
          return (
            <li key={p.code} className={cn('flex flex-col rounded-lg border bg-card p-4', p.code === s.plan.code && 'border-primary')}>
              <h2 className="text-base font-semibold">{name(p)}</h2>
              <p className="mt-1 text-lg font-semibold tabular">{paid(p) ? t('billing.perMember', { price: usd(price), cycle: tr(`billing.cycle_${c}` as MessageKey) }) : t('billing.free')}</p>
              <ul className="mt-3 flex-1 space-y-1.5 text-sm">
                <li className="flex gap-2"><Check className="mt-0.5 size-4 shrink-0 text-success" />{p.maxMembers ? t('billing.upTo', { count: p.maxMembers }) : t('billing.unlimitedMembers')}</li>
                <li className="flex gap-2"><Check className="mt-0.5 size-4 shrink-0 text-success" />{t('billing.storageGb', { count: p.storageGb })}</li>
                <li className="flex gap-2"><Check className="mt-0.5 size-4 shrink-0 text-success" />{t('billing.includes', { modules: p.modules.map(modName).join(', ') })}</li>
              </ul>
              {mine ? <p className="mt-4 text-sm font-medium text-primary">{t('billing.yourPlan')}</p>
                : s.canManage && s.status !== 'COMPLIMENTARY' && (
                  <Button className="mt-4" variant={paid(p) ? 'default' : 'outline'} disabled={change.isPending} onClick={() => {
                    if (!window.confirm(t('billing.chooseConfirm', { name: name(p) }))) return;
                    change.mutateAsync({ code: p.code, cycle: c }).then(() => toast.success(t('billing.changed', { name: name(p) }))).catch((e) => toast.error(errorMessage(e)));
                  }}>{t('billing.choose', { name: name(p) })}</Button>
                )}
            </li>
          );
        })}
      </ul>
      {!s.canManage && <p className="mt-4 text-sm text-muted-foreground">{t('billing.ownersOnly')}</p>}
    </>
  );
}
