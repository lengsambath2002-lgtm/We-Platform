'use client';

import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useOrgMutation, useWorkflows } from '@/lib/api/queries';
import type { Workflow } from '@/lib/api/types';

export function stepText(s: Workflow['steps'][number]) {
  return s.type === 'PROJECT_LEAD' ? 'Project lead' : s.type === 'ORG_ROLE' ? (s.role === 'OWNER' ? 'An owner' : 'An admin') : (s.userName ?? 'A person');
}

/** Sends any module's record through an approval workflow. The record decides who may see it. */
export function RequestApprovalDialog({ org, moduleKey, recordType, recordId, open, onOpenChange }: {
  org: string; moduleKey: string; recordType: string; recordId: string; open: boolean; onOpenChange: (v: boolean) => void;
}) {
  const { data = [], isPending } = useWorkflows(org);
  const flows = data.filter((w) => w.active && w.moduleKey === moduleKey);
  const [workflowId, setWorkflowId] = useState('');
  const [summary, setSummary] = useState('');
  const chosen = flows.find((w) => w.id === (workflowId || flows[0]?.id));
  const submit = useOrgMutation(org, () => orgApi.submitApproval(org, { workflowId: chosen!.id, moduleKey, recordType, recordId, summary: summary || null }));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Request approval</DialogTitle>
          <DialogDescription>Approvers are asked one step at a time. You can withdraw the request until it is decided.</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="wf">Workflow</Label>
            <Select value={chosen?.id ?? ''} onValueChange={setWorkflowId} disabled={isPending || flows.length === 0}>
              <SelectTrigger id="wf" className="w-full"><SelectValue placeholder={isPending ? 'Loading…' : 'No workflows'} /></SelectTrigger>
              <SelectContent>{flows.map((w) => <SelectItem key={w.id} value={w.id}>{w.name}</SelectItem>)}</SelectContent>
            </Select>
            {chosen && (
              <ol className="flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
                {chosen.steps.map((s, i) => <li key={s.order}>{i > 0 && '→ '}{stepText(s)}</li>)}
              </ol>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="sum">Note for the approvers <span className="font-normal text-muted-foreground">(optional)</span></Label>
            <Textarea id="sum" rows={3} value={summary} onChange={(e) => setSummary(e.target.value)} maxLength={5000} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={!chosen || submit.isPending} onClick={async () => {
            try { await submit.mutateAsync(undefined); toast.success('Approval requested'); onOpenChange(false); setSummary(''); } catch (e) { toast.error(errorMessage(e)); }
          }}>{submit.isPending ? 'Sending…' : 'Send request'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
