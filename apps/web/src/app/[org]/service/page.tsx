'use client';

import { AlertTriangle, ClipboardList, Hammer, LifeBuoy, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { TicketDialog } from '@/components/service/dialogs';
import { TicketList, WorkOrderList } from '@/components/service/lists';
import { TicketsChart } from '@/components/service/tickets-chart';
import { Panel, Stat, TICKET_LABEL } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@/i18n/provider';
import { OPEN_TICKET, TICKET_STATUSES, useSvcOverview } from '@/lib/api/service';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** The service dashboard: tickets by status, SLA breaches, this week's work orders, opened vs resolved, maintenance due. */
export default function ServiceHome() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = useSvcOverview(slug);
  const [asking, setAsking] = useState(false);
  const base = `/${slug}/service`;
  const manage = can('service.manage');

  return (
    <>
      <PageHeader title={t('service.homeTitle')} subtitle={manage ? t('service.homeSubtitle') : t('service.homeSubtitleTech')}
        actions={manage && <Button size="sm" onClick={() => setAsking(true)}><LifeBuoy className="size-4" />{t('service.newRequest')}</Button>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? (
        <div className="space-y-6"><div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-24" />)}</div><ListSkeleton rows={4} /></div>
      ) : (
        <div className="space-y-8">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <Stat href={`${base}/tickets?status=OPEN`} label={manage ? t('service.openTickets') : t('service.myOpenTickets')} value={data.openTickets}
              note={t('service.newCount', { count: data.ticketsByStatus.NEW })} />
            <Stat href={`${base}/tickets?sla=BREACHED`} label={t('service.slaBreached')} value={data.breached} tone={data.breached > 0 ? 'danger' : undefined}
              note={t('service.atRiskCount', { count: data.atRisk })} />
            <Stat href={`${base}/work-orders`} label={manage ? t('service.woThisWeek') : t('service.myWoThisWeek')} value={data.workOrdersThisWeek.length} />
            {manage
              ? <Stat href={`/${slug}/approvals`} label={t('service.awaitingApproval')} value={data.awaitingApproval} tone={data.awaitingApproval > 0 ? 'warning' : undefined} />
              : <Stat href={`${base}/tickets?sla=AT_RISK`} label={t('service.slaAtRisk')} value={data.atRisk} tone={data.atRisk > 0 ? 'warning' : undefined} />}
          </div>
          <section aria-labelledby="by-status-h" className="space-y-3">
            <h2 id="by-status-h" className="text-base font-semibold">{t('service.ticketsByStatus')}</h2>
            <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
              {TICKET_STATUSES.map((s) => (
                <li key={s}>
                  <Link href={`${base}/tickets?status=${s}`} className="flex items-center justify-between rounded-md border bg-card px-3 py-2 text-sm hover:bg-muted/40">
                    <span className="text-muted-foreground">{TICKET_LABEL[s]}</span>
                    <span className={cn('font-semibold tabular', OPEN_TICKET.includes(s) && data.ticketsByStatus[s] > 0 && 'text-foreground')}>{data.ticketsByStatus[s]}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
          <div className="grid gap-8 lg:grid-cols-2">
            <Panel id="breach-h" title={t('service.breachedTickets')}
              action={<Link href={`${base}/tickets?sla=BREACHED`} className="text-sm text-primary hover:underline">{t('service.allBreached')}</Link>}>
              {data.breachedTickets.length === 0
                ? <EmptyState icon={ShieldCheck} title={t('service.noBreachesTitle')} body={t('service.noBreachesBody')} />
                : <TicketList org={slug} items={data.breachedTickets} hideCustomer />}
            </Panel>
            <Panel id="week-h" title={t('service.thisWeek')}
              action={<Link href={`${base}/work-orders`} className="text-sm text-primary hover:underline">{t('service.allWorkOrders')}</Link>}>
              {data.workOrdersThisWeek.length === 0
                ? <EmptyState icon={ClipboardList} title={t('service.noWeekTitle')} body={manage ? t('service.noWeekBody') : t('service.noWeekBodyTech')}
                  action={manage && <Button asChild size="sm" variant="outline"><Link href={`${base}/work-orders/new`}>{t('service.newWorkOrder')}</Link></Button>} />
                : <WorkOrderList org={slug} items={data.workOrdersThisWeek} />}
            </Panel>
          </div>
          {manage && data.weeks.length > 0 && <TicketsChart weeks={data.weeks} />}
          {manage && (
            <Panel id="pm-h" title={t('service.upcomingMaintenance')}
              action={<Link href={`${base}/maintenance`} className="text-sm text-primary hover:underline">{t('service.allPlans')}</Link>}>
              {data.upcomingMaintenance.length === 0
                ? <EmptyState icon={Hammer} title={t('service.noUpcomingTitle')} body={t('service.noUpcomingBody')} />
                : (
                  <ul className="divide-y rounded-lg border bg-card">
                    {data.upcomingMaintenance.map((p) => (
                      <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
                        <span className="min-w-0"><span className="block truncate font-medium">{p.title}</span>
                          <span className="block truncate text-xs text-muted-foreground">{[p.asset?.name, p.customer?.name].filter(Boolean).join(' · ')}</span></span>
                        <span className="text-muted-foreground">{t('service.dueOn', { date: day(p.nextDue) })}</span>
                      </li>
                    ))}
                  </ul>
                )}
            </Panel>
          )}
          {data.breached > 0 && (
            <p className="flex items-center gap-2 text-sm text-destructive"><AlertTriangle className="size-4" />
              <Link href={`${base}/tickets?sla=BREACHED`} className="hover:underline">{t('service.breachNotice', { count: data.breached })}</Link></p>
          )}
        </div>
      )}
      {asking && <TicketDialog org={slug} open onOpenChange={setAsking} />}
    </>
  );
}
