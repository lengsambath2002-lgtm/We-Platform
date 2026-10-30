'use client';

import Link from 'next/link';

import { PersonAvatar } from '@/components/common/person';
import { PriorityIcon } from '@/components/common/badges';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import type { TicketSummary, WorkOrderSummary } from '@/lib/api/service';
import { ago } from '@/lib/format';
import { usd } from '@/lib/money';

import { CATEGORY_LABEL, SlaBadge, TicketBadge, WoBadge, windowText } from './ui';

/** Tickets: a table on wide screens, cards on phones; each opens its page. The SLA column is the server's verdict. */
export function TicketList({ org, items, hideCustomer }: { org: string; items: TicketSummary[]; hideCustomer?: boolean }) {
  const { t } = useT();
  const href = (x: TicketSummary) => `/${org}/service/tickets/${x.id}`;
  return (
    <>
      <div className="hidden rounded-lg border bg-card md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('service.number')}</TableHead>
              <TableHead>{t('service.ticketTitle')}</TableHead>
              {!hideCustomer && <TableHead>{t('service.customer')}</TableHead>}
              <TableHead>{t('service.priority')}</TableHead>
              <TableHead>{t('service.status')}</TableHead>
              <TableHead>{t('service.sla')}</TableHead>
              <TableHead>{t('service.assignee')}</TableHead>
              <TableHead className="text-right">{t('service.opened')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((x) => (
              <TableRow key={x.id}>
                <TableCell className="font-medium"><Link href={href(x)} className="text-primary hover:underline">{x.number}</Link></TableCell>
                <TableCell className="max-w-72"><span className="block truncate">{x.title}</span><span className="text-xs text-muted-foreground">{CATEGORY_LABEL[x.category]}</span></TableCell>
                {!hideCustomer && <TableCell className="max-w-48 truncate">{x.customer?.name ?? '—'}</TableCell>}
                <TableCell><PriorityIcon priority={x.priority} withLabel /></TableCell>
                <TableCell><TicketBadge status={x.status} /></TableCell>
                <TableCell><SlaBadge sla={x.sla} /></TableCell>
                <TableCell>{x.assignee ? <span className="inline-flex items-center gap-2"><PersonAvatar person={x.assignee} className="size-5" /><span className="truncate">{x.assignee.name}</span></span> : <span className="text-muted-foreground">{t('common.unassigned')}</span>}</TableCell>
                <TableCell className="whitespace-nowrap text-right text-muted-foreground">{ago(x.createdAt)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <ul className="divide-y rounded-lg border bg-card md:hidden">
        {items.map((x) => (
          <li key={x.id}>
            <Link href={href(x)} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
              <span className="min-w-0">
                <span className="block text-sm font-medium">{x.number} · <span className="font-normal">{x.title}</span></span>
                <span className="block truncate text-xs text-muted-foreground">{[x.customer?.name, x.assignee?.name ?? t('common.unassigned')].filter(Boolean).join(' · ')}</span>
              </span>
              <span className="flex shrink-0 flex-col items-end gap-1">
                <TicketBadge status={x.status} />
                {x.sla !== 'ON_TRACK' && <SlaBadge sla={x.sla} />}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}

/** Work orders: number, title, status, window, technicians, checklist progress and total. */
export function WorkOrderList({ org, items }: { org: string; items: WorkOrderSummary[] }) {
  const { t } = useT();
  const href = (x: WorkOrderSummary) => `/${org}/service/work-orders/${x.id}`;
  const techs = (x: WorkOrderSummary) => (x.technicians.length ? x.technicians.map((p) => p.name).join(', ') : t('service.noTechnician'));
  return (
    <>
      <div className="hidden rounded-lg border bg-card md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('service.number')}</TableHead>
              <TableHead>{t('service.woTitle')}</TableHead>
              <TableHead>{t('service.status')}</TableHead>
              <TableHead>{t('service.when')}</TableHead>
              <TableHead>{t('service.technicians')}</TableHead>
              <TableHead>{t('service.checklist')}</TableHead>
              <TableHead className="text-right">{t('service.total')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((x) => (
              <TableRow key={x.id}>
                <TableCell className="font-medium"><Link href={href(x)} className="text-primary hover:underline">{x.number}</Link></TableCell>
                <TableCell className="max-w-72">
                  <span className="block truncate">{x.title}</span>
                  <span className="block truncate text-xs text-muted-foreground">{[x.preventive ? t('service.preventive') : null, x.customer?.name, x.asset?.name].filter(Boolean).join(' · ')}</span>
                </TableCell>
                <TableCell><span className="flex flex-col items-start gap-1"><WoBadge status={x.status} />{x.approvalState === 'PENDING' && <span className="text-xs text-warning">{t('service.awaitingApproval')}</span>}</span></TableCell>
                <TableCell className="whitespace-nowrap text-sm">{windowText(x.scheduledStart, x.scheduledEnd)}</TableCell>
                <TableCell className="max-w-48 truncate">{techs(x)}</TableCell>
                <TableCell className="tabular text-muted-foreground">{x.checklistTotal ? `${x.checklistDone}/${x.checklistTotal}` : '—'}</TableCell>
                <TableCell className="text-right tabular">{usd(x.total)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <ul className="divide-y rounded-lg border bg-card md:hidden">
        {items.map((x) => (
          <li key={x.id}>
            <Link href={href(x)} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
              <span className="min-w-0">
                <span className="block text-sm font-medium">{x.number} · <span className="font-normal">{x.title}</span></span>
                <span className="block truncate text-xs text-muted-foreground">{windowText(x.scheduledStart, x.scheduledEnd)}</span>
                <span className="block truncate text-xs text-muted-foreground">{techs(x)}</span>
              </span>
              <span className="flex shrink-0 flex-col items-end gap-1">
                <WoBadge status={x.status} />
                <span className="text-sm tabular">{usd(x.total)}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
