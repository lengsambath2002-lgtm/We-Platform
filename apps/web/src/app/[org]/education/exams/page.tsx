'use client';

import { ClipboardList, Pencil, Plus, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';

import { AssessmentDialog } from '@/components/education/class-dialogs';
import { KIND_LABEL, num, subjectName } from '@/components/education/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Assessment, eduApi, useAssessments, useCourses, useYears } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

const ALL = 'all';

/** Exams and other assessments of the courses the caller teaches (admins: all), by term; each opens its gradebook. */
export default function Exams() {
  const { slug } = useOrg();
  const { t } = useT();
  const years = useYears(slug);
  const current = years.data?.find((y) => y.current);
  const terms = current?.terms ?? [];
  const [termId, setTermId] = useState(ALL);
  const courses = useCourses(slug);
  const mine = courses.data ?? [];
  const { data, error, isPending, refetch } = useAssessments(slug, { termId: termId === ALL ? undefined : termId });
  const [editing, setEditing] = useState<Assessment | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Assessment | null>(null);
  const remove = useOrgMutation(slug, (id: string) => eduApi.deleteAssessment(slug, id));
  const editable = (courses.data ?? []).length > 0 && terms.length > 0;
  const base = `/${slug}/education`;

  return (
    <>
      <PageHeader title={t('education.examsTitle')} subtitle={t('education.examsSubtitle')}
        actions={editable && <Button size="sm" onClick={() => setEditing('new')}><Plus className="size-4" />{t('education.newAssessment')}</Button>} />
      {terms.length > 0 && (
        <div className="mb-4 space-y-1">
          <Label htmlFor="e-t" className="text-xs text-muted-foreground">{t('education.term')}</Label>
          <Select value={termId} onValueChange={setTermId}>
            <SelectTrigger id="e-t" className="w-48"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value={ALL}>{t('education.allTerms')}</SelectItem>{terms.map((x) => <SelectItem key={x.id} value={x.id}>{x.name}</SelectItem>)}</SelectContent>
          </Select>
        </div>
      )}
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={5} />
        : data.length === 0 ? <EmptyState icon={ClipboardList} title={t('education.noAssessmentsTitle')}
          body={editable ? t('education.noAssessmentsBody') : terms.length === 0 ? t('education.noTermsBody') : t('education.noCoursesYou')}
          action={editable && <Button size="sm" variant="outline" onClick={() => setEditing('new')}>{t('education.newAssessment')}</Button>} />
          : (
            <>
              <div className="hidden rounded-lg border bg-card md:block">
                <Table>
                  <TableHeader><TableRow>
                    <TableHead>{t('education.assessmentName')}</TableHead><TableHead>{t('education.course')}</TableHead><TableHead>{t('education.heldOn')}</TableHead>
                    <TableHead className="text-right">{t('education.maxScore')}</TableHead><TableHead className="text-right">{t('education.weight')}</TableHead>
                    <TableHead className="text-right">{t('education.graded')}</TableHead><TableHead />
                  </TableRow></TableHeader>
                  <TableBody>
                    {data.map((a) => (
                      <TableRow key={a.id}>
                        <TableCell><Link href={`${base}/gradebook?course=${a.course.id}&term=${a.term.id}`} className="font-medium text-primary hover:underline">{a.name}</Link>
                          <span className="block text-xs text-muted-foreground">{KIND_LABEL[a.kind]} · {a.term.name}</span></TableCell>
                        <TableCell>{a.course.classRef.name} · {subjectName(a.course.subject)}</TableCell>
                        <TableCell className="whitespace-nowrap">{day(a.heldOn)}</TableCell>
                        <TableCell className="text-right tabular">{num(a.maxScore)}</TableCell>
                        <TableCell className="text-right tabular">{num(a.weight)}%</TableCell>
                        <TableCell className="text-right tabular">{a.graded}/{a.students}</TableCell>
                        <TableCell className="w-20 text-right">
                          {a.canEdit && <>
                            <Button variant="ghost" size="icon-sm" aria-label={t('education.editName', { name: a.name })} onClick={() => setEditing(a)}><Pencil className="size-4" /></Button>
                            <Button variant="ghost" size="icon-sm" aria-label={t('education.deleteName', { name: a.name })} onClick={() => setDeleting(a)}><Trash2 className="size-4" /></Button>
                          </>}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <ul className="divide-y rounded-lg border bg-card md:hidden">
                {data.map((a) => (
                  <li key={a.id}>
                    <Link href={`${base}/gradebook?course=${a.course.id}&term=${a.term.id}`} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                      <span className="min-w-0"><span className="block truncate font-medium">{a.name}</span>
                        <span className="block truncate text-xs text-muted-foreground">{a.course.classRef.name} · {subjectName(a.course.subject)} · {day(a.heldOn)}</span></span>
                      <span className="shrink-0 text-sm tabular text-muted-foreground">{a.graded}/{a.students}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}
      {editing && <AssessmentDialog org={slug} assessment={editing === 'new' ? undefined : editing} courses={mine} terms={terms}
        termId={termId === ALL ? undefined : termId} onClose={() => setEditing(null)} />}
      <AlertDialog open={deleting !== null} onOpenChange={(v) => !v && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('education.deleteAssessmentTitle', { name: deleting?.name ?? '' })}</AlertDialogTitle>
            <AlertDialogDescription>{t('education.deleteAssessmentBody', { count: deleting?.graded ?? 0 })}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={async () => {
              if (!deleting) return;
              try { await remove.mutateAsync(deleting.id); toast.success(t('education.assessmentDeleted')); } catch (e) { toast.error(errorMessage(e)); }
              setDeleting(null);
            }}>{t('education.delete')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
