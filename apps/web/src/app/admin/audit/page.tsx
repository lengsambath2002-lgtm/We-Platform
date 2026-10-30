'use client';

import { ScrollText } from 'lucide-react';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { AuditSentence, Pager, When } from '@/components/platform/ui';
import { moduleName } from '@/components/shell/nav';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { usePlatformAudit, usePlatformStats } from '@/lib/api/platform';

const SIZE = 50;

export default function PlatformAuditLog() {
  const { t } = useT();
  const [page, setPage] = useState(0);
  const { data, error, isPending, refetch } = usePlatformAudit('', page, SIZE);
  const stats = usePlatformStats();
  const name = (key: string) => {
    const m = stats.data?.modules.find((x) => x.key === key);
    return m ? moduleName(m) : key;
  };

  return (
    <>
      <PageHeader title={t('platform.navAudit')} subtitle={t('platform.auditLead')} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={8} /> : data.items.length === 0 ? (
        <EmptyState icon={ScrollText} title={t('platform.noAudit')} body={t('platform.noAuditBody')} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-44">{t('platform.colWhen')}</TableHead>
                  <TableHead>{t('platform.colWhat')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell className="align-top"><When at={e.at} pattern="d MMM yyyy HH:mm" /></TableCell>
                    <TableCell className="min-w-64 whitespace-normal"><AuditSentence e={e} moduleName={name} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={page} pages={Math.max(1, Math.ceil(data.total / SIZE))} onPage={setPage} />
        </>
      )}
    </>
  );
}
