'use client';

import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useOrgMutation, useWorkflows } from '@/lib/api/queries';
import type { Workflow } from '@/lib/api/types';

export function stepText(s: Workflow['steps'][number]) {
  return s.type === 'PROJECT_LEAD' ? tr('approvals.projectLead') : s.type === 'ORG_ROLE' ? tr(s.role === 'OWNER' ? 'approvals.anOwner' : 'approvals.anAdmin') : (s.userName ?? tr('approvals.aPerson'));
}

/** Sends any module's record through an approval workflow. The record decides who may see it. */
export function RequestApprovalDialog({ org, moduleKey, recordType, recordId, open, onOpenChange }: {
  org: string; moduleKey: string; recordType: string; recordId: string; open: boolean; onOpenChange: (v: boolean) => void;
}) {
  const { t } = useT();
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
          <DialogTitle>{t('approvals.requestApproval')}</DialogTitle>
          <DialogDescription>{t('approvals.requestBody')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="wf">{t('approvals.workflow')}</Label>
            <Select value={chosen?.id ?? ''} onValueChange={setWorkflowId} disabled={isPending || flows.length === 0}>
              <SelectTrigger id="wf" className="w-full"><SelectValue placeholder={t(isPending ? 'approvals.loadingWorkflows' : 'approvals.noWorkflows')} /></SelectTrigger>
              <SelectContent>{flows.map((w) => <SelectItem key={w.id} value={w.id}>{w.name}</SelectItem>)}</SelectContent>
            </Select>
            {chosen && (
              <ol className="flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
                {chosen.steps.map((s, i) => <li key={s.order}>{i > 0 && '→ '}{stepText(s)}</li>)}
              </ol>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="sum">{t('approvals.noteForApprovers')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></Label>
            <Textarea id="sum" rows={3} value={summary} onChange={(e) => setSummary(e.target.value)} maxLength={5000} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
          <Button disabled={!chosen || submit.isPending} onClick={async () => {
            try { await submit.mutateAsync(undefined); toast.success(t('approvals.approvalRequested')); onOpenChange(false); setSummary(''); } catch (e) { toast.error(errorMessage(e)); }
          }}>{submit.isPending ? t('approvals.sending') : t('approvals.sendRequest')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
