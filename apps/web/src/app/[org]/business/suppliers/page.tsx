'use client';

import { Plus, Truck } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { SupplierDialog } from '@/components/business/purchasing-ui';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { useSuppliers } from '@/lib/api/purchasing';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

/** The organisation's suppliers, with what is owed to each and this year's spend. */
export default function Suppliers() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const [archived, setArchived] = useState(false);
  const { data, error, isPending, refetch } = useSuppliers(slug, archived);
  const [creating, setCreating] = useState(false);
  if (!(can('business.view') || can('retail.view'))) return <PermissionDenied />;
  return (
    <>
      <PageHeader title={t('retail.suppliersTitle')} subtitle={t('retail.suppliersSubtitle')}
        actions={<><Button variant={archived ? 'secondary' : 'outline'} size="sm" aria-pressed={archived} onClick={() => setArchived(!archived)}>{t('retail.showArchived')}</Button>
          {(can('business.manage') || can('retail.manage')) && <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />{t('retail.newSupplier')}</Button>}</>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={4} />
        : data.length === 0 ? <EmptyState icon={Truck} title={t('retail.noSuppliers')} body={archived ? undefined : t('retail.noSuppliersBody')} /> : (
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader><TableRow><TableHead>{t('retail.supplier')}</TableHead><TableHead>{t('retail.contactName')}</TableHead><TableHead className="text-right">{t('retail.openOrders')}</TableHead><TableHead className="text-right">{t('retail.spentThisYear')}</TableHead><TableHead className="text-right">{t('retail.owed')}</TableHead></TableRow></TableHeader>
              <TableBody>{data.map((s) => (
                <TableRow key={s.id}>
                  <TableCell><Link className="font-medium text-primary hover:underline" href={`/${slug}/business/suppliers/${s.id}`}>{s.name}</Link></TableCell>
                  <TableCell className="text-sm">{[s.contactName, s.phone].filter(Boolean).join(' · ')}</TableCell>
                  <TableCell className="text-right tabular">{s.openOrders}</TableCell>
                  <TableCell className="text-right tabular">{usd(s.spentThisYear)}</TableCell>
                  <TableCell className="text-right font-medium tabular">{s.owed > 0 ? usd(s.owed) : ''}</TableCell>
                </TableRow>
              ))}</TableBody>
            </Table>
          </div>
        )}
      {creating && <SupplierDialog org={slug} supplier="new" onClose={() => setCreating(false)} />}
    </>
  );
}
