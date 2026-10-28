'use client';

import { Building2, SearchX, User, UserPlus, UsersRound } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { CustomerDialog } from '@/components/business/dialogs';
import { Money, Pager } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { useCustomers } from '@/lib/api/business';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;

/** Customers: search by name, contact, email, phone or VAT number; archived ones on their own tab. */
export default function Customers() {
  const { slug } = useOrg();
  const { t } = useT();
  const [q, setQ] = useState('');
  const [archived, setArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const { data, error, isPending, refetch } = useCustomers(slug, { q: q.trim() || undefined, archived, page, size: SIZE });
  const href = (id: string) => `/${slug}/business/customers/${id}`;

  return (
    <>
      <PageHeader title={t('business.customersTitle')} subtitle={data ? t('business.count', { count: data.total }) : undefined}
        actions={<Button size="sm" onClick={() => setAdding(true)}><UserPlus className="size-4" />{t('business.newCustomer')}</Button>} />
      <div className="mb-4 grid grid-cols-[1fr_auto] gap-3 sm:flex sm:items-end">
        <div className="space-y-1 sm:w-80">
          <Label htmlFor="c-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
          <Input id="c-q" type="search" value={q} placeholder={t('business.searchCustomers')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="c-a" className="text-xs text-muted-foreground">{t('business.show')}</Label>
          <Select value={archived ? 'archived' : 'active'} onValueChange={(v) => { setArchived(v === 'archived'); setPage(0); }}>
            <SelectTrigger id="c-a" className="w-36"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value="active">{t('business.activeCustomers')}</SelectItem><SelectItem value="archived">{t('business.archivedCustomers')}</SelectItem></SelectContent>
          </Select>
        </div>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.items.length === 0 ? (q || archived
          ? <EmptyState icon={SearchX} title={t('business.noMatchTitle')} body={t('business.noMatchBody')} />
          : <EmptyState icon={UsersRound} title={t('business.noCustomersTitle')} body={t('business.noCustomersBody')}
            action={<Button size="sm" variant="outline" onClick={() => setAdding(true)}>{t('business.newCustomer')}</Button>} />)
          : (
            <>
              <div className="hidden rounded-lg border bg-card md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('business.name')}</TableHead><TableHead>{t('business.contact')}</TableHead>
                      <TableHead>{t('business.taxId')}</TableHead><TableHead className="text-right">{t('business.owes')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.items.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell className="max-w-72"><Link href={href(c.id)} className="flex items-center gap-2 font-medium text-primary hover:underline">
                          {c.kind === 'COMPANY' ? <Building2 className="size-4 shrink-0 text-muted-foreground" /> : <User className="size-4 shrink-0 text-muted-foreground" />}<span className="truncate">{c.name}</span></Link></TableCell>
                        <TableCell className="max-w-64 truncate text-muted-foreground">{[c.contactName, c.phone, c.email].filter(Boolean).join(' · ') || '—'}</TableCell>
                        <TableCell className="text-muted-foreground">{c.taxId ?? '—'}</TableCell>
                        <TableCell className="text-right"><Money value={c.balance} className="items-end" /></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <ul className="divide-y rounded-lg border bg-card md:hidden">
                {data.items.map((c) => (
                  <li key={c.id}>
                    <Link href={href(c.id)} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium">{c.name}</span>
                        <span className="block truncate text-xs text-muted-foreground">{[c.contactName, c.phone].filter(Boolean).join(' · ') || c.email || '—'}</span>
                      </span>
                      <Money value={c.balance} className="shrink-0 items-end text-sm" />
                    </Link>
                  </li>
                ))}
              </ul>
              <Pager page={page} size={SIZE} total={data.total} onPage={setPage} />
            </>
          )}
      {adding && <CustomerDialog org={slug} open onOpenChange={setAdding} />}
    </>
  );
}
