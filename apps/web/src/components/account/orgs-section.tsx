'use client';

import { Building2, Settings } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { LeaveOrgDialog } from '@/components/org/leave-org-dialog';
import { ROLE_TEXT } from '@/components/org/invite-dialog';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useOrgs } from '@/lib/api/queries';
import type { OrgSummary } from '@/lib/api/types';

/** My organisations, with "Leave" for each (and a link to the settings where I'm an admin). */
export function OrgsSection() {
  const { t } = useT();
  const orgs = useOrgs();
  const [leaving, setLeaving] = useState<OrgSummary | null>(null);

  return (
    <section className="mt-4 rounded-lg border bg-card p-4 sm:p-6" aria-labelledby="orgs-title">
      <h2 id="orgs-title" className="mb-1 text-base font-semibold">{t('settings.orgsTitle')}</h2>
      <p className="mb-4 text-sm text-muted-foreground">{t('settings.orgsLead')}</p>
      {orgs.error ? <ErrorState error={orgs.error} retry={() => orgs.refetch()} /> : orgs.isPending ? <ListSkeleton rows={2} /> : orgs.data.length === 0 ? (
        <EmptyState icon={Building2} title={t('settings.noOrgs')} action={<Button size="sm" asChild><Link href="/onboarding">{t('shell.createOrg')}</Link></Button>} />
      ) : (
        <ul className="divide-y rounded-md border">
          {orgs.data.map((o) => (
            <li key={o.id} className="flex items-center gap-3 px-3 py-3">
              <span className="grid size-8 shrink-0 place-items-center rounded bg-muted text-xs font-semibold">{o.name.slice(0, 1)}</span>
              <div className="min-w-0 flex-1">
                <Link href={`/${o.slug}/overview`} className="block truncate text-sm font-medium hover:underline">{o.name}</Link>
                <p className="truncate text-xs text-muted-foreground">{ROLE_TEXT[o.role]?.label} · /{o.slug}</p>
              </div>
              {(o.role === 'OWNER' || o.role === 'ADMIN') && (
                <Button variant="ghost" size="sm" asChild className="shrink-0">
                  <Link href={`/${o.slug}/admin/settings`} aria-label={`${t('settings.settings')} — ${o.name}`}><Settings className="size-4" /><span className="hidden sm:inline">{t('settings.settings')}</span></Link>
                </Button>
              )}
              <Button variant="ghost" size="sm" className="shrink-0 text-destructive" aria-label={t('settings.leaveOrg', { org: o.name })} onClick={() => setLeaving(o)}>
                {t('settings.leave')}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {leaving && <LeaveOrgDialog org={leaving} open={!!leaving} onOpenChange={(v) => { if (!v) setLeaving(null); }} />}
    </section>
  );
}
