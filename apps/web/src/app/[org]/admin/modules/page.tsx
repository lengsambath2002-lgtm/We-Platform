'use client';

import { Blocks } from 'lucide-react';
import Link from 'next/link';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PermissionDenied } from '@/components/common/states';
import { MODULE_ICON } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import type { MessageKey } from '@/i18n/messages';
import { en as moduleText } from '@/i18n/messages/modules';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

/** The `modules.about_<key>` message key, if this module has a description. */
const aboutKey = (key: string) => (`about_${key}` in moduleText ? (`modules.about_${key}` as MessageKey) : null);

export default function ModulesAdmin() {
  const { slug, can, modules } = useOrg();
  const { t, locale } = useT();
  const toggle = useOrgMutation(slug, ({ key, on }: { key: string; on: boolean }) => orgApi.setModule(slug, key, on));
  if (!can('org.modules.manage')) return <PermissionDenied />;

  return (
    <>
      <PageHeader title={t('admin.modulesTitle')} subtitle={t('admin.modulesSubtitle')} />
      <ul className="grid gap-3 md:grid-cols-2">
        {modules.map((m) => {
          const Icon = MODULE_ICON[m.key] ?? Blocks;
          const name = locale === 'km' ? m.nameKm : m.nameEn;
          const about = aboutKey(m.key);
          return (
            <li key={m.key} className="flex gap-4 rounded-lg border bg-card p-4">
              <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary"><Icon className="size-5" /></span>
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 font-medium">
                  {name} <span className="text-sm font-normal text-muted-foreground" lang={locale === 'km' ? 'en' : 'km'}>{locale === 'km' ? m.nameEn : m.nameKm}</span>
                  {!m.available && <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] font-normal text-muted-foreground">{t('admin.comingSoon')}</span>}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">{about ? t(about) : ''}</p>
                <div className="mt-3 flex items-center gap-2">
                  {m.available ? (
                    <Button size="sm" variant={m.enabled ? 'outline' : 'default'} disabled={toggle.isPending}
                      onClick={() => toggle.mutateAsync({ key: m.key, on: !m.enabled }).then(() => toast.success(t(m.enabled ? 'admin.switchedOff' : 'admin.switchedOn', { name }))).catch((e) => toast.error(errorMessage(e)))}>
                      {m.enabled ? t('admin.switchOff') : t('admin.switchOn')}
                    </Button>
                  ) : <Button size="sm" variant="outline" asChild><Link href={`/${slug}/m/${m.key}`}>{t('admin.whatsPlanned')}</Link></Button>}
                  {m.enabled && <span className="text-xs text-success">{t('admin.on')}</span>}
                </div>
              </div>
            </li>
          );
        })}
      </ul>
    </>
  );
}
