'use client';

import { Check, FileText, Plus, Sheet, Trophy } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';

import { AssessmentDialog } from '@/components/education/class-dialogs';
import { LetterBadge, num, pct, studentName, subjectName } from '@/components/education/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { downloadCsv } from '@/components/reports/report-kit';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Course, eduApi, type Gradebook, type Term, useClassResults, useClasses, useCourses, useGradebook, useYears } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const SCORE = /^\d{1,4}(\.\d{1,2})?$/;

/** The course's scores for a term: one column per assessment; the teacher types scores and saves; results come from the API. */
function Grid({ org, book, courses, terms }: { org: string; book: Gradebook; courses: Course[]; terms: Term[] }) {
  const { t } = useT();
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [adding, setAdding] = useState(false);
  useEffect(() => { setDraft({}); }, [book]);
  const key = (a: string, s: string) => `${a}:${s}`;
  const value = (a: string, s: string, saved?: number) => draft[key(a, s)] ?? (saved === undefined ? '' : num(saved));
  const changed = useMemo(() => Object.keys(draft), [draft]);
  const invalid = changed.filter((k) => {
    const v = draft[k].trim();
    if (!v) return false;
    const max = book.assessments.find((a) => a.id === k.split(':')[0])?.maxScore ?? 0;
    return !SCORE.test(v) || Number(v) > max;
  });
  const save = useOrgMutation(org, async () => {
    const byAssessment = new Map<string, { studentId: string; score: string | null }[]>();
    for (const k of changed) {
      const [a, s] = k.split(':');
      const v = draft[k].trim();
      byAssessment.set(a, [...(byAssessment.get(a) ?? []), { studentId: s, score: v || null }]);
    }
    for (const [a, scores] of byAssessment) await eduApi.saveScores(org, a, scores);
  });
  const csv = () => downloadCsv(`gradebook_${book.course.classRef.name}_${book.course.subject.code}_${book.term.name}.csv`,
    [t('education.number'), t('education.name'), ...book.assessments.map((a) => `${a.name} (/${num(a.maxScore)}, ${num(a.weight)}%)`), t('education.resultPct'), t('education.letter')],
    book.rows.map((r) => [r.student.number, r.student.nameEn, ...book.assessments.map((a) => r.scores[a.id] ?? null), r.result == null ? null : r.result.toFixed(1), r.letter]));

  return (
    <section aria-labelledby="gb-h" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 id="gb-h" className="text-base font-semibold">{book.course.classRef.name} · {subjectName(book.course.subject)} · {book.term.name}</h2>
          <p className="text-sm text-muted-foreground">{t('education.gradebookNote', { weight: num(book.totalWeight), teacher: book.course.teacher.name })}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {book.rows.length > 0 && <Button variant="outline" size="sm" onClick={csv}>{t('reports.downloadCsv')}</Button>}
          {book.canEdit && <Button variant="outline" size="sm" onClick={() => setAdding(true)}><Plus className="size-4" />{t('education.newAssessment')}</Button>}
        </div>
      </div>
      {book.assessments.length === 0 ? (
        <EmptyState icon={Sheet} title={t('education.noAssessmentsTitle')} body={book.canEdit ? t('education.noAssessmentsCourse') : t('education.noAssessmentsYet')}
          action={book.canEdit && <Button size="sm" variant="outline" onClick={() => setAdding(true)}>{t('education.newAssessment')}</Button>} />
      ) : book.rows.length === 0 ? <EmptyState icon={Sheet} title={t('education.emptyClassTitle')} body={t('education.noRosterBody')} /> : (
        <>
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="sticky left-0 z-10 min-w-44 bg-card">{t('education.student')}</TableHead>
                  {book.assessments.map((a) => (
                    <TableHead key={a.id} className="min-w-28 text-right">
                      <span className="block">{a.name}</span>
                      <span className="block text-[11px] font-normal text-muted-foreground">/{num(a.maxScore)} · {num(a.weight)}%</span>
                    </TableHead>
                  ))}
                  <TableHead className="text-right">{t('education.result')}</TableHead>
                  <TableHead className="text-right">{t('education.letter')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {book.rows.map((r) => (
                  <TableRow key={r.student.id}>
                    <TableCell className="sticky left-0 z-10 bg-card font-medium">{studentName(r.student)}</TableCell>
                    {book.assessments.map((a) => {
                      const k = key(a.id, r.student.id);
                      const bad = invalid.includes(k);
                      return (
                        <TableCell key={a.id} className="text-right">
                          {book.canEdit ? (
                            <Input inputMode="decimal" value={value(a.id, r.student.id, r.scores[a.id])} aria-invalid={bad || undefined}
                              aria-label={t('education.scoreFor', { name: studentName(r.student), assessment: a.name })}
                              onChange={(e) => setDraft((d) => ({ ...d, [k]: e.target.value }))}
                              className={cn('ml-auto h-8 w-20 text-right tabular', bad && 'border-destructive', k in draft && !bad && 'border-primary')} />
                          ) : <span className="tabular">{r.scores[a.id] === undefined ? '—' : num(r.scores[a.id])}</span>}
                        </TableCell>
                      );
                    })}
                    <TableCell className="text-right font-medium tabular">{pct(r.result)}</TableCell>
                    <TableCell className="text-right"><LetterBadge letter={r.letter} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm text-muted-foreground">{t('education.classAverage', { value: pct(book.classAverage) })}</p>
            {book.canEdit && (
              <div className="flex items-center gap-3">
                {invalid.length > 0 && <p role="alert" className="text-sm text-destructive">{t('education.scoresInvalid')}</p>}
                <Button disabled={changed.length === 0 || invalid.length > 0 || save.isPending} onClick={async () => {
                  try { await save.mutateAsync(undefined); toast.success(t('education.scoresSaved')); } catch (e) { toast.error(errorMessage(e)); }
                }}><Check className="size-4" />{t('education.saveScores')}</Button>
              </div>
            )}
          </div>
        </>
      )}
      {adding && <AssessmentDialog org={org} courses={courses} terms={terms} courseId={book.course.id} termId={book.term.id} onClose={() => setAdding(false)} />}
    </section>
  );
}

/** A class's results for a term: each course, the average, letter, rank and pass / fail; each row opens the report card. */
function Results({ org, classId, termId }: { org: string; classId: string; termId: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useClassResults(org, classId, termId);
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={5} />;
  if (data.rows.length === 0) return <EmptyState icon={Trophy} title={t('education.emptyClassTitle')} body={t('education.noRosterBody')} />;
  const sorted = [...data.rows].sort((a, b) => (a.rank ?? 1e9) - (b.rank ?? 1e9));
  const csv = () => downloadCsv(`results_${data.classRef.name}_${data.term.name}.csv`,
    [t('education.rank'), t('education.number'), t('education.name'), ...data.courses.map((c) => c.subject.code), t('education.averagePct'), t('education.letter')],
    sorted.map((r) => [r.rank, r.student.number, r.student.nameEn, ...data.courses.map((c) => r.results[c.id] ?? null), r.average == null ? null : r.average.toFixed(1), r.letter]));
  return (
    <section aria-labelledby="res-h" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 id="res-h" className="text-base font-semibold">{t('education.resultsOf', { name: data.classRef.name, term: data.term.name })}</h2>
          <p className="text-sm text-muted-foreground">{t('education.resultsNote', { pass: num(data.passMark) })}</p>
        </div>
        <Button variant="outline" size="sm" onClick={csv}>{t('reports.downloadCsv')}</Button>
      </div>
      <div className="overflow-x-auto rounded-lg border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-12 text-right">{t('education.rank')}</TableHead>
              <TableHead className="min-w-44">{t('education.student')}</TableHead>
              {data.courses.map((c) => <TableHead key={c.id} className="text-right" title={subjectName(c.subject)}>{c.subject.code}</TableHead>)}
              <TableHead className="text-right">{t('education.average')}</TableHead>
              <TableHead className="text-right">{t('education.letter')}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map((r) => (
              <TableRow key={r.student.id}>
                <TableCell className="text-right tabular">{r.rank ?? '—'}</TableCell>
                <TableCell className="font-medium">{studentName(r.student)}</TableCell>
                {data.courses.map((c) => <TableCell key={c.id} className="text-right tabular">{pct(r.results[c.id])}</TableCell>)}
                <TableCell className={cn('text-right font-semibold tabular', r.passed === false && 'text-destructive')}>{pct(r.average)}</TableCell>
                <TableCell className="text-right"><LetterBadge letter={r.letter} /></TableCell>
                <TableCell className="text-right">
                  <Button asChild variant="ghost" size="icon-sm" aria-label={t('education.reportCardOf', { name: studentName(r.student) })}>
                    <Link href={`/${org}/education/students/${r.student.id}/report-card?term=${termId}`}><FileText className="size-4" /></Link>
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </section>
  );
}

function GradebookPage() {
  const { slug } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const params = useSearchParams();
  const years = useYears(slug);
  const courses = useCourses(slug);
  const classes = useClasses(slug);
  const allTerms = (years.data ?? []).flatMap((y) => y.terms);
  const current = years.data?.find((y) => y.current);
  const today = new Date().toISOString().slice(0, 10);
  const defaultTerm = current?.terms.find((x) => x.startsOn <= today && today <= x.endsOn)?.id ?? current?.terms[0]?.id ?? '';
  const view = params.get('class') ? 'results' : 'course';
  const courseId = params.get('course') ?? courses.data?.[0]?.id ?? '';
  const classId = params.get('class') ?? classes.data?.[0]?.id ?? '';
  const termId = params.get('term') ?? defaultTerm;
  const book = useGradebook(slug, view === 'course' ? courseId : '', termId);
  const set = (p: Record<string, string>) => {
    const q = new URLSearchParams({ ...(view === 'course' ? { course: courseId } : { class: classId }), term: termId, ...p });
    router.replace(`/${slug}/education/gradebook?${q.toString()}`);
  };
  const terms = current?.terms ?? allTerms;

  return (
    <>
      <PageHeader title={t('education.gradebookTitle')} subtitle={t('education.gradebookSubtitle')} />
      {years.error || courses.error ? <ErrorState error={years.error ?? courses.error} retry={() => { years.refetch(); courses.refetch(); }} />
        : years.isPending || courses.isPending || classes.isPending ? <ListSkeleton />
          : terms.length === 0 ? <EmptyState icon={Sheet} title={t('education.noTermsTitle')} body={t('education.noTermsBody')} />
            : (
              <div className="space-y-6">
                <Tabs value={view} onValueChange={(v) => router.replace(`/${slug}/education/gradebook?${v === 'results' ? `class=${classId}` : `course=${courseId}`}&term=${termId}`)}>
                  <TabsList>
                    <TabsTrigger value="course">{t('education.byCourse')}</TabsTrigger>
                    <TabsTrigger value="results">{t('education.classResults')}</TabsTrigger>
                  </TabsList>
                </Tabs>
                <div className="grid grid-cols-2 gap-3 sm:flex sm:items-end">
                  {view === 'course' ? (
                    <div className="col-span-2 space-y-1 sm:col-span-1">
                      <Label htmlFor="g-c" className="text-xs text-muted-foreground">{t('education.course')}</Label>
                      <Select value={courseId} onValueChange={(v) => set({ course: v })}>
                        <SelectTrigger id="g-c" className="w-full sm:w-64"><SelectValue placeholder={t('education.chooseCourse')} /></SelectTrigger>
                        <SelectContent>{(courses.data ?? []).map((c) => <SelectItem key={c.id} value={c.id}>{c.classRef.name} · {subjectName(c.subject)}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                  ) : (
                    <div className="space-y-1">
                      <Label htmlFor="g-k" className="text-xs text-muted-foreground">{t('education.class')}</Label>
                      <Select value={classId} onValueChange={(v) => set({ class: v })}>
                        <SelectTrigger id="g-k" className="w-full sm:w-44"><SelectValue /></SelectTrigger>
                        <SelectContent>{(classes.data ?? []).map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                  )}
                  <div className="space-y-1">
                    <Label htmlFor="g-t" className="text-xs text-muted-foreground">{t('education.term')}</Label>
                    <Select value={termId} onValueChange={(v) => set({ term: v })}>
                      <SelectTrigger id="g-t" className="w-full sm:w-44"><SelectValue /></SelectTrigger>
                      <SelectContent>{terms.map((x) => <SelectItem key={x.id} value={x.id}>{x.name}</SelectItem>)}</SelectContent>
                    </Select>
                  </div>
                </div>
                {view === 'results' ? (classId ? <Results org={slug} classId={classId} termId={termId} />
                  : <EmptyState icon={Trophy} title={t('education.noClassesTitle')} body={t('education.noClassesTeacher')} />)
                  : !courseId ? <EmptyState icon={Sheet} title={t('education.noCoursesTitle')} body={t('education.noCoursesYou')} />
                    : book.error ? <ErrorState error={book.error} retry={() => book.refetch()} /> : book.isPending ? <ListSkeleton rows={6} />
                      : <Grid org={slug} book={book.data} courses={courses.data ?? []} terms={terms} />}
              </div>
            )}
    </>
  );
}

export default function GradebookRoute() {
  return <Suspense fallback={<ListSkeleton />}><GradebookPage /></Suspense>;
}
