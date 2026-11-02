'use client';

import {
  Archive, ArchiveRestore, Briefcase, ChevronDown, ChevronRight, ChevronsDownUp, ChevronsUpDown, MapPin, MoreHorizontal, Network, Pencil, Phone,
  Plus, Search, Trash2, UserMinus, UserPlus, Users,
} from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { type ReactNode, Suspense, useMemo, useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import {
  AddPeopleDialog, ConfirmDialog, KindBadge, KindIcon, KIND_LABEL, PlacementDialog, PositionDialog, UnitDialog, removeFromUnit,
} from '@/components/org/structure-ui';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type ChartNode as PersonNode, useOrgChart } from '@/lib/api/hr';
import { useMembers, useOrgMutation } from '@/lib/api/queries';
import { type Assignment, type Position, type Structure, type Unit, structureApi, unitPath, unitTree, useStructure } from '@/lib/api/structure';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const ALL = '__all__';

type Editing =
  | { kind: 'unit'; unit: Unit | null; parentId?: string | null }
  | { kind: 'position'; position: Position | null; unitId?: string | null }
  | { kind: 'place'; membershipId: string; name: string }
  | { kind: 'add'; unitId: string | null; positionId?: string | null; title: string }
  | { kind: 'confirm'; title: string; body: string; confirm: string; run: () => Promise<unknown> }
  | null;

type Edit = (e: Editing) => void;

/* ---------- helpers ---------- */

/** Children of each unit (active ones unless `archived`), and each unit's people including everything below it. */
function useTree(s: Structure, archived = false) {
  return useMemo(() => {
    const shown = s.units.filter((u) => archived || !u.archived);
    const ids = new Set(shown.map((u) => u.id));
    const kids = new Map<string | null, Unit[]>();
    shown.forEach((u) => {
      const p = u.parentId && ids.has(u.parentId) ? u.parentId : null;
      kids.set(p, [...(kids.get(p) ?? []), u]);
    });
    kids.forEach((list) => list.sort((a, b) => a.name.localeCompare(b.name)));
    const total = new Map<string, number>();
    const count = (u: Unit, depth = 0): number => {
      if (total.has(u.id)) return total.get(u.id)!;
      const n = u.people + (depth > 30 ? 0 : (kids.get(u.id) ?? []).reduce((sum, c) => sum + count(c, depth + 1), 0));
      total.set(u.id, n);
      return n;
    };
    shown.forEach((u) => count(u));
    return { kids, total, roots: kids.get(null) ?? [] };
  }, [s.units, archived]);
}

const ancestors = (units: Unit[], id: string | null) => {
  const byId = new Map(units.map((u) => [u.id, u]));
  const out: Unit[] = [];
  for (let at = id ? byId.get(id) : undefined; at && out.length < 30; at = at.parentId ? byId.get(at.parentId) : undefined) out.unshift(at);
  return out;
};

function Vacancy({ p }: { p: Position }) {
  const { t } = useT();
  const open = p.headcount - p.filled;
  return (
    <span className="inline-flex items-center gap-2">
      <span className="text-sm tabular">{p.filled}/{p.headcount}</span>
      {open > 0 && <span className="rounded-md border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300">{t('structure.openCount', { count: open })}</span>}
      {open < 0 && <span className="rounded-md border border-rose-200 bg-rose-50 px-1.5 py-0.5 text-xs font-medium text-rose-700 dark:border-rose-900 dark:bg-rose-950 dark:text-rose-300">{t('structure.overCount', { count: -open })}</span>}
    </span>
  );
}

function Avatars({ people, max = 4 }: { people: { name: string; avatarUrl: string | null }[]; max?: number }) {
  if (people.length === 0) return null;
  return (
    <span className="flex -space-x-2">
      {people.slice(0, max).map((p, i) => <PersonAvatar key={i} person={p} className="size-7 ring-2 ring-card" />)}
      {people.length > max && <span className="grid size-7 place-items-center rounded-full bg-muted text-[10px] font-medium ring-2 ring-card">+{people.length - max}</span>}
    </span>
  );
}

function Section({ title, count, action, children }: { title: string; count?: number; action?: ReactNode; children: ReactNode }) {
  return (
    <section>
      <div className="mb-2.5 flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold">{title}{count !== undefined && <span className="ml-1.5 font-normal text-muted-foreground tabular">{count}</span>}</h3>
        {action}
      </div>
      {children}
    </section>
  );
}

const Empty = ({ children }: { children: ReactNode }) => <div className="rounded-lg border border-dashed px-4 py-5 text-center text-sm text-muted-foreground">{children}</div>;

/* ---------- units: tree + unit panel ---------- */

function UnitTreePanel({ s, selected, onSelect, archived, setArchived }: {
  s: Structure; selected: string | null; onSelect: (id: string) => void; archived: boolean; setArchived: (v: boolean) => void;
}) {
  const { t } = useT();
  const { kids, total, roots } = useTree(s, archived);
  const [q, setQ] = useState('');
  const [closed, setClosed] = useState<Set<string>>(new Set());
  const needle = q.trim().toLowerCase();
  // With a search: the matching units and their ancestors, all open.
  const visible = useMemo(() => {
    if (!needle) return null;
    const keep = new Set<string>();
    s.units.forEach((u) => {
      if (u.name.toLowerCase().includes(needle) || u.code?.toLowerCase().includes(needle)) ancestors(s.units, u.id).forEach((a) => keep.add(a.id));
    });
    return keep;
  }, [needle, s.units]);
  const parents = s.units.filter((u) => (kids.get(u.id) ?? []).length > 0).map((u) => u.id);

  const rows: ReactNode[] = [];
  const walk = (list: Unit[], depth: number) => list.forEach((u) => {
    if (visible && !visible.has(u.id)) return;
    const children = kids.get(u.id) ?? [];
    const open = !!visible || !closed.has(u.id);
    const match = !!needle && u.name.toLowerCase().includes(needle);
    rows.push(
      <li key={u.id} className="flex items-center" style={{ paddingLeft: `${Math.min(depth, 8) * 0.875}rem` }}>
        {children.length > 0 ? (
          <button type="button" aria-label={t('structure.toggleSub')} aria-expanded={open} disabled={!!visible}
            onClick={() => { const n = new Set(closed); if (n.has(u.id)) n.delete(u.id); else n.add(u.id); setClosed(n); }}
            className="grid size-6 shrink-0 place-items-center rounded text-muted-foreground hover:bg-muted hover:text-foreground disabled:opacity-40">
            {open ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />}
          </button>
        ) : <span className="size-6 shrink-0" />}
        <button type="button" onClick={() => onSelect(u.id)} aria-current={selected === u.id ? 'true' : undefined}
          className={cn('flex min-w-0 flex-1 items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-muted',
            selected === u.id && 'bg-accent font-medium text-accent-foreground hover:bg-accent', u.archived && 'opacity-60')}>
          <KindIcon kind={u.kind} className={cn(selected === u.id && 'text-primary')} />
          <span className={cn('min-w-0 flex-1 truncate', match && 'font-semibold')}>{u.name}</span>
          <span className="text-xs text-muted-foreground tabular" title={t('structure.peopleCount', { count: total.get(u.id) ?? 0 })}>{total.get(u.id) ?? 0}</span>
        </button>
      </li>,
    );
    if (open) walk(children, depth + 1);
  });
  walk(roots, 0);

  return (
    <div className="flex flex-col rounded-xl border bg-card shadow-xs lg:sticky lg:top-20 lg:max-h-[calc(100dvh-7rem)]">
      <div className="flex items-center gap-1 border-b p-2">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input type="search" aria-label={t('structure.searchUnits')} placeholder={t('structure.searchUnits')} className="h-8 border-0 bg-transparent pl-8 shadow-none focus-visible:ring-0" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <Button variant="ghost" size="icon" className="size-8" aria-label={t('structure.expandAll')} title={t('structure.expandAll')} onClick={() => setClosed(new Set())}><ChevronsUpDown className="size-4" /></Button>
        <Button variant="ghost" size="icon" className="size-8" aria-label={t('structure.collapseAll')} title={t('structure.collapseAll')} onClick={() => setClosed(new Set(parents))}><ChevronsDownUp className="size-4" /></Button>
      </div>
      <ul className="max-h-80 flex-1 space-y-0.5 overflow-y-auto lg:max-h-none p-1.5" aria-label={t('structure.unitsTab')}>
        {rows.length > 0 ? rows : <li className="px-3 py-6 text-center text-sm text-muted-foreground">{t('structure.noUnitMatch', { q })}</li>}
      </ul>
      <div className="border-t p-2">
        <Button variant={archived ? 'secondary' : 'ghost'} size="sm" className="w-full justify-start text-muted-foreground" aria-pressed={archived} onClick={() => setArchived(!archived)}>
          <Archive className="size-4" />{t('structure.showArchived')}
        </Button>
      </div>
    </div>
  );
}

function Stat({ label, children, hint }: { label: string; children: ReactNode; hint?: ReactNode }) {
  return (
    <div className="min-w-0 rounded-lg border bg-background/60 p-3">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1.5 min-w-0 text-sm font-medium">{children}</dd>
      {hint && <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

function UnitPanel({ s, unit, slug, edit, select, total, kids }: {
  s: Structure; unit: Unit; slug: string; edit: Edit; select: (id: string) => void; total: Map<string, number>; kids: Map<string | null, Unit[]>;
}) {
  const { t } = useT();
  const archive = useOrgMutation(slug, (archived: boolean) => structureApi.archiveUnit(slug, unit.id, archived));
  const remove = useOrgMutation(slug, () => structureApi.deleteUnit(slug, unit.id));
  const takeOut = useOrgMutation(slug, (membershipId: string) => removeFromUnit(slug, s, membershipId, unit.id));
  const children = s.units.filter((u) => u.parentId === unit.id && (!u.archived || unit.archived)).sort((a, b) => a.name.localeCompare(b.name));
  const positions = s.positions.filter((p) => p.unitId === unit.id && !p.archived).sort((a, b) => a.title.localeCompare(b.title));
  const people = s.assignments.filter((a) => a.unitId === unit.id).sort((a, b) => Number(b.primary) - Number(a.primary) || a.person.name.localeCompare(b.person.name));
  const planned = positions.reduce((n, p) => n + p.headcount, 0);
  const filled = positions.reduce((n, p) => n + Math.min(p.filled, p.headcount), 0);
  const manage = s.canManage && !unit.archived;
  const path = ancestors(s.units, unit.parentId);
  const run = (p: Promise<unknown>, done: string) => p.then(() => toast.success(done)).catch((e) => toast.error(errorMessage(e)));
  const addPeople = (positionId?: string, title?: string) =>
    edit({ kind: 'add', unitId: unit.id, positionId, title: title ?? t('structure.addPeopleTo', { unit: unit.name }) });

  return (
    <section aria-label={unit.name} className="min-w-0 rounded-xl border bg-card shadow-xs">
      <div className="border-b p-5">
        {path.length > 0 && (
          <nav className="mb-2 flex flex-wrap items-center gap-1 text-xs text-muted-foreground" aria-label={t('structure.partOf')}>
            {path.map((a) => (
              <span key={a.id} className="inline-flex items-center gap-1">
                <button type="button" onClick={() => select(a.id)} className="hover:text-foreground hover:underline">{a.name}</button>
                <ChevronRight className="size-3" aria-hidden />
              </span>
            ))}
          </nav>
        )}
        <div className="flex flex-wrap items-start gap-3">
          <div className="min-w-0 flex-1 basis-60">
            <div className="flex flex-wrap items-center gap-2">
              <KindBadge kind={unit.kind} />
              {unit.code && <span className="font-mono text-xs text-muted-foreground">{unit.code}</span>}
              {unit.archived && <span className="rounded-md bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">{t('structure.archived')}</span>}
            </div>
            <h2 className="mt-2 truncate text-xl font-semibold tracking-tight">{unit.name}</h2>
          </div>
          {s.canManage && (
            <div className="flex items-center gap-2">
              {manage && <Button size="sm" onClick={() => addPeople()}><UserPlus className="size-4" />{t('structure.addPeople')}</Button>}
              {manage && <Button variant="outline" size="sm" onClick={() => edit({ kind: 'unit', unit })}><Pencil className="size-4" />{t('common.edit')}</Button>}
              <DropdownMenu>
                <DropdownMenuTrigger asChild><Button variant="outline" size="icon" className="size-8" aria-label={t('structure.actionsFor', { name: unit.name })}><MoreHorizontal className="size-4" /></Button></DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {unit.archived
                    ? <DropdownMenuItem onSelect={() => run(archive.mutateAsync(false), t('structure.unitRestored'))}><ArchiveRestore className="size-4" />{t('structure.restore')}</DropdownMenuItem>
                    : <DropdownMenuItem onSelect={() => run(archive.mutateAsync(true), t('structure.unitArchived'))}><Archive className="size-4" />{t('structure.archive')}</DropdownMenuItem>}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem variant="destructive" onSelect={() => edit({
                    kind: 'confirm', title: t('structure.deleteUnitTitle', { name: unit.name }), body: t('structure.deleteUnitBody'), confirm: t('common.delete'),
                    run: () => remove.mutateAsync(undefined).then(() => toast.success(t('structure.unitDeleted'))).catch((e) => { toast.error(errorMessage(e)); }),
                  })}><Trash2 className="size-4" />{t('common.delete')}</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )}
        </div>

        <dl className="mt-5 grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Stat label={t('structure.head')}>
            {unit.head ? (
              <span className="flex min-w-0 items-center gap-2"><PersonAvatar person={{ name: unit.head.name, avatarUrl: unit.head.avatarUrl }} className="size-6" /><span className="truncate">{unit.head.name}</span></span>
            ) : manage ? (
              <button type="button" onClick={() => edit({ kind: 'unit', unit })} className="text-primary hover:underline">{t('structure.setHead')}</button>
            ) : <span className="text-muted-foreground">{t('structure.noHead')}</span>}
          </Stat>
          <Stat label={t('structure.statPeople')} hint={t('structure.statPeopleHint', { count: people.length })}>
            <span className="text-lg font-semibold tabular">{total.get(unit.id) ?? unit.people}</span>
          </Stat>
          <Stat label={t('structure.statSeats')}><span className="text-lg font-semibold tabular">{filled}/{planned}</span></Stat>
          <Stat label={t('structure.statOpen')}><span className={cn('text-lg font-semibold tabular', planned - filled > 0 && 'text-amber-700 dark:text-amber-300')}>{Math.max(planned - filled, 0)}</span></Stat>
        </dl>
        {(unit.address || unit.phone) && (
          <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-sm text-muted-foreground">
            {unit.address && <span className="inline-flex items-center gap-1.5"><MapPin className="size-3.5" aria-hidden />{unit.address}</span>}
            {unit.phone && <a href={`tel:${unit.phone}`} className="inline-flex items-center gap-1.5 hover:text-foreground hover:underline"><Phone className="size-3.5" aria-hidden />{unit.phone}</a>}
          </div>
        )}
      </div>

      <div className="space-y-7 p-5">
        <Section title={t('structure.people')} count={people.length}
          action={manage && people.length > 0 && <Button variant="ghost" size="sm" onClick={() => addPeople()}><UserPlus className="size-4" />{t('structure.addPeople')}</Button>}>
          {people.length === 0 ? (
            <Empty>
              <p>{t('structure.nobodyHere')}</p>
              {manage && <Button size="sm" variant="outline" className="mt-3" onClick={() => addPeople()}><UserPlus className="size-4" />{t('structure.addPeople')}</Button>}
            </Empty>
          ) : (
            <ul className="divide-y rounded-lg border">
              {people.map((a) => <PersonRow key={a.id} a={a} s={s} manage={manage} unit={unit} edit={edit}
                remove={() => run(takeOut.mutateAsync(a.person.membershipId), t('structure.removedFrom', { name: a.person.name, unit: unit.name }))} />)}
            </ul>
          )}
        </Section>

        <Section title={t('structure.positions')} count={positions.length}
          action={manage && <Button variant="ghost" size="sm" onClick={() => edit({ kind: 'position', position: null, unitId: unit.id })}><Plus className="size-4" />{t('structure.addPosition')}</Button>}>
          {positions.length === 0 ? <Empty>{t('structure.noPositionsHere')}</Empty> : (
            <ul className="divide-y rounded-lg border">
              {positions.map((p) => {
                const holders = s.assignments.filter((a) => a.positionId === p.id).map((a) => ({ name: a.person.name, avatarUrl: a.person.avatarUrl }));
                return (
                  <li key={p.id} className="flex flex-wrap items-center gap-3 px-3 py-2.5">
                    <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground"><Briefcase className="size-4" aria-hidden /></span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{p.title}</span>
                      <span className="block truncate text-xs text-muted-foreground">{[p.grade, s.positions.find((x) => x.id === p.reportsToId)?.title && `${t('structure.reportsTo')}: ${s.positions.find((x) => x.id === p.reportsToId)?.title}`].filter(Boolean).join(' · ') || (holders.length ? holders.map((h) => h.name).join(', ') : t('structure.nobodyHolds'))}</span>
                    </span>
                    <Avatars people={holders} />
                    <Vacancy p={p} />
                    {manage && p.headcount - p.filled > 0 && <Button size="sm" variant="outline" onClick={() => addPeople(p.id, t('structure.fillPosition', { title: p.title }))}>{t('structure.fill')}</Button>}
                    {s.canManage && <Button variant="ghost" size="icon" className="size-8" aria-label={t('structure.editPositionOf', { title: p.title })} onClick={() => edit({ kind: 'position', position: p })}><Pencil className="size-4" /></Button>}
                  </li>
                );
              })}
            </ul>
          )}
        </Section>

        <Section title={t('structure.subUnits')} count={children.length}
          action={manage && <Button variant="ghost" size="sm" onClick={() => edit({ kind: 'unit', unit: null, parentId: unit.id })}><Plus className="size-4" />{t('structure.addSubUnit')}</Button>}>
          {children.length === 0 ? <Empty>{t('structure.noSubUnits')}</Empty> : (
            <ul className="grid gap-2 sm:grid-cols-2">
              {children.map((c) => (
                <li key={c.id}>
                  <button type="button" onClick={() => select(c.id)} aria-label={t('structure.openUnit', { name: c.name })}
                    className={cn('group flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors hover:border-primary/30 hover:bg-muted/40', c.archived && 'opacity-60')}>
                    <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-muted"><KindIcon kind={c.kind} /></span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{c.name}</span>
                      <span className="block truncate text-xs text-muted-foreground">{KIND_LABEL[c.kind]}{c.head ? ` · ${c.head.name}` : ''}{(kids.get(c.id) ?? []).length ? ` · ${t('structure.subUnits')} ${(kids.get(c.id) ?? []).length}` : ''}</span>
                    </span>
                    <span className="inline-flex items-center gap-1 text-xs text-muted-foreground tabular"><Users className="size-3.5" aria-hidden />{total.get(c.id) ?? c.people}</span>
                    <ChevronRight className="size-4 text-muted-foreground/60 group-hover:text-muted-foreground" aria-hidden />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>
    </section>
  );
}

function PersonRow({ a, s, manage, unit, edit, remove }: { a: Assignment; s: Structure; manage: boolean; unit: Unit; edit: Edit; remove: () => void }) {
  const { t } = useT();
  const pos = s.positions.find((p) => p.id === a.positionId);
  return (
    <li className="flex items-center gap-3 px-3 py-2.5">
      <PersonAvatar person={{ name: a.person.name, avatarUrl: a.person.avatarUrl }} className="size-8" />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium">{a.person.name}</span>
        <span className="block truncate text-xs text-muted-foreground">{pos?.title ?? a.title ?? t('structure.noPosition')}</span>
      </span>
      {!a.primary && <span className="rounded-md border px-1.5 py-0.5 text-xs text-muted-foreground">{t('structure.additional')}</span>}
      {manage && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={t('structure.actionsFor', { name: a.person.name })}><MoreHorizontal className="size-4" /></Button></DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onSelect={() => edit({ kind: 'place', membershipId: a.person.membershipId, name: a.person.name })}><Pencil className="size-4" />{t('structure.changePlace')}</DropdownMenuItem>
            <DropdownMenuItem variant="destructive" onSelect={remove}><UserMinus className="size-4" />{t('structure.removeFrom', { unit: unit.name })}</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </li>
  );
}

function UnitsView({ s, slug, edit, selected, setSelected }: { s: Structure; slug: string; edit: Edit; selected: string | null; setSelected: (id: string) => void }) {
  const { t } = useT();
  const [archived, setArchived] = useState(false);
  const { total, kids } = useTree(s, true);
  const rows = unitTree(s.units, archived);
  const unit = s.units.find((u) => u.id === selected) ?? rows[0]?.unit ?? null;
  if (s.units.filter((u) => !u.archived).length === 0 && !archived) {
    return <EmptyState icon={Network} title={t('structure.noUnits')} body={s.canManage ? t('structure.noUnitsBody') : undefined}
      action={s.canManage ? <Button size="sm" onClick={() => edit({ kind: 'unit', unit: null })}><Plus className="size-4" />{t('structure.newUnit')}</Button> : undefined} />;
  }
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,20rem)_minmax(0,1fr)]">
      <UnitTreePanel s={s} selected={unit?.id ?? null} onSelect={setSelected} archived={archived} setArchived={setArchived} />
      {unit && <UnitPanel s={s} unit={unit} slug={slug} edit={edit} select={setSelected} total={total} kids={kids} />}
    </div>
  );
}

/* ---------- positions ---------- */

function PositionsView({ s, slug, edit }: { s: Structure; slug: string; edit: Edit }) {
  const { t } = useT();
  const [unit, setUnit] = useState(ALL);
  const [archived, setArchived] = useState(false);
  const archive = useOrgMutation(slug, ({ id, on }: { id: string; on: boolean }) => structureApi.archivePosition(slug, id, on));
  const remove = useOrgMutation(slug, (id: string) => structureApi.deletePosition(slug, id));
  const run = (p: Promise<unknown>, done: string) => p.then(() => toast.success(done)).catch((e) => toast.error(errorMessage(e)));
  const list = s.positions.filter((p) => (archived || !p.archived) && (unit === ALL || p.unitId === unit))
    .sort((a, b) => (a.unitId ? unitPath(s.units, a.unitId) : '').localeCompare(b.unitId ? unitPath(s.units, b.unitId) : '') || a.title.localeCompare(b.title));
  const live = list.filter((p) => !p.archived);
  const planned = live.reduce((n, p) => n + p.headcount, 0);
  const filled = live.reduce((n, p) => n + Math.min(p.filled, p.headcount), 0);
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Select value={unit} onValueChange={setUnit}>
          <SelectTrigger className="w-64 bg-card" aria-label={t('structure.unit')}><SelectValue /></SelectTrigger>
          <SelectContent><SelectItem value={ALL}>{t('structure.allUnits')}</SelectItem>{unitTree(s.units).map(({ unit: u, depth }) => <SelectItem key={u.id} value={u.id}>{'— '.repeat(depth)}{u.name}</SelectItem>)}</SelectContent>
        </Select>
        <Button variant={archived ? 'secondary' : 'ghost'} size="sm" aria-pressed={archived} onClick={() => setArchived(!archived)}><Archive className="size-4" />{t('structure.showArchived')}</Button>
        <span className="ml-auto text-sm text-muted-foreground tabular">{t('structure.headcountSummary', { filled, planned, open: Math.max(planned - filled, 0) })}</span>
      </div>
      {list.length === 0 ? <EmptyState icon={Briefcase} title={t('structure.noPositions')} body={s.canManage ? t('structure.noPositionsBody') : undefined}
        action={s.canManage ? <Button size="sm" onClick={() => edit({ kind: 'position', position: null })}><Plus className="size-4" />{t('structure.newPosition')}</Button> : undefined} /> : (
        <ul className="divide-y overflow-hidden rounded-xl border bg-card shadow-xs" aria-label={t('structure.positionsTab')}>
          {list.map((p) => {
            const holders = s.assignments.filter((a) => a.positionId === p.id).map((a) => ({ name: a.person.name, avatarUrl: a.person.avatarUrl }));
            const reports = s.positions.find((x) => x.id === p.reportsToId)?.title;
            return (
              <li key={p.id} className={cn('flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3.5 sm:px-5', p.archived && 'opacity-60')}>
                <span className="grid size-9 shrink-0 place-items-center rounded-lg border bg-muted/60 text-muted-foreground"><Briefcase className="size-4" aria-hidden /></span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{p.title}{p.grade && <span className="ml-2 text-xs font-normal text-muted-foreground">{p.grade}</span>}</span>
                  <span className="block truncate text-xs text-muted-foreground">
                    {[p.unitId ? unitPath(s.units, p.unitId) : t('structure.anyUnit'), reports && `${t('structure.reportsTo')}: ${reports}`, p.archived && t('structure.archived')].filter(Boolean).join(' · ')}
                  </span>
                </span>
                <span className="hidden min-w-0 max-w-56 items-center gap-2 md:flex">
                  <Avatars people={holders} />
                  <span className="truncate text-xs text-muted-foreground">{holders.length ? holders.map((h) => h.name).join(', ') : t('structure.nobodyHolds')}</span>
                </span>
                <Vacancy p={p} />
                {s.canManage && (
                  <span className="flex items-center gap-1">
                    {!p.archived && p.headcount - p.filled > 0 && (
                      <Button size="sm" variant="outline" onClick={() => edit({ kind: 'add', unitId: p.unitId, positionId: p.id, title: t('structure.fillPosition', { title: p.title }) })}>{t('structure.fill')}</Button>
                    )}
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={t('structure.actionsFor', { name: p.title })}><MoreHorizontal className="size-4" /></Button></DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {!p.archived && <DropdownMenuItem onSelect={() => edit({ kind: 'position', position: p })}><Pencil className="size-4" />{t('structure.editPosition')}</DropdownMenuItem>}
                        <DropdownMenuItem onSelect={() => run(archive.mutateAsync({ id: p.id, on: !p.archived }), p.archived ? t('structure.positionRestored') : t('structure.positionArchived'))}>
                          {p.archived ? <ArchiveRestore className="size-4" /> : <Archive className="size-4" />}{p.archived ? t('structure.restore') : t('structure.archive')}
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem variant="destructive" onSelect={() => edit({
                          kind: 'confirm', title: t('structure.deletePositionConfirm', { title: p.title }), body: t('structure.deletePositionBody'), confirm: t('common.delete'),
                          run: () => remove.mutateAsync(p.id).then(() => toast.success(t('structure.positionDeleted'))).catch((e) => { toast.error(errorMessage(e)); }),
                        })}><Trash2 className="size-4" />{t('common.delete')}</DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

/* ---------- chart ---------- */

function ChartNode({ u, kids, total, open, toggle, onOpen, depth }: {
  u: Unit; kids: Map<string | null, Unit[]>; total: Map<string, number>; open: Set<string>; toggle: (id: string) => void; onOpen: (id: string) => void; depth: number;
}) {
  const { t } = useT();
  const children = kids.get(u.id) ?? [];
  const expanded = open.has(u.id);
  return (
    <li>
      <div className="flex w-52 flex-col items-center">
        <button type="button" onClick={() => onOpen(u.id)} aria-label={t('structure.openUnit', { name: u.name })}
          className="w-full rounded-xl border bg-card p-3 text-left shadow-xs transition-all hover:-translate-y-px hover:border-primary/40 hover:shadow-md">
          <span className="flex items-center gap-1.5 text-[11px] text-muted-foreground"><KindIcon kind={u.kind} className="size-3" />{KIND_LABEL[u.kind]}</span>
          <span className="mt-1 block truncate text-sm font-semibold">{u.name}</span>
          <span className="mt-2 flex items-center justify-between gap-2">
            {u.head ? (
              <span className="flex min-w-0 items-center gap-1.5"><PersonAvatar person={{ name: u.head.name, avatarUrl: u.head.avatarUrl }} className="size-5" /><span className="truncate text-xs">{u.head.name}</span></span>
            ) : <span className="text-xs text-muted-foreground">{t('structure.noHead')}</span>}
            <span className="inline-flex shrink-0 items-center gap-1 text-xs text-muted-foreground tabular"><Users className="size-3" aria-hidden />{total.get(u.id) ?? 0}</span>
          </span>
        </button>
        {children.length > 0 && (
          <button type="button" onClick={() => toggle(u.id)} aria-expanded={expanded} aria-label={t('structure.toggleSub')}
            className="relative z-10 -mt-2.5 inline-flex h-5 items-center gap-0.5 rounded-full border bg-card px-2 text-[11px] font-medium text-muted-foreground shadow-xs hover:text-foreground">
            {expanded ? <ChevronDown className="size-3" /> : <ChevronRight className="size-3" />}{children.length}
          </button>
        )}
      </div>
      {expanded && children.length > 0 && depth < 30 && (
        <ul>{children.map((c) => <ChartNode key={c.id} u={c} kids={kids} total={total} open={open} toggle={toggle} onOpen={onOpen} depth={depth + 1} />)}</ul>
      )}
    </li>
  );
}

/** One person in the people chart, with the people who report to them below. */
function PersonChartNode({ node, byManager, slug, depth }: { node: PersonNode; byManager: Map<string, PersonNode[]>; slug: string; depth: number }) {
  const reports = byManager.get(node.person.id) ?? [];
  return (
    <li>
      <Link href={`/${slug}/hr/people/${node.person.id}`} className="flex w-52 items-center gap-2.5 rounded-xl border bg-card p-3 text-left shadow-xs transition-all hover:-translate-y-px hover:border-primary/40 hover:shadow-md">
        <PersonAvatar person={node.person} className="size-9" />
        <span className="min-w-0">
          <span className="block truncate text-sm font-semibold">{node.person.name}</span>
          <span className="block truncate text-xs text-muted-foreground">{[node.title, node.department].filter(Boolean).join(' · ') || '—'}</span>
        </span>
      </Link>
      {reports.length > 0 && depth < 20 && <ul>{reports.map((r) => <PersonChartNode key={r.person.id} node={r} byManager={byManager} slug={slug} depth={depth + 1} />)}</ul>}
    </li>
  );
}

/** Who reports to whom (from the managers on people's HR profiles). */
function PeopleChart({ slug }: { slug: string }) {
  const { t } = useT();
  const { data, error, isPending, refetch } = useOrgChart(slug);
  if (error) return <div className="p-6"><ErrorState error={error} retry={() => refetch()} /></div>;
  if (isPending) return <div className="p-6"><ListSkeleton rows={4} /></div>;
  if (data.length === 0) return <div className="p-6"><EmptyState icon={Users} title={t('hr.nobodyToShow')} /></div>;
  const byManager = new Map<string, PersonNode[]>();
  data.forEach((n) => { if (n.managerId) byManager.set(n.managerId, [...(byManager.get(n.managerId) ?? []), n]); });
  const ids = new Set(data.map((n) => n.person.id));
  const top = data.filter((n) => !n.managerId || !ids.has(n.managerId));
  return (
    <div className="overflow-x-auto bg-muted/30 p-8">
      <div className="org-chart mx-auto w-max"><ul aria-label={t('hr.orgChartTitle')}>{top.map((n) => <PersonChartNode key={n.person.id} node={n} byManager={byManager} slug={slug} depth={0} />)}</ul></div>
    </div>
  );
}

function ChartView({ s, slug, onOpen, people, mode, setMode }: {
  s: Structure; slug: string; onOpen: (id: string) => void; people: boolean; mode: 'units' | 'people'; setMode: (m: 'units' | 'people') => void;
}) {
  const { t } = useT();
  const { kids, total, roots } = useTree(s);
  // Open the top two levels to start with; big organisations stay readable.
  const [open, setOpen] = useState<Set<string>>(() => new Set([...roots.map((r) => r.id)]));
  const toggle = (id: string) => { const n = new Set(open); if (n.has(id)) n.delete(id); else n.add(id); setOpen(n); };
  const all = s.units.filter((u) => !u.archived && (kids.get(u.id) ?? []).length > 0).map((u) => u.id);
  const showPeople = people && mode === 'people';
  if (roots.length === 0 && !people) return <EmptyState icon={Network} title={t('structure.noUnits')} />;
  return (
    <div className="rounded-xl border bg-card shadow-xs">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-2.5">
        {people ? (
          <div role="radiogroup" aria-label={t('structure.chartTab')} className="inline-flex h-8 items-center gap-0.5 rounded-lg border bg-muted/60 p-0.5">
            {(['units', 'people'] as const).map((m) => (
              <button key={m} type="button" role="radio" aria-checked={mode === m} onClick={() => setMode(m)}
                className={cn('h-full rounded-md px-3 text-sm font-medium text-muted-foreground hover:text-foreground', mode === m && 'bg-card text-foreground shadow-xs')}>
                {t(m === 'units' ? 'structure.unitsTab' : 'structure.peopleTab')}
              </button>
            ))}
          </div>
        ) : <p className="text-sm text-muted-foreground">{t('structure.chartHint')}</p>}
        {!showPeople && <div className="flex gap-1">
          <Button variant="ghost" size="sm" onClick={() => setOpen(new Set(all))}><ChevronsUpDown className="size-4" />{t('structure.expandAll')}</Button>
          <Button variant="ghost" size="sm" onClick={() => setOpen(new Set())}><ChevronsDownUp className="size-4" />{t('structure.collapseAll')}</Button>
        </div>}
      </div>
      {showPeople ? <PeopleChart slug={slug} /> : roots.length === 0 ? <div className="p-6"><EmptyState icon={Network} title={t('structure.noUnits')} /></div> : (
        <div className="overflow-x-auto bg-muted/30 p-8">
          <div className="org-chart mx-auto w-max">
            <ul>{roots.map((r) => <ChartNode key={r.id} u={r} kids={kids} total={total} open={open} toggle={toggle} onOpen={onOpen} depth={0} />)}</ul>
          </div>
        </div>
      )}
    </div>
  );
}

/* ---------- page ---------- */

/**
 * The organisation structure: units in a tree (branch → … → team) with who works in each and the positions to fill,
 * every position, every person and where they sit, and a chart. Everyone but guests reads it; Owners and Admins
 * change it, and add people to a unit right where they look at it.
 */
function Structure() {
  const { slug, can, modules } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const { data, error, isPending, refetch } = useStructure(slug, can('org.structure.view'));
  const members = useMembers(slug);
  const [editing, setEditing] = useState<Editing>(null);
  const [view, setView] = useState(() => (['units', 'positions', 'chart'].includes(params.get('view') ?? '') ? params.get('view')! : 'units'));
  const [chartMode, setChartMode] = useState<'units' | 'people'>(params.get('mode') === 'people' ? 'people' : 'units');
  const peopleChart = modules.some((m) => m.key === 'hr' && m.enabled) && can('hr.view');
  const [selected, setSelected] = useState<string | null>(null);
  if (!can('org.structure.view')) return <PermissionDenied />;

  const unplaced = data ? (members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST' && !data.assignments.some((a) => a.person.membershipId === m.membershipId && a.primary)).length : 0;
  const openUnit = (id: string) => { setSelected(id); setView('units'); };

  return (
    <>
      <PageHeader title={t('structure.title')} subtitle={t('structure.subtitle')}
        actions={data?.canManage && <>
          <Button variant="outline" size="sm" onClick={() => setEditing({ kind: 'position', position: null })}><Plus className="size-4" />{t('structure.newPosition')}</Button>
          <Button size="sm" onClick={() => setEditing({ kind: 'unit', unit: null })}><Plus className="size-4" />{t('structure.newUnit')}</Button>
        </>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} /> : (
        <>
          {data.canManage && unplaced > 0 && data.units.length > 0 && (
            <div role="status" className="mb-4 flex flex-wrap items-center gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-2.5 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
              <UserPlus className="size-4 shrink-0" aria-hidden />
              <span className="flex-1">{t('structure.unplacedBanner', { count: unplaced })}</span>
              <Button size="sm" variant="outline" className="h-7 bg-card" asChild><Link href={`/${slug}/teams?filter=unplaced`}>{t('structure.review')}</Link></Button>
            </div>
          )}
          <Tabs value={view} onValueChange={setView}>
            <TabsList variant="line" aria-label={t('structure.viewLabel')}>
              <TabsTrigger value="units">{t('structure.unitsTab')}</TabsTrigger>
              <TabsTrigger value="positions">{t('structure.positionsTab')}</TabsTrigger>
              <TabsTrigger value="chart">{t('structure.chartTab')}</TabsTrigger>
            </TabsList>
            <TabsContent value="units" className="mt-4"><UnitsView s={data} slug={slug} edit={setEditing} selected={selected} setSelected={setSelected} /></TabsContent>
            <TabsContent value="positions" className="mt-4"><PositionsView s={data} slug={slug} edit={setEditing} /></TabsContent>
            <TabsContent value="chart" className="mt-4"><ChartView s={data} slug={slug} onOpen={openUnit} people={peopleChart} mode={chartMode} setMode={setChartMode} /></TabsContent>
          </Tabs>
        </>
      )}
      {data && editing?.kind === 'unit' && <UnitDialog org={slug} structure={data} unit={editing.unit} parentId={editing.parentId} onSaved={(u) => setSelected(u.id)} onClose={() => setEditing(null)} />}
      {data && editing?.kind === 'position' && <PositionDialog org={slug} structure={data} position={editing.position} unitId={editing.unitId} onClose={() => setEditing(null)} />}
      {data && editing?.kind === 'place' && <PlacementDialog org={slug} structure={data} member={editing} onClose={() => setEditing(null)} />}
      {data && editing?.kind === 'add' && <AddPeopleDialog org={slug} structure={data} unitId={editing.unitId} positionId={editing.positionId} title={editing.title} onClose={() => setEditing(null)} />}
      {editing?.kind === 'confirm' && <ConfirmDialog title={editing.title} body={editing.body} confirm={editing.confirm} onConfirm={editing.run} onClose={() => setEditing(null)} />}
    </>
  );
}

export default function StructurePage() {
  return <Suspense><Structure /></Suspense>;
}
