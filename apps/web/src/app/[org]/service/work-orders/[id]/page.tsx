'use client';

import { parseISO } from 'date-fns';
import { CalendarCheck, CheckCircle2, Pause, Pencil, Play, Send, XCircle } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { RecordApprovals } from '@/components/approvals/record-approvals';
import { PriorityIcon } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { CompleteDialog, ErrorLine, ReasonDialog } from '@/components/service/dialogs';
import { Crumb, Field, Money, WoBadge, windowText } from '@/components/service/ui';
import { type LineDraft, LinesEditor, linesPayload, toDrafts } from '@/components/service/work-order-form';
import { ActivityTimeline } from '@/components/tasks/activity';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { svcApi, useSvcSettings, useWorkOrder, useWorkOrderHistory } from '@/lib/api/service';
import { day, fmt } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/**
 * One work order: schedule (or send for approval), start, hold, tick the checklist, record parts and labour, complete
 * with the customer's sign-off, or cancel — each button only when the server says the caller may.
 */
export default function WorkOrderPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data: w, error, isPending, refetch } = useWorkOrder(slug, id);
  const history = useWorkOrderHistory(slug, id);
  const settings = useSvcSettings(slug);
  const [dialog, setDialog] = useState<'schedule' | 'hold' | 'cancel' | 'complete' | null>(null);
  const [lines, setLines] = useState<LineDraft[] | null>(null);
  const [lineError, setLineError] = useState<string | null>(null);
  const schedule = useOrgMutation(slug, () => svcApi.woAction(slug, id, 'schedule'));
  const start = useOrgMutation(slug, () => svcApi.woAction(slug, id, 'start'));
  const check = useOrgMutation(slug, ({ item, done }: { item: string; done: boolean }) => svcApi.check(slug, id, item, done));
  const saveLines = useOrgMutation(slug, (l: unknown[]) => svcApi.setLines(slug, id, l));
  const hold = useOrgMutation(slug, (reason: string) => svcApi.hold(slug, id, reason));
  const cancel = useOrgMutation(slug, (reason: string) => svcApi.cancel(slug, id, reason));
  const complete = useOrgMutation(slug, (v: { notes: string | null; signedBy: string }) => svcApi.complete(slug, id, v));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const s = w.summary;
  const run = async (fn: () => Promise<unknown>, done: string) => {
    try { await fn(); toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };
  const allTicked = w.checklist.every((c) => c.done);
  const manage = can('service.manage');

  return (
    <div className="mx-auto max-w-6xl">
      <Crumb href={`/${slug}/service/work-orders`} label={manage ? t('service.navWorkOrders') : t('service.navMyWorkOrders')} current={s.number} />
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-xl font-semibold tracking-tight">{s.number} · {s.title}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <WoBadge status={s.status} /><PriorityIcon priority={s.priority} withLabel />
            {s.preventive && <span>{t('service.preventive')}</span>}
            {s.approvalState === 'PENDING' && <span className="text-warning">{t('service.awaitingApproval')}</span>}
            {s.approvalState === 'REJECTED' && <span className="text-destructive">{t('service.approvalRejected')}</span>}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {w.canSchedule && (
            <Button size="sm" onClick={() => setDialog('schedule')}>
              {w.needsApproval ? <><Send className="size-4" />{t('service.sendForApproval')}</> : <><CalendarCheck className="size-4" />{t('service.schedule')}</>}
            </Button>
          )}
          {w.canStart && <Button size="sm" disabled={start.isPending} onClick={() => run(() => start.mutateAsync(undefined), t('service.started'))}>
            <Play className="size-4" />{s.status === 'ON_HOLD' ? t('service.resume') : t('service.start')}</Button>}
          {w.canComplete && <Button size="sm" onClick={() => (allTicked ? setDialog('complete') : toast.error(t('service.tickFirst')))}><CheckCircle2 className="size-4" />{t('service.complete')}</Button>}
          {w.canHold && <Button size="sm" variant="outline" onClick={() => setDialog('hold')}><Pause className="size-4" />{t('service.hold')}</Button>}
          {w.canEdit && <Button size="sm" variant="outline" asChild><Link href={`/${slug}/service/work-orders/${id}/edit`}><Pencil className="size-4" />{t('common.edit')}</Link></Button>}
          {w.canCancel && <Button size="sm" variant="ghost" className="text-destructive" onClick={() => setDialog('cancel')}><XCircle className="size-4" />{t('service.cancelWo')}</Button>}
        </div>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-6">
          {w.holdReason && s.status === 'ON_HOLD' && <p className="rounded-lg border border-warning/40 bg-warning/10 px-4 py-3 text-sm">{t('service.onHoldBecause', { reason: w.holdReason })}</p>}
          {s.status === 'CANCELLED' && w.cancelReason && <p className="rounded-lg border bg-muted px-4 py-3 text-sm">{t('service.cancelledBecause', { reason: w.cancelReason })}</p>}
          {s.status === 'COMPLETED' && (
            <section className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm">
              <p className="font-medium">{t('service.completedBy', { name: w.completedBy?.name ?? '', date: w.completedAt ? fmt(parseISO(w.completedAt), 'd MMM yyyy, HH:mm') : '' })}</p>
              <p className="mt-1">{t('service.signedOffBy', { name: w.signedBy ?? '' })}</p>
              {w.completionNotes && <p className="mt-2 whitespace-pre-wrap">{w.completionNotes}</p>}
            </section>
          )}
          {w.description && <section className="rounded-lg border bg-card p-4"><h2 className="mb-2 text-sm font-semibold">{t('service.details')}</h2><p className="whitespace-pre-wrap text-sm">{w.description}</p></section>}
          <section aria-labelledby="check-h" className="space-y-3 rounded-lg border bg-card p-4">
            <h2 id="check-h" className="text-sm font-semibold">{t('service.checklist')} {w.checklist.length > 0 && <span className="font-normal text-muted-foreground">{s.checklistDone}/{s.checklistTotal}</span>}</h2>
            {w.checklist.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noChecklist')}</p> : (
              <ul className="space-y-2">
                {w.checklist.map((c) => (
                  <li key={c.id}>
                    <label className="flex items-start gap-3 text-sm">
                      <Checkbox checked={c.done} disabled={!w.canCheck || check.isPending} aria-label={c.label} className="mt-0.5"
                        onCheckedChange={(v) => run(() => check.mutateAsync({ item: c.id, done: v === true }), v === true ? t('service.ticked') : t('service.unticked'))} />
                      <span className="min-w-0 flex-1">
                        <span className={c.done ? 'text-muted-foreground line-through' : ''}>{c.label}</span>
                        {c.done && c.doneBy && <span className="block text-xs text-muted-foreground">{c.doneBy.name}{c.doneAt ? ` · ${fmt(parseISO(c.doneAt), 'd MMM, HH:mm')}` : ''}</span>}
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section aria-labelledby="lines-h" className="space-y-3 rounded-lg border bg-card p-4">
            <div className="flex items-center justify-between gap-2">
              <h2 id="lines-h" className="text-sm font-semibold">{t('service.partsAndLabour')}</h2>
              {w.canEditLines && !lines && <Button size="sm" variant="outline" onClick={() => setLines(toDrafts(w))}><Pencil className="size-4" />{t('service.editLines')}</Button>}
            </div>
            {lines ? (
              <form className="space-y-3" noValidate onSubmit={async (e) => {
                e.preventDefault();
                const p = linesPayload(lines, t);
                if (p.error) { setLineError(p.error); return; }
                setLineError(null);
                try { await saveLines.mutateAsync(p.lines ?? []); toast.success(t('service.linesSaved')); setLines(null); } catch (x) { setLineError(errorMessage(x)); }
              }}>
                <ErrorLine error={lineError} />
                <LinesEditor lines={lines} onChange={setLines} labourRate={settings.data?.labourRate ?? 15} />
                <div className="flex justify-end gap-2">
                  <Button type="button" variant="outline" size="sm" onClick={() => { setLines(null); setLineError(null); }}>{t('common.cancel')}</Button>
                  <Button type="submit" size="sm" disabled={saveLines.isPending}>{t('service.saveLines')}</Button>
                </div>
              </form>
            ) : w.lines.length === 0 ? <p className="text-sm text-muted-foreground">{t('service.noLines')}</p> : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('service.lineDescription')}</TableHead>
                      <TableHead className="text-right">{t('service.qty')}</TableHead>
                      <TableHead className="hidden text-right sm:table-cell">{t('service.unitPrice')}</TableHead>
                      <TableHead className="text-right">{t('service.amount')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {w.lines.map((l) => (
                      <TableRow key={l.id}>
                        <TableCell><span className="block">{l.description}</span><span className="text-xs text-muted-foreground">{l.kind === 'LABOUR' ? t('service.kindLabour') : t('service.kindPart')}</span></TableCell>
                        <TableCell className="text-right tabular">{Number(l.quantity)}{l.kind === 'LABOUR' ? ` ${t('service.hoursShort')}` : ''}</TableCell>
                        <TableCell className="hidden text-right tabular sm:table-cell">{usd(l.unitPrice)}</TableCell>
                        <TableCell className="text-right tabular">{usd(l.amount)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                  <TableFooter>
                    <TableRow><TableCell colSpan={2}>{t('service.parts')}</TableCell><TableCell className="hidden sm:table-cell" /><TableCell className="text-right tabular">{usd(w.partsTotal)}</TableCell></TableRow>
                    <TableRow><TableCell colSpan={2}>{t('service.labour')}</TableCell><TableCell className="hidden sm:table-cell" /><TableCell className="text-right tabular">{usd(w.labourTotal)}</TableCell></TableRow>
                    <TableRow><TableCell colSpan={2} className="font-semibold">{t('service.total')}</TableCell><TableCell className="hidden sm:table-cell" /><TableCell className="text-right"><Money value={s.total} strong className="items-end" /></TableCell></TableRow>
                  </TableFooter>
                </Table>
              </div>
            )}
            {w.needsApproval && s.status === 'DRAFT' && w.approvalThreshold != null && (
              <p className="text-sm text-warning">{t('service.overThreshold', { amount: usd(w.approvalThreshold) })}</p>
            )}
          </section>
          <section aria-labelledby="whist-h" className="space-y-3">
            <h2 id="whist-h" className="text-base font-semibold">{t('service.history')}</h2>
            {history.error ? <ErrorState error={history.error} retry={() => history.refetch()} /> : history.isPending ? <ListSkeleton rows={3} />
              : <ActivityTimeline items={[...history.data].reverse()} />}
          </section>
        </div>
        <aside className="space-y-4">
          <section className="rounded-lg border bg-card p-4">
            <dl className="grid gap-4">
              <Field label={t('service.when')}>{windowText(s.scheduledStart, s.scheduledEnd)}</Field>
              <Field label={t('service.technicians')}>
                {s.technicians.length === 0 ? <span className="text-muted-foreground">{t('service.noTechnician')}</span> : (
                  <ul className="space-y-1">{s.technicians.map((p) => <li key={p.id} className="flex items-center gap-2"><PersonAvatar person={p} className="size-5" />{p.name}</li>)}</ul>
                )}
              </Field>
              <Field label={t('service.customer')}>{s.customer ? (manage ? <Link className="text-primary hover:underline" href={`/${slug}/service/customers/${s.customer.id}`}>{s.customer.name}</Link> : s.customer.name) : '—'}</Field>
              <Field label={t('service.site')}>{w.site?.name ?? '—'}</Field>
              <Field label={t('service.asset')}>{s.asset ? (manage ? <Link className="text-primary hover:underline" href={`/${slug}/service/assets/${s.asset.id}`}>{s.asset.name}</Link> : s.asset.name) : '—'}</Field>
              {w.ticket && <Field label={t('service.ticket')}><Link className="text-primary hover:underline" href={`/${slug}/service/tickets/${w.ticket.id}`}>{w.ticketNumber} · {w.ticket.name}</Link></Field>}
              {w.plan && <Field label={t('service.plan')}>{w.plan.name}{w.planDue ? ` · ${day(w.planDue)}` : ''}</Field>}
              <Field label={t('service.total')}><Money value={s.total} strong /></Field>
              <Field label={t('service.createdBy')}>{w.createdBy?.name ?? t('service.bySystem')}</Field>
            </dl>
          </section>
          {w.approvalId && <RecordApprovals org={slug} recordId={s.id} canRequest={false} moduleKey="service" recordType="work_order" />}
        </aside>
      </div>
      <AlertDialog open={dialog === 'schedule'} onOpenChange={(v) => !v && setDialog(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{w.needsApproval ? t('service.sendForApprovalTitle') : t('service.scheduleTitle')}</AlertDialogTitle>
            <AlertDialogDescription>{w.needsApproval
              ? t('service.sendForApprovalBody', { total: usd(s.total), threshold: usd(w.approvalThreshold ?? 0) })
              : t('service.scheduleBody', { when: windowText(s.scheduledStart, s.scheduledEnd) })}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={() => run(() => schedule.mutateAsync(undefined), w.needsApproval ? t('service.sentForApproval') : t('service.scheduled'))}>
              {w.needsApproval ? t('service.sendForApproval') : t('service.schedule')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <ReasonDialog open={dialog === 'hold'} onOpenChange={(v) => !v && setDialog(null)} title={t('service.holdTitle')} body={t('service.holdBody')}
        label={t('service.reason')} action={t('service.hold')} onConfirm={async (r) => { await hold.mutateAsync(r); toast.success(t('service.held')); }} />
      <ReasonDialog open={dialog === 'cancel'} onOpenChange={(v) => !v && setDialog(null)} title={t('service.cancelTitle')} body={t('service.cancelBody')}
        label={t('service.reason')} action={t('service.cancelWo')} destructive onConfirm={async (r) => { await cancel.mutateAsync(r); toast.success(t('service.cancelled')); }} />
      <CompleteDialog open={dialog === 'complete'} onOpenChange={(v) => !v && setDialog(null)}
        onConfirm={async (v) => { await complete.mutateAsync(v); toast.success(t('service.completed')); }} />
    </div>
  );
}
