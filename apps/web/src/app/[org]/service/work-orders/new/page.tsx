'use client';

import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Crumb } from '@/components/service/ui';
import { WorkOrderForm } from '@/components/service/work-order-form';
import { useT } from '@/i18n/provider';
import { useTicket } from '@/lib/api/service';
import { useOrg } from '@/lib/org-context';

/** A new work order; `?ticket=` starts it from a ticket (its title, customer, site and asset). */
function NewWorkOrder() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const ticketId = useSearchParams().get('ticket');
  const ticket = useTicket(slug, ticketId ?? '');
  if (!can('service.manage')) return <PermissionDenied />;
  if (ticketId && ticket.error) return <ErrorState error={ticket.error} retry={() => ticket.refetch()} />;
  if (ticketId && ticket.isPending) return <ListSkeleton />;
  return (
    <>
      <Crumb href={`/${slug}/service/work-orders`} label={t('service.navWorkOrders')} current={t('service.newWorkOrder')} />
      <PageHeader title={t('service.newWorkOrder')} subtitle={ticket.data ? t('service.fromTicket', { number: ticket.data.summary.number }) : t('service.newWorkOrderBody')} />
      <WorkOrderForm org={slug} ticket={ticketId ? ticket.data : undefined} />
    </>
  );
}

export default function Page() {
  return <Suspense><NewWorkOrder /></Suspense>;
}
