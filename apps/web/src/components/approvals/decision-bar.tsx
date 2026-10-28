'use client';

import { Check, RotateCcw, Undo2, X } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { ApprovalDetail } from '@/lib/api/types';

type Action = 'approve' | 'reject' | 'request-changes' | 'resubmit' | 'withdraw';
const COPY: Record<Action, { title: MessageKey; done: MessageKey; needs: boolean; label: MessageKey | null }> = {
  approve: { title: 'approvals.approve', done: 'approvals.approved', needs: false, label: 'approvals.commentOptional' },
  reject: { title: 'approvals.reject', done: 'approvals.rejected', needs: true, label: 'approvals.whyRejected' },
  'request-changes': { title: 'approvals.requestChanges', done: 'approvals.changesRequested', needs: true, label: 'approvals.whatToChange' },
  resubmit: { title: 'approvals.resubmit', done: 'approvals.resubmitted', needs: false, label: 'approvals.whatChanged' },
  withdraw: { title: 'approvals.withdrawRequest', done: 'approvals.withdrawn', needs: false, label: null },
};

/** The actions the server says this person may take on an approval request. */
export function DecisionBar({ org, detail }: { org: string; detail: ApprovalDetail }) {
  const { t } = useT();
  const [action, setAction] = useState<Action | null>(null);
  const [comment, setComment] = useState('');
  const decide = useOrgMutation(org, (a: Action) => orgApi.decide(org, detail.summary.id, a, comment || undefined));
  const c = action ? COPY[action] : null;
  if (!detail.canDecide && !detail.canResubmit && !detail.canWithdraw) return null;

  return (
    <div className="flex flex-wrap gap-2">
      {detail.canDecide && (
        <>
          <Button size="sm" onClick={() => setAction('approve')}><Check className="size-4" />{t('approvals.approve')}</Button>
          <Button size="sm" variant="outline" onClick={() => setAction('request-changes')}><RotateCcw className="size-4" />{t('approvals.requestChanges')}</Button>
          <Button size="sm" variant="outline" className="text-destructive" onClick={() => setAction('reject')}><X className="size-4" />{t('approvals.reject')}</Button>
        </>
      )}
      {detail.canResubmit && <Button size="sm" onClick={() => setAction('resubmit')}><RotateCcw className="size-4" />{t('approvals.resubmit')}</Button>}
      {detail.canWithdraw && <Button size="sm" variant="ghost" onClick={() => setAction('withdraw')}><Undo2 className="size-4" />{t('approvals.withdraw')}</Button>}
      <Dialog open={!!action} onOpenChange={(v) => { if (!v) { setAction(null); setComment(''); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{c && t(c.title)}</DialogTitle>
            <DialogDescription>{action === 'withdraw' ? t('approvals.withdrawBody') : detail.summary.title}</DialogDescription>
          </DialogHeader>
          {c?.label && (
            <div className="space-y-2">
              <Label htmlFor="decision-comment">{t(c.label)}</Label>
              <Textarea id="decision-comment" rows={3} value={comment} onChange={(e) => setComment(e.target.value)} maxLength={5000} />
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setAction(null)}>{t('common.cancel')}</Button>
            <Button variant={action === 'reject' ? 'destructive' : 'default'} disabled={decide.isPending || (!!c?.needs && !comment.trim())}
              onClick={async () => {
                try { await decide.mutateAsync(action!); toast.success(t(c!.done)); setAction(null); setComment(''); } catch (e) { toast.error(errorMessage(e)); }
              }}>{decide.isPending ? t('common.saving') : c && t(c.title)}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
