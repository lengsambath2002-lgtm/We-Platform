'use client';

import { Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { moduleName } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
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
  const [editing, setEditing] = useState<Plan | 'new' | null>(null);
  return (
    <>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="flex-1"><h1 className="text-xl font-semibold">{t('platform.navPlans')}</h1><p className="text-sm text-muted-foreground">{t('platform.plansLead')}</p></div>
        <Button size="sm" onClick={() => setEditing('new')}><Plus className="size-4" />{t('platform.planNew')}</Button>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton /> : (
        <div className="overflow-x-auto rounded-lg border bg-card">
          <Table>
            <TableHeader><TableRow><TableHead>{t('platform.planCode')}</TableHead><TableHead>{t('platform.planMonthly')}</TableHead><TableHead>{t('platform.planMembers')}</TableHead><TableHead>{t('platform.planStorage')}</TableHead><TableHead>{t('platform.colModules')}</TableHead><TableHead className="w-12" /></TableRow></TableHeader>
            <TableBody>{data.map((p) => (
              <TableRow key={p.code}>
                <TableCell><span className="font-medium">{p.nameEn}</span><span className="block text-xs text-muted-foreground">{p.code}{p.publicPlan ? '' : ` · ${t('platform.planHidden')}`}{p.trialDays ? ` · ${t('platform.planTrialDays', { count: p.trialDays })}` : ''}</span></TableCell>
                <TableCell className="tabular">{usd(p.priceMonthly)} / {usd(p.priceYearly)}</TableCell>
                <TableCell className="tabular">{p.maxMembers ?? '∞'}</TableCell>
                <TableCell className="tabular">{p.storageGb} GB</TableCell>
                <TableCell className="text-sm">{p.modules.join(', ')}</TableCell>
                <TableCell><Button variant="ghost" size="icon" aria-label={t('platform.planEdit')} onClick={() => setEditing(p)}><Pencil className="size-4" /></Button></TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        </div>
      )}
      {editing && <PlanDialog plan={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  );
}
