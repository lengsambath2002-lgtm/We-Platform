'use client';

import { ArrowDown, GitBranch, Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { stepText } from '@/components/approvals/request-approval-dialog';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useMembers, useOrgMutation, useWorkflows } from '@/lib/api/queries';
import type { Workflow } from '@/lib/api/types';
import { useOrg } from '@/lib/org-context';

type Step = { type: 'USER' | 'ORG_ROLE' | 'PROJECT_LEAD' | 'MANAGER'; userId: string | null; role: string | null };
const STEP_KIND = [
  { v: 'PROJECT_LEAD', key: 'admin.stepKind_PROJECT_LEAD' },
  { v: 'MANAGER', key: 'admin.stepKind_MANAGER' },
  { v: 'ORG_ROLE:ADMIN', key: 'admin.stepKind_ADMIN' },
  { v: 'ORG_ROLE:OWNER', key: 'admin.stepKind_OWNER' },
  { v: 'USER', key: 'admin.stepKind_USER' },
] as const;
const kindOf = (s: Step) => (s.type === 'ORG_ROLE' ? `ORG_ROLE:${s.role}` : s.type);
/** Step kinds that make sense for a module's records: the project lead for tasks, the manager for HR. */
const kindsFor = (moduleKey: string) => STEP_KIND.filter((k) => (k.v !== 'PROJECT_LEAD' || moduleKey === 'projects') && (k.v !== 'MANAGER' || moduleKey === 'hr'));
const fits = (moduleKey: string, s: Step) => kindsFor(moduleKey).some((k) => k.v === kindOf(s));

function Editor({ org, wf, onClose }: { org: string; wf: Workflow | 'new'; onClose: () => void }) {
  const { t, locale } = useT();
  const { modules } = useOrg();
  // Modules whose records go through approvals: Projects, and HR when it is switched on.
  const usable = modules.filter((m) => (m.key === 'projects' || m.key === 'hr') && (m.enabled || (wf !== 'new' && wf.moduleKey === m.key)));
  const [moduleKey, setModuleKey] = useState(wf === 'new' ? 'projects' : wf.moduleKey);
  const members = useMembers(org);
  const people = (members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST');
  const [name, setName] = useState(wf === 'new' ? '' : wf.name);
  const [active, setActive] = useState(wf === 'new' ? true : wf.active);
  const [steps, setSteps] = useState<Step[]>(wf === 'new' ? [{ type: 'PROJECT_LEAD', userId: null, role: null }]
    : wf.steps.map((s) => ({ type: s.type, userId: s.userId, role: s.role })));
  const save = useOrgMutation(org, () => orgApi.saveWorkflow(org, wf === 'new' ? null : wf.id, { name: name.trim(), moduleKey, steps, active }));
  const valid = name.trim().length >= 2 && steps.length > 0 && steps.every((s) => (s.type !== 'USER' || s.userId) && fits(moduleKey, s));
  const set = (i: number, s: Step) => setSteps((x) => x.map((y, j) => (j === i ? s : y)));

  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{wf === 'new' ? t('admin.newWorkflow') : t('admin.editName', { name: wf.name })}</DialogTitle>
          <DialogDescription>{t('admin.workflowDialogBody')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2"><Label htmlFor="wf-name">{t('admin.name')}</Label><Input id="wf-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={120} autoFocus /></div>
          {usable.length > 1 && (
            <div className="space-y-2">
              <Label htmlFor="wf-module">{t('admin.workflowModule')}</Label>
              <Select value={moduleKey} onValueChange={(v) => { setModuleKey(v); setSteps((x) => x.map((s) => (fits(v, s) ? s : { type: 'ORG_ROLE', role: 'ADMIN', userId: null }))); }}>
                <SelectTrigger id="wf-module" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{usable.map((m) => <SelectItem key={m.key} value={m.key}>{locale === 'km' ? m.nameKm : m.nameEn}</SelectItem>)}</SelectContent>
              </Select>
            </div>
          )}
          <ol className="space-y-2">
            {steps.map((s, i) => (
              <li key={i}>
                {i > 0 && <ArrowDown className="mx-auto mb-2 size-4 text-muted-foreground" aria-hidden />}
                <div className="flex flex-wrap items-center gap-2 rounded-md border p-2">
                  <span className="w-12 text-xs font-medium text-muted-foreground">{t('admin.step', { n: i + 1 })}</span>
                  <Select value={kindOf(s)} onValueChange={(v) => set(i, v.startsWith('ORG_ROLE') ? { type: 'ORG_ROLE', role: v.split(':')[1]!, userId: null } : { type: v as Step['type'], role: null, userId: null })}>
                    <SelectTrigger size="sm" className="w-52" aria-label={t('admin.stepApprover', { n: i + 1 })}><SelectValue /></SelectTrigger>
                    <SelectContent>{kindsFor(moduleKey).map((k) => <SelectItem key={k.v} value={k.v}>{t(k.key)}</SelectItem>)}</SelectContent>
                  </Select>
                  {s.type === 'USER' && (
                    <Select value={s.userId ?? ''} onValueChange={(v) => set(i, { ...s, userId: v })}>
                      <SelectTrigger size="sm" className="w-44" aria-label={t('admin.stepPerson', { n: i + 1 })}><SelectValue placeholder={t('admin.choose')} /></SelectTrigger>
                      <SelectContent>{people.map((p) => <SelectItem key={p.userId} value={p.userId}>{p.name}</SelectItem>)}</SelectContent>
                    </Select>
                  )}
                  {steps.length > 1 && <Button variant="ghost" size="icon" className="ml-auto size-7" aria-label={t('admin.removeStep', { n: i + 1 })} onClick={() => setSteps((x) => x.filter((_, j) => j !== i))}><Trash2 className="size-3.5" /></Button>}
                </div>
              </li>
            ))}
          </ol>
          {steps.length < 5 && <Button variant="outline" size="sm" onClick={() => setSteps((x) => [...x, { type: 'ORG_ROLE', role: 'ADMIN', userId: null }])}><Plus className="size-4" />{t('admin.addStep')}</Button>}
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={active} onCheckedChange={(v) => setActive(v === true)} />{t('admin.offerWorkflow')}</label>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={!valid || save.isPending} onClick={async () => {
            try { await save.mutateAsync(undefined); toast.success(t('admin.workflowSaved')); onClose(); } catch (e) { toast.error(errorMessage(e)); }
          }}>{save.isPending ? t('common.saving') : t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function WorkflowsAdmin() {
  const { slug, can, modules } = useOrg();
  const { t, locale } = useT();
  const moduleName = (key: string) => {
    const m = modules.find((x) => x.key === key);
    return m ? (locale === 'km' ? m.nameKm : m.nameEn) : key;
  };
  const { data = [], error, isPending, refetch } = useWorkflows(slug);
  const [editing, setEditing] = useState<Workflow | 'new' | null>(null);
  if (!can('workflows.manage')) return <PermissionDenied />;

  return (
    <>
      <PageHeader title={t('admin.workflowsTitle')} subtitle={t('admin.workflowsSubtitle')}
        actions={<Button size="sm" onClick={() => setEditing('new')}><Plus className="size-4" />{t('admin.newWorkflow')}</Button>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={3} /> : data.length === 0 ? (
        <EmptyState icon={GitBranch} title={t('admin.noWorkflowsTitle')} body={t('admin.noWorkflowsBody')}
          action={<Button size="sm" onClick={() => setEditing('new')}>{t('admin.createWorkflow')}</Button>} />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {data.map((w) => (
            <li key={w.id} className="rounded-lg border bg-card p-4">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="font-medium">{w.name}</p>
                  <p className="text-xs capitalize text-muted-foreground">{moduleName(w.moduleKey)}{!w.active && ` · ${t('admin.notOffered')}`}</p>
                </div>
                <Button variant="ghost" size="icon" className="size-7" aria-label={t('admin.editName', { name: w.name })} onClick={() => setEditing(w)}><Pencil className="size-3.5" /></Button>
              </div>
              <ol className="mt-3 flex flex-wrap items-center gap-1.5 text-xs">
                {w.steps.map((s, i) => <li key={s.order} className="flex items-center gap-1.5">{i > 0 && <span className="text-muted-foreground">→</span>}<span className="rounded bg-muted px-2 py-0.5">{stepText(s)}</span></li>)}
              </ol>
            </li>
          ))}
        </ul>
      )}
      {editing && <Editor org={slug} wf={editing} onClose={() => setEditing(null)} />}
    </>
  );
}
