'use client';

import { FileSignature, SearchX } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { QuoteList } from '@/components/business/lists';
import { QUOTE_LABEL, Pager } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { QUOTE_STATUSES, useQuotes } from '@/lib/api/business';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;
const ALL = 'all';
const FILTERS = [...QUOTE_STATUSES, 'EXPIRED'] as const;

/** Quotes, newest first: search by number or customer, filter by status (Expired included). */
function Quotes() {
  const { slug } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const initial = params.get('status');
  const [status, setStatus] = useState<string>(FILTERS.includes(initial as never) ? initial! : ALL);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const { data, error, isPending, refetch } = useQuotes(slug, { q: q.trim() || undefined, status: status === ALL ? undefined : status, page, size: SIZE });
  const filtered = status !== ALL || !!q.trim();

  return (
    <>
      <PageHeader title={t('business.quotesTitle')} subtitle={data ? t('business.count', { count: data.total }) : undefined}
        actions={<Button size="sm" asChild><Link href={`/${slug}/business/quotes/new`}><FileSignature className="size-4" />{t('business.newQuote')}</Link></Button>} />
      <div className="mb-4 grid grid-cols-[1fr_auto] gap-3 sm:flex sm:items-end">
        <div className="space-y-1 sm:w-80">
          <Label htmlFor="i-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
          <Input id="i-q" type="search" value={q} placeholder={t('business.searchDocs')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="i-s" className="text-xs text-muted-foreground">{t('business.status')}</Label>
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
            <SelectTrigger id="i-s" className="w-40"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value={ALL}>{t('business.anyStatus')}</SelectItem>{FILTERS.map((s) => <SelectItem key={s} value={s}>{QUOTE_LABEL[s]}</SelectItem>)}</SelectContent>
          </Select>
        </div>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.items.length === 0 ? (filtered
          ? <EmptyState icon={SearchX} title={t('business.noMatchTitle')} body={t('business.noMatchBody')} />
          : <EmptyState icon={FileSignature} title={t('business.noQuotesTitle')} body={t('business.noQuotesBody')}
            action={<Button size="sm" variant="outline" asChild><Link href={`/${slug}/business/quotes/new`}>{t('business.newQuote')}</Link></Button>} />)
          : <><QuoteList org={slug} items={data.items} /><Pager page={page} size={SIZE} total={data.total} onPage={setPage} /></>}
    </>
  );
}

export default function Page() {
  return <Suspense><Quotes /></Suspense>;
}
