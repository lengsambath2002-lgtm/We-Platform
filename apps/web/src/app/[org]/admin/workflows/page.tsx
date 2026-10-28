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
import { errorMessage } from '@/lib/api/client';
import { orgApi, useMembers, useOrgMutation, useWorkflows } from '@/lib/api/queries';
import type { Workflow } from '@/lib/api/types';
import { useOrg } from '@/lib/org-context';

type Step = { type: 'USER' | 'ORG_ROLE' | 'PROJECT_LEAD'; userId: string | null; role: string | null };
const STEP_KIND = [
  { v: 'PROJECT_LEAD', label: "The record's project lead" },
  { v: 'ORG_ROLE:ADMIN', label: 'Any admin' },
  { v: 'ORG_ROLE:OWNER', label: 'Any owner' },
  { v: 'USER', label: 'A specific person' },
];
const kindOf = (s: Step) => (s.type === 'ORG_ROLE' ? `ORG_ROLE:${s.role}` : s.type);

function Editor({ org, wf, onClose }: { org: string; wf: Workflow | 'new'; onClose: () => void }) {
  const members = useMembers(org);
  const people = (members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST');
  const [name, setName] = useState(wf === 'new' ? '' : wf.name);
  const [active, setActive] = useState(wf === 'new' ? true : wf.active);
  const [steps, setSteps] = useState<Step[]>(wf === 'new' ? [{ type: 'PROJECT_LEAD', userId: null, role: null }]
    : wf.steps.map((s) => ({ type: s.type, userId: s.userId, role: s.role })));
  const save = useOrgMutation(org, () => orgApi.saveWorkflow(org, wf === 'new' ? null : wf.id, { name: name.trim(), moduleKey: 'projects', steps, active }));
  const valid = name.trim().length >= 2 && steps.length > 0 && steps.every((s) => s.type !== 'USER' || s.userId);
  const set = (i: number, s: Step) => setSteps((x) => x.map((y, j) => (j === i ? s : y)));

  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{wf === 'new' ? 'New workflow' : `Edit ${wf.name}`}</DialogTitle>
          <DialogDescription>Approvers decide in order. Requests already in progress keep the steps they started with.</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2"><Label htmlFor="wf-name">Name</Label><Input id="wf-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={120} autoFocus /></div>
          <ol className="space-y-2">
            {steps.map((s, i) => (
              <li key={i}>
                {i > 0 && <ArrowDown className="mx-auto mb-2 size-4 text-muted-foreground" aria-hidden />}
                <div className="flex flex-wrap items-center gap-2 rounded-md border p-2">
                  <span className="w-12 text-xs font-medium text-muted-foreground">Step {i + 1}</span>
                  <Select value={kindOf(s)} onValueChange={(v) => set(i, v.startsWith('ORG_ROLE') ? { type: 'ORG_ROLE', role: v.split(':')[1]!, userId: null } : { type: v as Step['type'], role: null, userId: null })}>
                    <SelectTrigger size="sm" className="w-52" aria-label={`Step ${i + 1} approver`}><SelectValue /></SelectTrigger>
                    <SelectContent>{STEP_KIND.map((k) => <SelectItem key={k.v} value={k.v}>{k.label}</SelectItem>)}</SelectContent>
                  </Select>
                  {s.type === 'USER' && (
                    <Select value={s.userId ?? ''} onValueChange={(v) => set(i, { ...s, userId: v })}>
                      <SelectTrigger size="sm" className="w-44" aria-label={`Step ${i + 1} person`}><SelectValue placeholder="Choose" /></SelectTrigger>
                      <SelectContent>{people.map((p) => <SelectItem key={p.userId} value={p.userId}>{p.name}</SelectItem>)}</SelectContent>
                    </Select>
                  )}
                  {steps.length > 1 && <Button variant="ghost" size="icon" className="ml-auto size-7" aria-label={`Remove step ${i + 1}`} onClick={() => setSteps((x) => x.filter((_, j) => j !== i))}><Trash2 className="size-3.5" /></Button>}
                </div>
              </li>
            ))}
          </ol>
          {steps.length < 5 && <Button variant="outline" size="sm" onClick={() => setSteps((x) => [...x, { type: 'ORG_ROLE', role: 'ADMIN', userId: null }])}><Plus className="size-4" />Add step</Button>}
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={active} onCheckedChange={(v) => setActive(v === true)} />Offer this workflow when people request approval</label>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={!valid || save.isPending} onClick={async () => {
            try { await save.mutateAsync(undefined); toast.success('Workflow saved'); onClose(); } catch (e) { toast.error(errorMessage(e)); }
          }}>{save.isPending ? 'Saving…' : 'Save'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function WorkflowsAdmin() {
  const { slug, can } = useOrg();
  const { data = [], error, isPending, refetch } = useWorkflows(slug);
  const [editing, setEditing] = useState<Workflow | 'new' | null>(null);
  if (!can('workflows.manage')) return <PermissionDenied />;

  return (
    <>
      <PageHeader title="Workflows" subtitle="Approval routes people choose from when they request approval."
        actions={<Button size="sm" onClick={() => setEditing('new')}><Plus className="size-4" />New workflow</Button>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={3} /> : data.length === 0 ? (
        <EmptyState icon={GitBranch} title="No workflows yet" body="The two default workflows appear the first time someone requests approval, or create your own."
          action={<Button size="sm" onClick={() => setEditing('new')}>Create a workflow</Button>} />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {data.map((w) => (
            <li key={w.id} className="rounded-lg border bg-card p-4">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="font-medium">{w.name}</p>
                  <p className="text-xs capitalize text-muted-foreground">{w.moduleKey}{!w.active && ' · not offered'}</p>
                </div>
                <Button variant="ghost" size="icon" className="size-7" aria-label={`Edit ${w.name}`} onClick={() => setEditing(w)}><Pencil className="size-3.5" /></Button>
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
