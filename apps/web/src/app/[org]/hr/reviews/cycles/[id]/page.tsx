'use client';

import { ArrowLeft, Lock, Pencil, Rocket } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { CycleDialog, ReviewBadge, Stars } from '@/components/hr/development-ui';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { developmentApi, useCycle } from '@/lib/api/development';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/** HR managers: a cycle's questions, launching and closing it, and everyone's progress. */
export default function CyclePage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useCycle(slug, id);
  const [editing, setEditing] = useState(false);
  const run = useOrgMutation(slug, (fn: () => Promise<unknown>) => fn());
  if (!can('hr.manage')) return <PermissionDenied />;
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={6} />;
  const c = data.cycle;
  const act = async (fn: () => Promise<unknown>, done: string, confirm: string) => {
    if (!window.confirm(confirm)) return;
    try { await run.mutateAsync(fn); toast.success(done); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <>
      <Link href={`/${slug}/hr/reviews?tab=cycles`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('hr.cycles')}</Link>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{c.name}</h1>
          <p className="text-sm text-muted-foreground">{t(`hr.cycle_${c.status}`)} · {day(c.periodStart)} – {day(c.periodEnd)} · {t('hr.dueOn', { date: day(c.dueDate) })}</p>
        </div>
        <div className="flex gap-2">
          {c.status === 'DRAFT' && <Button variant="outline" size="sm" onClick={() => setEditing(true)}><Pencil className="size-4" />{t('common.edit')}</Button>}
          {c.status === 'DRAFT' && <Button size="sm" onClick={() => act(() => developmentApi.launch(slug, c.id), t('hr.cycleLaunched'), t('hr.launchConfirm'))}><Rocket className="size-4" />{t('hr.launch')}</Button>}
          {c.status === 'OPEN' && <Button variant="outline" size="sm" onClick={() => act(() => developmentApi.close(slug, c.id), t('hr.cycleClosed'), t('hr.closeConfirm'))}><Lock className="size-4" />{t('hr.closeCycle')}</Button>}
        </div>
      </div>
      <section className="mb-4 rounded-lg border bg-card p-4">
        <h2 className="mb-2 text-sm font-semibold">{t('hr.questions')}</h2>
        <ol className="list-decimal space-y-1 pl-5 text-sm">{c.questions.map((q, i) => <li key={i}>{q}</li>)}</ol>
      </section>
      {c.status !== 'DRAFT' && (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[[t('hr.reviewsTotal'), c.total], [t('hr.selfDone'), c.selfDone], [t('hr.reviewedCount'), c.reviewed], [t('hr.acknowledgedCount'), c.acknowledged]].map(([l, n]) => (
              <div key={l as string} className="rounded-lg border bg-card p-3"><p className="text-xs text-muted-foreground">{l}</p><p className="text-lg font-semibold tabular">{n}</p></div>
            ))}
          </div>
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader><TableRow><TableHead>{t('hr.person')}</TableHead><TableHead>{t('hr.reviewer')}</TableHead><TableHead>{t('hr.colStatus')}</TableHead><TableHead>{t('hr.overallRating')}</TableHead></TableRow></TableHeader>
              <TableBody>
                {data.reviews.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell><Link href={`/${slug}/hr/reviews/${r.id}`} className="flex items-center gap-2 font-medium text-primary hover:underline"><PersonAvatar person={r.employee} className="size-6" />{r.employee.name}</Link></TableCell>
                    <TableCell className="text-sm">{r.reviewer.name}</TableCell>
                    <TableCell><ReviewBadge status={r.status} /></TableCell>
                    <TableCell>{r.overallRating ? <Stars value={r.overallRating} label="" /> : ''}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </>
      )}
      {editing && <CycleDialog org={slug} cycle={c} onClose={() => setEditing(false)} />}
    </>
  );
}
