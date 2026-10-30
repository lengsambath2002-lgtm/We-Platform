'use client';

import { Inbox, LifeBuoy } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { TicketDialog } from '@/components/service/dialogs';
import { TicketList } from '@/components/service/lists';
import { Pager } from '@/components/service/ui';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useTickets } from '@/lib/api/service';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;

/** Service requests: what came in and nobody triaged yet (NEW), oldest SLA first in the list; take a new one here. */
export default function RequestsPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [asking, setAsking] = useState(false);
  const { data, error, isPending, refetch } = useTickets(slug, { status: 'NEW', page, size: SIZE });
  if (!can('service.manage')) return <PermissionDenied />;
  const action = <Button size="sm" onClick={() => setAsking(true)}><LifeBuoy className="size-4" />{t('service.newRequest')}</Button>;
  return (
    <>
      <PageHeader title={t('service.requestsTitle')} subtitle={t('service.requestsSubtitle')} actions={action} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={5} />
        : data.items.length === 0
          ? <EmptyState icon={Inbox} title={t('service.noRequestsTitle')} body={t('service.noRequestsBody')} action={action} />
          : <><TicketList org={slug} items={data.items} /><Pager page={page} size={SIZE} total={data.total} onPage={setPage} /></>}
      {asking && <TicketDialog org={slug} open onOpenChange={setAsking} onSaved={(x) => router.push(`/${slug}/service/tickets/${x.summary.id}`)} />}
    </>
  );
}
