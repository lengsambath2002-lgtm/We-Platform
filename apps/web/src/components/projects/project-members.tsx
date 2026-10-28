'use client';

import { Plus, Trash2, Users } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useMembers, useOrgMutation, useProjectMembers } from '@/lib/api/queries';
import type { Project, ProjectMember, ProjectRole } from '@/lib/api/types';

const ROLE_LABEL: Record<ProjectRole, string> = { LEAD: 'Lead', MEMBER: 'Member', VIEWER: 'Viewer' };
const ROLE_HINT: Record<ProjectRole, string> = {
  LEAD: 'Manages the project and its members; approves on "project lead" steps',
  MEMBER: 'Creates and edits tasks',
  VIEWER: 'Reads only',
};

/** Project members and roles. Leads and org admins edit; everyone who can see the project reads. */
export function ProjectMembers({ org, project }: { org: string; project: Project }) {
  const { data, error, isPending, refetch } = useProjectMembers(org, project.key);
  const members = useMembers(org);
  const [draft, setDraft] = useState<ProjectMember[]>([]);
  const [adding, setAdding] = useState('');
  useEffect(() => { if (data) setDraft(data); }, [data]);
  const save = useOrgMutation(org, (list: ProjectMember[]) =>
    orgApi.setProjectMembers(org, project.key, list.map((m) => ({ userId: m.person.id, role: m.role }))));

  const candidates = useMemo(() => (members.data ?? [])
    .filter((m) => m.status === 'ACTIVE' && !draft.some((d) => d.person.id === m.userId)), [members.data, draft]);
  const dirty = JSON.stringify(draft) !== JSON.stringify(data ?? []);
  const leads = draft.filter((m) => m.role === 'LEAD').length;

  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={3} />;

  return (
    <div className="max-w-3xl space-y-4">
      {draft.length === 0 ? (
        <EmptyState icon={Users} title="No members" body="Add people so they can work on this project." />
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {draft.map((m) => (
            <li key={m.person.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <PersonAvatar person={m.person} className="size-8" />
              <span className="min-w-0 flex-1 truncate text-sm font-medium">{m.person.name}</span>
              {project.canManage ? (
                <>
                  <Select value={m.role} onValueChange={(r) => setDraft((d) => d.map((x) => x.person.id === m.person.id ? { ...x, role: r as ProjectRole } : x))}>
                    <SelectTrigger className="w-32" aria-label={`Role of ${m.person.name}`}><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {(Object.keys(ROLE_LABEL) as ProjectRole[]).map((r) => (
                        <SelectItem key={r} value={r}><span>{ROLE_LABEL[r]}</span></SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Button variant="ghost" size="icon" aria-label={`Remove ${m.person.name}`} onClick={() => setDraft((d) => d.filter((x) => x.person.id !== m.person.id))}>
                    <Trash2 className="size-4" />
                  </Button>
                </>
              ) : <span className="text-sm text-muted-foreground">{ROLE_LABEL[m.role]}</span>}
            </li>
          ))}
        </ul>
      )}
      {project.canManage && (
        <>
          <div className="flex flex-wrap gap-2">
            <Select value={adding} onValueChange={setAdding}>
              <SelectTrigger className="w-64" aria-label="Person to add"><SelectValue placeholder={candidates.length ? 'Choose a person' : 'Everyone is already a member'} /></SelectTrigger>
              <SelectContent>{candidates.map((m) => <SelectItem key={m.userId} value={m.userId}>{m.name}</SelectItem>)}</SelectContent>
            </Select>
            <Button variant="outline" disabled={!adding} onClick={() => {
              const m = candidates.find((c) => c.userId === adding);
              if (m) setDraft((d) => [...d, { person: { id: m.userId, name: m.name, avatarUrl: m.avatarUrl }, role: 'MEMBER' }]);
              setAdding('');
            }}><Plus className="size-4" />Add</Button>
          </div>
          <ul className="space-y-1 text-xs text-muted-foreground">
            {(Object.keys(ROLE_HINT) as ProjectRole[]).map((r) => <li key={r}><span className="font-medium text-foreground">{ROLE_LABEL[r]}</span> — {ROLE_HINT[r]}</li>)}
          </ul>
          {leads === 0 && <p role="alert" className="text-sm text-destructive">A project needs at least one lead.</p>}
          <div className="flex gap-2">
            <Button disabled={!dirty || leads === 0 || save.isPending} onClick={async () => {
              try { await save.mutateAsync(draft); toast.success('Members saved'); } catch (e) { toast.error(errorMessage(e)); }
            }}>{save.isPending ? 'Saving…' : 'Save members'}</Button>
            {dirty && <Button variant="ghost" onClick={() => setDraft(data ?? [])}>Discard</Button>}
          </div>
        </>
      )}
    </div>
  );
}
