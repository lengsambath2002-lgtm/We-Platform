'use client';

import { Package, Plus } from 'lucide-react';
import { useState } from 'react';

import { ProductDialog } from '@/components/business/dialogs';
import { Money, productName } from '@/components/business/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { type Product, useProducts } from '@/lib/api/business';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** Products and services with their USD price; admins add and change them, everyone picks them on documents. */
export default function Products() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const manage = can('business.manage');
  const { data, error, isPending, refetch } = useProducts(slug, manage);
  const [q, setQ] = useState('');
  const [editing, setEditing] = useState<Product | 'new' | null>(null);
  const needle = q.trim().toLowerCase();
  const shown = (data ?? []).filter((p) => !needle || p.nameEn.toLowerCase().includes(needle) || (p.nameKm ?? '').toLowerCase().includes(needle));

  return (
    <>
      <PageHeader title={t('business.productsTitle')} subtitle={t('business.productsSubtitle')}
        actions={manage && <Button size="sm" onClick={() => setEditing('new')}><Plus className="size-4" />{t('business.newProduct')}</Button>} />
      <div className="mb-4 sm:w-80">
        <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('business.searchProducts')} aria-label={t('common.search')} />
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={5} />
        : shown.length === 0 ? (
          <EmptyState icon={Package} title={needle ? t('business.noMatchTitle') : t('business.noProductsTitle')}
            body={needle ? t('business.noMatchBody') : manage ? t('business.noProductsBody') : t('business.noProductsMember')}
            action={!needle && manage && <Button size="sm" variant="outline" onClick={() => setEditing('new')}>{t('business.newProduct')}</Button>} />
        ) : (
          <ul className="divide-y rounded-lg border bg-card">
            {shown.map((p) => (
              <li key={p.id} className={cn('flex items-center justify-between gap-3 px-4 py-3', !p.active && 'opacity-60')}>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium">{productName(p)}</span>
                  <span className="block truncate text-xs text-muted-foreground">
                    {t('business.perUnit', { unit: p.unit })} · {p.vat ? t('business.vatApplies') : t('business.vatExempt')}{!p.active ? ` · ${t('business.inactive')}` : ''}
                  </span>
                </span>
                <span className="flex shrink-0 items-center gap-3">
                  <Money value={p.unitPrice} className="items-end text-sm" />
                  {manage && <Button size="sm" variant="ghost" onClick={() => setEditing(p)} aria-label={t('business.editNamed', { name: productName(p) })}>{t('common.edit')}</Button>}
                </span>
              </li>
            ))}
          </ul>
        )}
      {editing && <ProductDialog key={editing === 'new' ? 'new' : editing.id} org={slug} product={editing === 'new' ? undefined : editing} open onOpenChange={(v) => !v && setEditing(null)} />}
    </>
  );
}
