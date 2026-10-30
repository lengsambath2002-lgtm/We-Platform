'use client';

import { ClipboardCheck, Plus } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { CycleDialog, ReviewBadge, Stars } from '@/components/hr/development-ui';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { type ReviewRow, useCycles, useMyReviews, useToReview } from '@/lib/api/development';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

function Rows({ org, rows, who }: { org: string; rows: ReviewRow[]; who: 'employee' | 'reviewer' }) {
  const { t } = useT();
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {rows.map((r) => {
        const p = who === 'employee' ? r.employee : r.reviewer;
        return (
          <li key={r.id}>
            <Link href={`/${org}/hr/reviews/${r.id}`} className="flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-muted/50">
              <PersonAvatar person={p} className="size-7" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{who === 'employee' ? p.name : r.cycleName}</span>
                <span className="block truncate text-xs text-muted-foreground">{who === 'employee' ? r.cycleName : t('hr.reviewedBy', { name: p.name })} · {t('hr.dueOn', { date: day(r.dueDate) })}</span>
              </span>
              {r.overallRating && <Stars value={r.overallRating} label="" />}
              <ReviewBadge status={r.status} />
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

function Cycles({ org }: { org: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useCycles(org);
  const [creating, setCreating] = useState(false);
  return (
    <section aria-labelledby="cy-h">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div><h2 id="cy-h" className="text-base font-semibold">{t('hr.cycles')}</h2><p className="text-sm text-muted-foreground">{t('hr.cyclesBody')}</p></div>
        <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />{t('hr.newCycle')}</Button>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={2} />
        : data.length === 0 ? <EmptyState icon={ClipboardCheck} title={t('hr.noCycles')} /> : (
          <ul className="divide-y rounded-lg border bg-card">
            {data.map((c) => (
              <li key={c.id}>
                <Link href={`/${org}/hr/reviews/cycles/${c.id}`} className="flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-muted/50">
                  <span className="min-w-0 flex-1"><span className="block font-medium">{c.name}</span><span className="block text-xs text-muted-foreground">{t(`hr.cycle_${c.status}`)} · {t('hr.dueOn', { date: day(c.dueDate) })}</span></span>
                  {c.status !== 'DRAFT' && (
                    <span className="w-40"><span className="mb-1 block text-right text-xs text-muted-foreground tabular">{t('hr.reviewedOf', { done: c.reviewed, total: c.total })}</span><Progress value={c.total ? (c.reviewed / c.total) * 100 : 0} aria-hidden /></span>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        )}
      {creating && <CycleDialog org={org} cycle="new" onClose={() => setCreating(false)} />}
    </section>
  );
}

/** Reviews: mine, the ones I write, and (HR) the cycles. */
function Reviews() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const mine = useMyReviews(slug);
  const toDo = useToReview(slug);
  const manage = can('hr.manage');
  const tabs = ['mine', 'to-review', ...(manage ? ['cycles'] : [])];
  const tab = tabs.includes(params.get('tab') ?? '') ? params.get('tab')! : 'mine';
  return (
    <>
      <PageHeader title={t('hr.reviewsTitle')} subtitle={t('hr.reviewsSubtitle')} />
      <Tabs value={tab} onValueChange={(v) => router.replace(`/${slug}/hr/reviews${v === 'mine' ? '' : `?tab=${v}`}`, { scroll: false })}>
        <TabsList className="mb-4">
          <TabsTrigger value="mine">{t('hr.myReviews')}</TabsTrigger>
          <TabsTrigger value="to-review">{t('hr.toReview')}{toDo.data?.some((r) => r.status === 'MANAGER') ? ` (${toDo.data.filter((r) => r.status === 'MANAGER').length})` : ''}</TabsTrigger>
          {manage && <TabsTrigger value="cycles">{t('hr.cycles')}</TabsTrigger>}
        </TabsList>
        <TabsContent value="mine">
          {mine.error ? <ErrorState error={mine.error} retry={() => mine.refetch()} /> : mine.isPending ? <ListSkeleton rows={2} />
            : mine.data.length === 0 ? <EmptyState icon={ClipboardCheck} title={t('hr.noReviews')} body={t('hr.noReviewsBody')} /> : <Rows org={slug} rows={mine.data} who="reviewer" />}
        </TabsContent>
        <TabsContent value="to-review">
          {toDo.error ? <ErrorState error={toDo.error} retry={() => toDo.refetch()} /> : toDo.isPending ? <ListSkeleton rows={2} />
            : toDo.data.length === 0 ? <EmptyState icon={ClipboardCheck} title={t('hr.nothingToReview')} /> : <Rows org={slug} rows={toDo.data} who="employee" />}
        </TabsContent>
        {manage && <TabsContent value="cycles"><Cycles org={slug} /></TabsContent>}
      </Tabs>
    </>
  );
}

export default function Page() {
  return <Suspense><Reviews /></Suspense>;
}
