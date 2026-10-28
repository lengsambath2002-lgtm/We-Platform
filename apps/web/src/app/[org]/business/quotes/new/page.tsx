'use client';

import { DocumentForm } from '@/components/business/document-form';
import { Crumb } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

/** A new quote (numbered as soon as it is saved). */
export default function NewQuote() {
  const { slug } = useOrg();
  const { t } = useT();
  return (
    <div className="mx-auto max-w-6xl">
      <Crumb href={`/${slug}/business/quotes`} label={t('business.navQuotes')} current={t('business.newQuote')} />
      <PageHeader title={t('business.newQuote')} subtitle={t('business.newQuoteBody')} />
      <DocumentForm org={slug} kind="quote" />
    </div>
  );
}
