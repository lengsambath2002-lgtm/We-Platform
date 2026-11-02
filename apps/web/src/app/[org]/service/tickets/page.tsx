'use client';

import { LifeBuoy, SearchX, Ticket } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { PRIORITIES, PRIORITY_LABEL } from '@/components/common/badges';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { TicketDialog } from '@/components/service/dialogs';
import { TicketList } from '@/components/service/lists';
import { Pager, SLA_LABEL, TICKET_LABEL } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { TICKET_STATUSES, useTickets } from '@/lib/api/service';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;
const ALL = 'all';
const STATUS_FILTERS = ['OPEN', ...TICKET_STATUSES] as const;
/** The quick views over the filters: what nobody picked up yet (managers), open, mine and everything. */
const VIEWS = { new: ['NEW', 'all'], open: ['OPEN', 'all'], mine: ['OPEN', 'me'], all: ['all', 'all'] } as const;
type View = keyof typeof VIEWS;
const SLA_FILTERS = ['BREACHED', 'AT_RISK'] as const;

/** Tickets, newest first: search, status (open by default), priority, SLA and assignee; `?status=` / `?sla=` preselect. */
function Tickets() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const params = useSearchParams();
  const manage = can('service.manage');
  const startView = (params.get('view') ?? '') in VIEWS && (manage || params.get('view') !== 'new') ? VIEWS[params.get('view') as View] : null;
  const [status, setStatus] = useState<string>(startView?.[0] ?? (STATUS_FILTERS.includes(params.get('status') as never) ? params.get('status')! : 'OPEN'));
  const [sla, setSla] = useState<string>(SLA_FILTERS.includes(params.get('sla') as never) ? params.get('sla')! : ALL);
  const [priority, setPriority] = useState<string>(ALL);
  const [assignee, setAssignee] = useState<string>(startView?.[1] ?? ALL);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [asking, setAsking] = useState(false);
  const { data, error, isPending, refetch } = useTickets(slug, {
    q: q.trim() || undefined, status: status === ALL ? undefined : status, sla: sla === ALL ? undefined : sla,
    priority: priority === ALL ? undefined : priority, assignee: assignee === ALL ? undefined : assignee, page, size: SIZE,
  });
  const filtered = status !== 'OPEN' || sla !== ALL || priority !== ALL || assignee !== ALL || !!q.trim();
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0); };
  const filter = (id: string, label: string, value: string, onChange: (v: string) => void, options: { value: string; label: string }[], width = 'w-40') => (
    <div className="space-y-1">
      <Label htmlFor={id} className="text-xs text-muted-foreground">{label}</Label>
      <Select value={value} onValueChange={reset(onChange)}>
        <SelectTrigger id={id} className={`${width} max-w-full`}><SelectValue /></SelectTrigger>
        <SelectContent>{options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}</SelectContent>
      </Select>
    </div>
  );

  const views = (Object.keys(VIEWS) as View[]).filter((v) => manage || (v !== 'new' && v !== 'mine'));
  const view = views.find((v) => VIEWS[v][0] === status && VIEWS[v][1] === assignee && sla === ALL && priority === ALL) ?? '';
  const pick = (v: string) => { const [st, as] = VIEWS[v as View]; setStatus(st); setAssignee(as); setSla(ALL); setPriority(ALL); setPage(0); };
  const viewLabel: Record<View, string> = { new: t('service.viewNew'), open: t('service.openOnly'), mine: t('service.assignedToMe'), all: t('platform.all') };

  return (
    <>
      <PageHeader title={manage ? t('service.ticketsTitle') : t('service.myTicketsTitle')} subtitle={data ? t('service.count', { count: data.total }) : undefined}
        actions={manage && <Button size="sm" onClick={() => setAsking(true)}><LifeBuoy className="size-4" />{t('service.newRequest')}</Button>} />
      {views.length > 1 && (
        <Tabs value={view} onValueChange={pick} className="mb-4">
          <TabsList variant="line" aria-label={t('service.ticketViews')}>
            {views.map((v) => <TabsTrigger key={v} value={v}>{viewLabel[v]}</TabsTrigger>)}
          </TabsList>
        </Tabs>
      )}
      <div className="mb-4 grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-end">
        <div className="col-span-2 space-y-1 sm:w-72">
          <Label htmlFor="t-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
          <Input id="t-q" type="search" value={q} placeholder={t('service.searchTickets')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        {filter('t-status', t('service.status'), status, setStatus, [
          { value: ALL, label: t('service.anyStatus') }, ...STATUS_FILTERS.map((s) => ({ value: s, label: s === 'OPEN' ? t('service.openOnly') : TICKET_LABEL[s] }))])}
        {filter('t-sla', t('service.sla'), sla, setSla, [{ value: ALL, label: t('service.anySla') }, ...SLA_FILTERS.map((s) => ({ value: s, label: SLA_LABEL[s] }))])}
        {filter('t-priority', t('service.priority'), priority, setPriority, [{ value: ALL, label: t('service.anyPriority') }, ...PRIORITIES.map((p) => ({ value: p, label: PRIORITY_LABEL[p] }))])}
        {manage && filter('t-assignee', t('service.assignee'), assignee, setAssignee, [
          { value: ALL, label: t('service.anyone') }, { value: 'me', label: t('service.assignedToMe') }, { value: 'none', label: t('common.unassigned') }])}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.items.length === 0 ? (filtered
          ? <EmptyState icon={SearchX} title={t('service.noMatchTitle')} body={t('service.noMatchBody')} />
          : <EmptyState icon={Ticket} title={t('service.noTicketsTitle')} body={manage ? t('service.noTicketsBody') : t('service.noTicketsBodyTech')}
            action={manage && <Button size="sm" variant="outline" onClick={() => setAsking(true)}>{t('service.newRequest')}</Button>} />)
          : <><TicketList org={slug} items={data.items} /><Pager page={page} size={SIZE} total={data.total} onPage={setPage} /></>}
      {asking && <TicketDialog org={slug} open onOpenChange={setAsking} onSaved={(x) => router.push(`/${slug}/service/tickets/${x.summary.id}`)} />}
    </>
  );
}

export default function Page() {
  return <Suspense><Tickets /></Suspense>;
}
