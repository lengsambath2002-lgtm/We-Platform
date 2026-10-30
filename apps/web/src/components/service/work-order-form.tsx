'use client';

import { Plus, Trash2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PRIORITIES, PRIORITY_LABEL } from '@/components/common/badges';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { ApiError, errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { type LineKind, type Ticket, type WorkOrder, svcApi, useStaff, useSvcCustomer, useSvcCustomers, useSvcSettings } from '@/lib/api/service';
import { cents, khr, usd } from '@/lib/money';

import { ErrorLine } from './dialogs';
import { fromLocalInput, toLocalInput } from './ui';

const NONE = 'none';
const QTY = /^\d{1,9}(\.\d{1,3})?$/;
const MONEY = /^\d{1,10}(\.\d{1,2})?$/;

export interface LineDraft { kind: LineKind; description: string; quantity: string; unitPrice: string }

/** Lines as the API wants them, or a message for the first one that isn't valid. */
export function linesPayload(lines: LineDraft[], t: ReturnType<typeof useT>['t']) {
  for (const l of lines) {
    if (!l.description.trim()) return { error: t('service.errLineDescription') };
    if (!QTY.test(l.quantity.trim()) || Number(l.quantity) <= 0) return { error: t('service.errQuantity') };
    if (!MONEY.test(l.unitPrice.trim())) return { error: t('service.errMoney') };
  }
  return { lines: lines.map((l) => ({ kind: l.kind, description: l.description.trim(), quantity: l.quantity.trim(), unitPrice: l.unitPrice.trim() })) };
}

/** The same arithmetic as the API (each line half-up to cents), for display while editing; the API's are the real totals. */
export function lineTotals(lines: LineDraft[]) {
  let parts = 0;
  let labour = 0;
  const amounts = lines.map((l) => {
    const a = cents((Number(l.quantity) || 0) * (Number(l.unitPrice) || 0));
    if (l.kind === 'LABOUR') labour = cents(labour + a); else parts = cents(parts + a);
    return a;
  });
  return { amounts, parts, labour, total: cents(parts + labour) };
}

/** Parts and labour: kind, description, quantity (hours for labour), unit price; live amounts and totals in USD and riel. */
export function LinesEditor({ lines, onChange, labourRate }: { lines: LineDraft[]; onChange: (l: LineDraft[]) => void; labourRate: number }) {
  const { t } = useT();
  const totals = lineTotals(lines);
  const set = (i: number, patch: Partial<LineDraft>) => onChange(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  return (
    <div className="space-y-3">
      {lines.length === 0 && <p className="text-sm text-muted-foreground">{t('service.noLines')}</p>}
      <ul className="space-y-3">
        {lines.map((l, i) => (
          <li key={i} className="grid grid-cols-2 gap-2 rounded-md border p-3 sm:grid-cols-[120px_1fr_90px_110px_100px_auto] sm:items-end sm:border-0 sm:p-0">
            <div className="space-y-1">
              <Label htmlFor={`l-kind-${i}`} className="text-xs text-muted-foreground">{t('service.lineKind')}</Label>
              <Select value={l.kind} onValueChange={(v) => set(i, { kind: v as LineKind })}>
                <SelectTrigger id={`l-kind-${i}`} className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value="PART">{t('service.kindPart')}</SelectItem><SelectItem value="LABOUR">{t('service.kindLabour')}</SelectItem></SelectContent>
              </Select>
            </div>
            <div className="col-span-2 space-y-1 sm:col-span-1">
              <Label htmlFor={`l-desc-${i}`} className="text-xs text-muted-foreground">{t('service.lineDescription')}</Label>
              <Input id={`l-desc-${i}`} value={l.description} onChange={(e) => set(i, { description: e.target.value })} />
            </div>
            <div className="space-y-1">
              <Label htmlFor={`l-qty-${i}`} className="text-xs text-muted-foreground">{l.kind === 'LABOUR' ? t('service.hours') : t('service.qty')}</Label>
              <Input id={`l-qty-${i}`} inputMode="decimal" value={l.quantity} onChange={(e) => set(i, { quantity: e.target.value })} />
            </div>
            <div className="space-y-1">
              <Label htmlFor={`l-price-${i}`} className="text-xs text-muted-foreground">{t('service.unitPrice')}</Label>
              <Input id={`l-price-${i}`} inputMode="decimal" value={l.unitPrice} onChange={(e) => set(i, { unitPrice: e.target.value })} />
            </div>
            <p className="self-center text-right text-sm tabular sm:self-end sm:pb-2">{usd(totals.amounts[i])}</p>
            <Button type="button" variant="ghost" size="icon-sm" className="justify-self-end sm:mb-0.5" aria-label={t('service.removeLine')}
              onClick={() => onChange(lines.filter((_, j) => j !== i))}><Trash2 className="size-4" /></Button>
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="sm" variant="outline" onClick={() => onChange([...lines, { kind: 'PART', description: '', quantity: '1', unitPrice: '' }])}>
          <Plus className="size-4" />{t('service.addPart')}</Button>
        <Button type="button" size="sm" variant="outline" onClick={() => onChange([...lines, { kind: 'LABOUR', description: t('service.labourDefault'), quantity: '1', unitPrice: labourRate.toFixed(2) }])}>
          <Plus className="size-4" />{t('service.addLabour')}</Button>
      </div>
      <dl className="ml-auto grid max-w-xs grid-cols-2 gap-x-6 gap-y-1 text-sm">
        <dt className="text-muted-foreground">{t('service.parts')}</dt><dd className="text-right tabular">{usd(totals.parts)}</dd>
        <dt className="text-muted-foreground">{t('service.labour')}</dt><dd className="text-right tabular">{usd(totals.labour)}</dd>
        <dt className="font-medium">{t('service.total')}</dt>
        <dd className="text-right"><span className="font-semibold tabular">{usd(totals.total)}</span><span className="block text-xs text-muted-foreground tabular">{khr(totals.total)}</span></dd>
      </dl>
    </div>
  );
}

export const toDrafts = (w?: WorkOrder): LineDraft[] =>
  (w?.lines ?? []).map((l) => ({ kind: l.kind, description: l.description, quantity: String(Number(l.quantity)), unitPrice: Number(l.unitPrice).toFixed(2) }));

/** Creates (from a ticket or on its own) or edits a work order: what, where, when, who, the checklist and the parts and labour. */
export function WorkOrderForm({ org, workOrder, ticket }: { org: string; workOrder?: WorkOrder; ticket?: Ticket }) {
  const { t } = useT();
  const router = useRouter();
  const settings = useSvcSettings(org);
  const staff = useStaff(org);
  const customers = useSvcCustomers(org, { size: 100 });
  const w = workOrder;
  const [title, setTitle] = useState(w?.summary.title ?? ticket?.summary.title ?? '');
  const [description, setDescription] = useState(w?.description ?? ticket?.description ?? '');
  const [priority, setPriority] = useState<string>(w?.summary.priority ?? ticket?.summary.priority ?? 'MEDIUM');
  const [customerId, setCustomerId] = useState(w?.summary.customer?.id ?? ticket?.summary.customer?.id ?? NONE);
  const [siteId, setSiteId] = useState(w?.site?.id ?? ticket?.site?.id ?? NONE);
  const [assetId, setAssetId] = useState(w?.summary.asset?.id ?? ticket?.asset?.id ?? NONE);
  const [start, setStart] = useState(toLocalInput(w?.summary.scheduledStart ?? null));
  const [end, setEnd] = useState(toLocalInput(w?.summary.scheduledEnd ?? null));
  const [techs, setTechs] = useState<string[]>(w?.summary.technicians.map((p) => p.id) ?? []);
  const [checklist, setChecklist] = useState((w?.checklist ?? []).map((c) => c.label).join('\n'));
  const [lines, setLines] = useState<LineDraft[]>(toDrafts(w));
  const [error, setError] = useState<string | null>(null);
  const detail = useSvcCustomer(org, customerId !== NONE ? customerId : null);
  const technicians = (staff.data ?? []).filter((s) => s.active && s.role === 'TECHNICIAN');
  const save = useOrgMutation(org, (body: unknown) => svcApi.saveWorkOrder(org, w?.summary.id ?? null, body));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) { setError(t('service.errTitle')); return; }
    if (!!start !== !!end) { setError(t('service.errWindow')); return; }
    const l = linesPayload(lines, t);
    if (l.error) { setError(l.error); return; }
    setError(null);
    try {
      const saved = await save.mutateAsync({
        title: title.trim(), description: description.trim() || null, priority, ticketId: w ? null : ticket?.summary.id ?? null,
        customerId: customerId !== NONE ? customerId : null, siteId: siteId !== NONE ? siteId : null, assetId: assetId !== NONE ? assetId : null,
        scheduledStart: fromLocalInput(start), scheduledEnd: fromLocalInput(end), technicianIds: techs,
        checklist: checklist.split('\n').map((x) => x.trim()).filter(Boolean), lines: l.lines,
      });
      toast.success(w ? t('service.woSaved') : t('service.woCreated', { number: saved.summary.number }));
      router.push(`/${org}/service/work-orders/${saved.summary.id}`);
    } catch (x) { setError(x instanceof ApiError && x.fields.length ? x.fields[0]!.message : errorMessage(x)); }
  };

  const pick = (id: string, label: string, value: string, onChange: (v: string) => void, options: { value: string; label: string }[], none?: string) => (
    <div className="min-w-0 space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger id={id} className="w-full min-w-0"><SelectValue /></SelectTrigger>
        <SelectContent>{none && <SelectItem value={NONE}>{none}</SelectItem>}{options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}</SelectContent>
      </Select>
    </div>
  );

  return (
    <form onSubmit={submit} className="mx-auto max-w-4xl space-y-6" noValidate>
      <ErrorLine error={error} />
      <section className="space-y-4 rounded-lg border bg-card p-4">
        <div className="space-y-2"><Label htmlFor="wo-title">{t('service.woTitle')}</Label><Input id="wo-title" value={title} onChange={(e) => setTitle(e.target.value)} /></div>
        <div className="space-y-2"><Label htmlFor="wo-desc">{t('service.details')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></Label>
          <Textarea id="wo-desc" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} /></div>
        <div className="grid gap-4 sm:grid-cols-4">
          {pick('wo-priority', t('service.priority'), priority, setPriority, PRIORITIES.map((p) => ({ value: p, label: PRIORITY_LABEL[p] })))}
          {pick('wo-customer', t('service.customer'), customerId, (v) => { setCustomerId(v); setSiteId(NONE); setAssetId(NONE); },
            (customers.data?.items ?? []).map((c) => ({ value: c.id, label: c.name })), t('service.noCustomer'))}
          {pick('wo-site', t('service.site'), siteId, setSiteId, (detail.data?.sites ?? []).map((x) => ({ value: x.id, label: x.name })), t('service.noSite'))}
          {pick('wo-asset', t('service.asset'), assetId, setAssetId,
            (detail.data?.assets ?? []).map((a) => ({ value: a.id, label: a.serial ? `${a.name} · ${a.serial}` : a.name })), t('service.noAsset'))}
        </div>
      </section>
      <section className="space-y-4 rounded-lg border bg-card p-4" aria-labelledby="wo-when-h">
        <h2 id="wo-when-h" className="text-base font-semibold">{t('service.whenAndWho')}</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2"><Label htmlFor="wo-start">{t('service.starts')}</Label><Input id="wo-start" type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="wo-end">{t('service.ends')}</Label><Input id="wo-end" type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} /></div>
        </div>
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">{t('service.technicians')}</legend>
          {technicians.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noTechniciansYet')}</p> : (
            <div className="grid gap-2 sm:grid-cols-2">
              {technicians.map((s) => (
                <label key={s.id} className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm">
                  <Checkbox checked={techs.includes(s.person.id)} aria-label={s.person.name}
                    onCheckedChange={(v) => setTechs(v === true ? [...techs, s.person.id] : techs.filter((x) => x !== s.person.id))} />
                  <span className="min-w-0 flex-1 truncate">{s.person.name}</span>
                  {s.skills.length > 0 && <span className="truncate text-xs text-muted-foreground">{s.skills.join(', ')}</span>}
                </label>
              ))}
            </div>
          )}
        </fieldset>
      </section>
      <section className="space-y-2 rounded-lg border bg-card p-4">
        <Label htmlFor="wo-check" className="text-base font-semibold">{t('service.checklist')} <span className="text-sm font-normal text-muted-foreground">{t('service.onePerLine')}</span></Label>
        <Textarea id="wo-check" rows={4} value={checklist} onChange={(e) => setChecklist(e.target.value)} />
      </section>
      <section className="space-y-3 rounded-lg border bg-card p-4" aria-labelledby="wo-lines-h">
        <h2 id="wo-lines-h" className="text-base font-semibold">{t('service.partsAndLabour')}</h2>
        <LinesEditor lines={lines} onChange={setLines} labourRate={settings.data?.labourRate ?? 15} />
        {settings.data?.approvalThreshold != null && lineTotals(lines).total > settings.data.approvalThreshold && (
          <p className="text-sm text-warning">{t('service.overThreshold', { amount: usd(settings.data.approvalThreshold) })}</p>
        )}
      </section>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={() => router.back()}>{t('common.cancel')}</Button>
        <Button type="submit" disabled={save.isPending}>{w ? t('common.save') : t('service.createWorkOrder')}</Button>
      </div>
    </form>
  );
}
