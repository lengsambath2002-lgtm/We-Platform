'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { type SubscriptionBrief, billingApi, usePlatformPlans } from '@/lib/api/billing';
import { errorMessage } from '@/lib/api/client';
import { day } from '@/lib/format';

const STATUSES = ['TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'COMPLIMENTARY'] as const;
const dateOnly = (iso: string | null) => (iso ? iso.slice(0, 10) : '');
const toInstant = (d: string) => (d ? new Date(`${d}T00:00:00+07:00`).toISOString() : null);

/** Super admins: an organisation's plan as agreed with them (contract, complimentary, longer trial …). */
export function SubscriptionCard({ orgId, sub }: { orgId: string; sub: SubscriptionBrief | null }) {
  const { t } = useT();
  const qc = useQueryClient();
  const plans = usePlatformPlans();
  const [editing, setEditing] = useState(false);
  const [v, setV] = useState({
    planCode: sub?.planCode ?? 'FREE', status: sub?.status ?? 'ACTIVE', cycle: sub?.billingCycle ?? 'MONTHLY',
    trialEndsAt: dateOnly(sub?.trialEndsAt ?? null), periodEnd: dateOnly(sub?.currentPeriodEnd ?? null), note: sub?.note ?? '',
  });
  const [busy, setBusy] = useState(false);
  return (
    <section className="rounded-xl border bg-card p-5 shadow-xs" aria-labelledby="sub-h">
      <div className="mb-3 flex items-center gap-2">
        <h2 id="sub-h" className="flex-1 text-base font-semibold">{t('platform.subscription')}</h2>
        {!editing && <Button variant="outline" size="sm" onClick={() => setEditing(true)}>{t('common.edit')}</Button>}
      </div>
      {!editing ? (
        sub ? (
          <dl className="grid gap-3 text-sm sm:grid-cols-3">
            <div className="rounded-lg border bg-muted/30 p-3"><dt className="text-xs text-muted-foreground">{t('platform.subPlan')}</dt><dd className="mt-1 text-base font-semibold">{sub.planCode}</dd></div>
            <div className="rounded-lg border bg-muted/30 p-3"><dt className="text-xs text-muted-foreground">{t('platform.subStatus')}</dt><dd className="mt-1 font-medium">{tr(`billing.status_${sub.status}` as MessageKey)} · {tr(`billing.cycle_${sub.billingCycle}` as MessageKey)}</dd></div>
            <div className="rounded-lg border bg-muted/30 p-3"><dt className="text-xs text-muted-foreground">{t('platform.subDates')}</dt><dd className="mt-1 font-medium">{sub.trialEndsAt ? t('platform.subTrialTo', { date: day(sub.trialEndsAt) }) : sub.currentPeriodEnd ? t('platform.subPeriodTo', { date: day(sub.currentPeriodEnd) }) : '—'}</dd></div>
            {sub.note && <div className="sm:col-span-3"><dt className="text-xs text-muted-foreground">{t('platform.subNote')}</dt><dd className="mt-1">{sub.note}</dd></div>}
          </dl>
        ) : <p className="text-sm text-muted-foreground">—</p>
      ) : (
        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="space-y-1.5"><Label htmlFor="sub-plan">{t('platform.subPlan')}</Label>
              <Select value={v.planCode} onValueChange={(x) => setV({ ...v, planCode: x })}><SelectTrigger id="sub-plan" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{(plans.data ?? []).map((p) => <SelectItem key={p.code} value={p.code}>{p.nameEn}</SelectItem>)}</SelectContent></Select></div>
            <div className="space-y-1.5"><Label htmlFor="sub-status">{t('platform.subStatus')}</Label>
              <Select value={v.status} onValueChange={(x) => setV({ ...v, status: x as typeof v.status })}><SelectTrigger id="sub-status" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{STATUSES.map((s) => <SelectItem key={s} value={s}>{tr(`billing.status_${s}` as MessageKey)}</SelectItem>)}</SelectContent></Select></div>
            <div className="space-y-1.5"><Label htmlFor="sub-cycle">{t('platform.subCycle')}</Label>
              <Select value={v.cycle} onValueChange={(x) => setV({ ...v, cycle: x as typeof v.cycle })}><SelectTrigger id="sub-cycle" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value="MONTHLY">{t('billing.monthly')}</SelectItem><SelectItem value="YEARLY">{t('billing.yearly')}</SelectItem></SelectContent></Select></div>
            {v.status === 'TRIALING' && <div className="space-y-1.5"><Label htmlFor="sub-trial">{t('platform.subTrialEnds')}</Label><Input id="sub-trial" type="date" value={v.trialEndsAt} onChange={(e) => setV({ ...v, trialEndsAt: e.target.value })} /></div>}
            <div className="space-y-1.5"><Label htmlFor="sub-period">{t('platform.subPeriodEnds')}</Label><Input id="sub-period" type="date" value={v.periodEnd} onChange={(e) => setV({ ...v, periodEnd: e.target.value })} /></div>
          </div>
          <div className="space-y-1.5"><Label htmlFor="sub-note">{t('platform.subNote')}</Label><Input id="sub-note" maxLength={500} value={v.note} onChange={(e) => setV({ ...v, note: e.target.value })} /></div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setEditing(false)}>{t('common.cancel')}</Button>
            <Button disabled={busy} onClick={async () => {
              setBusy(true);
              try {
                await billingApi.setSubscription(orgId, { planCode: v.planCode, status: v.status, cycle: v.cycle, trialEndsAt: toInstant(v.trialEndsAt), currentPeriodEnd: toInstant(v.periodEnd), note: v.note.trim() || null });
                await qc.invalidateQueries({ queryKey: ['platform'] });
                toast.success(t('platform.subSaved'));
                setEditing(false);
              } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
            }}>{t('common.save')}</Button>
          </div>
        </div>
      )}
    </section>
  );
}
