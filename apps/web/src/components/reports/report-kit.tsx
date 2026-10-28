'use client';

import { Download } from 'lucide-react';
import { type ReactNode, useEffect, useMemo, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCaption, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { cn } from '@/lib/utils';

import './reports.css';

/** A series on a chart: its data key, label and colour token (see reports.css). */
export interface Series { key: string; label: string; color: string }

/** Numbers in the current language's style. */
export function useNumber() {
  const { locale } = useT();
  return useMemo(() => {
    const f = new Intl.NumberFormat(locale === 'km' ? 'km-KH' : 'en-US', { maximumFractionDigits: 1 });
    return (n: number) => f.format(n);
  }, [locale]);
}

/** "under 1 h", "5.5 h", "2.3 d": hours to a decision, readable at a glance. */
export function useDuration() {
  const { t } = useT();
  const num = useNumber();
  return (hours: number | null) => hours == null ? '—'
    : hours < 1 ? t('reports.underHour')
      : hours < 48 ? t('reports.hours', { n: num(Math.round(hours * 10) / 10) })
        : t('reports.days', { n: num(Math.round(hours / 2.4) / 10) });
}

/** True at the `sm` breakpoint and up (charts give long labels more room there). */
export function useWide() {
  const [wide, setWide] = useState(true);
  useEffect(() => {
    const m = window.matchMedia('(min-width: 640px)');
    const on = () => setWide(m.matches);
    on();
    m.addEventListener('change', on);
    return () => m.removeEventListener('change', on);
  }, []);
  return wide;
}

/** Shortens a long axis label; the full text is in the tooltip and the table. */
export const clip = (s: string, max: number) => (s.length > max ? `${s.slice(0, max - 1)}…` : s);

/**
 * Builds and downloads a CSV (UTF-8 with BOM so spreadsheet apps read Khmer). Numbers stay plain; text cells
 * that start like a formula are prefixed with ' so a spreadsheet won't run them.
 */
export function downloadCsv(filename: string, headers: string[], rows: (string | number | null)[][]) {
  const cell = (v: string | number | null) => {
    if (v == null) return '';
    let s = String(v);
    if (typeof v === 'string' && /^[=+\-@\t\r]/.test(s)) s = `'${s}`;
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const text = [headers, ...rows].map((r) => r.map(cell).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['﻿', text], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/**
 * One report: the business question as its title, a CSV download, then the chart, its table and notes.
 * While new numbers load, the previous ones stay on screen, dimmed.
 */
export function ReportCard({ id, title, subtitle, onCsv, refreshing, children }: {
  id: string; title: string; subtitle?: ReactNode; onCsv?: () => void; refreshing?: boolean; children: ReactNode;
}) {
  const { t } = useT();
  return (
    <Card className="report-viz min-w-0 gap-0 py-0" aria-labelledby={`${id}-title`} role="region">
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 border-b px-4 py-3">
        <div className="min-w-0 flex-1">
          <h2 id={`${id}-title`} className="text-base font-semibold">{title}</h2>
          {subtitle && <p className="mt-0.5 text-sm text-muted-foreground">{subtitle}</p>}
        </div>
        {onCsv && (
          <Button variant="outline" size="sm" onClick={onCsv} aria-label={t('reports.downloadCsvOf', { name: title })}>
            <Download className="size-4" aria-hidden />{t('reports.downloadCsv')}
          </Button>
        )}
      </div>
      <div className={cn('space-y-4 p-4 transition-opacity', refreshing && 'opacity-60')} aria-busy={refreshing || undefined}>{children}</div>
    </Card>
  );
}

export function ReportSkeleton() {
  const { t } = useT();
  return <div className="space-y-3" aria-busy="true" aria-label={t('common.loading')}><Skeleton className="h-56 w-full" /><Skeleton className="h-24 w-full" /></div>;
}

/** The chart, labelled for screen readers as a picture of the table below it. */
export function ChartFigure({ title, children }: { title: string; children: ReactNode }) {
  const { t } = useT();
  return <figure className="m-0 min-w-0" aria-label={t('reports.chartLabel', { title })}>{children}</figure>;
}

/** Legend for two or more series: a swatch that mirrors the mark (rect for bars, line for lines), then the label. */
export function Legend({ series, mark = 'rect', totals }: { series: Series[]; mark?: 'rect' | 'line'; totals?: Record<string, number> }) {
  const num = useNumber();
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
      {series.map((s) => (
        <li key={s.key} className="flex items-center gap-1.5">
          <span aria-hidden className={mark === 'line' ? 'h-0.5 w-3.5 rounded-full' : 'size-2.5 rounded-sm'} style={{ background: `var(${s.color})` }} />
          {s.label}{totals && <span className="font-medium text-foreground tabular">{num(totals[s.key] ?? 0)}</span>}
        </li>
      ))}
    </ul>
  );
}

/** Tooltip: the row's name, then each series' value (value strong, label secondary, keyed by a short line). */
export function ChartTooltip({ active, payload, label, series, title }: {
  active?: boolean; payload?: readonly { payload?: unknown }[]; label?: unknown;
  series: Series[]; title?: (row: Record<string, unknown>) => string;
}) {
  const num = useNumber();
  if (!active || !payload?.length) return null;
  const row = payload[0].payload as Record<string, unknown>;
  return (
    <div className="min-w-36 rounded-md border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      <p className="mb-1 font-medium">{title ? title(row) : String(label)}</p>
      <ul className="space-y-0.5">
        {series.map((s) => (
          <li key={s.key} className="flex items-center gap-2">
            <span aria-hidden className="h-0.5 w-3 rounded-full" style={{ background: `var(${s.color})` }} />
            <span className="text-muted-foreground">{s.label}</span>
            <span className="ml-auto pl-3 font-semibold tabular">{num(Number(row[s.key] ?? 0))}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export interface Column<R> { label: string; value: (r: R) => ReactNode; numeric?: boolean; total?: ReactNode }

/** The chart's numbers as a table: readable without the chart, and what the CSV contains. */
export function ReportTable<R>({ caption, columns, rows, rowKey }: {
  caption: string; columns: Column<R>[]; rows: R[]; rowKey: (r: R) => string;
}) {
  const { t } = useT();
  const hasTotal = columns.some((c) => c.total !== undefined);
  return (
    <div className="rounded-md border">
      <Table>
        <TableCaption className="sr-only">{t('reports.tableCaption', { title: caption })}</TableCaption>
        <TableHeader>
          <TableRow>
            {columns.map((c) => <TableHead key={c.label} className={cn('h-9 text-xs', c.numeric && 'text-right')}>{c.label}</TableHead>)}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((r) => (
            <TableRow key={rowKey(r)}>
              {columns.map((c, i) => (
                <TableCell key={c.label} className={cn('py-1.5 text-[13px]', c.numeric && 'text-right tabular', i === 0 && 'max-w-56 truncate')}>{c.value(r)}</TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
        {hasTotal && (
          <TableFooter>
            <TableRow>
              {columns.map((c, i) => (
                <TableCell key={c.label} className={cn('py-1.5 text-[13px] font-medium', c.numeric && 'text-right tabular')}>{i === 0 ? t('reports.total') : c.total}</TableCell>
              ))}
            </TableRow>
          </TableFooter>
        )}
      </Table>
    </div>
  );
}

/** Shared chart chrome: recessive hairline grid and axis text in the muted ink. */
export const axisTick = { fill: 'var(--muted-foreground)', fontSize: 12 };
export const gridStroke = 'var(--rv-grid)';
