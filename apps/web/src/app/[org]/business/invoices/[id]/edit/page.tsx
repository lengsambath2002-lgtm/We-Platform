'use client';

import { useParams } from 'next/navigation';

import { DocumentForm } from '@/components/business/document-form';
import { Crumb } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { useT } from '@/i18n/provider';
import { useInvoice } from '@/lib/api/business';
import { useOrg } from '@/lib/org-context';
import { Lock } from 'lucide-react';

/** Edits a draft invoice; issued ones are fixed (void and issue a new one). */
export default function EditInvoice() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useInvoice(slug, id);
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  return (
    <div className="mx-auto max-w-6xl">
      <Crumb href={`/${slug}/business/invoices/${id}`} label={data.number ?? t('business.draftInvoice')} current={t('common.edit')} />
      <PageHeader title={t('business.editInvoice')} subtitle={data.customer.name} />
      {data.canEdit ? <DocumentForm org={slug} kind="invoice" invoice={data} />
        : <EmptyState icon={Lock} title={t('business.cantEditTitle')} body={t('business.cantEditInvoice')} />}
    </div>
  );
}
