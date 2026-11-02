'use client';

import { Boxes, Building2, Check, Layers, LayoutGrid, type LucideIcon, MapPin, Plus, Search, Trash2, UsersRound } from 'lucide-react';
import Link from 'next/link';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import {
  AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { labels, tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useMembers, useOrgMutation } from '@/lib/api/queries';
import { type Placement, type Position, type Structure, type Unit, UNIT_KINDS, type UnitKind, structureApi, unitPath, unitTree } from '@/lib/api/structure';
import { cn } from '@/lib/utils';

const NONE = '__none__';
export const KIND_LABEL = labels(UNIT_KINDS, (k) => `structure.kind_${k}`);
const alert = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;

/** Each unit type's icon: types are told apart by icon and word, not colour (colour is for status). */
export const KIND_ICON: Record<UnitKind, LucideIcon> = {
  BRANCH: MapPin, DIVISION: Layers, DEPARTMENT: Building2, CENTER: Boxes, SECTION: LayoutGrid, TEAM: UsersRound,
};

export function KindIcon({ kind, className }: { kind: UnitKind; className?: string }) {
  const Icon = KIND_ICON[kind];
  return <Icon className={cn('size-4 shrink-0 text-muted-foreground', className)} aria-hidden />;
}

/** The unit type as a quiet label with its icon. */
export function KindBadge({ kind }: { kind: UnitKind }) {
  return (
    <span className="inline-flex shrink-0 items-center gap-1 whitespace-nowrap rounded-md border bg-background px-1.5 py-0.5 text-xs font-medium text-muted-foreground">
      <KindIcon kind={kind} className="size-3" />{KIND_LABEL[kind]}
    </span>
  );
}

/** Active units as select options, indented by depth ("— — Team A"). */
function UnitOptions({ units, exclude }: { units: Unit[]; exclude?: Set<string> }) {
  return <>{unitTree(units).filter(({ unit }) => !exclude?.has(unit.id)).map(({ unit, depth }) => (
    <SelectItem key={unit.id} value={unit.id}>{'— '.repeat(depth)}{unit.name} <span className="text-muted-foreground">· {KIND_LABEL[unit.kind]}</span></SelectItem>
  ))}</>;
}

/** The unit and everything below it, which can't become its parent. */
function subtree(units: Unit[], id: string): Set<string> {
  const out = new Set([id]);
  let grew = true;
  while (grew) {
    grew = false;
    units.forEach((u) => { if (u.parentId && out.has(u.parentId) && !out.has(u.id)) { out.add(u.id); grew = true; } });
  }
  return out;
}

/** Owners and Admins: adds a unit (optionally under {@code parentId}) or edits one. */
export function UnitDialog({ org, structure, unit, parentId, onClose, onSaved }: {
  org: string; structure: Structure; unit: Unit | null; parentId?: string | null; onClose: () => void; onSaved?: (u: Unit) => void;
}) {
  const { t } = useT();
  const members = useMembers(org);
  const [v, setV] = useState({
    name: unit?.name ?? '', kind: (unit?.kind ?? (parentId ? 'DEPARTMENT' : 'BRANCH')) as UnitKind, parentId: unit?.parentId ?? parentId ?? NONE,
    code: unit?.code ?? '', headUserId: unit?.head?.userId ?? NONE, address: unit?.address ?? '', phone: unit?.phone ?? '',
  });
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => structureApi.saveUnit(org, unit?.id ?? null, body));
  const exclude = unit ? subtree(structure.units, unit.id) : undefined;
  const people = (members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST');
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{unit ? t('structure.editUnit') : t('structure.newUnit')}</DialogTitle><DialogDescription>{t('structure.unitBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-[1fr_9rem]">
            <div className="space-y-1.5"><Label htmlFor="unit-name">{t('structure.name')}</Label><Input id="unit-name" autoFocus maxLength={120} value={v.name} onChange={(e) => setV({ ...v, name: e.target.value })} /></div>
            <div className="space-y-1.5"><Label htmlFor="unit-code">{t('structure.code')}</Label><Input id="unit-code" maxLength={20} placeholder="PP-SAL" value={v.code} onChange={(e) => setV({ ...v, code: e.target.value })} /></div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="unit-kind">{t('structure.kind')}</Label>
              <Select value={v.kind} onValueChange={(k) => setV({ ...v, kind: k as UnitKind })}>
                <SelectTrigger id="unit-kind" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{UNIT_KINDS.map((k) => <SelectItem key={k} value={k}>{KIND_LABEL[k]}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="unit-parent">{t('structure.partOf')}</Label>
              <Select value={v.parentId} onValueChange={(p) => setV({ ...v, parentId: p })}>
                <SelectTrigger id="unit-parent" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value={NONE}>{t('structure.topLevel')}</SelectItem><UnitOptions units={structure.units} exclude={exclude} /></SelectContent>
              </Select>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="unit-head">{t('structure.head')}</Label>
            <Select value={v.headUserId} onValueChange={(h) => setV({ ...v, headUserId: h })}>
              <SelectTrigger id="unit-head" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent><SelectItem value={NONE}>{t('structure.noHead')}</SelectItem>{people.map((m) => <SelectItem key={m.userId} value={m.userId}>{m.name}</SelectItem>)}</SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">{t('structure.headHint')}</p>
          </div>
          {(v.kind === 'BRANCH' || v.address || v.phone) && <div className="grid gap-3 sm:grid-cols-[1fr_10rem]">
            <div className="space-y-1.5"><Label htmlFor="unit-address">{t('structure.address')}</Label><Input id="unit-address" maxLength={300} value={v.address} onChange={(e) => setV({ ...v, address: e.target.value })} /></div>
            <div className="space-y-1.5"><Label htmlFor="unit-phone">{t('structure.phone')}</Label><Input id="unit-phone" type="tel" maxLength={30} value={v.phone} onChange={(e) => setV({ ...v, phone: e.target.value })} /></div>
          </div>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!v.name.trim()) { setErr(t('structure.errName')); return; }
            if (v.code && !/^[A-Za-z0-9][A-Za-z0-9._-]{0,19}$/.test(v.code.trim())) { setErr(t('structure.errCode')); return; }
            setErr(null);
            try {
              const saved = await save.mutateAsync({
                name: v.name.trim(), kind: v.kind, parentId: v.parentId === NONE ? null : v.parentId, code: v.code.trim() || null,
                headUserId: v.headUserId === NONE ? null : v.headUserId, address: v.address.trim() || null, phone: v.phone.trim() || null,
              });
              toast.success(t('structure.unitSaved'));
              onSaved?.(saved);
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Owners and Admins: adds a position (optionally in {@code unitId}) or edits one. */
export function PositionDialog({ org, structure, position, unitId, onClose }: {
  org: string; structure: Structure; position: Position | null; unitId?: string | null; onClose: () => void;
}) {
  const { t } = useT();
  const [v, setV] = useState({
    title: position?.title ?? '', unitId: position?.unitId ?? unitId ?? NONE, code: position?.code ?? '', grade: position?.grade ?? '',
    reportsToId: position?.reportsToId ?? NONE, headcount: String(position?.headcount ?? 1), description: position?.description ?? '',
  });
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => structureApi.savePosition(org, position?.id ?? null, body));
  const others = structure.positions.filter((p) => !p.archived && p.id !== position?.id)
    .map((p) => ({ p, label: p.unitId ? `${p.title} · ${unitPath(structure.units, p.unitId)}` : p.title }))
    .sort((a, b) => a.label.localeCompare(b.label));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{position ? t('structure.editPosition') : t('structure.newPosition')}</DialogTitle><DialogDescription>{t('structure.positionBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          <div className="space-y-1.5"><Label htmlFor="pos-title">{t('structure.positionTitle')}</Label><Input id="pos-title" autoFocus maxLength={80} value={v.title} onChange={(e) => setV({ ...v, title: e.target.value })} /></div>
          <div className="space-y-1.5">
            <Label htmlFor="pos-unit">{t('structure.unit')}</Label>
            <Select value={v.unitId} onValueChange={(u) => setV({ ...v, unitId: u })}>
              <SelectTrigger id="pos-unit" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent><SelectItem value={NONE}>{t('structure.anyUnit')}</SelectItem><UnitOptions units={structure.units} /></SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="pos-reports">{t('structure.reportsTo')}</Label>
            <Select value={v.reportsToId} onValueChange={(r) => setV({ ...v, reportsToId: r })}>
              <SelectTrigger id="pos-reports" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent><SelectItem value={NONE}>{t('structure.reportsToNobody')}</SelectItem>{others.map(({ p, label }) => <SelectItem key={p.id} value={p.id}>{label}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5"><Label htmlFor="pos-headcount">{t('structure.headcount')}</Label><Input id="pos-headcount" type="number" min={0} max={10000} value={v.headcount} onChange={(e) => setV({ ...v, headcount: e.target.value })} /></div>
            <div className="space-y-1.5"><Label htmlFor="pos-grade">{t('structure.grade')}</Label><Input id="pos-grade" maxLength={20} placeholder="G5" value={v.grade} onChange={(e) => setV({ ...v, grade: e.target.value })} /></div>
            <div className="space-y-1.5"><Label htmlFor="pos-code">{t('structure.code')}</Label><Input id="pos-code" maxLength={20} value={v.code} onChange={(e) => setV({ ...v, code: e.target.value })} /></div>
          </div>
          <div className="space-y-1.5"><Label htmlFor="pos-desc">{t('structure.description')}</Label><Textarea id="pos-desc" rows={3} maxLength={2000} value={v.description} onChange={(e) => setV({ ...v, description: e.target.value })} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!v.title.trim()) { setErr(t('structure.errTitle')); return; }
            if (!/^\d{1,5}$/.test(v.headcount) || Number(v.headcount) > 10000) { setErr(t('structure.errHeadcount')); return; }
            if (v.code && !/^[A-Za-z0-9][A-Za-z0-9._-]{0,19}$/.test(v.code.trim())) { setErr(t('structure.errCode')); return; }
            setErr(null);
            try {
              await save.mutateAsync({
                title: v.title.trim(), unitId: v.unitId === NONE ? null : v.unitId, code: v.code.trim() || null, grade: v.grade.trim() || null,
                reportsToId: v.reportsToId === NONE ? null : v.reportsToId, headcount: Number(v.headcount), description: v.description.trim() || null,
              });
              toast.success(t('structure.positionSaved'));
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type Row = { unitId: string; positionId: string };

/** Unit and position pickers for one place; a position in a unit sets that unit. */
function PlaceFields({ structure, row, onChange, id, keep }: { structure: Structure; row: Row; onChange: (r: Row) => void; id: string; keep: Set<string> }) {
  const { t } = useT();
  const units = structure.units.filter((u) => !u.archived || keep.has(u.id));
  const positions = structure.positions.filter((p) => (!p.archived || keep.has(p.id)) && (row.unitId === NONE || !p.unitId || p.unitId === row.unitId))
    .sort((a, b) => a.title.localeCompare(b.title));
  return (
    <div className="grid flex-1 gap-2 sm:grid-cols-2">
      <Select value={row.unitId} onValueChange={(u) => {
        const pos = structure.positions.find((p) => p.id === row.positionId);
        onChange({ unitId: u, positionId: pos && pos.unitId && pos.unitId !== u ? NONE : row.positionId });
      }}>
        <SelectTrigger id={`${id}-unit`} aria-label={t('structure.unit')} className="w-full"><SelectValue /></SelectTrigger>
        <SelectContent><SelectItem value={NONE}>{t('structure.noUnit')}</SelectItem><UnitOptions units={units} /></SelectContent>
      </Select>
      <Select value={row.positionId} onValueChange={(p) => {
        const pos = structure.positions.find((x) => x.id === p);
        onChange({ positionId: p, unitId: pos?.unitId ?? row.unitId });
      }}>
        <SelectTrigger id={`${id}-position`} aria-label={t('structure.position')} className="w-full"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value={NONE}>{t('structure.noPosition')}</SelectItem>
          {positions.map((p) => <SelectItem key={p.id} value={p.id}>{p.title}{p.headcount - p.filled > 0 ? ` (${t('structure.openCount', { count: p.headcount - p.filled })})` : ''}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  );
}

const toPlacement = (r: Row): Placement | null => (r.unitId === NONE && r.positionId === NONE ? null
  : { unitId: r.unitId === NONE ? null : r.unitId, positionId: r.positionId === NONE ? null : r.positionId });

/** Owners and Admins: where a member sits: their primary unit and position, and any additional ones. */
export function PlacementDialog({ org, structure, member, onClose }: {
  org: string; structure: Structure; member: { membershipId: string; name: string }; onClose: () => void;
}) {
  const { t } = useT();
  const mine = structure.assignments.filter((a) => a.person.membershipId === member.membershipId);
  const asRow = (a?: { unitId: string | null; positionId: string | null }): Row => ({ unitId: a?.unitId ?? NONE, positionId: a?.positionId ?? NONE });
  const [primary, setPrimary] = useState<Row>(asRow(mine.find((a) => a.primary)));
  const [extra, setExtra] = useState<Row[]>(mine.filter((a) => !a.primary).map(asRow));
  const keep = new Set(mine.flatMap((a) => [a.unitId, a.positionId]).filter((x): x is string => !!x));
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: { primary: Placement | null; additional: Placement[] }) => structureApi.place(org, member.membershipId, body));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
        <DialogHeader><DialogTitle>{t('structure.placeTitle', { name: member.name })}</DialogTitle><DialogDescription>{t('structure.placeBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <fieldset className="space-y-2">
          <legend className="mb-1 text-sm font-medium">{t('structure.primaryPlace')}</legend>
          <PlaceFields id="place-primary" structure={structure} row={primary} onChange={setPrimary} keep={keep} />
        </fieldset>
        <fieldset className="space-y-2">
          <legend className="mb-1 text-sm font-medium">{t('structure.additionalPlaces')}</legend>
          {extra.length === 0 && <p className="text-sm text-muted-foreground">{t('structure.noAdditional')}</p>}
          {extra.map((r, i) => (
            <div key={i} className="flex items-start gap-2">
              <PlaceFields id={`place-${i}`} structure={structure} row={r} keep={keep} onChange={(n) => setExtra(extra.map((x, j) => (j === i ? n : x)))} />
              <Button variant="ghost" size="icon" aria-label={t('structure.removePlace')} onClick={() => setExtra(extra.filter((_, j) => j !== i))}><Trash2 className="size-4" /></Button>
            </div>
          ))}
          {extra.length < 10 && <Button variant="outline" size="sm" onClick={() => setExtra([...extra, { unitId: NONE, positionId: NONE }])}><Plus className="size-4" />{t('structure.addPlace')}</Button>}
        </fieldset>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            const additional = extra.map(toPlacement).filter((p): p is Placement => !!p);
            setErr(null);
            try {
              await save.mutateAsync({ primary: toPlacement(primary), additional });
              toast.success(t('structure.placed', { name: member.name }));
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** "Sales Officer · Sales › Team A", for lists. */
export function placeText(structure: Structure, a: { unitId: string | null; positionId: string | null }): string {
  const pos = structure.positions.find((p) => p.id === a.positionId);
  return [pos?.title, a.unitId ? unitPath(structure.units, a.unitId) : null].filter(Boolean).join(' · ') || tr('structure.notPlaced');
}

/** A confirmation for a destructive action, in place of the browser's confirm(). */
export function ConfirmDialog({ title, body, confirm, onConfirm, onClose }: {
  title: string; body: string; confirm: string; onConfirm: () => Promise<unknown>; onClose: () => void;
}) {
  const { t } = useT();
  const [busy, setBusy] = useState(false);
  return (
    <AlertDialog open onOpenChange={(o) => !o && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader><AlertDialogTitle>{title}</AlertDialogTitle><AlertDialogDescription>{body}</AlertDialogDescription></AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
          <Button variant="destructive" disabled={busy} onClick={async () => {
            setBusy(true);
            try { await onConfirm(); onClose(); } finally { setBusy(false); }
          }}>{confirm}</Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

/** A member's places as the API takes them (primary and additional), from the structure. */
export function placesOf(structure: Structure, membershipId: string) {
  const mine = structure.assignments.filter((a) => a.person.membershipId === membershipId);
  const p = mine.find((a) => a.primary);
  return {
    primary: p ? { unitId: p.unitId, positionId: p.positionId } as Placement : null,
    additional: mine.filter((a) => !a.primary).map((a) => ({ unitId: a.unitId, positionId: a.positionId }) as Placement),
  };
}

const samePlace = (a: Placement, b: Placement) => a.unitId === b.unitId && a.positionId === b.positionId;

/**
 * Owners and Admins: places several members in one unit (and optionally one position) at once. "Main place" moves
 * each person here; "Also works here" keeps their main place and adds this one (someone with no place yet gets it
 * as their main place either way).
 */
export function AddPeopleDialog({ org, structure, unitId, positionId, title, onClose }: {
  org: string; structure: Structure; unitId: string | null; positionId?: string | null; title: string; onClose: () => void;
}) {
  const { t } = useT();
  const qc = useQueryClient();
  const members = useMembers(org);
  const [q, setQ] = useState('');
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [position, setPosition] = useState(positionId ?? NONE);
  const [mode, setMode] = useState<'main' | 'also'>('main');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const target = { unitId, positionId: position === NONE ? null : position };
  const here = (m: string) => structure.assignments.some((a) => a.person.membershipId === m && a.unitId === unitId && (!target.positionId || a.positionId === target.positionId));
  const people = (members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST');
  const needle = q.trim().toLowerCase();
  const shown = people.filter((m) => !needle || m.name.toLowerCase().includes(needle) || m.email.toLowerCase().includes(needle))
    .sort((a, b) => Number(here(a.membershipId)) - Number(here(b.membershipId)) || a.name.localeCompare(b.name));
  const positions = structure.positions.filter((p) => !p.archived && (unitId ? p.unitId === unitId || !p.unitId : !p.unitId))
    .sort((a, b) => a.title.localeCompare(b.title));
  const toggle = (id: string) => { const n = new Set(picked); if (n.has(id)) n.delete(id); else n.add(id); setPicked(n); };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="flex max-h-[90dvh] flex-col gap-4 sm:max-w-xl">
        <DialogHeader><DialogTitle>{title}</DialogTitle><DialogDescription>{t('structure.addPeopleBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input aria-label={t('structure.searchMembers')} placeholder={t('structure.searchMembers')} className="pl-9" value={q} onChange={(e) => setQ(e.target.value)} autoFocus />
        </div>
        <ul className="min-h-40 flex-1 divide-y overflow-y-auto rounded-lg border" aria-label={t('structure.people')}>
          {members.isPending ? <li className="p-4 text-sm text-muted-foreground">…</li> : shown.length === 0 ? (
            <li className="p-4 text-sm text-muted-foreground">{t('structure.noMembersMatch')}</li>
          ) : shown.map((m) => {
            const already = here(m.membershipId);
            const on = picked.has(m.membershipId);
            const now = structure.assignments.find((a) => a.person.membershipId === m.membershipId && a.primary);
            return (
              <li key={m.membershipId}>
                <button type="button" role="checkbox" aria-checked={on} disabled={already} onClick={() => toggle(m.membershipId)}
                  className={cn('flex w-full items-center gap-3 px-3 py-2.5 text-left transition-colors hover:bg-muted/50 disabled:cursor-default disabled:opacity-60 disabled:hover:bg-transparent', on && 'bg-accent/50')}>
                  <span className={cn('grid size-4 shrink-0 place-items-center rounded border', on ? 'border-primary bg-primary text-primary-foreground' : 'border-input')}>{on && <Check className="size-3" />}</span>
                  <PersonAvatar person={m} className="size-8" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{m.name}</span>
                    <span className="block truncate text-xs text-muted-foreground">{now ? t('structure.nowIn', { place: placeText(structure, now) }) : m.email}</span>
                  </span>
                  {already && <span className="shrink-0 text-xs text-muted-foreground">{t('structure.alreadyHere')}</span>}
                </button>
              </li>
            );
          })}
        </ul>
        <p className="-mt-2 text-xs text-muted-foreground">{t('structure.notListed')} <Link href={`/${org}/admin/members`} className="font-medium text-primary hover:underline">{t('structure.inviteMembers')}</Link></p>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="add-position">{t('structure.asPosition')}</Label>
            <Select value={position} onValueChange={setPosition}>
              <SelectTrigger id="add-position" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>{t('structure.noPosition')}</SelectItem>
                {positions.map((p) => <SelectItem key={p.id} value={p.id}>{p.title}{p.headcount - p.filled > 0 ? ` (${t('structure.openCount', { count: p.headcount - p.filled })})` : ''}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <fieldset className="space-y-1.5">
            <legend className="mb-1.5 text-sm font-medium">{t('structure.placeAs')}</legend>
            <div role="radiogroup" className="grid grid-cols-2 gap-1 rounded-lg border bg-muted/50 p-0.5">
              {(['main', 'also'] as const).map((k) => (
                <button key={k} type="button" role="radio" aria-checked={mode === k} onClick={() => setMode(k)}
                  className={cn('rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground', mode === k && 'bg-card text-foreground shadow-xs')}>
                  {t(k === 'main' ? 'structure.placeMain' : 'structure.placeAlso')}
                </button>
              ))}
            </div>
            <p className="text-xs text-muted-foreground">{t(mode === 'main' ? 'structure.placeMainHint' : 'structure.placeAlsoHint')}</p>
          </fieldset>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={busy || picked.size === 0} onClick={async () => {
            setBusy(true);
            setErr(null);
            try {
              for (const id of picked) {
                const cur = placesOf(structure, id);
                const body = mode === 'main' || !cur.primary
                  ? { primary: target, additional: cur.additional.filter((p) => !samePlace(p, target)) }
                  : { primary: cur.primary, additional: [...cur.additional.filter((p) => !samePlace(p, target)), target].slice(0, 10) };
                await structureApi.place(org, id, body);
              }
              await qc.invalidateQueries({ queryKey: ['org', org] });
              const unitName = unitId ? structure.units.find((u) => u.id === unitId)?.name ?? '' : t('structure.orgWide');
              toast.success(t('structure.addedN', { count: picked.size, unit: unitName }));
              onClose();
            } catch (e) {
              await qc.invalidateQueries({ queryKey: ['org', org] });
              setErr(errorMessage(e));
            } finally { setBusy(false); }
          }}>{picked.size > 0 ? t('structure.addN', { count: picked.size }) : t('structure.addPeople')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Takes a member out of one unit (their place there, main or additional), keeping their other places. */
export async function removeFromUnit(org: string, structure: Structure, membershipId: string, unitId: string) {
  const cur = placesOf(structure, membershipId);
  return structureApi.place(org, membershipId, {
    primary: cur.primary && cur.primary.unitId === unitId ? null : cur.primary,
    additional: cur.additional.filter((p) => p.unitId !== unitId),
  });
}
