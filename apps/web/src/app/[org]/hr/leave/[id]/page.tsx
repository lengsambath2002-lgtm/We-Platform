'use client';

import { Ban, ChevronRight } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { RecordApprovals } from '@/components/approvals/record-approvals';
import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { daysText, LeaveBadge, rangeText, typeName } from '@/components/hr/leave-ui';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { hrApi, useLeave } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/** One leave request: what, when, its state, its approval (decided or withdrawn from its panel), and cancelling approved leave. */
export default function LeavePage() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data: l, error, isPending, refetch } = useLeave(slug, id);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const cancel = useOrgMutation(slug, () => hrApi.cancel(slug, id, reason.trim() || null));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;

  return (
    <div className="mx-auto max-w-4xl">
      <nav aria-label={t('hr.breadcrumb')} className="mb-2 flex items-center gap-1 text-sm text-muted-foreground">
        <Link href={`/${slug}/hr`} className="hover:text-foreground">{t('hr.navHome')}</Link><ChevronRight className="size-3.5" /><span>{t('hr.leaveRequest')}</span>
      </nav>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight">{typeName(l.type)} · {daysText(l.days)}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground"><LeaveBadge leave={l} /> {rangeText(l)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {l.canCancel && <Button size="sm" variant="outline" className="text-destructive" onClick={() => setCancelling(true)}><Ban className="size-4" />{t('hr.cancelLeave')}</Button>}
        </div>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-6">
          <section className="rounded-lg border bg-card p-4">
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs text-muted-foreground">{t('hr.person')}</dt>
                <dd className="mt-1"><Link href={`/${slug}/hr/people/${l.person.id}`} className="inline-flex items-center gap-2 hover:underline"><PersonAvatar person={l.person} />{l.person.name}</Link></dd>
              </div>
              <div><dt className="text-xs text-muted-foreground">{t('hr.leaveType')}</dt><dd className="mt-1">{typeName(l.type)} · {t(l.type.paid ? 'hr.paid' : 'hr.unpaid')}</dd></div>
              <div><dt className="text-xs text-muted-foreground">{t('hr.dates')}</dt><dd className="mt-1">{rangeText(l)}</dd></div>
              <div><dt className="text-xs text-muted-foreground">{t('hr.workingDays')}</dt><dd className="mt-1 tabular">{daysText(l.days)}</dd></div>
              <div><dt className="text-xs text-muted-foreground">{t('hr.requested')}</dt><dd className="mt-1">{day(l.createdAt)}</dd></div>
              {l.reason && <div className="sm:col-span-2"><dt className="text-xs text-muted-foreground">{t('hr.reason')}</dt><dd className="mt-1 whitespace-pre-wrap break-words">{l.reason}</dd></div>}
              {l.status === 'CANCELLED' && l.cancelReason && (
                <div className="sm:col-span-2"><dt className="text-xs text-muted-foreground">{t('hr.cancelReason')}</dt><dd className="mt-1 whitespace-pre-wrap break-words">{l.cancelReason}</dd></div>
              )}
            </dl>
          </section>
        </div>
        <aside className="space-y-3">
          <RecordApprovals org={slug} recordId={l.id} canRequest={false} moduleKey="hr" recordType="leave" />
        </aside>
      </div>
      <Dialog open={cancelling} onOpenChange={(v) => { if (!v) { setCancelling(false); setReason(''); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t('hr.cancelLeave')}</DialogTitle>
            <DialogDescription>{t('hr.cancelBody', { days: daysText(l.days) })}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="cancel-reason">{t('hr.cancelReason')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></Label>
            <Textarea id="cancel-reason" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelling(false)}>{t('common.back')}</Button>
            <Button variant="destructive" disabled={cancel.isPending} onClick={async () => {
              try {
                await cancel.mutateAsync(undefined);
                toast.success(t('hr.cancelled'));
                setCancelling(false);
              } catch (e) { toast.error(errorMessage(e)); }
            }}>{t('hr.cancelLeave')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
