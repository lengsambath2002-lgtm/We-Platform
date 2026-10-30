'use client';

import { ArrowLeft, FileSignature, Pencil, Trash2, Trophy, UserPlus, X } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { DealDialog, DealStageBadge, LostDialog, dealWho } from '@/components/business/crm-ui';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { ACTIVITY_KINDS, type ActivityKind, type DealStage, OPEN_STAGES, crmApi, useDeal } from '@/lib/api/crm';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** One deal: who and how much, its stage (won / lost), next step, the activity timeline, and turning it into a quote. */
export default function DealPage() {
  const { slug } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useDeal(slug, id);
  const [dialog, setDialog] = useState<'edit' | 'lost' | null>(null);
  const [kind, setKind] = useState<ActivityKind>('CALL');
  const [body, setBody] = useState('');
  const run = useOrgMutation(slug, (fn: () => Promise<unknown>) => fn());
  const act = async (fn: () => Promise<unknown>, done?: string) => {
    try { const r = await run.mutateAsync(fn); if (done) toast.success(done); return r; } catch (e) { toast.error(errorMessage(e)); return null; }
  };
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={6} />;
  const d = data.deal;
  const open = d.stage !== 'WON' && d.stage !== 'LOST';
  const info = (label: string, value: React.ReactNode) => value ? <div><dt className="text-xs text-muted-foreground">{label}</dt><dd className="text-sm">{value}</dd></div> : null;

  return (
    <>
      <Link href={`/${slug}/business/deals`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('business.dealsTitle')}</Link>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2"><h1 className="text-xl font-semibold tracking-tight">{d.title}</h1><DealStageBadge stage={d.stage} /></div>
          <p className="mt-1 text-sm text-muted-foreground">{dealWho(d)} · <span className="font-medium text-foreground tabular">{usd(d.value)}</span> · {t('business.probability', { n: d.probability })}</p>
        </div>
        {d.canEdit && (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => setDialog('edit')}><Pencil className="size-4" />{t('common.edit')}</Button>
            {!d.customer && <Button variant="outline" size="sm" onClick={() => act(() => crmApi.convert(slug, d.id), t('business.dealConverted'))}><UserPlus className="size-4" />{t('business.makeCustomer')}</Button>}
            {open && !d.quote && <Button variant="outline" size="sm" onClick={async () => { const q = await act(() => crmApi.startQuote(slug, d.id)) as { id: string } | null; if (q) router.push(`/${slug}/business/quotes/${q.id}`); }}><FileSignature className="size-4" />{t('business.startQuote')}</Button>}
            {open && <Button size="sm" onClick={() => act(() => crmApi.move(slug, d.id, 'WON'), t('business.dealWon'))}><Trophy className="size-4" />{t('business.markWon')}</Button>}
            {open && <Button variant="outline" size="sm" onClick={() => setDialog('lost')}><X className="size-4" />{t('business.markLost')}</Button>}
            {!open && <Button variant="outline" size="sm" onClick={() => act(() => crmApi.move(slug, d.id, 'NEGOTIATION'), t('business.dealReopened'))}>{t('business.reopenDeal')}</Button>}
            <Button variant="ghost" size="sm" aria-label={t('common.delete')} onClick={async () => {
              if (!window.confirm(t('business.deleteDealConfirm'))) return;
              if (await act(() => crmApi.deleteDeal(slug, d.id), t('business.dealDeleted')) !== null) router.replace(`/${slug}/business/deals`);
            }}><Trash2 className="size-4" /></Button>
          </div>
        )}
      </div>
      {d.stage === 'LOST' && d.lostReason && <p role="status" className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{t('business.lostBecause', { reason: d.lostReason })}</p>}

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {d.canEdit && open && (
            <section className="flex flex-wrap items-center gap-3 rounded-lg border bg-card p-4">
              <Label htmlFor="deal-stage" className="text-sm font-medium">{t('business.dealStage')}</Label>
              <Select value={d.stage} onValueChange={(s) => act(() => crmApi.move(slug, d.id, s as DealStage), t('business.stageChanged'))}>
                <SelectTrigger id="deal-stage" className="w-48"><SelectValue /></SelectTrigger>
                <SelectContent>{OPEN_STAGES.map((s) => <SelectItem key={s} value={s}>{t(`business.stage_${s}`)}</SelectItem>)}</SelectContent>
              </Select>
            </section>
          )}
          <section aria-labelledby="tl-h" className="rounded-lg border bg-card p-4">
            <h2 id="tl-h" className="mb-3 text-base font-semibold">{t('business.dealActivity')}</h2>
            <form className="mb-4 space-y-2" onSubmit={async (e) => {
              e.preventDefault();
              if (!body.trim()) return;
              if (await act(() => crmApi.addActivity(slug, d.id, { kind, body: body.trim(), happenedOn: null }), t('business.activityAdded')) !== null) setBody('');
            }}>
              <div className="flex gap-2">
                <Select value={kind} onValueChange={(k) => setKind(k as ActivityKind)}>
                  <SelectTrigger className="w-36" aria-label={t('business.activityKind')}><SelectValue /></SelectTrigger>
                  <SelectContent>{ACTIVITY_KINDS.map((k) => <SelectItem key={k} value={k}>{t(`business.activity_${k}`)}</SelectItem>)}</SelectContent>
                </Select>
                <Button type="submit" size="sm" variant="outline" disabled={!body.trim()}>{t('business.logActivity')}</Button>
              </div>
              <Textarea rows={2} maxLength={4000} value={body} onChange={(e) => setBody(e.target.value)} aria-label={t('business.activityWhat')} placeholder={t('business.activityWhat')} />
            </form>
            {data.activities.length === 0 ? <p className="text-sm text-muted-foreground">{t('business.noActivity')}</p> : (
              <ul className="space-y-3">
                {data.activities.map((a) => (
                  <li key={a.id} className="flex gap-3">
                    <PersonAvatar person={a.by} className="mt-0.5 size-7" />
                    <div className="min-w-0 flex-1">
                      <p className="text-xs text-muted-foreground"><span className="font-medium text-foreground">{t(`business.activity_${a.kind}`)}</span> · {a.by.name} · {day(a.happenedOn)}</p>
                      <p className="whitespace-pre-line break-words text-sm">{a.body}</p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
        <aside className="space-y-4">
          <section className="rounded-lg border bg-card p-4">
            <dl className="space-y-3">
              {info(t('business.dealOwner'), d.owner.name)}
              {info(t('business.customer'), d.customer ? <Link className="text-primary hover:underline" href={`/${slug}/business/customers/${d.customer.id}`}>{d.customer.name}</Link> : null)}
              {!d.customer && info(t('business.leadContact'), [d.company, d.contactName, d.email, d.phone].filter(Boolean).join(' · '))}
              {info(t('business.nextStep'), d.nextStep ? `${d.nextStep}${d.nextStepDate ? ` · ${day(d.nextStepDate)}` : ''}` : null)}
              {info(t('business.expectedClose'), d.expectedClose ? day(d.expectedClose) : null)}
              {info(t('business.dealSource'), t(`business.dealSource_${d.source}`))}
              {info(t('business.quote'), d.quote ? <Link className="text-primary hover:underline" href={`/${slug}/business/quotes/${d.quote.id}`}>{d.quote.number ?? t('business.draft')}</Link> : null)}
              {info(t('business.notes'), d.notes)}
            </dl>
          </section>
        </aside>
      </div>
      {dialog === 'edit' && <DealDialog org={slug} deal={d} onClose={() => setDialog(null)} />}
      {dialog === 'lost' && <LostDialog org={slug} dealId={d.id} onClose={() => setDialog(null)} />}
    </>
  );
}
