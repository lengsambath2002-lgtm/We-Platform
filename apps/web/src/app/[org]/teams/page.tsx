'use client';

import { FileUp, Pencil, Plus, Search, SearchX, UserPlus, Users } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useMemo, useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { InviteDialog, ROLE_TEXT } from '@/components/org/invite-dialog';
import { PlacementDialog, placeText } from '@/components/org/structure-ui';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { usePeople } from '@/lib/api/hr';
import { orgApi, useMembers, useOrgMutation, useTeams } from '@/lib/api/queries';
import { unitPath, useStructure } from '@/lib/api/structure';
import type { Member, Team } from '@/lib/api/types';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

function TeamDialog({ org, team, members, onClose }: { org: string; team: Team | 'new'; members: Member[]; onClose: () => void }) {
  const [name, setName] = useState(team === 'new' ? '' : team.name);
  const [ids, setIds] = useState<string[]>(team === 'new' ? [] : team.membershipIds);
  const { t } = useT();
  const save = useOrgMutation(org, () => orgApi.saveTeam(org, team === 'new' ? null : team.id, { name: name.trim(), membershipIds: ids }));
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{team === 'new' ? t('org.newTeam') : t('org.editName', { name: team.name })}</DialogTitle><DialogDescription>{t('org.teamDialogBody')}</DialogDescription></DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2"><Label htmlFor="team-name">{t('org.teamName')}</Label><Input id="team-name" autoFocus value={name} onChange={(e) => setName(e.target.value)} maxLength={80} /></div>
          <div className="space-y-2">
            <Label>{t('org.people')}</Label>
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
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={name.trim().length < 2 || save.isPending} onClick={async () => {
            try { await save.mutateAsync(undefined); toast.success(t('org.teamSaved')); onClose(); } catch (e) { toast.error(errorMessage(e)); }
          }}>{save.isPending ? t('common.saving') : t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type Filter = 'all' | 'unplaced';
const ALL = '__all__';

/**
 * Everyone in the organisation, in one directory: where each person sits (position and unit), who they report to
 * (with HR), and, for those who manage the structure, placing whoever isn't placed yet. Opens the HR profile.
 */
function Directory({ members, initialFilter }: { members: Member[]; initialFilter: Filter }) {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const hrOn = modules.some((m) => m.key === 'hr' && m.enabled);
  const structure = useStructure(slug, can('org.structure.view'));
  const people = usePeople(slug, hrOn);
  const [q, setQ] = useState('');
  const [unit, setUnit] = useState(ALL);
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [placing, setPlacing] = useState<Member | null>(null);
  const s = structure.data;
  const canPlace = !!s?.canManage;
  const hr = useMemo(() => new Map((people.data ?? []).map((p) => [p.person.id, p])), [people.data]);
  const primaryOf = (id: string) => s?.assignments.find((a) => a.person.membershipId === id && a.primary);
  const placeable = members.filter((m) => m.role !== 'GUEST');
  const unplaced = s ? placeable.filter((m) => !primaryOf(m.membershipId)).length : 0;
  const units = useMemo(() => (s?.units ?? []).filter((u) => !u.archived).map((u) => ({ id: u.id, path: unitPath(s!.units, u.id) })).sort((a, b) => a.path.localeCompare(b.path)), [s]);
  const needle = q.trim().toLowerCase();
  const list = members
    .filter((m) => filter === 'all' || (m.role !== 'GUEST' && !primaryOf(m.membershipId)))
    .filter((m) => unit === ALL || s?.assignments.some((a) => a.person.membershipId === m.membershipId && a.unitId === unit))
    .filter((m) => !needle || [m.name, m.email, m.title, m.unit, hr.get(m.userId)?.department].some((x) => x?.toLowerCase().includes(needle)))
    .sort((a, b) => a.name.localeCompare(b.name));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 flex-1 basis-56 sm:max-w-80">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('org.searchPeople')} aria-label={t('org.searchPeople')} className="bg-card pl-9" />
        </div>
        {units.length > 0 && (
          <Select value={unit} onValueChange={setUnit}>
            <SelectTrigger className="w-full bg-card sm:w-56" aria-label={t('org.unitFilter')}><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('org.allUnits')}</SelectItem>
              {units.map((u) => <SelectItem key={u.id} value={u.id}>{u.path}</SelectItem>)}
            </SelectContent>
          </Select>
        )}
        {canPlace && (
          <div role="radiogroup" aria-label={t('org.peopleFilter')} className="inline-flex h-9 items-center gap-0.5 rounded-lg border bg-muted/60 p-0.5">
            {([['all', `${t('platform.all')} · ${members.length}`], ['unplaced', `${t('structure.notPlaced')} · ${unplaced}`]] as const).map(([v, label]) => (
              <button key={v} type="button" role="radio" aria-checked={filter === v} onClick={() => setFilter(v)}
                className={cn('h-full rounded-md px-3 text-sm font-medium whitespace-nowrap text-muted-foreground tabular-nums hover:text-foreground', filter === v && 'bg-card text-foreground shadow-xs')}>
                {label}
              </button>
            ))}
          </div>
        )}
      </div>

      {list.length === 0 ? (
        filter === 'unplaced' ? <EmptyState icon={UserPlus} title={t('structure.nobodyPlaced')} />
          : <EmptyState icon={SearchX} title={t('org.noMatchTitle')} body={t('org.noMatchBody')} />
      ) : (
        <ul aria-label={t('org.people')} className="divide-y overflow-hidden rounded-xl border bg-card shadow-xs">
          {list.map((m) => {
            const main = primaryOf(m.membershipId);
            const also = s ? s.assignments.filter((a) => a.person.membershipId === m.membershipId && !a.primary) : [];
            const info = hr.get(m.userId);
            const position = main && s ? (s.positions.find((p) => p.id === main.positionId)?.title ?? main.title) : m.title;
            const where = main?.unitId && s ? unitPath(s.units, main.unitId) : (m.unit ?? info?.department);
            const name = <span className="block truncate text-sm font-medium">{m.name}</span>;
            return (
              <li key={m.membershipId} className="flex items-center gap-3 px-4 py-3 sm:gap-4 sm:px-5">
                <PersonAvatar person={m} className="size-10" />
                <div className="min-w-0 flex-1">
                  <div className="flex min-w-0 items-center gap-2">
                    {hrOn ? <Link href={`/${slug}/hr/people/${m.userId}`} className="min-w-0 hover:underline">{name}</Link> : name}
                    {m.role !== 'MEMBER' && <span className="shrink-0 rounded-md bg-muted px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground">{ROLE_TEXT[m.role]?.label}</span>}
                  </div>
                  <p className="truncate text-xs text-muted-foreground">{m.email}{info?.workPhone && ` · ${info.workPhone}`}</p>
                </div>
                <div className="hidden min-w-0 text-sm sm:block sm:w-[38%]">
                  {position || where ? (
                    <>
                      <p className="truncate font-medium">{position ?? t('structure.noPosition')}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {where ?? '—'}{also.length > 0 && s && ` · ${t('structure.alsoIn')}: ${also.map((x) => placeText(s, x)).join('; ')}`}
                      </p>
                    </>
                  ) : m.role !== 'GUEST' && s ? (
                    <span className="rounded-md border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300">{t('structure.notPlaced')}</span>
                  ) : <span className="text-muted-foreground">—</span>}
                </div>
                <div className="hidden w-40 min-w-0 text-xs text-muted-foreground lg:block">
                  {info?.manager && <span className="block truncate">{t('hr.reportsTo', { name: info.manager.name })}</span>}
                </div>
                {canPlace && m.role !== 'GUEST' && (
                  <Button variant={main ? 'ghost' : 'outline'} size={main ? 'icon' : 'sm'} className={cn('shrink-0', main && 'size-8 text-muted-foreground')} aria-label={t('structure.placeOf', { name: m.name })} onClick={() => setPlacing(m)}>
                    {main ? <Pencil className="size-4" /> : <UserPlus className="size-4" />}{!main && <span className="hidden md:inline">{t('structure.place')}</span>}
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {placing && s && <PlacementDialog org={slug} structure={s} member={placing} onClose={() => setPlacing(null)} />}
    </div>
  );
}

function PeoplePage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const members = useMembers(slug);
  const teams = useTeams(slug);
  const [tab, setTab] = useState(params.get('tab') === 'teams' ? 'teams' : 'people');
  const [inviting, setInviting] = useState(false);
  const [editing, setEditing] = useState<Team | 'new' | null>(null);
  const active = useMemo(() => (members.data ?? []).filter((m) => m.status === 'ACTIVE'), [members.data]);
  const byId = new Map(active.map((m) => [m.membershipId, m]));
  const manage = can('org.members.manage');

  return (
    <>
      <PageHeader title={t('org.peopleTitle')} subtitle={t('org.teamsSubtitle', { count: active.length })}
        actions={manage && <>
          <Button variant="outline" size="sm" asChild><Link href={`/${slug}/admin/members/import`}><FileUp className="size-4" />{t('imports.open')}</Link></Button>
          <Button size="sm" onClick={() => setInviting(true)}><UserPlus className="size-4" />{t('org.invite')}</Button>
        </>} />
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList variant="line" className="mb-4">
          <TabsTrigger value="people">{t('org.tabDirectory')}</TabsTrigger>
          <TabsTrigger value="teams">{t('org.tabTeams')}</TabsTrigger>
        </TabsList>
        <TabsContent value="people">
          {members.error ? <ErrorState error={members.error} retry={() => members.refetch()} /> : members.isPending ? <ListSkeleton rows={8} />
            : <Directory members={active} initialFilter={params.get('filter') === 'unplaced' ? 'unplaced' : 'all'} />}
        </TabsContent>
        <TabsContent value="teams">
          {can('teams.manage') && <Button size="sm" variant="outline" className="mb-4" onClick={() => setEditing('new')}><Plus className="size-4" />{t('org.newTeam')}</Button>}
          {teams.error ? <ErrorState error={teams.error} retry={() => teams.refetch()} /> : teams.isPending ? <ListSkeleton rows={3} /> : (teams.data ?? []).length === 0 ? (
            <EmptyState icon={Users} title={t('org.noTeamsTitle')} body={t('org.noTeamsBody')}
              action={can('teams.manage') ? <Button size="sm" onClick={() => setEditing('new')}>{t('org.createTeam')}</Button> : undefined} />
          ) : (
            <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {teams.data!.map((team) => (
                <li key={team.id} className="rounded-xl border bg-card p-4 shadow-xs">
                  <div className="flex items-center justify-between gap-2">
                    <p className="font-medium">{team.name}</p>
                    {can('teams.manage') && <Button variant="ghost" size="icon" className="size-7" aria-label={t('org.editName', { name: team.name })} onClick={() => setEditing(team)}><Pencil className="size-3.5" /></Button>}
                  </div>
                  <p className="mb-2 text-xs text-muted-foreground">{t('org.peopleCount', { count: team.membershipIds.length })}</p>
                  <div className="flex -space-x-1.5">
                    {team.membershipIds.slice(0, 8).map((id) => byId.get(id)).filter(Boolean).map((m) => <PersonAvatar key={m!.membershipId} person={m!} className="size-7 ring-2 ring-card" />)}
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

export default function Page() {
  return <Suspense><PeoplePage /></Suspense>;
}
