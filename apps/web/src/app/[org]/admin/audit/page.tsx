'use client';

import { parseISO } from 'date-fns';
import { ChevronLeft, ChevronRight, ScrollText, SearchX } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { moduleName } from '@/components/shell/nav';
import { describe } from '@/components/tasks/activity';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { type AuditEntry, type AuditFilter, useAudit } from '@/lib/api/collab';
import { useMembers } from '@/lib/api/queries';
import { ago, fmt } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

const SIZE = 50;

/** The "Record" filter, as API filters. */
const RECORDS: Record<string, { label: MessageKey; filter: AuditFilter }> = {
  tasks: { label: 'collab.recTasks', filter: { recordType: 'task', verb: 'task.' } },
  approvals: { label: 'collab.recApprovals', filter: { verb: 'approval.' } },
  projects: { label: 'collab.recProjects', filter: { recordType: 'project' } },
  events: { label: 'collab.recEvents', filter: { module: 'calendar' } },
  hr: { label: 'hr.auditRecords', filter: { module: 'hr' } },
  retail: { label: 'retail.auditRecords', filter: { module: 'retail' } },
  org: { label: 'collab.recOrg', filter: { module: 'org' } },
};

const s = (v: unknown) => (typeof v === 'string' ? v : '');

/** The record's current name and link, or what the entry itself says about a record that is gone. */
function RecordCell({ e }: { e: AuditEntry }) {
  const { t } = useT();
  if (e.record?.link) return <Link href={e.record.link} className="break-words text-primary hover:underline">{e.record.title}</Link>;
  if (e.record) return <span className="break-words">{e.record.title}</span>;
  const d = e.data;
  const said = [s(d.key), s(d.title) || s(d.name) || s(d.email)].filter(Boolean).join(' ');
  if (e.module === 'org') return <span className="text-muted-foreground">{said || t('collab.recOrg')}</span>;
  return <span className="text-muted-foreground">{said} <span className="text-xs">({t('collab.recordGone')})</span></span>;
}

export default function AuditLog() {
  const { slug, can, org, modules } = useOrg();
  const { t } = useT();
  const members = useMembers(slug);
  const [actorId, setActorId] = useState('all');
  const [record, setRecord] = useState('all');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const badDates = !!from && !!to && to < from;
  const filter: AuditFilter = {
    ...(actorId !== 'all' ? { actorId } : {}), ...(RECORDS[record]?.filter ?? {}),
    ...(from ? { from } : {}), ...(to && !badDates ? { to } : {}),
  };
  const { data, error, isPending, isFetching, refetch } = useAudit(slug, filter, page, SIZE, can('activity.view'));
  if (!can('activity.view')) return <PermissionDenied />;
  const name = (key: string) => {
    const m = modules.find((x) => x.key === key);
    return m ? moduleName(m) : key;
  };
  const pages = data ? Math.max(1, Math.ceil(data.total / SIZE)) : 1;
  const filtered = actorId !== 'all' || record !== 'all' || !!from || !!to;
  const reset = (fn: () => void) => { fn(); setPage(0); };
  const sentence = (e: AuditEntry) => describe(e, name);
  const when = (e: AuditEntry) => <time dateTime={e.at} title={ago(e.at)} className="whitespace-nowrap tabular">{fmt(parseISO(e.at), 'd MMM yyyy HH:mm')}</time>;

  return (
    <>
      <PageHeader title={t('collab.auditTitle')} subtitle={t('collab.auditSubtitle', { org: org.name })} />
      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-[repeat(4,minmax(0,1fr))_auto] lg:items-end">
        <div className="space-y-1.5">
          <Label htmlFor="audit-person">{t('collab.person')}</Label>
          <Select value={actorId} onValueChange={(v) => reset(() => setActorId(v))}>
            <SelectTrigger id="audit-person" className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('collab.anyone')}</SelectItem>
              {(members.data ?? []).map((m) => <SelectItem key={m.userId} value={m.userId}>{m.name}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="audit-record">{t('collab.record')}</Label>
          <Select value={record} onValueChange={(v) => reset(() => setRecord(v))}>
            <SelectTrigger id="audit-record" className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('collab.anyRecord')}</SelectItem>
              {Object.entries(RECORDS).map(([k, r]) => <SelectItem key={k} value={k}>{t(r.label)}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="audit-from">{t('collab.from')}</Label>
          <Input id="audit-from" type="date" value={from} max={to || undefined} onChange={(e) => reset(() => setFrom(e.target.value))} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="audit-to">{t('collab.to')}</Label>
          <Input id="audit-to" type="date" value={to} min={from || undefined} onChange={(e) => reset(() => setTo(e.target.value))}
            aria-invalid={badDates} aria-describedby={badDates ? 'audit-dates' : undefined} />
        </div>
        {filtered && (
          <Button variant="ghost" onClick={() => reset(() => { setActorId('all'); setRecord('all'); setFrom(''); setTo(''); })}>{t('common.clear')}</Button>
        )}
      </div>
      {badDates && <p id="audit-dates" role="alert" className="mb-3 text-sm text-destructive">{t('collab.errDates')}</p>}

      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={8} /> : data.items.length === 0 ? (
        filtered ? <EmptyState icon={SearchX} title={t('collab.noMatch')} body={t('collab.noMatchBody')} />
          : <EmptyState icon={ScrollText} title={t('collab.noEntries')} body={t('collab.noEntriesBody')} />
      ) : (
        <div className={cn('space-y-3', isFetching && 'opacity-70')} aria-busy={isFetching}>
          <p className="text-sm text-muted-foreground">{t('collab.entries', { count: data.total })}</p>
          {/* Desktop: a table; phone: one card per entry. */}
          <div className="hidden overflow-hidden rounded-lg border bg-card md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-48">{t('collab.colWho')}</TableHead><TableHead>{t('collab.colWhat')}</TableHead>
                  <TableHead className="w-64">{t('collab.colRecord')}</TableHead><TableHead className="w-40">{t('collab.colWhen')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell><span className="flex items-center gap-2"><PersonAvatar person={e.actor} className="size-6" /><span className="truncate text-sm font-medium">{e.actor?.name ?? t('tasks.system')}</span></span></TableCell>
                    <TableCell className="whitespace-normal break-words text-sm">{sentence(e)}</TableCell>
                    <TableCell className="whitespace-normal text-sm"><RecordCell e={e} /></TableCell>
                    <TableCell className="text-sm text-muted-foreground">{when(e)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <ul className="divide-y rounded-lg border bg-card md:hidden">
            {data.items.map((e) => (
              <li key={e.id} className="flex gap-3 px-3 py-3">
                <PersonAvatar person={e.actor} className="size-7" />
                <div className="min-w-0 flex-1 text-sm">
                  <p className="break-words"><span className="font-medium">{e.actor?.name ?? t('tasks.system')}</span> {sentence(e)}</p>
                  <p className="mt-0.5"><RecordCell e={e} /></p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{when(e)}</p>
                </div>
              </li>
            ))}
          </ul>
          {pages > 1 && (
            <nav className="flex items-center justify-between gap-2" aria-label={t('collab.pageOf', { page: page + 1, pages })}>
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}><ChevronLeft className="size-4" />{t('collab.newer')}</Button>
              <span className="text-sm text-muted-foreground tabular">{t('collab.pageOf', { page: page + 1, pages })}</span>
              <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>{t('collab.older')}<ChevronRight className="size-4" /></Button>
            </nav>
          )}
        </div>
      )}
    </>
  );
}
