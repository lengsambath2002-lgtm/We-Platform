'use client';

import { RouteTabs } from '@/components/common/route-tabs';
import { useT } from '@/i18n/provider';

/** Assets and the customers (with their sites) they belong to. */
export function AssetTabs() {
  const { t } = useT();
  return <RouteTabs tabs={[{ href: 'service/assets', label: t('service.navAssets') }, { href: 'service/customers', label: t('service.customersAndSites') }]} />;
}
