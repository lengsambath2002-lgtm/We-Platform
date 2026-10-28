'use client';

import { Search, SearchX, Users } from 'lucide-react';
import Link from 'next/link';
import { useMemo, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { usePeople } from '@/lib/api/hr';
import { useOrg } from '@/lib/org-context';

const ALL = '__all__';

/** The people directory: name, title, department, manager, work phone; search and a department filter. */
export default function PeopleDirectory() {
  const { slug } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = usePeople(slug);
  const [q, setQ] = useState('');
  const [dept, setDept] = useState(ALL);
  const departments = useMemo(() => [...new Set((data ?? []).map((p) => p.department).filter((d): d is string => !!d))].sort(), [data]);
  const needle = q.trim().toLowerCase();
  const shown = (data ?? []).filter((p) => (dept === ALL || p.department === dept)
    && (!needle || [p.person.name, p.title, p.department, p.email].some((x) => x?.toLowerCase().includes(needle))));

  return (
    <>
      <PageHeader title={t('hr.peopleTitle')} subtitle={data ? t('hr.peopleCount', { count: data.length }) : t('hr.peopleSubtitle')} />
      <div className="mb-4 flex flex-wrap gap-2">
        <div className="relative min-w-0 flex-1 basis-56">
          <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground" aria-hidden />
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('hr.searchPeople')} aria-label={t('hr.searchPeople')} className="pl-8" />
        </div>
        {departments.length > 0 && (
          <Select value={dept} onValueChange={setDept}>
            <SelectTrigger className="w-full sm:w-52" aria-label={t('hr.department')}><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('hr.allDepartments')}</SelectItem>
              {departments.map((d) => <SelectItem key={d} value={d}>{d}</SelectItem>)}
            </SelectContent>
          </Select>
        )}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.length === 0 ? <EmptyState icon={Users} title={t('hr.noPeopleTitle')} body={t('hr.noPeopleBody')} />
          : shown.length === 0 ? <EmptyState icon={SearchX} title={t('hr.noMatchTitle')} body={t('hr.noMatchBody')} />
            : (
              <ul className="divide-y rounded-lg border bg-card">
                {shown.map((p) => (
                  <li key={p.person.id}>
                    <Link href={`/${slug}/hr/people/${p.person.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50">
                      <PersonAvatar person={p.person} className="size-9" />
                      <span className="grid min-w-0 flex-1 gap-0.5 sm:grid-cols-[1.2fr_1fr_1fr] sm:items-center sm:gap-4">
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium">{p.person.name}</span>
                          <span className="block truncate text-xs text-muted-foreground">{p.title ?? t('hr.noTitle')}</span>
                        </span>
                        <span className="truncate text-xs text-muted-foreground sm:text-sm sm:text-foreground">{p.department ?? <span className="text-muted-foreground">{t('hr.noDepartment')}</span>}</span>
                        <span className="truncate text-xs text-muted-foreground">{p.manager ? t('hr.reportsTo', { name: p.manager.name }) : ''}</span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
    </>
  );
}
