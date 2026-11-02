'use client';

import { Check, Clock, EyeOff, HardDrive, Pencil, Plus, Users } from 'lucide-react';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/states';
import { ConsoleHeader } from '@/components/platform/ui';
import { moduleName } from '@/components/shell/nav';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { type Plan, billingApi, useModuleCatalogue, usePlatformPlans } from '@/lib/api/billing';
import { errorMessage } from '@/lib/api/client';
import { usd } from '@/lib/money';

function PlanDialog({ plan, onClose }: { plan: Plan | null; onClose: () => void }) {
  const { t } = useT();
  const qc = useQueryClient();
  const catalogue = useModuleCatalogue();
  const [v, setV] = useState({
    code: plan?.code ?? '', nameEn: plan?.nameEn ?? '', nameKm: plan?.nameKm ?? '', priceMonthly: String(plan?.priceMonthly ?? 0), priceYearly: String(plan?.priceYearly ?? 0),
    maxMembers: plan?.maxMembers == null ? '' : String(plan.maxMembers), storageGb: String(plan?.storageGb ?? 1), trialDays: String(plan?.trialDays ?? 0),
    sortOrder: String(plan?.sortOrder ?? 40), publicPlan: plan?.publicPlan ?? true, modules: new Set(plan?.modules ?? ['projects']),
  });
  const [busy, setBusy] = useState(false);
  const field = (k: 'code' | 'nameEn' | 'nameKm' | 'priceMonthly' | 'priceYearly' | 'maxMembers' | 'storageGb' | 'trialDays' | 'sortOrder', label: string, disabled = false) => (
    <div className="space-y-1.5"><Label htmlFor={`pl-${k}`}>{label}</Label><Input id={`pl-${k}`} disabled={disabled} value={v[k]} onChange={(e) => setV({ ...v, [k]: e.target.value })} /></div>
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{plan ? t('platform.planEdit') : t('platform.planNew')}</DialogTitle><DialogDescription>{t('platform.planBody')}</DialogDescription></DialogHeader>
        <div className="grid gap-3 sm:grid-cols-2">
          {field('code', t('platform.planCode'), !!plan)}
          {field('sortOrder', t('platform.planOrder'))}
          {field('nameEn', t('platform.planNameEn'))}
          {field('nameKm', t('platform.planNameKm'))}
          {field('priceMonthly', t('platform.planMonthly'))}
          {field('priceYearly', t('platform.planYearly'))}
          {field('maxMembers', t('platform.planMembers'))}
          {field('storageGb', t('platform.planStorage'))}
          {field('trialDays', t('platform.planTrial'))}
          <label className="flex items-center gap-2 self-end pb-2 text-sm"><Checkbox checked={v.publicPlan} onCheckedChange={(x) => setV({ ...v, publicPlan: x === true })} />{t('platform.planPublic')}</label>
        </div>
        <fieldset className="space-y-1.5">
          <legend className="mb-1 text-sm font-medium">{t('platform.colModules')}</legend>
          {(catalogue.data ?? []).map((m) => (
            <label key={m.key} className="flex items-center gap-2 text-sm"><Checkbox checked={v.modules.has(m.key)} onCheckedChange={(x) => {
              const n = new Set(v.modules); if (x) n.add(m.key); else n.delete(m.key); setV({ ...v, modules: n });
            }} />{moduleName(m)}</label>
          ))}
        </fieldset>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={busy} onClick={async () => {
            setBusy(true);
            try {
              await billingApi.savePlan(v.code.trim().toUpperCase(), {
                nameEn: v.nameEn.trim(), nameKm: v.nameKm.trim(), priceMonthly: v.priceMonthly, priceYearly: v.priceYearly,
                maxMembers: v.maxMembers.trim() ? Number(v.maxMembers) : null, storageGb: Number(v.storageGb), modules: [...v.modules],
                trialDays: Number(v.trialDays), publicPlan: v.publicPlan, sortOrder: Number(v.sortOrder),
              });
              await qc.invalidateQueries({ queryKey: ['platform'] });
              toast.success(t('platform.planSaved'));
              onClose();
            } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Super admins: the plans companies can choose: prices per member, limits, included modules, trial. */
export default function PlansAdmin() {
  const { t } = useT();
  const { data, error, isPending, refetch } = usePlatformPlans();
  const catalogue = useModuleCatalogue();
  const [editing, setEditing] = useState<Plan | 'new' | null>(null);
  const name = (key: string) => {
    const m = catalogue.data?.find((x) => x.key === key);
    return m ? moduleName(m) : key;
  };
  return (
    <>
      <ConsoleHeader title={t('platform.navPlans')} subtitle={t('platform.plansLead')}
        actions={<Button onClick={() => setEditing('new')}><Plus className="size-4" />{t('platform.planNew')}</Button>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? (
        <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-96 rounded-xl" />)}</div>
      ) : (
        <div className="grid items-stretch gap-6 md:grid-cols-2 xl:grid-cols-3">
          {data.map((p) => (
            <article key={p.code} className="group flex flex-col rounded-xl border bg-card p-6 shadow-xs transition-shadow hover:shadow-md">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="truncate text-lg font-semibold">{p.nameEn}</h2>
                  <p className="mt-0.5 font-mono text-xs text-muted-foreground">{p.code}</p>
                </div>
                <div className="flex flex-wrap justify-end gap-1">
                  {!p.publicPlan && <Badge variant="outline" className="gap-1"><EyeOff />{t('platform.planHidden')}</Badge>}
                  {p.trialDays > 0 && <Badge variant="outline" className="border-primary/30 bg-accent/50 text-accent-foreground">{t('platform.planTrialDays', { count: p.trialDays })}</Badge>}
                </div>
              </div>
              <div className="mt-6">
                <p className="flex items-baseline gap-1.5"><span className="text-4xl font-semibold tracking-tight tabular">{usd(p.priceMonthly)}</span><span className="text-sm text-muted-foreground">{t('platform.perMemberMonth')}</span></p>
                <p className="mt-1 text-sm text-muted-foreground tabular">{t('platform.perMemberYear', { amount: usd(p.priceYearly) })}</p>
              </div>
              <ul className="mt-6 space-y-2.5 border-t pt-5 text-sm">
                <li className="flex items-center gap-2.5"><Users className="size-4 text-muted-foreground" aria-hidden />{p.maxMembers == null ? t('platform.planUnlimited') : t('platform.planUpTo', { count: p.maxMembers })}</li>
                <li className="flex items-center gap-2.5"><HardDrive className="size-4 text-muted-foreground" aria-hidden />{t('platform.planStorageGb', { count: p.storageGb })}</li>
                <li className="flex items-center gap-2.5"><Clock className="size-4 text-muted-foreground" aria-hidden />{p.trialDays ? t('platform.planTrialDays', { count: p.trialDays }) : t('platform.planNoTrial')}</li>
              </ul>
              <div className="mt-5 flex-1">
                <p className="mb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">{t('platform.colModules')}</p>
                <ul className="space-y-1.5 text-sm">
                  {p.modules.map((k) => <li key={k} className="flex items-center gap-2"><Check className="size-4 text-primary" aria-hidden />{name(k)}</li>)}
                </ul>
              </div>
              <Button variant="outline" className="mt-6 w-full" onClick={() => setEditing(p)}><Pencil className="size-4" />{t('platform.planEdit')}</Button>
            </article>
          ))}
        </div>
      )}
      {editing && <PlanDialog plan={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  );
}
