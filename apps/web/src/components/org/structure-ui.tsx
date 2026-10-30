'use client';

import { Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

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

const TONE: Record<UnitKind, string> = {
  BRANCH: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  DIVISION: 'bg-violet-50 text-violet-700 dark:bg-violet-950 dark:text-violet-300',
  DEPARTMENT: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  CENTER: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  SECTION: 'bg-rose-50 text-rose-700 dark:bg-rose-950 dark:text-rose-300',
  TEAM: 'bg-muted text-foreground',
};

export function KindBadge({ kind }: { kind: UnitKind }) {
  return <span className={cn('inline-flex shrink-0 items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', TONE[kind])}>{KIND_LABEL[kind]}</span>;
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
          <div className="grid gap-3 sm:grid-cols-[1fr_10rem]">
            <div className="space-y-1.5"><Label htmlFor="unit-address">{t('structure.address')}</Label><Input id="unit-address" maxLength={300} value={v.address} onChange={(e) => setV({ ...v, address: e.target.value })} /></div>
            <div className="space-y-1.5"><Label htmlFor="unit-phone">{t('structure.phone')}</Label><Input id="unit-phone" type="tel" maxLength={30} value={v.phone} onChange={(e) => setV({ ...v, phone: e.target.value })} /></div>
          </div>
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
