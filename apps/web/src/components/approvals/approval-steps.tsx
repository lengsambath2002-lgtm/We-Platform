import { Check, Circle, RotateCcw, X } from 'lucide-react';

import type { ApprovalDetail } from '@/lib/api/types';
import { ago } from '@/lib/format';
import { cn } from '@/lib/utils';

const ACTION: Record<string, string> = {
  SUBMIT: 'requested approval', APPROVE: 'approved', REJECT: 'rejected', REQUEST_CHANGES: 'requested changes',
  RESUBMIT: 'resubmitted', WITHDRAW: 'withdrew the request',
};

/** Steps in order with who decided, then the request's history. */
export function ApprovalSteps({ detail }: { detail: ApprovalDetail }) {
  return (
    <div className="space-y-5">
      <ol className="space-y-3">
        {detail.steps.map((s) => {
          const Icon = s.status === 'APPROVED' ? Check : s.status === 'REJECTED' ? X : s.status === 'CHANGES_REQUESTED' ? RotateCcw : Circle;
          const current = detail.summary.status === 'PENDING' && detail.summary.currentStep === s.order;
          return (
            <li key={s.order} className="flex items-start gap-3">
              <span className={cn('mt-0.5 grid size-6 shrink-0 place-items-center rounded-full border',
                s.status === 'APPROVED' && 'border-success bg-success/10 text-success',
                s.status === 'REJECTED' && 'border-destructive bg-destructive/10 text-destructive',
                s.status === 'CHANGES_REQUESTED' && 'border-warning bg-warning/10 text-warning',
                current && 'border-primary text-primary')}>
                <Icon className="size-3.5" aria-hidden />
              </span>
              <div className="text-sm">
                <p className="font-medium">Step {s.order}: {s.approver?.name ?? (s.role === 'OWNER' ? 'An owner' : s.role === 'ADMIN' ? 'An admin' : 'Approver')}</p>
                <p className="text-xs text-muted-foreground">
                  {s.decidedBy && s.decidedAt ? `${s.status === 'APPROVED' ? 'Approved' : s.status === 'REJECTED' ? 'Rejected' : 'Changes requested'} by ${s.decidedBy.name} ${ago(s.decidedAt)}`
                    : current ? 'Waiting for a decision' : 'Not reached yet'}
                </p>
              </div>
            </li>
          );
        })}
      </ol>
      {detail.history.length > 0 && (
        <div>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">History</h3>
          <ol className="space-y-2">
            {detail.history.map((h, i) => (
              <li key={i} className="text-sm">
                <span className="font-medium">{h.actor.name}</span> {ACTION[h.action] ?? h.action.toLowerCase()}
                {h.step && h.action !== 'SUBMIT' ? ` (step ${h.step})` : ''} <span className="text-xs text-muted-foreground">{ago(h.at)}</span>
                {h.comment && <p className="mt-0.5 whitespace-pre-wrap rounded-md bg-muted px-2 py-1 text-sm">{h.comment}</p>}
              </li>
            ))}
          </ol>
        </div>
      )}
    </div>
  );
}
