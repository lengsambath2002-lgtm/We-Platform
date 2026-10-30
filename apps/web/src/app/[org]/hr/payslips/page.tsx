'use client';

import { ChevronRight, ReceiptText } from 'lucide-react';
import Link from 'next/link';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { monthText } from '@/components/hr/payroll-ui';
import { useT } from '@/i18n/provider';
import { useMyPayslips } from '@/lib/api/payroll';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** My payslips, from paid months, newest first. */
export default function MyPayslips() {
  const { slug } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = useMyPayslips(slug);
  return (
    <>
      <PageHeader title={t('hr.myPayslips')} subtitle={t('hr.myPayslipsBody')} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={3} />
        : data.length === 0 ? <EmptyState icon={ReceiptText} title={t('hr.noPayslips')} body={t('hr.noPayslipsBody')} /> : (
          <ul className="divide-y rounded-lg border bg-card">
            {data.map((s) => (
              <li key={s.id}>
                <Link href={`/${slug}/hr/payslips/${s.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50">
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-medium">{monthText(s.month)}</span>
                    <span className="block text-xs text-muted-foreground">{t('hr.grossPay')} {usd(s.gross)}</span>
                  </span>
                  <span className="text-right"><span className="block text-xs text-muted-foreground">{t('hr.netPay')}</span><span className="font-semibold tabular">{usd(s.net)}</span></span>
                  <ChevronRight className="size-4 text-muted-foreground" />
                </Link>
              </li>
            ))}
          </ul>
        )}
    </>
  );
}
