'use client';

import { useEffect, useState } from 'react';
import { toast } from 'sonner';

import { PRIORITY_LABEL } from '@/components/common/badges';
import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { ErrorLine } from '@/components/service/dialogs';
import { minutesText } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { type SlaPolicy, svcApi, useSla, useSvcSettings } from '@/lib/api/service';
import { khr } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

const MONEY = /^\d{1,10}(\.\d{1,2})?$/;

/** Admins: the approval threshold for work orders, the labour rate, and the SLA targets per priority (in hours). */
export default function ServiceSettings() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const settings = useSvcSettings(slug);
  const sla = useSla(slug);
  const [useThreshold, setUseThreshold] = useState(true);
  const [threshold, setThreshold] = useState('');
  const [rate, setRate] = useState('');
  const [hours, setHours] = useState<Record<string, { response: string; resolution: string }>>({});
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (settings.data) {
      setUseThreshold(settings.data.approvalThreshold != null);
      setThreshold(settings.data.approvalThreshold != null ? Number(settings.data.approvalThreshold).toFixed(2) : '500.00');
      setRate(Number(settings.data.labourRate).toFixed(2));
    }
  }, [settings.data]);
  useEffect(() => {
    if (sla.data) setHours(Object.fromEntries(sla.data.map((p) => [p.priority, { response: String(p.responseMinutes / 60), resolution: String(p.resolutionMinutes / 60) }])));
  }, [sla.data]);
  const save = useOrgMutation(slug, async () => {
    await svcApi.saveSettings(slug, { approvalThreshold: useThreshold ? threshold.trim() : null, labourRate: rate.trim() });
    await svcApi.saveSla(slug, (sla.data ?? []).map((p: SlaPolicy) => ({
      priority: p.priority, responseMinutes: Math.round(Number(hours[p.priority]?.response) * 60), resolutionMinutes: Math.round(Number(hours[p.priority]?.resolution) * 60),
    })));
  });
  if (!can('service.admin')) return <PermissionDenied />;
  const err = settings.error ?? sla.error;
  if (err) return <ErrorState error={err} retry={() => { settings.refetch(); sla.refetch(); }} />;
  if (settings.isPending || sla.isPending) return <ListSkeleton rows={4} />;

  return (
    <div className="max-w-3xl">
      <PageHeader title={t('service.settingsTitle')} subtitle={t('service.settingsSubtitle')} />
      <form className="space-y-6" noValidate onSubmit={async (e) => {
        e.preventDefault();
        if ((useThreshold && !MONEY.test(threshold.trim())) || !MONEY.test(rate.trim())) { setError(t('service.errMoney')); return; }
        for (const p of sla.data ?? []) {
          const h = hours[p.priority];
          if (!h || !(Number(h.response) > 0) || !(Number(h.resolution) > 0)) { setError(t('service.errHours')); return; }
          if (Number(h.resolution) < Number(h.response)) { setError(t('service.errResolutionShorter')); return; }
        }
        setError(null);
        try { await save.mutateAsync(undefined); toast.success(t('service.settingsSaved')); } catch (x) { setError(errorMessage(x)); }
      }}>
        <ErrorLine error={error} />
        <section className="space-y-4 rounded-lg border bg-card p-4">
          <h2 className="text-base font-semibold">{t('service.approvalSection')}</h2>
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={useThreshold} onCheckedChange={(v) => setUseThreshold(v === true)} />{t('service.useThreshold')}</label>
          {useThreshold && (
            <div className="max-w-xs space-y-1">
              <Label htmlFor="s-threshold">{t('service.thresholdUsd')}</Label>
              <Input id="s-threshold" inputMode="decimal" value={threshold} onChange={(e) => setThreshold(e.target.value)} />
              {Number(threshold) > 0 && <p className="text-xs text-muted-foreground tabular">{khr(threshold)}</p>}
              <p className="text-xs text-muted-foreground">{t('service.thresholdHint')}</p>
            </div>
          )}
          <div className="max-w-xs space-y-1">
            <Label htmlFor="s-rate">{t('service.labourRateUsd')}</Label>
            <Input id="s-rate" inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value)} />
            {Number(rate) > 0 && <p className="text-xs text-muted-foreground tabular">{khr(rate)}</p>}
          </div>
        </section>
        <section className="space-y-4 rounded-lg border bg-card p-4">
          <h2 className="text-base font-semibold">{t('service.slaSection')}</h2>
          <p className="text-sm text-muted-foreground">{t('service.slaHint')}</p>
          <div className="space-y-3">
            {(sla.data ?? []).map((p) => (
              <div key={p.priority} className="grid grid-cols-2 items-end gap-3 sm:grid-cols-[120px_1fr_1fr]">
                <p className="col-span-2 text-sm font-medium sm:col-span-1 sm:pb-2">{PRIORITY_LABEL[p.priority]}</p>
                <div className="space-y-1">
                  <Label htmlFor={`r-${p.priority}`} className="text-xs text-muted-foreground">{t('service.respondWithinHours')}</Label>
                  <Input id={`r-${p.priority}`} inputMode="decimal" value={hours[p.priority]?.response ?? ''} aria-describedby={`rh-${p.priority}`}
                    onChange={(e) => setHours({ ...hours, [p.priority]: { ...hours[p.priority]!, response: e.target.value } })} />
                  <p id={`rh-${p.priority}`} className="text-xs text-muted-foreground">{t('service.now', { value: minutesText(p.responseMinutes) })}</p>
                </div>
                <div className="space-y-1">
                  <Label htmlFor={`s-${p.priority}`} className="text-xs text-muted-foreground">{t('service.resolveWithinHours')}</Label>
                  <Input id={`s-${p.priority}`} inputMode="decimal" value={hours[p.priority]?.resolution ?? ''}
                    onChange={(e) => setHours({ ...hours, [p.priority]: { ...hours[p.priority]!, resolution: e.target.value } })} />
                  <p className="text-xs text-muted-foreground">{t('service.now', { value: minutesText(p.resolutionMinutes) })}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
        <div className="flex justify-end"><Button type="submit" disabled={save.isPending}>{t('common.save')}</Button></div>
      </form>
    </div>
  );
}
