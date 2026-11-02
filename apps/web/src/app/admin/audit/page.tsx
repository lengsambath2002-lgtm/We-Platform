'use client';

import { parseISO } from 'date-fns';
import { ScrollText } from 'lucide-react';
import { useState } from 'react';

import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { AuditRow, ConsoleHeader, Pager, type PlatformAuditEntryGroup } from '@/components/platform/ui';
import { moduleName } from '@/components/shell/nav';
import { useT } from '@/i18n/provider';
import { usePlatformAudit, usePlatformStats } from '@/lib/api/platform';
import { fmt } from '@/lib/format';

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

  // The page's entries by day, newest first (the API already orders them).
  const days: PlatformAuditEntryGroup[] = [];
  for (const e of data?.items ?? []) {
    const key = e.at.slice(0, 10);
    const last = days[days.length - 1];
    if (last?.key === key) last.items.push(e); else days.push({ key, label: fmt(parseISO(e.at), 'EEEE, d MMMM yyyy'), items: [e] });
  }

  return (
    <>
      <ConsoleHeader title={t('platform.navAudit')} subtitle={t('platform.auditLead')} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={8} /> : data.items.length === 0 ? (
        <EmptyState icon={ScrollText} title={t('platform.noAudit')} body={t('platform.noAuditBody')} />
      ) : (
        <div className="overflow-hidden rounded-xl border bg-card shadow-xs">
          {days.map((d) => (
            <section key={d.key} className="border-b last:border-b-0">
              <h2 className="border-b bg-muted/40 px-5 py-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">{d.label}</h2>
              <ul className="px-5 py-4">{d.items.map((e) => <AuditRow key={e.id} e={e} moduleName={name} pattern="HH:mm" />)}</ul>
            </section>
          ))}
          <div className="bg-muted/20">
            <Pager page={page} pages={Math.max(1, Math.ceil(data.total / SIZE))} onPage={setPage} total={data.total} size={SIZE} />
          </div>
        </div>
      )}
    </>
  );
}
