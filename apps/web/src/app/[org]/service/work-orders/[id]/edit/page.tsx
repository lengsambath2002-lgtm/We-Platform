'use client';

import { useParams } from 'next/navigation';

import { PageHeader } from '@/components/common/page-header';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Crumb } from '@/components/service/ui';
import { WorkOrderForm } from '@/components/service/work-order-form';
import { useT } from '@/i18n/provider';
import { useWorkOrder } from '@/lib/api/service';
import { useOrg } from '@/lib/org-context';

export default function EditWorkOrder() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useWorkOrder(slug, id);
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  if (!data.canEdit) return <PermissionDenied />;
  return (
    <>
      <Crumb href={`/${slug}/service/work-orders/${id}`} label={data.summary.number} current={t('common.edit')} />
      <PageHeader title={t('service.editWorkOrder', { number: data.summary.number })} />
      <WorkOrderForm org={slug} workOrder={data} />
    </>
  );
}
