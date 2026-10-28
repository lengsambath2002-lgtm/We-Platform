'use client';

import { Blocks, Construction } from 'lucide-react';
import Link from 'next/link';
import { notFound, redirect, useParams } from 'next/navigation';

import { MODULE_ICON } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import type { MessageKey } from '@/i18n/messages';
import { en as moduleText } from '@/i18n/messages/modules';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

/** The `modules.planned_<key>` message key (one item per line), if this module has a plan. */
const plannedKey = (key: string) => (`planned_${key}` in moduleText ? (`modules.planned_${key}` as MessageKey) : null);

/** Honest page for an industry module that isn't built yet: what it will do, nothing fake. */
export default function ModulePage() {
  const { module } = useParams<{ module: string }>();
  const { slug, modules, can } = useOrg();
  const { t, locale } = useT();
  const m = modules.find((x) => x.key === module);
  if (!m) notFound();
  if (m.key === 'projects') redirect(`/${slug}/projects`);
  if (m.key === 'hr' && m.available) redirect(`/${slug}/hr`);
  if (m.key === 'business' && m.available) redirect(`/${slug}/business`);
  if (m.key === 'retail' && m.available) redirect(`/${slug}/retail`);
  const Icon = MODULE_ICON[m.key] ?? Blocks;
  const planned = plannedKey(m.key);

  return (
    <div className="mx-auto max-w-2xl py-6">
      <div className="flex items-center gap-3">
        <span className="grid size-12 place-items-center rounded-lg bg-primary/10 text-primary"><Icon className="size-6" /></span>
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{locale === 'km' ? m.nameKm : m.nameEn}</h1>
          <p className="text-sm text-muted-foreground" lang={locale === 'km' ? 'en' : 'km'}>{locale === 'km' ? m.nameEn : m.nameKm}</p>
        </div>
      </div>
      <div className="mt-6 rounded-lg border bg-card p-5">
        <p className="flex items-center gap-2 font-medium"><Construction className="size-4 text-warning" />{t('modules.notAvailable')}</p>
        <p className="mt-1 text-sm text-muted-foreground">{t('modules.plannedBody')}</p>
        <ul className="mt-3 list-disc space-y-1 pl-5 text-sm">{(planned ? t(planned).split('\n') : []).map((x) => <li key={x}>{x}</li>)}</ul>
      </div>
      <div className="mt-4 flex gap-2">
        <Button asChild variant="outline"><Link href={`/${slug}/overview`}>{t('modules.backToOverview')}</Link></Button>
        {can('org.modules.manage') && <Button asChild variant="ghost"><Link href={`/${slug}/admin/modules`}>{t('modules.manageModules')}</Link></Button>}
      </div>
    </div>
  );
}

