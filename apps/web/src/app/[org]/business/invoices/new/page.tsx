'use client';

import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

import { DocumentForm } from '@/components/business/document-form';
import { Crumb } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

/** A new draft invoice (`?customer=` preselects the customer); it gets its number when issued. */
function NewInvoice() {
  const { slug } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  return (
    <div className="mx-auto max-w-6xl">
      <Crumb href={`/${slug}/business/invoices`} label={t('business.navInvoices')} current={t('business.newInvoice')} />
      <PageHeader title={t('business.newInvoice')} subtitle={t('business.newInvoiceBody')} />
      <DocumentForm org={slug} kind="invoice" customerId={params.get('customer') ?? undefined} />
    </div>
  );
}

export default function Page() {
  return <Suspense><NewInvoice /></Suspense>;
}
