'use client';

import { ExternalLink, Link2Off, Plug, PowerOff, Store, Unplug } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import {
  AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type LinkedShop, useRetailShops, useUnlinkShop, virtualbiz } from '@/lib/api/sso';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

/** Confirms unlinking a shop on the platform (VirtualBiz keeps it). */
function UnlinkDialog({ shop, onClose }: { shop: LinkedShop | null; onClose: () => void }) {
  const { t } = useT();
  const { slug } = useOrg();
  const unlink = useUnlinkShop(slug);
  const [error, setError] = useState<string | null>(null);
  const close = () => { setError(null); onClose(); };
  return (
    <AlertDialog open={!!shop} onOpenChange={(v) => { if (!v) close(); }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('retail.unlinkTitle', { name: shop?.businessName ?? '' })}</AlertDialogTitle>
          <AlertDialogDescription>{t('retail.unlinkBody')}</AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
          <Button variant="destructive" disabled={unlink.isPending} onClick={async () => {
            if (!shop) return;
            setError(null);
            try {
              await unlink.mutateAsync(shop.businessId);
              toast.success(t('retail.unlinked', { name: shop.businessName }));
              close();
            } catch (e) { setError(errorMessage(e)); }
          }}>{t(unlink.isPending ? 'retail.unlinking' : 'retail.unlink')}</Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

/** Retail & Commerce: the organisation's VirtualBiz shops, opened and connected through single sign-on. */
export default function RetailPage() {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const mod = modules.find((m) => m.key === 'retail');
  const on = !!mod?.available && !!mod.enabled;
  const shops = useRetailShops(slug, on && can('retail.view'));
  const [unlinking, setUnlinking] = useState<LinkedShop | null>(null);

  if (!mod?.available) {
    return (
      <>
        <PageHeader title={t('retail.title')} />
        <EmptyState icon={Unplug} title={t('retail.notConfiguredTitle')} body={t('retail.notConfiguredBody')} />
      </>
    );
  }
  if (!mod.enabled) {
    return (
      <>
        <PageHeader title={t('retail.title')} />
        <EmptyState icon={PowerOff} title={t('common.moduleOffTitle')} body={t('retail.moduleOffBody')}
          action={can('org.modules.manage') && <Button asChild size="sm"><Link href={`/${slug}/admin/modules`}>{t('modules.manageModules')}</Link></Button>} />
      </>
    );
  }
  if (!can('retail.view')) return <PermissionDenied />;

  const data = shops.data;
  const manage = !!data?.canManage && !!data.virtualbizUrl;
  const connect = manage && data?.virtualbizUrl ? (
    <Button asChild><a href={virtualbiz.connect(data.virtualbizUrl, data.orgId)}><Plug className="size-4" />{t('retail.connect')}</a></Button>
  ) : null;

  return (
    <>
      <PageHeader title={t('retail.title')}
        subtitle={data && data.shops.length > 0 ? t('retail.shopsCount', { count: data.shops.length }) : t('retail.subtitle')}
        actions={data && data.shops.length > 0 ? connect : null} />
      {shops.error ? <ErrorState error={shops.error} retry={() => shops.refetch()} />
        : shops.isPending ? <ListSkeleton rows={3} />
          : !data!.configured || !data!.virtualbizUrl ? (
            <EmptyState icon={Unplug} title={t('retail.notConfiguredTitle')} body={t('retail.notConfiguredBody')} />
          ) : data!.shops.length === 0 ? (
            <EmptyState icon={Store} title={t('retail.emptyTitle')} body={t(manage ? 'retail.emptyAdmin' : 'retail.emptyMember')} action={connect} />
          ) : (
            <>
              <ul className="divide-y rounded-lg border bg-card">
                {data!.shops.map((s) => (
                  <li key={s.businessId} className="flex flex-wrap items-center gap-3 px-4 py-3">
                    <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary"><Store className="size-5" aria-hidden /></span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{s.businessName}</p>
                      <p className="text-xs text-muted-foreground">
                        {s.linkedBy ? t('retail.linkedBy', { name: s.linkedBy.name, date: day(s.linkedAt) }) : t('retail.linkedOn', { date: day(s.linkedAt) })}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      <Button asChild size="sm" variant="outline">
                        <a href={virtualbiz.open(data!.virtualbizUrl!, s.businessId)} aria-label={t('retail.openOf', { name: s.businessName })}>
                          <ExternalLink className="size-4" />{t('retail.open')}
                        </a>
                      </Button>
                      {data!.canManage && (
                        <Button size="sm" variant="ghost" aria-label={t('retail.unlinkOf', { name: s.businessName })} onClick={() => setUnlinking(s)}>
                          <Link2Off className="size-4" /><span className="hidden sm:inline">{t('retail.unlink')}</span>
                        </Button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              <p className="mt-3 text-xs text-muted-foreground">{t('retail.ssoHint')}</p>
            </>
          )}
      <UnlinkDialog shop={unlinking} onClose={() => setUnlinking(null)} />
    </>
  );
}
