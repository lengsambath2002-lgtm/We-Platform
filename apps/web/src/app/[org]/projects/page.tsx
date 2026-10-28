'use client';

import { FolderKanban, Lock, Plus, Search } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useMemo, useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { NewProjectDialog } from '@/components/projects/new-project-dialog';
import { DueDate } from '@/components/tasks/task-row';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useProjects } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

const STATUS: Record<string, string> = { ACTIVE: 'Active', ON_HOLD: 'On hold', DONE: 'Done', ARCHIVED: 'Archived' };

function Projects() {
  const { slug, can } = useOrg();
  const router = useRouter();
  const params = useSearchParams();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const { data = [], error, isPending, refetch } = useProjects(slug);
  const open = params.get('new') === '1';
  const setOpen = (v: boolean) => router.replace(`/${slug}/projects${v ? '?new=1' : ''}`);
  const shown = useMemo(() => data.filter((p) => (status === 'ALL' || p.status === status)
    && (!q || `${p.key} ${p.name}`.toLowerCase().includes(q.toLowerCase()))), [data, q, status]);

  return (
    <>
      <PageHeader title="Projects" subtitle={`${data.length} project${data.length === 1 ? '' : 's'} you can see`}
        actions={can('projects.create') && <Button size="sm" onClick={() => setOpen(true)}><Plus className="size-4" />New project</Button>} />
      <div className="mb-4 flex flex-wrap gap-2">
        <div className="relative w-full sm:w-72">
          <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search projects" className="pl-8" aria-label="Search projects" />
        </div>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="w-40" aria-label="Status"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All statuses</SelectItem>
            {Object.entries(STATUS).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton /> : data.length === 0 ? (
        <EmptyState icon={FolderKanban} title="No projects yet" body="Projects hold your team's tasks, deadlines and approvals."
          action={can('projects.create') ? <Button size="sm" onClick={() => setOpen(true)}>Create the first project</Button> : undefined} />
      ) : shown.length === 0 ? <EmptyState icon={Search} title="No projects match" body="Try another search or status." /> : (
        <div className="overflow-hidden rounded-lg border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Project</TableHead>
                <TableHead className="hidden md:table-cell">Lead</TableHead>
                <TableHead className="hidden sm:table-cell w-48">Progress</TableHead>
                <TableHead className="hidden lg:table-cell">Due</TableHead>
                <TableHead className="hidden lg:table-cell">Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {shown.map((p) => (
                <TableRow key={p.id} className="cursor-pointer" onClick={() => router.push(`/${slug}/projects/${p.key}`)}>
                  <TableCell>
                    <Link href={`/${slug}/projects/${p.key}`} className="flex items-center gap-2 font-medium hover:underline" onClick={(e) => e.stopPropagation()}>
                      <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">{p.key}</span>
                      <span className="truncate">{p.name}</span>
                      {p.visibility === 'PRIVATE' && <Lock className="size-3.5 text-muted-foreground" aria-label="Private" />}
                    </Link>
                  </TableCell>
                  <TableCell className="hidden md:table-cell"><span className="flex items-center gap-2"><PersonAvatar person={p.lead} /><span className="truncate text-sm">{p.lead?.name}</span></span></TableCell>
                  <TableCell className="hidden sm:table-cell"><span className="flex items-center gap-2"><Progress value={p.progress} className="h-1.5" /><span className="w-9 text-right text-xs tabular text-muted-foreground">{p.progress}%</span></span></TableCell>
                  <TableCell className="hidden lg:table-cell">{p.dueDate ? <DueDate date={p.dueDate} done={p.status === 'DONE'} /> : <span className="text-xs text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="hidden lg:table-cell text-sm">{STATUS[p.status]}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      <NewProjectDialog org={slug} open={open} onOpenChange={setOpen} />
    </>
  );
}

export default function Page() {
  return <Suspense><Projects /></Suspense>;
}
