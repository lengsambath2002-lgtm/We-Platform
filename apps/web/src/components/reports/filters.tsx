'use client';

import { differenceInCalendarDays, format, parseISO, subDays } from 'date-fns';

import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import type { ReportFilter } from '@/lib/api/reports';
import type { Project } from '@/lib/api/types';

export type Preset = '7' | '30' | '90' | 'custom';
export const STALE_OPTIONS = [3, 7, 14, 30];

/** What the URL says (`?range=30&from&to&project&stale`), as a filter for every report on the page. */
export interface FilterState { preset: Preset; from: string; to: string; project?: string; staleDays: number }

const iso = (d: Date) => format(d, 'yyyy-MM-dd');

export function readFilter(p: URLSearchParams): FilterState {
  const preset = (['7', '30', '90', 'custom'] as const).find((v) => v === p.get('range')) ?? '30';
  const today = new Date();
  const stale = Number(p.get('stale'));
  const base = { project: p.get('project') || undefined, staleDays: STALE_OPTIONS.includes(stale) ? stale : 7 };
  if (preset !== 'custom') return { preset, from: iso(subDays(today, Number(preset) - 1)), to: iso(today), ...base };
  return { preset, from: p.get('from') || iso(subDays(today, 29)), to: p.get('to') || iso(today), ...base };
}

export function writeFilter(f: FilterState) {
  const p = new URLSearchParams();
  if (f.preset !== '30') p.set('range', f.preset);
  if (f.preset === 'custom') { p.set('from', f.from); p.set('to', f.to); }
  if (f.project) p.set('project', f.project);
  if (f.staleDays !== 7) p.set('stale', String(f.staleDays));
  return p.toString();
}

/** A custom range the API will accept, or the message saying why not (the API checks the same). */
export function rangeProblem(f: FilterState): 'reports.rangeInvalid' | 'reports.rangeTooLong' | null {
  const from = parseISO(f.from), to = parseISO(f.to);
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime()) || from > to) return 'reports.rangeInvalid';
  return differenceInCalendarDays(to, from) + 1 > 366 ? 'reports.rangeTooLong' : null;
}

export const toReportFilter = (f: FilterState): ReportFilter => ({ from: f.from, to: f.to, project: f.project, staleDays: f.staleDays });

/** One row above every report: date range first, then project, then the approval waiting threshold. */
export function ReportFilters({ value, onChange, projects }: { value: FilterState; onChange: (f: FilterState) => void; projects?: Project[] }) {
  const { t } = useT();
  const problem = value.preset === 'custom' ? rangeProblem(value) : null;
  return (
    <div role="group" aria-label={t('reports.filters')} className="mb-6 flex flex-wrap items-end gap-3">
      <div className="grid gap-1.5">
        <Label htmlFor="report-range" className="text-xs text-muted-foreground">{t('reports.range')}</Label>
        <Select value={value.preset} onValueChange={(v) => onChange({ ...value, preset: v as Preset, ...(v === 'custom' ? {} : { from: iso(subDays(new Date(), Number(v) - 1)), to: iso(new Date()) }) })}>
          <SelectTrigger id="report-range" className="w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="7">{t('reports.last7')}</SelectItem>
            <SelectItem value="30">{t('reports.last30')}</SelectItem>
            <SelectItem value="90">{t('reports.last90')}</SelectItem>
            <SelectItem value="custom">{t('reports.custom')}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {value.preset === 'custom' && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid gap-1.5">
            <Label htmlFor="report-from" className="text-xs text-muted-foreground">{t('reports.from')}</Label>
            <Input id="report-from" type="date" className="w-40" value={value.from} max={value.to}
              aria-invalid={!!problem || undefined} aria-describedby={problem ? 'report-range-problem' : undefined}
              onChange={(e) => e.target.value && onChange({ ...value, from: e.target.value })} />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="report-to" className="text-xs text-muted-foreground">{t('reports.to')}</Label>
            <Input id="report-to" type="date" className="w-40" value={value.to} min={value.from}
              aria-invalid={!!problem || undefined} aria-describedby={problem ? 'report-range-problem' : undefined}
              onChange={(e) => e.target.value && onChange({ ...value, to: e.target.value })} />
          </div>
        </div>
      )}
      {projects && (
        <div className="grid gap-1.5">
          <Label htmlFor="report-project" className="text-xs text-muted-foreground">{t('reports.project')}</Label>
          <Select value={value.project ?? '*'} onValueChange={(v) => onChange({ ...value, project: v === '*' ? undefined : v })}>
            <SelectTrigger id="report-project" className="w-60 max-w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="*">{t('reports.allProjects')}</SelectItem>
              {projects.map((p) => <SelectItem key={p.key} value={p.key}>{p.name}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      )}
      <div className="grid gap-1.5">
        <Label htmlFor="report-stale" className="text-xs text-muted-foreground">{t('reports.staleDays')}</Label>
        <Select value={String(value.staleDays)} onValueChange={(v) => onChange({ ...value, staleDays: Number(v) })}>
          <SelectTrigger id="report-stale" className="w-48"><SelectValue /></SelectTrigger>
          <SelectContent>
            {STALE_OPTIONS.map((d) => <SelectItem key={d} value={String(d)}>{t('reports.staleOption', { days: d })}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      {problem && <p id="report-range-problem" role="alert" className="basis-full text-sm text-destructive">{t(problem)}</p>}
    </div>
  );
}
