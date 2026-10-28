'use client';

import { Plus, Trash2, Users } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { labels } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useMembers, useOrgMutation, useProjectMembers } from '@/lib/api/queries';
import type { Project, ProjectMember, ProjectRole } from '@/lib/api/types';

const ROLES: ProjectRole[] = ['LEAD', 'MEMBER', 'VIEWER'];
const ROLE_LABEL = labels(ROLES, (r) => `projects.role_${r}`);
const ROLE_HINT = labels(ROLES, (r) => `projects.hint_${r}`);

/** Project members and roles. Leads and org admins edit; everyone who can see the project reads. */
export function ProjectMembers({ org, project }: { org: string; project: Project }) {
  const { t } = useT();
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
        <EmptyState icon={Users} title={t('projects.noMembers')} body={t('projects.noMembersBody')} />
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {draft.map((m) => (
            <li key={m.person.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <PersonAvatar person={m.person} className="size-8" />
              <span className="min-w-0 flex-1 truncate text-sm font-medium">{m.person.name}</span>
              {project.canManage ? (
                <>
                  <Select value={m.role} onValueChange={(r) => setDraft((d) => d.map((x) => x.person.id === m.person.id ? { ...x, role: r as ProjectRole } : x))}>
                    <SelectTrigger className="w-32" aria-label={t('projects.roleOf', { name: m.person.name })}><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {ROLES.map((r) => (
                        <SelectItem key={r} value={r}><span>{ROLE_LABEL[r]}</span></SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Button variant="ghost" size="icon" aria-label={t('projects.removePerson', { name: m.person.name })} onClick={() => setDraft((d) => d.filter((x) => x.person.id !== m.person.id))}>
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
              <SelectTrigger className="w-64" aria-label={t('projects.personToAdd')}><SelectValue placeholder={candidates.length ? t('projects.choosePerson') : t('projects.everyoneMember')} /></SelectTrigger>
              <SelectContent>{candidates.map((m) => <SelectItem key={m.userId} value={m.userId}>{m.name}</SelectItem>)}</SelectContent>
            </Select>
            <Button variant="outline" disabled={!adding} onClick={() => {
              const m = candidates.find((c) => c.userId === adding);
              if (m) setDraft((d) => [...d, { person: { id: m.userId, name: m.name, avatarUrl: m.avatarUrl }, role: 'MEMBER' }]);
              setAdding('');
            }}><Plus className="size-4" />{t('common.add')}</Button>
          </div>
          <ul className="space-y-1 text-xs text-muted-foreground">
            {ROLES.map((r) => <li key={r}><span className="font-medium text-foreground">{ROLE_LABEL[r]}</span> — {ROLE_HINT[r]}</li>)}
          </ul>
          {leads === 0 && <p role="alert" className="text-sm text-destructive">{t('projects.needsLead')}</p>}
          <div className="flex gap-2">
            <Button disabled={!dirty || leads === 0 || save.isPending} onClick={async () => {
              try { await save.mutateAsync(draft); toast.success(t('projects.membersSaved')); } catch (e) { toast.error(errorMessage(e)); }
            }}>{save.isPending ? t('common.saving') : t('projects.saveMembers')}</Button>
            {dirty && <Button variant="ghost" onClick={() => setDraft(data ?? [])}>{t('common.discard')}</Button>}
          </div>
        </>
      )}
    </div>
  );
}
