'use client';

import { Pencil, Plus, Search, UserPlus, Users } from 'lucide-react';
import { useMemo, useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { InviteDialog, ROLE_TEXT } from '@/components/org/invite-dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useMembers, useOrgMutation, useTeams } from '@/lib/api/queries';
import type { Member, Team } from '@/lib/api/types';
import { useOrg } from '@/lib/org-context';

function TeamDialog({ org, team, members, onClose }: { org: string; team: Team | 'new'; members: Member[]; onClose: () => void }) {
  const [name, setName] = useState(team === 'new' ? '' : team.name);
  const [ids, setIds] = useState<string[]>(team === 'new' ? [] : team.membershipIds);
  const save = useOrgMutation(org, () => orgApi.saveTeam(org, team === 'new' ? null : team.id, { name: name.trim(), membershipIds: ids }));
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{team === 'new' ? 'New team' : `Edit ${team.name}`}</DialogTitle><DialogDescription>Teams group people, e.g. by department.</DialogDescription></DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2"><Label htmlFor="team-name">Name</Label><Input id="team-name" autoFocus value={name} onChange={(e) => setName(e.target.value)} maxLength={80} /></div>
          <div className="space-y-2">
            <Label>People</Label>
            <div className="max-h-56 space-y-1 overflow-y-auto rounded-md border p-2">
              {members.map((m) => (
                <label key={m.membershipId} className="flex items-center gap-2 text-sm">
                  <Checkbox checked={ids.includes(m.membershipId)} onCheckedChange={(v) => setIds((x) => v ? [...x, m.membershipId] : x.filter((i) => i !== m.membershipId))} />
                  {m.name}
                </label>
              ))}
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={name.trim().length < 2 || save.isPending} onClick={async () => {
            try { await save.mutateAsync(undefined); toast.success('Team saved'); onClose(); } catch (e) { toast.error(errorMessage(e)); }
          }}>{save.isPending ? 'Saving…' : 'Save'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function TeamsPage() {
  const { slug, can } = useOrg();
  const members = useMembers(slug);
  const teams = useTeams(slug);
  const [q, setQ] = useState('');
  const [inviting, setInviting] = useState(false);
  const [editing, setEditing] = useState<Team | 'new' | null>(null);
  const active = useMemo(() => (members.data ?? []).filter((m) => m.status === 'ACTIVE'), [members.data]);
  const shown = active.filter((m) => !q || `${m.name} ${m.email}`.toLowerCase().includes(q.toLowerCase()));
  const byId = new Map(active.map((m) => [m.membershipId, m]));

  return (
    <>
      <PageHeader title="Teams" subtitle={`${active.length} people in this organisation`}
        actions={can('org.members.manage') && <Button size="sm" onClick={() => setInviting(true)}><UserPlus className="size-4" />Invite</Button>} />
      <Tabs defaultValue="people">
        <TabsList className="mb-4"><TabsTrigger value="people">People</TabsTrigger><TabsTrigger value="teams">Teams</TabsTrigger></TabsList>
        <TabsContent value="people">
          <div className="relative mb-4 w-full sm:w-72">
            <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search people" className="pl-8" aria-label="Search people" />
          </div>
          {members.error ? <ErrorState error={members.error} retry={() => members.refetch()} /> : members.isPending ? <ListSkeleton /> : shown.length === 0 ? (
            <EmptyState icon={Search} title="Nobody matches" body="Try another name or email." />
          ) : (
            <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {shown.map((m) => (
                <li key={m.membershipId} className="flex items-center gap-3 rounded-lg border bg-card p-3">
                  <PersonAvatar person={m} className="size-10" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{m.name}</p>
                    <p className="truncate text-xs text-muted-foreground">{m.title ?? m.email}</p>
                  </div>
                  <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">{ROLE_TEXT[m.role]?.label}</span>
                </li>
              ))}
            </ul>
          )}
        </TabsContent>
        <TabsContent value="teams">
          {can('teams.manage') && <Button size="sm" variant="outline" className="mb-4" onClick={() => setEditing('new')}><Plus className="size-4" />New team</Button>}
          {teams.error ? <ErrorState error={teams.error} retry={() => teams.refetch()} /> : teams.isPending ? <ListSkeleton rows={3} /> : (teams.data ?? []).length === 0 ? (
            <EmptyState icon={Users} title="No teams yet" body="Teams group people, for example by department or location."
              action={can('teams.manage') ? <Button size="sm" onClick={() => setEditing('new')}>Create a team</Button> : undefined} />
          ) : (
            <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {teams.data!.map((t) => (
                <li key={t.id} className="rounded-lg border bg-card p-4">
                  <div className="flex items-center justify-between gap-2">
                    <p className="font-medium">{t.name}</p>
                    {can('teams.manage') && <Button variant="ghost" size="icon" className="size-7" aria-label={`Edit ${t.name}`} onClick={() => setEditing(t)}><Pencil className="size-3.5" /></Button>}
                  </div>
                  <p className="mb-2 text-xs text-muted-foreground">{t.membershipIds.length} {t.membershipIds.length === 1 ? 'person' : 'people'}</p>
                  <div className="flex -space-x-1.5">
                    {t.membershipIds.slice(0, 8).map((id) => byId.get(id)).filter(Boolean).map((m) => <PersonAvatar key={m!.membershipId} person={m!} className="size-7 ring-2 ring-card" />)}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </TabsContent>
      </Tabs>
      <InviteDialog org={slug} open={inviting} onOpenChange={setInviting} />
      {editing && <TeamDialog org={slug} team={editing} members={active} onClose={() => setEditing(null)} />}
    </>
  );
}
