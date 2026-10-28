'use client';

import { ChevronRight, Lock, Mail, Pencil, Phone } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { BalanceCards } from '@/components/hr/leave-ui';
import { ContactDialog, EMPLOYMENT_LABEL, ProfileDialog } from '@/components/hr/profile-dialogs';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useBalances, useProfile } from '@/lib/api/hr';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-words text-sm">{children}</dd>
    </div>
  );
}

/** A person's profile. Private details only for the person and HR; HR edits all of it, the person their contacts. */
export default function ProfilePage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { userId } = useParams<{ userId: string }>();
  const { data: p, error, isPending, refetch } = useProfile(slug, userId);
  const [editing, setEditing] = useState<'all' | 'contact' | null>(null);
  const showBalances = !!p && (p.canEditContact || can('hr.manage'));
  const balances = useBalances(slug, p?.canEditContact ? '' : userId, new Date().getFullYear(), showBalances);
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const dash = <span className="text-muted-foreground">{t('common.none')}</span>;

  return (
    <div className="mx-auto max-w-4xl">
      <nav aria-label={t('hr.breadcrumb')} className="mb-3 flex items-center gap-1 text-sm text-muted-foreground">
        <Link href={`/${slug}/hr/people`} className="hover:text-foreground">{t('hr.navPeople')}</Link><ChevronRight className="size-3.5" /><span className="truncate">{p.person.name}</span>
      </nav>
      <div className="mb-6 flex flex-wrap items-center gap-4">
        <PersonAvatar person={p.person} className="size-14 text-base" />
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-xl font-semibold tracking-tight">{p.person.name}</h1>
          <p className="text-sm text-muted-foreground">{[p.title, p.department].filter(Boolean).join(' · ') || t('hr.noTitle')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {p.canEdit && <Button size="sm" variant="outline" onClick={() => setEditing('all')}><Pencil className="size-4" />{t('hr.editProfile')}</Button>}
          {!p.canEdit && p.canEditContact && <Button size="sm" variant="outline" onClick={() => setEditing('contact')}><Pencil className="size-4" />{t('hr.editContact')}</Button>}
        </div>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_280px]">
        <div className="min-w-0 space-y-6">
          <section aria-labelledby="work-h" className="rounded-lg border bg-card p-4">
            <h2 id="work-h" className="mb-4 text-sm font-semibold">{t('hr.work')}</h2>
            <dl className="grid gap-4 sm:grid-cols-2">
              <Field label={t('hr.jobTitle')}>{p.title ?? dash}</Field>
              <Field label={t('hr.department')}>{p.department ?? dash}</Field>
              <Field label={t('hr.manager')}>{p.manager ? <Link href={`/${slug}/hr/people/${p.manager.id}`} className="inline-flex items-center gap-2 hover:underline"><PersonAvatar person={p.manager} />{p.manager.name}</Link> : dash}</Field>
              <Field label={t('hr.email')}><a href={`mailto:${p.email}`} className="inline-flex items-center gap-1.5 hover:underline"><Mail className="size-3.5 shrink-0" />{p.email}</a></Field>
              <Field label={t('hr.workPhone')}>{p.workPhone ? <a href={`tel:${p.workPhone}`} className="inline-flex items-center gap-1.5 hover:underline"><Phone className="size-3.5" />{p.workPhone}</a> : dash}</Field>
              {p.privateShown && (
                <>
                  <Field label={t('hr.employmentType')}>{p.employmentType ? EMPLOYMENT_LABEL[p.employmentType] : dash}</Field>
                  <Field label={t('hr.startDate')}>{p.startDate ? day(p.startDate) : dash}</Field>
                </>
              )}
            </dl>
          </section>
          {p.privateShown ? (
            <section aria-labelledby="em-h" className="rounded-lg border bg-card p-4">
              <h2 id="em-h" className="mb-1 text-sm font-semibold">{t('hr.emergencyContact')}</h2>
              <p className="mb-4 flex items-center gap-1.5 text-xs text-muted-foreground"><Lock className="size-3" />{t('hr.privateNote')}</p>
              <dl className="grid gap-4 sm:grid-cols-3">
                <Field label={t('hr.emergencyName')}>{p.emergencyName ?? dash}</Field>
                <Field label={t('hr.emergencyPhone')}>{p.emergencyPhone ?? dash}</Field>
                <Field label={t('hr.emergencyRelation')}>{p.emergencyRelation ?? dash}</Field>
              </dl>
            </section>
          ) : (
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground"><Lock className="size-3" />{t('hr.privateHidden')}</p>
          )}
          {showBalances && (
            <section aria-labelledby="bal-h">
              <h2 id="bal-h" className="mb-3 text-sm font-semibold">{t('hr.balancesThisYear')}</h2>
              {balances.error ? <ErrorState error={balances.error} retry={() => balances.refetch()} />
                : balances.isPending ? <ListSkeleton rows={2} /> : <BalanceCards balances={balances.data} />}
              {can('hr.manage') && <Link href={`/${slug}/hr/settings?person=${p.person.id}`} className="mt-2 inline-block text-xs text-primary hover:underline">{t('hr.adjustBalances')}</Link>}
            </section>
          )}
        </div>
        <aside className="space-y-3">
          <section aria-labelledby="rep-h" className="rounded-lg border bg-card p-4">
            <h2 id="rep-h" className="mb-2 text-sm font-semibold">{t('hr.directReports')}</h2>
            {p.reports.length === 0 ? <p className="text-sm text-muted-foreground">{t('hr.noReports')}</p> : (
              <ul className="space-y-1">
                {p.reports.map((r) => (
                  <li key={r.id}><Link href={`/${slug}/hr/people/${r.id}`} className="flex items-center gap-2 rounded-md p-1 text-sm hover:bg-muted/50"><PersonAvatar person={r} />{r.name}</Link></li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>
      {editing === 'all' && <ProfileDialog org={slug} profile={p} onClose={() => setEditing(null)} />}
      {editing === 'contact' && <ContactDialog org={slug} profile={p} onClose={() => setEditing(null)} />}
    </div>
  );
}
