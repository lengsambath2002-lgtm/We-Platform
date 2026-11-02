'use client';

import { GraduationCap, SearchX, UserPlus } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';

import { Money, Pager, StudentBadge, STUDENT_STATUS_LABEL, studentName } from '@/components/education/ui';
import { StudentsTabs } from '@/components/common/merged-tabs';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { STUDENT_STATUSES, useClasses, useStudents } from '@/lib/api/education';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;
const ALL = 'all';

/** Students: search by name or number, filter by status and class; admins see what each owes. */
function Students() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [classId, setClassId] = useState(params.get('class') ?? ALL);
  const [page, setPage] = useState(0);
  const classes = useClasses(slug);
  const { data, error, isPending, refetch } = useStudents(slug, {
    q: q.trim() || undefined, status: status === ALL ? undefined : status, classId: classId === ALL ? undefined : classId, page, size: SIZE,
  });
  const manage = can('education.manage');
  const href = (id: string) => `/${slug}/education/students/${id}`;
  const filtered = q.trim() || classId !== ALL || status !== 'ACTIVE';

  return (
    <>
      <StudentsTabs />
      <PageHeader title={t('education.studentsTitle')} subtitle={data ? t('education.count', { count: data.total }) : undefined}
        actions={manage && <Button asChild size="sm"><Link href={`/${slug}/education/students/new`}><UserPlus className="size-4" />{t('education.registerStudent')}</Link></Button>} />
      <div className="mb-4 grid grid-cols-2 gap-3 sm:flex sm:items-end">
        <div className="col-span-2 space-y-1 sm:w-72">
          <Label htmlFor="s-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
          <Input id="s-q" type="search" value={q} placeholder={t('education.searchStudents')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="s-c" className="text-xs text-muted-foreground">{t('education.class')}</Label>
          <Select value={classId} onValueChange={(v) => { setClassId(v); setPage(0); }}>
            <SelectTrigger id="s-c" className="w-full sm:w-36"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('education.allClasses')}</SelectItem>
              {(classes.data ?? []).map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="s-s" className="text-xs text-muted-foreground">{t('education.status')}</Label>
          <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
            <SelectTrigger id="s-s" className="w-full sm:w-36"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('education.allStatuses')}</SelectItem>
              {STUDENT_STATUSES.map((s) => <SelectItem key={s} value={s}>{STUDENT_STATUS_LABEL[s]}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.items.length === 0 ? (filtered
          ? <EmptyState icon={SearchX} title={t('education.noMatchTitle')} body={t('education.noMatchBody')} />
          : <EmptyState icon={GraduationCap} title={t('education.noStudentsTitle')} body={manage ? t('education.noStudentsBody') : t('education.noStudentsTeacher')}
            action={manage && <Button asChild size="sm" variant="outline"><Link href={`/${slug}/education/students/new`}>{t('education.registerStudent')}</Link></Button>} />)
          : (
            <>
              <div className="hidden rounded-lg border bg-card md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('education.number')}</TableHead><TableHead>{t('education.name')}</TableHead><TableHead>{t('education.class')}</TableHead>
                      <TableHead>{t('education.mainContact')}</TableHead><TableHead>{t('education.status')}</TableHead>
                      {manage && <TableHead className="text-right">{t('education.owes')}</TableHead>}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.items.map((s) => (
                      <TableRow key={s.id}>
                        <TableCell className="tabular text-muted-foreground">{s.number}</TableCell>
                        <TableCell className="max-w-64"><Link href={href(s.id)} className="block truncate font-medium text-primary hover:underline">{studentName(s)}</Link>
                          {s.nameKm && <span className="block truncate text-xs text-muted-foreground">{s.nameKm === studentName(s) ? s.nameEn : s.nameKm}</span>}</TableCell>
                        <TableCell>{s.currentClass?.name ?? '—'}</TableCell>
                        <TableCell className="max-w-56 truncate text-muted-foreground">{[s.primaryGuardian, s.guardianPhone].filter(Boolean).join(' · ') || '—'}</TableCell>
                        <TableCell><StudentBadge status={s.status} /></TableCell>
                        {manage && <TableCell className="text-right">{s.balance ? <Money value={s.balance} className="items-end" /> : <span className="text-muted-foreground">—</span>}</TableCell>}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <ul className="divide-y rounded-lg border bg-card md:hidden">
                {data.items.map((s) => (
                  <li key={s.id}>
                    <Link href={href(s.id)} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium">{studentName(s)}</span>
                        <span className="block truncate text-xs text-muted-foreground">{[s.number, s.currentClass?.name, s.primaryGuardian].filter(Boolean).join(' · ')}</span>
                      </span>
                      <StudentBadge status={s.status} />
                    </Link>
                  </li>
                ))}
              </ul>
              <Pager page={page} size={SIZE} total={data.total} onPage={setPage} />
            </>
          )}
    </>
  );
}

export default function StudentsPage() {
  return <Suspense fallback={<ListSkeleton />}><Students /></Suspense>;
}
