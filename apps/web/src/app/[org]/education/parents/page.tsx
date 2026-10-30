'use client';

import { HeartHandshake, Pencil, Phone, Plus, SearchX } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { GuardianDialog } from '@/components/education/people-dialogs';
import { Pager, studentName } from '@/components/education/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { type Guardian, useGuardians } from '@/lib/api/education';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;

/** Parents and guardians: contacts and their children. Admins edit; teachers see those of their students. */
export default function Parents() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Guardian | 'new' | null>(null);
  const { data, error, isPending, refetch } = useGuardians(slug, { q: q.trim() || undefined, page, size: SIZE });
  const manage = can('education.manage');

  return (
    <>
      <PageHeader title={t('education.parentsTitle')} subtitle={data ? t('education.count', { count: data.total }) : t('education.parentsSubtitle')}
        actions={manage && <Button size="sm" onClick={() => setEditing('new')}><Plus className="size-4" />{t('education.newGuardian')}</Button>} />
      <div className="mb-4 space-y-1 sm:w-80">
        <Label htmlFor="g-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
        <Input id="g-q" type="search" value={q} placeholder={t('education.searchGuardians')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.items.length === 0 ? (q ? <EmptyState icon={SearchX} title={t('education.noMatchTitle')} body={t('education.noMatchBody')} />
          : <EmptyState icon={HeartHandshake} title={t('education.noGuardiansTitle')} body={t('education.noGuardiansBody')} />)
          : (
            <>
              <ul className="divide-y rounded-lg border bg-card">
                {data.items.map((g) => (
                  <li key={g.id} className="flex flex-wrap items-start justify-between gap-3 px-4 py-3">
                    <div className="min-w-0">
                      <p className="font-medium">{g.name}</p>
                      <p className="text-sm text-muted-foreground">{[g.phone, g.email, g.occupation].filter(Boolean).join(' · ')}</p>
                      <p className="mt-1 flex flex-wrap gap-x-3 text-sm">
                        {g.children.map((c) => <Link key={c.id} href={`/${slug}/education/students/${c.id}`} className="text-primary hover:underline">{studentName(c)} ({c.number})</Link>)}
                      </p>
                    </div>
                    <div className="flex gap-1">
                      {g.phone && <Button asChild variant="ghost" size="icon-sm" aria-label={t('education.callName', { name: g.name })}><a href={`tel:${g.phone.replace(/\s/g, '')}`}><Phone className="size-4" /></a></Button>}
                      {manage && <Button variant="ghost" size="icon-sm" aria-label={t('education.editName', { name: g.name })} onClick={() => setEditing(g)}><Pencil className="size-4" /></Button>}
                    </div>
                  </li>
                ))}
              </ul>
              <Pager page={page} size={SIZE} total={data.total} onPage={setPage} />
            </>
          )}
      {editing && <GuardianDialog org={slug} guardian={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}
