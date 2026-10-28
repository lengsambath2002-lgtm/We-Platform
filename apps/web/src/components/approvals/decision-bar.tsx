'use client';

import { Check, RotateCcw, Undo2, X } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { ApprovalDetail } from '@/lib/api/types';

type Action = 'approve' | 'reject' | 'request-changes' | 'resubmit' | 'withdraw';
const COPY: Record<Action, { title: string; done: string; needs: boolean; label: string }> = {
  approve: { title: 'Approve', done: 'Approved', needs: false, label: 'Comment (optional)' },
  reject: { title: 'Reject', done: 'Rejected', needs: true, label: 'Why is it rejected?' },
  'request-changes': { title: 'Request changes', done: 'Changes requested', needs: true, label: 'What needs to change?' },
  resubmit: { title: 'Resubmit', done: 'Resubmitted', needs: false, label: 'What changed? (optional)' },
  withdraw: { title: 'Withdraw request', done: 'Withdrawn', needs: false, label: '' },
};

/** The actions the server says this person may take on an approval request. */
export function DecisionBar({ org, detail }: { org: string; detail: ApprovalDetail }) {
  const [action, setAction] = useState<Action | null>(null);
  const [comment, setComment] = useState('');
  const decide = useOrgMutation(org, (a: Action) => orgApi.decide(org, detail.summary.id, a, comment || undefined));
  const c = action ? COPY[action] : null;
  if (!detail.canDecide && !detail.canResubmit && !detail.canWithdraw) return null;

  return (
    <div className="flex flex-wrap gap-2">
      {detail.canDecide && (
        <>
          <Button size="sm" onClick={() => setAction('approve')}><Check className="size-4" />Approve</Button>
          <Button size="sm" variant="outline" onClick={() => setAction('request-changes')}><RotateCcw className="size-4" />Request changes</Button>
          <Button size="sm" variant="outline" className="text-destructive" onClick={() => setAction('reject')}><X className="size-4" />Reject</Button>
        </>
      )}
      {detail.canResubmit && <Button size="sm" onClick={() => setAction('resubmit')}><RotateCcw className="size-4" />Resubmit</Button>}
      {detail.canWithdraw && <Button size="sm" variant="ghost" onClick={() => setAction('withdraw')}><Undo2 className="size-4" />Withdraw</Button>}
      <Dialog open={!!action} onOpenChange={(v) => { if (!v) { setAction(null); setComment(''); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{c?.title}</DialogTitle>
            <DialogDescription>{action === 'withdraw' ? 'The approvers will no longer be asked. You can request approval again later.' : detail.summary.title}</DialogDescription>
          </DialogHeader>
          {c?.label && (
            <div className="space-y-2">
              <Label htmlFor="decision-comment">{c.label}</Label>
              <Textarea id="decision-comment" rows={3} value={comment} onChange={(e) => setComment(e.target.value)} maxLength={5000} />
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setAction(null)}>Cancel</Button>
            <Button variant={action === 'reject' ? 'destructive' : 'default'} disabled={decide.isPending || (!!c?.needs && !comment.trim())}
              onClick={async () => {
                try { await decide.mutateAsync(action!); toast.success(c!.done); setAction(null); setComment(''); } catch (e) { toast.error(errorMessage(e)); }
              }}>{decide.isPending ? 'Saving…' : c?.title}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
