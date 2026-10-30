'use client';

import { AlertTriangle, ArrowLeft, Check, Pencil, Plus, Trash2, X } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { BalanceCards } from '@/components/hr/leave-ui';
import { DepartureDialog, KIND_LABEL, OffBadge } from '@/components/hr/offboarding-ui';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { offboardingApi, useOffboarding } from '@/lib/api/offboarding';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/** One departure: what is agreed, what to hand over, the checklist, unused leave, and finishing it. */
export default function OffboardingDetailPage() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data: d, error, isPending, refetch } = useOffboarding(slug, id);
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState('');
  const act = useOrgMutation(slug, (fn: () => Promise<unknown>) => fn());
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const s = d.summary;
  const run = (fn: () => Promise<unknown>, done?: string) => act.mutateAsync(fn).then(() => done && toast.success(done)).catch((e) => toast.error(errorMessage(e)));
  const h = d.handover;
  const handoverNeeded = !!h && (h.reports.length > 0 || h.waitingApprovals > 0 || h.unitsHeaded.length > 0);

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <Link href={`/${slug}/hr/offboarding`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('offboarding.title')}</Link>
      <div className="flex flex-wrap items-center gap-4">
        <PersonAvatar person={s.person} className="size-12" />
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-xl font-semibold">{s.person.name}</h1>
          <p className="text-sm text-muted-foreground">{KIND_LABEL[s.kind]} · {t('offboarding.noticeOn', { date: day(s.noticeOn) })} · {t('offboarding.lastDayOn', { date: day(s.lastDay) })}</p>
        </div>
        <OffBadge status={s.status} />
      </div>
      {d.reason && <p className="whitespace-pre-wrap rounded-md bg-muted px-3 py-2 text-sm">{d.reason}</p>}

      {d.canManage && (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant={s.status === 'REQUESTED' ? 'default' : 'outline'} onClick={() => setEditing(true)}>
            {s.status === 'REQUESTED' ? <Check className="size-4" /> : <Pencil className="size-4" />}{s.status === 'REQUESTED' ? t('offboarding.acceptTitle') : t('offboarding.editTitle')}
          </Button>
          {s.status === 'IN_PROGRESS' && <Button size="sm" onClick={() => {
            if (window.confirm(t('offboarding.completeConfirm', { name: s.person.name }))) run(() => offboardingApi.complete(slug, s.id), t('offboarding.completed'));
          }}>{t('offboarding.complete')}</Button>}
          {d.canCancel && <Button size="sm" variant="ghost" className="text-destructive" onClick={() => {
            if (window.confirm(t('offboarding.cancelConfirm'))) run(() => offboardingApi.cancel(slug, s.id), t('offboarding.cancelled'));
          }}><X className="size-4" />{t('offboarding.cancel')}</Button>}
        </div>
      )}
      {!d.canManage && d.canCancel && (
        <Button size="sm" variant="outline" onClick={() => { if (window.confirm(t('offboarding.withdrawConfirm'))) run(() => offboardingApi.cancel(slug, s.id), t('offboarding.withdrawn')); }}>
          <X className="size-4" />{t('offboarding.withdraw')}
        </Button>
      )}

      {handoverNeeded && d.canManage && (
        <section aria-labelledby="ho-h" className="space-y-2 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm dark:border-amber-900 dark:bg-amber-950">
          <h2 id="ho-h" className="flex items-center gap-2 font-semibold"><AlertTriangle className="size-4" />{t('offboarding.handover')}</h2>
          {h!.reports.length > 0 && <p>{t('offboarding.reportsTo', { count: h!.reports.length })} {h!.reports.map((r, i) => <span key={r.id}>{i > 0 && ', '}<Link className="underline" href={`/${slug}/hr/people/${r.id}`}>{r.name}</Link></span>)}</p>}
          {h!.waitingApprovals > 0 && <p>{t('offboarding.approvalsWaiting', { count: h!.waitingApprovals })} <Link className="underline" href={`/${slug}/approvals`}>{t('offboarding.setActing')}</Link></p>}
          {h!.unitsHeaded.length > 0 && <p>{t('offboarding.unitsHeaded', { units: h!.unitsHeaded.join(', ') })} <Link className="underline" href={`/${slug}/structure`}>{t('offboarding.openStructure')}</Link></p>}
        </section>
      )}

      {(s.status === 'IN_PROGRESS' || d.items.length > 0) && (
        <section aria-labelledby="cl-h" className="rounded-lg border bg-card p-4">
          <h2 id="cl-h" className="mb-3 text-sm font-semibold">{t('offboarding.checklist')} <span className="font-normal text-muted-foreground">· {t('offboarding.progress', { done: s.done, total: s.total })}</span></h2>
          <ul className="space-y-2">{d.items.map((i) => (
            <li key={i.id} className="flex items-start gap-3 text-sm">
              <Checkbox className="mt-0.5" checked={i.done} disabled={!d.canManage} aria-label={i.title}
                onCheckedChange={(v) => run(() => offboardingApi.done(slug, i.id, v === true))} />
              <span className="min-w-0 flex-1"><span className={i.done ? 'text-muted-foreground line-through' : ''}>{i.title}</span>
                <span className="block text-xs text-muted-foreground">{[i.dueDate ? t('offboarding.due', { date: day(i.dueDate) }) : null, i.doneBy ? t('offboarding.doneBy', { name: i.doneBy.name }) : null].filter(Boolean).join(' · ')}</span></span>
              {d.canManage && <Button variant="ghost" size="icon" className="size-7" aria-label={t('common.remove')} onClick={() => run(() => offboardingApi.removeItem(slug, i.id))}><Trash2 className="size-3.5" /></Button>}
            </li>
          ))}</ul>
          {d.canManage && s.status === 'IN_PROGRESS' && (
            <form className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (title.trim()) run(() => offboardingApi.addItem(slug, s.id, { title: title.trim(), dueDate: null })).then(() => setTitle('')); }}>
              <Input aria-label={t('offboarding.addStep')} placeholder={t('offboarding.addStep')} maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} />
              <Button type="submit" variant="outline" size="sm"><Plus className="size-4" />{t('common.add')}</Button>
            </form>
          )}
        </section>
      )}

      {d.leave.length > 0 && (
        <section aria-labelledby="lv-h">
          <h2 id="lv-h" className="mb-1 text-sm font-semibold">{t('offboarding.leaveTitle')}</h2>
          <p className="mb-3 text-xs text-muted-foreground">{t('offboarding.leaveBody')}</p>
          <BalanceCards balances={d.leave} />
        </section>
      )}
      {editing && <DepartureDialog org={slug} detail={d} onClose={() => setEditing(false)} />}
    </div>
  );
}
