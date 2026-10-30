'use client';

import { ArrowLeft, Download, FileUp, Network } from 'lucide-react';
import Link from 'next/link';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type ImportAction, type ImportResult, type ImportRow, TEMPLATE_HEADERS, TEMPLATE_ROWS, peopleImportApi, toImportRows } from '@/lib/api/people-import';
import { useOrgMutation } from '@/lib/api/queries';
import { csvLine, parseCsv } from '@/lib/csv';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const TONE: Record<ImportAction, string> = {
  CREATE: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  INVITE: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  UPDATE: 'bg-muted text-foreground',
  ERROR: 'bg-rose-50 text-rose-700 dark:bg-rose-950 dark:text-rose-300',
};

const HINTS: [string, MessageKey | null][] = [
  ['Name', 'imports.colHint_name'], ['Email', null], ['Role', 'imports.colHint_role'], ['Unit', 'imports.colHint_unit'],
  ['Unit types', 'imports.colHint_unitTypes'], ['Position', null], ['Manager email', 'imports.colHint_manager'],
  ['2nd approver email', 'imports.colHint_second'], ['Employment type', 'imports.colHint_employment'],
  ['Start date', 'imports.colHint_start'], ['Unit head', 'imports.colHint_head'],
];

function downloadTemplate() {
  const text = [TEMPLATE_HEADERS, ...TEMPLATE_ROWS].map(csvLine).join('\r\n');
  const url = URL.createObjectURL(new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' }));
  const a = Object.assign(document.createElement('a'), { href: url, download: 'people-import-template.csv' });
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Owners and Admins: many people at once from a spreadsheet (CSV), with their units, positions, managers and 2nd
 * approvers. The server checks every row first; nothing is imported until every row is right.
 */
export default function PeopleImport() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const file = useRef<HTMLInputElement>(null);
  const [rows, setRows] = useState<ImportRow[] | null>(null);
  const [fileName, setFileName] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [ignored, setIgnored] = useState<string[]>([]);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [checking, setChecking] = useState(false);
  const apply = useOrgMutation(slug, (r: ImportRow[]) => peopleImportApi.apply(slug, r));
  if (!can('org.members.manage')) return <PermissionDenied />;

  const choose = async (f: File) => {
    setFileName(f.name);
    setResult(null);
    setRows(null);
    setProblem(null);
    const parsed = toImportRows(parseCsv(await f.text()));
    if (!parsed) { setProblem(t('imports.noEmailColumn')); return; }
    if (parsed.rows.length === 0) { setProblem(t('imports.empty')); return; }
    if (parsed.rows.length > 2000) { setProblem(t('imports.tooMany')); return; }
    setIgnored(parsed.unknown);
    setRows(parsed.rows);
    setChecking(true);
    try { setResult(await peopleImportApi.preview(slug, parsed.rows)); } catch (e) { setProblem(errorMessage(e)); } finally { setChecking(false); }
  };

  const ready = !!rows && !!result && !result.applied && result.errors === 0;
  const count = result ? result.creates + result.invites + result.updates : 0;
  return (
    <>
      <Link href={`/${slug}/admin/members`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />{t('imports.viewMembers')}</Link>
      <PageHeader title={t('imports.title')} subtitle={t('imports.subtitle')} />

      <section aria-labelledby="imp-1" className="mb-6 space-y-3 rounded-lg border bg-card p-4">
        <h2 id="imp-1" className="text-base font-semibold">{t('imports.step1')}</h2>
        <p className="max-w-3xl text-sm text-muted-foreground">{t('imports.step1Body')}</p>
        <Button variant="outline" size="sm" onClick={downloadTemplate}><Download className="size-4" />{t('imports.template')}</Button>
        <details className="text-sm">
          <summary className="cursor-pointer text-muted-foreground">{t('imports.columns')}</summary>
          <dl className="mt-2 grid gap-1 sm:grid-cols-2">
            {HINTS.map(([c, hint]) => <div key={c}><dt className="inline font-medium">{c}</dt>{hint && <dd className="inline text-muted-foreground"> — {tr(hint)}</dd>}</div>)}
          </dl>
        </details>
      </section>

      <section aria-labelledby="imp-2" className="mb-6 space-y-3 rounded-lg border bg-card p-4">
        <h2 id="imp-2" className="text-base font-semibold">{t('imports.step2')}</h2>
        <input ref={file} type="file" accept=".csv,text/csv" className="sr-only" aria-label={t('imports.choose')}
          onChange={(e) => { const f = e.target.files?.[0]; if (f) void choose(f); e.target.value = ''; }} />
        <div className="flex flex-wrap items-center gap-3">
          <Button size="sm" onClick={() => file.current?.click()}><FileUp className="size-4" />{t('imports.choose')}</Button>
          {fileName && <span className="text-sm text-muted-foreground">{fileName}</span>}
          {checking && <span className="text-sm text-muted-foreground">{t('imports.checking')}</span>}
        </div>
        {problem && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{problem}</p>}
        {ignored.length > 0 && <p className="text-xs text-muted-foreground">{t('imports.ignored', { columns: ignored.join(', ') })}</p>}
      </section>

      {result && (
        <section aria-labelledby="imp-3" className="space-y-3">
          <h2 id="imp-3" className="text-base font-semibold">{t('imports.step3')}</h2>
          {result.applied ? (
            <div role="status" className="space-y-2 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm dark:border-emerald-900 dark:bg-emerald-950">
              <p className="font-medium">{t('imports.done', { creates: result.creates, invites: result.invites, updates: result.updates })}</p>
              <p className="text-muted-foreground">{t('imports.doneBody')}</p>
              <Button variant="outline" size="sm" asChild><Link href={`/${slug}/structure`}><Network className="size-4" />{t('imports.viewStructure')}</Link></Button>
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className={cn('rounded-full px-2 py-0.5', TONE.CREATE)}>{t('imports.creates', { count: result.creates })}</span>
              <span className={cn('rounded-full px-2 py-0.5', TONE.INVITE)}>{t('imports.invites', { count: result.invites })}</span>
              <span className={cn('rounded-full px-2 py-0.5', TONE.UPDATE)}>{t('imports.updates', { count: result.updates })}</span>
              {result.errors > 0 && <span className={cn('rounded-full px-2 py-0.5', TONE.ERROR)}>{t('imports.errors', { count: result.errors })}</span>}
              <Button size="sm" className="ml-auto" disabled={!ready || apply.isPending} onClick={async () => {
                try {
                  const r = await apply.mutateAsync(rows!);
                  setResult(r);
                  if (r.applied) toast.success(t('imports.done', { creates: r.creates, invites: r.invites, updates: r.updates }));
                } catch (e) { toast.error(errorMessage(e)); }
              }}>{apply.isPending ? t('imports.applying') : t('imports.apply', { count })}</Button>
            </div>
          )}
          {!result.applied && result.errors > 0 && <p className="text-sm text-destructive">{t('imports.fixFirst')}</p>}
          {!result.applied && result.newUnits.length > 0 && <p className="text-sm text-muted-foreground">{t('imports.newUnits', { list: result.newUnits.join(', ') })}</p>}
          {!result.applied && result.newPositions.length > 0 && <p className="text-sm text-muted-foreground">{t('imports.newPositions', { list: result.newPositions.join(', ') })}</p>}
          <div className="overflow-x-auto rounded-lg border bg-card">
            <Table>
              <TableHeader><TableRow>
                <TableHead className="w-14">{t('imports.colRow')}</TableHead><TableHead>{t('imports.colPerson')}</TableHead><TableHead>{t('imports.colResult')}</TableHead>
                <TableHead className="hidden md:table-cell">{t('imports.colPlace')}</TableHead><TableHead className="hidden lg:table-cell">{t('imports.colManager')}</TableHead>
              </TableRow></TableHeader>
              <TableBody>{result.rows.map((r) => (
                <TableRow key={r.row} className={r.action === 'ERROR' ? 'bg-rose-50/50 dark:bg-rose-950/30' : undefined}>
                  <TableCell className="tabular text-sm text-muted-foreground">{r.row + 1}</TableCell>
                  <TableCell><span className="block text-sm font-medium">{r.name ?? '—'}</span><span className="block text-xs text-muted-foreground">{r.email ?? '—'}</span></TableCell>
                  <TableCell>
                    <span className={cn('inline-flex rounded-full px-2 py-0.5 text-xs font-medium', TONE[r.action])}>{tr(`imports.action_${r.action}` as MessageKey)}</span>
                    {r.problems.map((p) => <span key={p} className="mt-1 block text-xs text-destructive">{p}</span>)}
                    {r.notes.map((n) => <span key={n} className="mt-1 block text-xs text-muted-foreground">{n}</span>)}
                  </TableCell>
                  <TableCell className="hidden text-sm md:table-cell">{[r.unit, r.position].filter(Boolean).join(' · ') || '—'}</TableCell>
                  <TableCell className="hidden text-sm lg:table-cell">{r.manager ?? '—'}</TableCell>
                </TableRow>
              ))}</TableBody>
            </Table>
          </div>
        </section>
      )}
    </>
  );
}
