'use client';

import { CalendarClock, Handshake, Plus } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { DealDialog, DealStageBadge, dealWho } from '@/components/business/crm-ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { DEAL_STAGES, usePipeline } from '@/lib/api/crm';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useSession } from '@/lib/auth/session';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** The sales pipeline: deals by stage with their totals, the weighted forecast, this month's wins and the win rate. */
export default function Deals() {
  const { slug } = useOrg();
  const { t } = useT();
  const { user } = useSession();
  const { data, error, isPending, refetch } = usePipeline(slug);
  const [mine, setMine] = useState(false);
  const [creating, setCreating] = useState(false);
  const tile = (label: string, value: string, hint?: string) => (
    <div className="rounded-lg border bg-card p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="mt-0.5 text-lg font-semibold tabular">{value}</p>{hint && <p className="text-xs text-muted-foreground">{hint}</p>}</div>
  );

  return (
    <>
      <PageHeader title={t('business.dealsTitle')} subtitle={t('business.dealsSubtitle')}
        actions={<><Button variant={mine ? 'secondary' : 'outline'} size="sm" aria-pressed={mine} onClick={() => setMine(!mine)}>{t('business.myDeals')}</Button>
          <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />{t('business.newDeal')}</Button></>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
            {tile(t('business.openPipeline'), usd(data.openValue))}
            {tile(t('business.weightedForecast'), usd(data.weighted), t('business.weightedHint'))}
            {tile(t('business.wonThisMonth'), usd(data.wonThisMonth), t('business.dealsCount', { count: data.wonCountThisMonth }))}
            {tile(t('business.winRate'), data.winRate === null ? '—' : `${data.winRate}%`, t('business.winRateHint'))}
          </div>
          {data.deals.length === 0 ? <EmptyState icon={Handshake} title={t('business.noDeals')} body={t('business.noDealsBody')} /> : (
            <div className="-mx-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
              <div className="grid min-w-[66rem] grid-cols-6 gap-3">
                {DEAL_STAGES.map((s) => {
                  const total = data.stages.find((x) => x.stage === s);
                  const here = data.deals.filter((d) => d.stage === s && (!mine || d.owner.id === user?.id));
                  return (
                    <section key={s} aria-label={t(`business.stage_${s}`)} className="rounded-lg bg-muted/40 p-2">
                      <h2 className="mb-2 px-1">
                        <span className="flex items-center justify-between text-xs"><DealStageBadge stage={s} /><span className="tabular text-muted-foreground">{here.length}</span></span>
                        <span className="mt-1 block text-xs text-muted-foreground tabular">{usd(total?.value ?? 0)}</span>
                      </h2>
                      <ul className="space-y-2">
                        {here.map((d) => (
                          <li key={d.id}>
                            <Link href={`/${slug}/business/deals/${d.id}`} className="block rounded-md border bg-card p-2.5 text-sm shadow-sm hover:border-primary/40">
                              <span className="block truncate font-medium">{d.title}</span>
                              <span className="block truncate text-xs text-muted-foreground">{dealWho(d)}</span>
                              <span className="mt-1 flex items-center justify-between gap-2 text-xs">
                                <span className="font-medium tabular">{usd(d.value)}</span>
                                {d.nextStepDate && <span className={cn('inline-flex items-center gap-1', d.followUpDue ? 'text-destructive' : 'text-muted-foreground')}><CalendarClock className="size-3" />{day(d.nextStepDate)}</span>}
                              </span>
                            </Link>
                          </li>
                        ))}
                      </ul>
                    </section>
                  );
                })}
              </div>
            </div>
          )}
        </>
      )}
      {creating && <DealDialog org={slug} deal="new" onClose={() => setCreating(false)} />}
    </>
  );
}
