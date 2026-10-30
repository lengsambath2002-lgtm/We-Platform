'use client';

import { CalendarClock, Download, FileUp, Lock, Pencil, Plus, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { labels } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { CONTRACT_KINDS, type Contract, DOC_CATEGORIES, type DocCategory, type ExpiringItem, type Personal, type Records, recordsApi, useExpiring, usePayHistory, useRecords } from '@/lib/api/records';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { cn } from '@/lib/utils';

const NONE = '__none__';
export const CONTRACT_LABEL = labels(CONTRACT_KINDS, (k) => `records.kind_${k}`);
const DOC_LABEL = labels(DOC_CATEGORIES, (k) => `records.doc_${k}`);
const GENDERS = ['MALE', 'FEMALE', 'OTHER'] as const;
const GENDER_LABEL = labels(GENDERS, (k) => `records.gender_${k}`);
const ID_TYPES = ['NATIONAL_ID', 'PASSPORT', 'OTHER'] as const;
const ID_LABEL = labels(ID_TYPES, (k) => `records.id_${k}`);
const EXPIRY_KINDS = ['CONTRACT_END', 'PROBATION_END', 'ID_EXPIRY', 'DOCUMENT_EXPIRY'] as const;
const EXPIRY_LABEL = labels(EXPIRY_KINDS, (k) => `records.exp_${k}`);
const alert = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm">{children}</dd></div>;
}

/** HR managers: every personal detail; the person: their address and personal contacts. */
function PersonalDialog({ org, userId, r, onClose }: { org: string; userId: string; r: Records; onClose: () => void }) {
  const { t } = useT();
  const p = r.personal;
  const [v, setV] = useState({
    nickname: p.nickname ?? '', duty: p.duty ?? '', category: p.category ?? '', gender: p.gender ?? NONE, dateOfBirth: p.dateOfBirth ?? '',
    nationality: p.nationality ?? '', idType: p.idType ?? NONE, idNumber: p.idNumber ?? '', idExpiresOn: p.idExpiresOn ?? '',
    nssfNumber: p.nssfNumber ?? '', homeAddress: p.homeAddress ?? '', personalEmail: p.personalEmail ?? '', personalPhone: p.personalPhone ?? '',
  });
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => recordsApi.personal(org, userId, body));
  const text = (k: keyof typeof v, label: string, type = 'text', max = 120) => (
    <div className="space-y-1.5"><Label htmlFor={`pd-${k}`}>{label}</Label><Input id={`pd-${k}`} type={type} maxLength={max} value={v[k]} onChange={(e) => setV({ ...v, [k]: e.target.value })} /></div>
  );
  const choose = (k: 'gender' | 'idType', label: string, opts: readonly string[], names: Record<string, string>) => (
    <div className="space-y-1.5">
      <Label htmlFor={`pd-${k}`}>{label}</Label>
      <Select value={v[k]} onValueChange={(x) => setV({ ...v, [k]: x })}>
        <SelectTrigger id={`pd-${k}`} className="w-full"><SelectValue /></SelectTrigger>
        <SelectContent><SelectItem value={NONE}>{t('records.notSet')}</SelectItem>{opts.map((o) => <SelectItem key={o} value={o}>{names[o]}</SelectItem>)}</SelectContent>
      </Select>
    </div>
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
        <DialogHeader><DialogTitle>{t('records.editPersonal')}</DialogTitle><DialogDescription>{r.canEdit ? t('records.personalBodyHr') : t('records.personalBodySelf')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="grid gap-3 sm:grid-cols-2">
          {r.canEdit && <>
            {text('nickname', t('records.nickname'), 'text', 40)}
            {text('duty', t('records.duty'))}
            {text('category', t('records.category'), 'text', 60)}
            {choose('gender', t('records.gender'), GENDERS, GENDER_LABEL)}
            {text('dateOfBirth', t('records.dateOfBirth'), 'date')}
            {text('nationality', t('records.nationality'), 'text', 60)}
            {choose('idType', t('records.idType'), ID_TYPES, ID_LABEL)}
            {text('idNumber', t('records.idNumber'), 'text', 40)}
            {text('idExpiresOn', t('records.idExpiresOn'), 'date')}
            {text('nssfNumber', t('records.nssfNumber'), 'text', 30)}
          </>}
          {text('personalEmail', t('records.personalEmail'), 'email', 254)}
          {text('personalPhone', t('records.personalPhone'), 'tel', 30)}
          <div className="space-y-1.5 sm:col-span-2"><Label htmlFor="pd-address">{t('records.homeAddress')}</Label><Textarea id="pd-address" rows={2} maxLength={300} value={v.homeAddress} onChange={(e) => setV({ ...v, homeAddress: e.target.value })} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            setErr(null);
            const or = (x: string) => x.trim() || null;
            try {
              await save.mutateAsync({
                nickname: or(v.nickname), duty: or(v.duty), category: or(v.category), gender: v.gender === NONE ? null : v.gender, dateOfBirth: v.dateOfBirth || null,
                nationality: or(v.nationality), idType: v.idType === NONE ? null : v.idType, idNumber: or(v.idNumber), idExpiresOn: v.idExpiresOn || null,
                nssfNumber: or(v.nssfNumber), homeAddress: or(v.homeAddress), personalEmail: or(v.personalEmail), personalPhone: or(v.personalPhone),
              });
              toast.success(t('records.saved'));
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ContractDialog({ org, userId, contract, payroll, onClose }: { org: string; userId: string; contract: Contract | null; payroll: boolean; onClose: () => void }) {
  const { t } = useT();
  const [v, setV] = useState({
    kind: contract?.kind ?? 'PROBATION', startsOn: contract?.startsOn ?? '', endsOn: contract?.endsOn ?? '', probationEndsOn: contract?.probationEndsOn ?? '',
    monthlySalary: contract?.monthlySalary == null ? '' : Number(contract.monthlySalary).toFixed(2), note: contract?.note ?? '',
  });
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => (contract ? recordsApi.updateContract(org, contract.id, body) : recordsApi.addContract(org, userId, body)));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader><DialogTitle>{contract ? t('records.editContract') : t('records.addContract')}</DialogTitle><DialogDescription>{t('records.contractBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="ct-kind">{t('records.contractKind')}</Label>
            <Select value={v.kind} onValueChange={(k) => setV({ ...v, kind: k as Contract['kind'] })}>
              <SelectTrigger id="ct-kind" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>{CONTRACT_KINDS.map((k) => <SelectItem key={k} value={k}>{CONTRACT_LABEL[k]}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5"><Label htmlFor="ct-start">{t('records.startsOn')}</Label><Input id="ct-start" type="date" value={v.startsOn} onChange={(e) => setV({ ...v, startsOn: e.target.value })} /></div>
          {v.kind !== 'UNLIMITED' && <div className="space-y-1.5"><Label htmlFor="ct-end">{t('records.endsOn')}</Label><Input id="ct-end" type="date" min={v.startsOn} value={v.endsOn} onChange={(e) => setV({ ...v, endsOn: e.target.value })} /></div>}
          <div className="space-y-1.5"><Label htmlFor="ct-prob">{t('records.probationEndsOn')}</Label><Input id="ct-prob" type="date" min={v.startsOn} value={v.probationEndsOn} onChange={(e) => setV({ ...v, probationEndsOn: e.target.value })} /></div>
          {payroll && <div className="space-y-1.5"><Label htmlFor="ct-salary">{t('records.contractSalary')}</Label><Input id="ct-salary" inputMode="decimal" value={v.monthlySalary} onChange={(e) => setV({ ...v, monthlySalary: e.target.value })} /></div>}
          <div className="space-y-1.5 sm:col-span-2"><Label htmlFor="ct-note">{t('records.note')}</Label><Textarea id="ct-note" rows={2} maxLength={1000} value={v.note} onChange={(e) => setV({ ...v, note: e.target.value })} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!v.startsOn) { setErr(t('records.errStart')); return; }
            if (v.monthlySalary && !/^\d{1,10}(\.\d{1,2})?$/.test(v.monthlySalary.trim())) { setErr(t('records.errSalary')); return; }
            setErr(null);
            try {
              await save.mutateAsync({ kind: v.kind, startsOn: v.startsOn, endsOn: v.kind === 'UNLIMITED' ? null : v.endsOn || null, probationEndsOn: v.probationEndsOn || null,
                monthlySalary: v.monthlySalary.trim() || null, note: v.note.trim() || null });
              toast.success(t('records.contractSaved'));
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function DocumentDialog({ org, userId, hr, onClose }: { org: string; userId: string; hr: boolean; onClose: () => void }) {
  const { t } = useT();
  const [file, setFile] = useState<File | null>(null);
  const [category, setCategory] = useState<DocCategory>(hr ? 'ID' : 'CERTIFICATE');
  const [title, setTitle] = useState('');
  const [expires, setExpires] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, () => recordsApi.addDocument(org, userId, file!, category, title.trim(), expires || null));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('records.addDocument')}</DialogTitle><DialogDescription>{t('records.documentBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-3">
          <div className="space-y-1.5"><Label htmlFor="doc-file">{t('records.file')}</Label><Input id="doc-file" type="file" onChange={(e) => { const f = e.target.files?.[0] ?? null; setFile(f); if (f && !title) setTitle(f.name.replace(/\.[^.]+$/, '')); }} /></div>
          <div className="space-y-1.5">
            <Label htmlFor="doc-cat">{t('records.docKind')}</Label>
            <Select value={category} onValueChange={(c) => setCategory(c as DocCategory)}>
              <SelectTrigger id="doc-cat" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>{DOC_CATEGORIES.filter((c) => hr || !['ID', 'CONTRACT'].includes(c)).map((c) => <SelectItem key={c} value={c}>{DOC_LABEL[c]}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5"><Label htmlFor="doc-title">{t('records.docTitle')}</Label><Input id="doc-title" maxLength={160} value={title} onChange={(e) => setTitle(e.target.value)} /></div>
          <div className="space-y-1.5"><Label htmlFor="doc-exp">{t('records.expiresOn')}</Label><Input id="doc-exp" type="date" value={expires} onChange={(e) => setExpires(e.target.value)} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!file) { setErr(t('records.errFile')); return; }
            if (!title.trim()) { setErr(t('records.errTitle')); return; }
            setErr(null);
            try { await save.mutateAsync(undefined); toast.success(t('records.documentSaved')); onClose(); } catch (e) { setErr(errorMessage(e)); }
          }}>{t('records.upload')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function PayHistory({ org, userId }: { org: string; userId: string }) {
  const { t } = useT();
  const { data } = usePayHistory(org, userId);
  if (!data) return null;
  return (
    <section aria-labelledby="pay-h" className="rounded-lg border bg-card p-4">
      <h2 id="pay-h" className="mb-1 text-sm font-semibold">{t('records.payHistory')}</h2>
      <p className="mb-3 flex items-center gap-1.5 text-xs text-muted-foreground"><Lock className="size-3" />{t('records.payrollOnly')}</p>
      {data.length === 0 ? <p className="text-sm text-muted-foreground">{t('records.noPayHistory')}</p> : (
        <ol className="space-y-2">{data.map((c) => (
          <li key={c.id} className="flex flex-wrap items-baseline gap-x-3 text-sm">
            <span className="w-24 text-muted-foreground">{day(c.effectiveOn)}</span>
            <span className="font-medium tabular">{usd(c.monthlySalary)}</span>
            {c.previous != null && <span className="text-xs text-muted-foreground">{t('records.from', { amount: usd(c.previous) })}</span>}
            {c.reason && <span className="text-xs">· {c.reason}</span>}
          </li>
        ))}</ol>
      )}
    </section>
  );
}

/** Personal details, contracts, documents and (payroll) salary history on someone's profile page. */
export function RecordSections({ org, userId, payroll }: { org: string; userId: string; payroll: boolean }) {
  const { t } = useT();
  const { data: r, error, isPending, refetch } = useRecords(org, userId);
  const [editing, setEditing] = useState<'personal' | 'document' | Contract | 'contract' | null>(null);
  const fileFor = useRef<string | null>(null);
  const picker = useRef<HTMLInputElement>(null);
  const attach = useOrgMutation(org, ({ id, file }: { id: string; file: File }) => recordsApi.contractFile(org, id, file));
  const removeContract = useOrgMutation(org, (id: string) => recordsApi.deleteContract(org, id));
  const removeDoc = useOrgMutation(org, (id: string) => recordsApi.deleteDocument(org, id));
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={2} />;
  const p: Personal = r.personal;
  const dash = <span className="text-muted-foreground">{t('common.none')}</span>;
  const get = (kind: 'contracts' | 'documents', id: string, name: string) => recordsApi.download(org, kind, id, name).catch((e) => toast.error(errorMessage(e)));
  return (
    <>
      {(p.nickname || p.duty || p.category) && !r.shown && (
        <dl className="grid gap-4 rounded-lg border bg-card p-4 sm:grid-cols-3">
          <Field label={t('records.nickname')}>{p.nickname ?? dash}</Field><Field label={t('records.duty')}>{p.duty ?? dash}</Field><Field label={t('records.category')}>{p.category ?? dash}</Field>
        </dl>
      )}
      {r.shown && (
        <>
          <section aria-labelledby="pd-h" className="rounded-lg border bg-card p-4">
            <div className="mb-1 flex items-center gap-2">
              <h2 id="pd-h" className="flex-1 text-sm font-semibold">{t('records.personalTitle')}</h2>
              {(r.canEdit || r.self) && <Button variant="ghost" size="sm" onClick={() => setEditing('personal')}><Pencil className="size-4" />{t('common.edit')}</Button>}
            </div>
            <p className="mb-4 flex items-center gap-1.5 text-xs text-muted-foreground"><Lock className="size-3" />{t('records.privateNote')}</p>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label={t('records.nickname')}>{p.nickname ?? dash}</Field>
              <Field label={t('records.duty')}>{p.duty ?? dash}</Field>
              <Field label={t('records.category')}>{p.category ?? dash}</Field>
              <Field label={t('records.gender')}>{p.gender ? GENDER_LABEL[p.gender] : dash}</Field>
              <Field label={t('records.dateOfBirth')}>{p.dateOfBirth ? day(p.dateOfBirth) : dash}</Field>
              <Field label={t('records.nationality')}>{p.nationality ?? dash}</Field>
              <Field label={p.idType ? ID_LABEL[p.idType] : t('records.idNumber')}>{p.idNumber ? `${p.idNumber}${p.idExpiresOn ? ` · ${t('records.expires', { date: day(p.idExpiresOn) })}` : ''}` : dash}</Field>
              <Field label={t('records.nssfNumber')}>{p.nssfNumber ?? dash}</Field>
              <Field label={t('records.personalPhone')}>{p.personalPhone ?? dash}</Field>
              <Field label={t('records.personalEmail')}>{p.personalEmail ?? dash}</Field>
              <div className="sm:col-span-2"><Field label={t('records.homeAddress')}>{p.homeAddress ?? dash}</Field></div>
            </dl>
          </section>

          <section aria-labelledby="ct-h" className="rounded-lg border bg-card p-4">
            <div className="mb-3 flex items-center gap-2">
              <h2 id="ct-h" className="flex-1 text-sm font-semibold">{t('records.contracts')}</h2>
              {r.canEdit && <Button variant="ghost" size="sm" onClick={() => setEditing('contract')}><Plus className="size-4" />{t('records.addContract')}</Button>}
            </div>
            <input ref={picker} type="file" className="sr-only" aria-label={t('records.attachSigned')} onChange={(e) => {
              const f = e.target.files?.[0];
              if (f && fileFor.current) attach.mutateAsync({ id: fileFor.current, file: f }).then(() => toast.success(t('records.fileAttached'))).catch((x) => toast.error(errorMessage(x)));
              e.target.value = '';
            }} />
            {r.contracts.length === 0 ? <p className="text-sm text-muted-foreground">{t('records.noContracts')}</p> : (
              <ul className="divide-y rounded-md border">{r.contracts.map((c) => (
                <li key={c.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2 text-sm">
                  <span className={cn('rounded-full px-2 py-0.5 text-xs', c.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300' : 'bg-muted')}>{t(`records.status_${c.status}`)}</span>
                  <span className="min-w-0 flex-1">
                    <span className="font-medium">{CONTRACT_LABEL[c.kind]}</span>
                    <span className="block text-xs text-muted-foreground">{day(c.startsOn)} – {c.endsOn ? day(c.endsOn) : t('records.noEnd')}
                      {c.probationEndsOn ? ` · ${t('records.probationTo', { date: day(c.probationEndsOn) })}` : ''}{c.monthlySalary != null ? ` · ${usd(c.monthlySalary)}` : ''}</span>
                  </span>
                  {c.fileName && <Button variant="ghost" size="sm" onClick={() => get('contracts', c.id, c.fileName!)}><Download className="size-4" />{c.fileName}</Button>}
                  {r.canEdit && <>
                    <Button variant="ghost" size="icon" aria-label={t('records.attachSigned')} onClick={() => { fileFor.current = c.id; picker.current?.click(); }}><FileUp className="size-4" /></Button>
                    <Button variant="ghost" size="icon" aria-label={t('records.editContract')} onClick={() => setEditing(c)}><Pencil className="size-4" /></Button>
                    <Button variant="ghost" size="icon" className="text-destructive" aria-label={t('common.delete')} onClick={() => {
                      if (window.confirm(t('records.deleteContractConfirm'))) removeContract.mutateAsync(c.id).catch((x) => toast.error(errorMessage(x)));
                    }}><Trash2 className="size-4" /></Button>
                  </>}
                </li>
              ))}</ul>
            )}
          </section>

          <section aria-labelledby="doc-h" className="rounded-lg border bg-card p-4">
            <div className="mb-3 flex items-center gap-2">
              <h2 id="doc-h" className="flex-1 text-sm font-semibold">{t('records.documents')}</h2>
              <Button variant="ghost" size="sm" onClick={() => setEditing('document')}><FileUp className="size-4" />{t('records.addDocument')}</Button>
            </div>
            {r.documents.length === 0 ? <p className="text-sm text-muted-foreground">{t('records.noDocuments')}</p> : (
              <ul className="divide-y rounded-md border">{r.documents.map((d) => (
                <li key={d.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2 text-sm">
                  <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{DOC_LABEL[d.category]}</span>
                  <span className="min-w-0 flex-1"><span className="font-medium">{d.title}</span>
                    <span className="block text-xs text-muted-foreground">{[d.expiresOn ? t('records.expires', { date: day(d.expiresOn) }) : null, d.uploadedBy?.name, day(d.createdAt)].filter(Boolean).join(' · ')}</span></span>
                  {d.fileName && <Button variant="ghost" size="icon" aria-label={t('records.downloadOf', { name: d.title })} onClick={() => get('documents', d.id, d.fileName!)}><Download className="size-4" /></Button>}
                  {d.canDelete && <Button variant="ghost" size="icon" className="text-destructive" aria-label={t('records.deleteOf', { name: d.title })} onClick={() => {
                    if (window.confirm(t('records.deleteDocumentConfirm', { name: d.title }))) removeDoc.mutateAsync(d.id).catch((x) => toast.error(errorMessage(x)));
                  }}><Trash2 className="size-4" /></Button>}
                </li>
              ))}</ul>
            )}
          </section>
          {payroll && <PayHistory org={org} userId={userId} />}
        </>
      )}
      {editing === 'personal' && <PersonalDialog org={org} userId={userId} r={r} onClose={() => setEditing(null)} />}
      {editing === 'document' && <DocumentDialog org={org} userId={userId} hr={r.canEdit} onClose={() => setEditing(null)} />}
      {(editing === 'contract' || (typeof editing === 'object' && editing)) && (
        <ContractDialog org={org} userId={userId} contract={editing === 'contract' ? null : editing} payroll={payroll} onClose={() => setEditing(null)} />
      )}
    </>
  );
}

/** HR dashboard: contracts, probations, IDs and documents ending in the next 30 days (and lately). */
export function ExpiringCard({ org }: { org: string }) {
  const { t } = useT();
  const { data } = useExpiring(org);
  if (!data) return null;
  return (
    <section aria-labelledby="exp-h" className="rounded-lg border bg-card p-4">
      <h2 id="exp-h" className="mb-3 flex items-center gap-2 text-sm font-semibold"><CalendarClock className="size-4" />{t('records.expiringTitle')}</h2>
      {data.length === 0 ? <p className="text-sm text-muted-foreground">{t('records.expiringNone')}</p> : (
        <ul className="divide-y">{data.map((x: ExpiringItem, i) => (
          <li key={i} className="flex flex-wrap items-center gap-x-3 py-2 text-sm">
            <Link href={`/${org}/hr/people/${x.person.id}`} className="min-w-0 flex-1 font-medium hover:underline">{x.person.name}</Link>
            <span className="text-muted-foreground">{EXPIRY_LABEL[x.kind]}{x.kind === 'DOCUMENT_EXPIRY' && x.label ? `: ${x.label}` : ''}</span>
            <span className={cn('rounded-full px-2 py-0.5 text-xs', x.days < 0 ? 'bg-rose-50 text-rose-700 dark:bg-rose-950 dark:text-rose-300' : x.days <= 7 ? 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300' : 'bg-muted')}>
              {day(x.date)} · {x.days < 0 ? t('records.daysAgo', { count: -x.days }) : t('records.inDays', { count: x.days })}
            </span>
          </li>
        ))}</ul>
      )}
    </section>
  );
}
