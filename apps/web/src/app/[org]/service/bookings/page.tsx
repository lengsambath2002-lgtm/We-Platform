'use client';

import { ExternalLink, PowerOff, Store, Unplug } from 'lucide-react';
import Link from 'next/link';

import { VirtualBizMark } from '@/components/common/virtualbiz-mark';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useRetailShops, virtualbiz } from '@/lib/api/sso';
import { useOrg } from '@/lib/org-context';

/**
 * Bookings are taken and managed in VirtualBiz (provider bookings), not copied here. When Retail & Commerce links the
 * organisation's VirtualBiz shops, each opens straight in VirtualBiz with single sign-on; otherwise this says how.
 */
export default function BookingsPage() {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const retail = modules.find((m) => m.key === 'retail');
  const on = !!retail?.available && !!retail.enabled && can('retail.view');
  const shops = useRetailShops(slug, on);

  const intro = (
    <section className="mb-6 flex gap-3 rounded-lg border bg-card p-4">
      <VirtualBizMark className="size-10 shrink-0" aria-hidden />
      <div className="min-w-0 space-y-1 text-sm">
        <p className="font-medium">{t('service.bookingsLiveTitle')}</p>
        <p className="text-muted-foreground">{t('service.bookingsLiveBody')}</p>
      </div>
    </section>
  );

  let body;
  if (!retail?.available) {
    body = <EmptyState icon={Unplug} title={t('service.bookingsNoVbizTitle')} body={t('service.bookingsNoVbizBody')} />;
  } else if (!retail.enabled) {
    body = <EmptyState icon={PowerOff} title={t('service.bookingsRetailOffTitle')} body={t('service.bookingsRetailOffBody')}
      action={can('org.modules.manage') && <Button asChild size="sm"><Link href={`/${slug}/admin/modules`}>{t('modules.manageModules')}</Link></Button>} />;
  } else if (!can('retail.view')) {
    body = <EmptyState icon={Store} title={t('service.bookingsNoAccessTitle')} body={t('service.bookingsNoAccessBody')} />;
  } else if (shops.error) {
    body = <ErrorState error={shops.error} retry={() => shops.refetch()} />;
  } else if (shops.isPending) {
    body = <ListSkeleton rows={2} />;
  } else if (!shops.data.virtualbizUrl || shops.data.shops.length === 0) {
    body = <EmptyState icon={Store} title={t('service.bookingsNoShopsTitle')} body={t('service.bookingsNoShopsBody')}
      action={<Button asChild size="sm" variant="outline"><Link href={`/${slug}/retail`}>{t('service.openRetail')}</Link></Button>} />;
  } else {
    const base = shops.data.virtualbizUrl;
    body = (
      <>
        <ul className="divide-y rounded-lg border bg-card">
          {shops.data.shops.map((s) => (
            <li key={s.businessId} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <VirtualBizMark className="size-10 shrink-0" aria-hidden />
              <p className="min-w-0 flex-1 truncate font-medium">{s.businessName}</p>
              <Button asChild size="sm">
                <a href={virtualbiz.open(base, s.businessId)} aria-label={t('service.openBookingsOf', { name: s.businessName })}>
                  <ExternalLink className="size-4" />{t('service.openBookings')}
                </a>
              </Button>
            </li>
          ))}
        </ul>
        <p className="mt-3 text-xs text-muted-foreground">{t('service.bookingsSsoHint')}</p>
      </>
    );
  }
  return (
    <>
      <PageHeader title={t('service.bookingsTitle')} subtitle={t('service.bookingsSubtitle')} />
      {intro}
      {body}
    </>
  );
}
