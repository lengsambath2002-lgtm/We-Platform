'use client';

import { useParams } from 'next/navigation';

import { DocumentForm } from '@/components/business/document-form';
import { Crumb } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { useT } from '@/i18n/provider';
import { useQuote } from '@/lib/api/business';
import { useOrg } from '@/lib/org-context';
import { Lock } from 'lucide-react';

/** Edits a draft or sent quote (not once accepted, declined or converted). */
export default function EditQuote() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useQuote(slug, id);
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  return (
    <div className="mx-auto max-w-6xl">
      <Crumb href={`/${slug}/business/quotes/${id}`} label={data.number} current={t('common.edit')} />
      <PageHeader title={t('business.editQuote')} subtitle={data.customer.name} />
      {data.canEdit ? <DocumentForm org={slug} kind="quote" quote={data} />
        : <EmptyState icon={Lock} title={t('business.cantEditTitle')} body={t('business.cantEditQuote')} />}
    </div>
  );
}
