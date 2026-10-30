'use client';

import { Archive, ArchiveRestore, Briefcase, MapPin, Network, Pencil, Phone, Plus, Trash2, UserPlus } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { KindBadge, PlacementDialog, PositionDialog, UnitDialog } from '@/components/org/structure-ui';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { type Position, type Structure, type Unit, structureApi, unitPath, unitTree, useStructure } from '@/lib/api/structure';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const ALL = '__all__';

type Editing =
  | { kind: 'unit'; unit: Unit | null; parentId?: string | null }
  | { kind: 'position'; position: Position | null; unitId?: string | null }
  | { kind: 'place'; membershipId: string; name: string }
  | null;

function Vacancy({ p }: { p: Position }) {
  const { t } = useT();
  const open = p.headcount - p.filled;
  return (
    <span className="inline-flex items-center gap-2">
      <span className="tabular text-sm">{p.filled}/{p.headcount}</span>
      {open > 0 && <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-300">{t('structure.openCount', { count: open })}</span>}
      {open < 0 && <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700 dark:bg-rose-950 dark:text-rose-300">{t('structure.overCount', { count: -open })}</span>}
    </span>
  );
}

/** One unit's card: where it sits, its head and contacts, its sub-units, positions and people. */
function UnitDetail({ s, unit, slug, edit, select }: { s: Structure; unit: Unit; slug: string; edit: (e: Editing) => void; select: (id: string) => void }) {
  const { t } = useT();
  const archive = useOrgMutation(slug, (archived: boolean) => structureApi.archiveUnit(slug, unit.id, archived));
  const remove = useOrgMutation(slug, () => structureApi.deleteUnit(slug, unit.id));
  const children = s.units.filter((u) => u.parentId === unit.id && !u.archived).sort((a, b) => a.name.localeCompare(b.name));
  const positions = s.positions.filter((p) => p.unitId === unit.id && !p.archived);
  const people = s.assignments.filter((a) => a.unitId === unit.id);
  const run = (p: Promise<unknown>, done: string) => p.then(() => toast.success(done)).catch((e) => toast.error(errorMessage(e)));
  return (
    <section aria-label={unit.name} className="space-y-5 rounded-lg border bg-card p-4">
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2"><KindBadge kind={unit.kind} />{unit.code && <span className="text-xs text-muted-foreground">{unit.code}</span>}{unit.archived && <span className="text-xs text-muted-foreground">{t('structure.archived')}</span>}</div>
          <h2 className="mt-1 text-lg font-semibold">{unit.name}</h2>
          {unit.parentId && <p className="text-xs text-muted-foreground">{unitPath(s.units, unit.parentId)}</p>}
        </div>
        {s.canManage && (
          <div className="flex flex-wrap gap-1">
            {!unit.archived && <Button variant="outline" size="sm" onClick={() => edit({ kind: 'unit', unit })}><Pencil className="size-4" />{t('common.edit')}</Button>}
            {!unit.archived && <Button variant="ghost" size="sm" onClick={() => run(archive.mutateAsync(true), t('structure.unitArchived'))}><Archive className="size-4" />{t('structure.archive')}</Button>}
            {unit.archived && <Button variant="ghost" size="sm" onClick={() => run(archive.mutateAsync(false), t('structure.unitRestored'))}><ArchiveRestore className="size-4" />{t('structure.restore')}</Button>}
            <Button variant="ghost" size="sm" className="text-destructive" onClick={() => {
              if (window.confirm(t('structure.deleteUnitConfirm', { name: unit.name }))) run(remove.mutateAsync(undefined), t('structure.unitDeleted'));
            }}><Trash2 className="size-4" />{t('common.delete')}</Button>
          </div>
        )}
      </div>

      <dl className="grid gap-3 text-sm sm:grid-cols-3">
        <div><dt className="text-xs text-muted-foreground">{t('structure.head')}</dt><dd className="mt-1">{unit.head
          ? <span className="inline-flex items-center gap-2"><PersonAvatar person={{ name: unit.head.name, avatarUrl: unit.head.avatarUrl }} className="size-6" />{unit.head.name}</span>
          : <span className="text-muted-foreground">{t('structure.noHead')}</span>}</dd></div>
        {unit.address && <div><dt className="text-xs text-muted-foreground">{t('structure.address')}</dt><dd className="mt-1 flex gap-1"><MapPin className="mt-0.5 size-3.5 shrink-0" />{unit.address}</dd></div>}
        {unit.phone && <div><dt className="text-xs text-muted-foreground">{t('structure.phone')}</dt><dd className="mt-1 flex gap-1"><Phone className="mt-0.5 size-3.5 shrink-0" /><a className="hover:underline" href={`tel:${unit.phone}`}>{unit.phone}</a></dd></div>}
      </dl>

      <div>
        <div className="mb-2 flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold">{t('structure.subUnits')}</h3>
          {s.canManage && !unit.archived && <Button variant="ghost" size="sm" onClick={() => edit({ kind: 'unit', unit: null, parentId: unit.id })}><Plus className="size-4" />{t('structure.addSubUnit')}</Button>}
        </div>
        {children.length === 0 ? <p className="text-sm text-muted-foreground">{t('structure.noSubUnits')}</p> : (
          <ul className="flex flex-wrap gap-2">{children.map((c) => (
            <li key={c.id}><button type="button" onClick={() => select(c.id)} className="inline-flex items-center gap-2 rounded-md border px-2 py-1 text-sm hover:border-primary/40"><KindBadge kind={c.kind} />{c.name}</button></li>
          ))}</ul>
        )}
      </div>

      <div>
        <div className="mb-2 flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold">{t('structure.positions')}</h3>
          {s.canManage && !unit.archived && <Button variant="ghost" size="sm" onClick={() => edit({ kind: 'position', position: null, unitId: unit.id })}><Plus className="size-4" />{t('structure.addPosition')}</Button>}
        </div>
        {positions.length === 0 ? <p className="text-sm text-muted-foreground">{t('structure.noPositionsHere')}</p> : (
          <ul className="divide-y rounded-md border">{positions.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center gap-2 px-3 py-2">
              <span className="min-w-0 flex-1 text-sm font-medium">{p.title}{p.grade && <span className="ml-2 text-xs font-normal text-muted-foreground">{p.grade}</span>}</span>
              <Vacancy p={p} />
              {s.canManage && <Button variant="ghost" size="icon" aria-label={t('structure.editPositionOf', { title: p.title })} onClick={() => edit({ kind: 'position', position: p })}><Pencil className="size-4" /></Button>}
            </li>
          ))}</ul>
        )}
      </div>

      <div>
        <h3 className="mb-2 text-sm font-semibold">{t('structure.people')}</h3>
        {people.length === 0 ? <p className="text-sm text-muted-foreground">{t('structure.nobodyHere')}</p> : (
          <ul className="divide-y rounded-md border">{people.map((a) => (
            <li key={a.id} className="flex items-center gap-3 px-3 py-2">
              <PersonAvatar person={{ name: a.person.name, avatarUrl: a.person.avatarUrl }} className="size-7" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{a.person.name}</span>
                <span className="block truncate text-xs text-muted-foreground">{[s.positions.find((p) => p.id === a.positionId)?.title, a.primary ? null : t('structure.additional')].filter(Boolean).join(' · ')}</span>
              </span>
              {s.canManage && <Button variant="ghost" size="icon" aria-label={t('structure.placeOf', { name: a.person.name })} onClick={() => edit({ kind: 'place', membershipId: a.person.membershipId, name: a.person.name })}><UserPlus className="size-4" /></Button>}
            </li>
          ))}</ul>
        )}
      </div>
    </section>
  );
}

function UnitsTab({ s, slug, edit }: { s: Structure; slug: string; edit: (e: Editing) => void }) {
  const { t } = useT();
  const [archived, setArchived] = useState(false);
  const rows = unitTree(s.units, archived);
  const [picked, setPicked] = useState<string | null>(null);
  const selected = s.units.find((u) => u.id === picked) ?? rows[0]?.unit ?? null;
  if (rows.length === 0) {
    return <EmptyState icon={Network} title={t('structure.noUnits')} body={s.canManage ? t('structure.noUnitsBody') : undefined}
      action={s.canManage ? <Button size="sm" onClick={() => edit({ kind: 'unit', unit: null })}><Plus className="size-4" />{t('structure.newUnit')}</Button> : undefined} />;
  }
  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,22rem)_minmax(0,1fr)]">
      <div className="space-y-2">
        <ul className="rounded-lg border bg-card p-1" aria-label={t('structure.unitsTab')}>
          {rows.map(({ unit, depth }) => (
            <li key={unit.id}>
              <button type="button" onClick={() => setPicked(unit.id)} aria-current={selected?.id === unit.id ? 'true' : undefined}
                className={cn('flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-muted', selected?.id === unit.id && 'bg-muted font-medium', unit.archived && 'opacity-60')}
                style={{ paddingLeft: `${0.5 + Math.min(depth, 8) * 1}rem` }}>
                <KindBadge kind={unit.kind} />
                <span className="min-w-0 flex-1 truncate">{unit.name}</span>
                <span className="tabular text-xs text-muted-foreground" title={t('structure.peopleCount', { count: unit.people })}>{unit.people}</span>
              </button>
            </li>
          ))}
        </ul>
        <Button variant={archived ? 'secondary' : 'ghost'} size="sm" aria-pressed={archived} onClick={() => setArchived(!archived)}>{t('structure.showArchived')}</Button>
      </div>
      {selected && <UnitDetail s={s} unit={selected} slug={slug} edit={edit} select={setPicked} />}
    </div>
  );
}

function PositionsTab({ s, slug, edit }: { s: Structure; slug: string; edit: (e: Editing) => void }) {
  const { t } = useT();
  const [unit, setUnit] = useState(ALL);
  const [archived, setArchived] = useState(false);
  const archive = useOrgMutation(slug, ({ id, on }: { id: string; on: boolean }) => structureApi.archivePosition(slug, id, on));
  const remove = useOrgMutation(slug, (id: string) => structureApi.deletePosition(slug, id));
  const run = (p: Promise<unknown>, done: string) => p.then(() => toast.success(done)).catch((e) => toast.error(errorMessage(e)));
  const list = s.positions.filter((p) => (archived || !p.archived) && (unit === ALL || p.unitId === unit));
  const holders = (id: string) => s.assignments.filter((a) => a.positionId === id).map((a) => a.person.name);
  const planned = list.filter((p) => !p.archived).reduce((n, p) => n + p.headcount, 0);
  const filled = list.filter((p) => !p.archived).reduce((n, p) => n + Math.min(p.filled, p.headcount), 0);
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Select value={unit} onValueChange={setUnit}>
          <SelectTrigger className="w-64" aria-label={t('structure.unit')}><SelectValue /></SelectTrigger>
          <SelectContent><SelectItem value={ALL}>{t('structure.allUnits')}</SelectItem>{unitTree(s.units).map(({ unit: u, depth }) => <SelectItem key={u.id} value={u.id}>{'— '.repeat(depth)}{u.name}</SelectItem>)}</SelectContent>
        </Select>
        <Button variant={archived ? 'secondary' : 'ghost'} size="sm" aria-pressed={archived} onClick={() => setArchived(!archived)}>{t('structure.showArchived')}</Button>
        <span className="ml-auto text-sm text-muted-foreground">{t('structure.headcountSummary', { filled, planned, open: Math.max(planned - filled, 0) })}</span>
      </div>
      {list.length === 0 ? <EmptyState icon={Briefcase} title={t('structure.noPositions')} body={s.canManage ? t('structure.noPositionsBody') : undefined} /> : (
        <div className="overflow-x-auto rounded-lg border bg-card">
          <Table>
            <TableHeader><TableRow><TableHead>{t('structure.position')}</TableHead><TableHead>{t('structure.unit')}</TableHead><TableHead className="hidden md:table-cell">{t('structure.reportsTo')}</TableHead><TableHead>{t('structure.filled')}</TableHead><TableHead className="hidden lg:table-cell">{t('structure.heldBy')}</TableHead>{s.canManage && <TableHead className="w-32" />}</TableRow></TableHeader>
            <TableBody>{list.map((p) => (
              <TableRow key={p.id} className={p.archived ? 'opacity-60' : undefined}>
                <TableCell><span className="font-medium">{p.title}</span><span className="block text-xs text-muted-foreground">{[p.code, p.grade, p.archived ? t('structure.archived') : null].filter(Boolean).join(' · ')}</span></TableCell>
                <TableCell className="text-sm">{p.unitId ? unitPath(s.units, p.unitId) : <span className="text-muted-foreground">{t('structure.anyUnit')}</span>}</TableCell>
                <TableCell className="hidden text-sm md:table-cell">{s.positions.find((x) => x.id === p.reportsToId)?.title ?? '—'}</TableCell>
                <TableCell><Vacancy p={p} /></TableCell>
                <TableCell className="hidden max-w-64 truncate text-sm lg:table-cell">{holders(p.id).join(', ') || '—'}</TableCell>
                {s.canManage && (
                  <TableCell className="text-right">
                    {!p.archived && <Button variant="ghost" size="icon" aria-label={t('structure.editPositionOf', { title: p.title })} onClick={() => edit({ kind: 'position', position: p })}><Pencil className="size-4" /></Button>}
                    <Button variant="ghost" size="icon" aria-label={p.archived ? t('structure.restore') : t('structure.archive')}
                      onClick={() => run(archive.mutateAsync({ id: p.id, on: !p.archived }), p.archived ? t('structure.positionRestored') : t('structure.positionArchived'))}>
                      {p.archived ? <ArchiveRestore className="size-4" /> : <Archive className="size-4" />}
                    </Button>
                    <Button variant="ghost" size="icon" className="text-destructive" aria-label={t('common.delete')}
                      onClick={() => { if (window.confirm(t('structure.deletePositionConfirm', { title: p.title }))) run(remove.mutateAsync(p.id), t('structure.positionDeleted')); }}>
                      <Trash2 className="size-4" />
                    </Button>
                  </TableCell>
                )}
              </TableRow>
            ))}</TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}

function PeopleTab({ s, edit }: { s: Structure; edit: (e: Editing) => void }) {
  const { t } = useT();
  const primary = s.assignments.filter((a) => a.primary);
  return primary.length === 0 ? <EmptyState icon={UserPlus} title={t('structure.nobodyPlaced')} body={s.canManage ? t('structure.nobodyPlacedBody') : undefined} /> : (
    <div className="overflow-x-auto rounded-lg border bg-card">
      <Table>
        <TableHeader><TableRow><TableHead>{t('structure.person')}</TableHead><TableHead>{t('structure.position')}</TableHead><TableHead>{t('structure.unit')}</TableHead><TableHead className="hidden md:table-cell">{t('structure.alsoIn')}</TableHead>{s.canManage && <TableHead className="w-12" />}</TableRow></TableHeader>
        <TableBody>{primary.map((a) => {
          const also = s.assignments.filter((x) => !x.primary && x.person.membershipId === a.person.membershipId);
          return (
            <TableRow key={a.id}>
              <TableCell><span className="flex items-center gap-2"><PersonAvatar person={{ name: a.person.name, avatarUrl: a.person.avatarUrl }} className="size-7" /><span className="font-medium">{a.person.name}</span></span></TableCell>
              <TableCell className="text-sm">{s.positions.find((p) => p.id === a.positionId)?.title ?? a.title ?? '—'}</TableCell>
              <TableCell className="text-sm">{a.unitId ? unitPath(s.units, a.unitId) : '—'}</TableCell>
              <TableCell className="hidden text-sm md:table-cell">{also.map((x) => [s.positions.find((p) => p.id === x.positionId)?.title, x.unitId ? s.units.find((u) => u.id === x.unitId)?.name : null].filter(Boolean).join(' · ')).join('; ') || '—'}</TableCell>
              {s.canManage && <TableCell><Button variant="ghost" size="icon" aria-label={t('structure.placeOf', { name: a.person.name })} onClick={() => edit({ kind: 'place', membershipId: a.person.membershipId, name: a.person.name })}><Pencil className="size-4" /></Button></TableCell>}
            </TableRow>
          );
        })}</TableBody>
      </Table>
    </div>
  );
}

/**
 * The organisation structure: branches, divisions, departments, centers, sections and teams in a tree, the
 * positions in them with planned and filled headcount, and who sits where. Everyone but guests reads it;
 * Owners and Admins change it.
 */
export default function StructurePage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = useStructure(slug, can('org.structure.view'));
  const [editing, setEditing] = useState<Editing>(null);
  if (!can('org.structure.view')) return <PermissionDenied />;
  return (
    <>
      <PageHeader title={t('structure.title')} subtitle={t('structure.subtitle')}
        actions={data?.canManage && <>
          <Button variant="outline" size="sm" asChild><Link href={`/${slug}/admin/members`}><UserPlus className="size-4" />{t('structure.placePeople')}</Link></Button>
          <Button variant="outline" size="sm" onClick={() => setEditing({ kind: 'position', position: null })}><Plus className="size-4" />{t('structure.newPosition')}</Button>
          <Button size="sm" onClick={() => setEditing({ kind: 'unit', unit: null })}><Plus className="size-4" />{t('structure.newUnit')}</Button>
        </>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : (
        <Tabs defaultValue="units">
          <TabsList>
            <TabsTrigger value="units">{t('structure.unitsTab')}</TabsTrigger>
            <TabsTrigger value="positions">{t('structure.positionsTab')}</TabsTrigger>
            <TabsTrigger value="people">{t('structure.peopleTab')}</TabsTrigger>
          </TabsList>
          <TabsContent value="units" className="mt-4"><UnitsTab s={data} slug={slug} edit={setEditing} /></TabsContent>
          <TabsContent value="positions" className="mt-4"><PositionsTab s={data} slug={slug} edit={setEditing} /></TabsContent>
          <TabsContent value="people" className="mt-4"><PeopleTab s={data} edit={setEditing} /></TabsContent>
        </Tabs>
      )}
      {data && editing?.kind === 'unit' && <UnitDialog org={slug} structure={data} unit={editing.unit} parentId={editing.parentId} onClose={() => setEditing(null)} />}
      {data && editing?.kind === 'position' && <PositionDialog org={slug} structure={data} position={editing.position} unitId={editing.unitId} onClose={() => setEditing(null)} />}
      {data && editing?.kind === 'place' && <PlacementDialog org={slug} structure={data} member={editing} onClose={() => setEditing(null)} />}
    </>
  );
}
