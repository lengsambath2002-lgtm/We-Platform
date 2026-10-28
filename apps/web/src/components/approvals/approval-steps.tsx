import { Check, Circle, RotateCcw, X } from 'lucide-react';

import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import type { ApprovalDetail } from '@/lib/api/types';
import { ago } from '@/lib/format';
import { cn } from '@/lib/utils';

const ACTION: Record<string, MessageKey> = {
  SUBMIT: 'approvals.actSubmit', APPROVE: 'approvals.actApprove', REJECT: 'approvals.actReject', REQUEST_CHANGES: 'approvals.actRequestChanges',
  RESUBMIT: 'approvals.actResubmit', WITHDRAW: 'approvals.actWithdraw',
};

/** Steps in order with who decided, then the request's history. */
export function ApprovalSteps({ detail }: { detail: ApprovalDetail }) {
  const { t } = useT();
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
                <p className="font-medium">{t('approvals.stepTitle', { n: s.order, who: s.approver?.name ?? t(s.role === 'OWNER' ? 'approvals.anOwner' : s.role === 'ADMIN' ? 'approvals.anAdmin' : 'approvals.approver') })}</p>
                <p className="text-xs text-muted-foreground">
                  {s.decidedBy && s.decidedAt
                    ? t(s.status === 'APPROVED' ? 'approvals.approvedBy' : s.status === 'REJECTED' ? 'approvals.rejectedBy' : 'approvals.changesRequestedBy', { name: s.decidedBy.name, ago: ago(s.decidedAt) })
                    : t(current ? 'approvals.waitingDecision' : 'approvals.notReached')}
                </p>
              </div>
            </li>
          );
        })}
      </ol>
      {detail.history.length > 0 && (
        <div>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">{t('approvals.history')}</h3>
          <ol className="space-y-2">
            {detail.history.map((h, i) => (
              <li key={i} className="text-sm">
                <span className="font-medium">{h.actor.name}</span> {ACTION[h.action] ? t(ACTION[h.action]) : h.action.toLowerCase()}
                {h.step && h.action !== 'SUBMIT' ? t('approvals.inStep', { n: h.step }) : ''} <span className="text-xs text-muted-foreground">{ago(h.at)}</span>
                {h.comment && <p className="mt-0.5 whitespace-pre-wrap rounded-md bg-muted px-2 py-1 text-sm">{h.comment}</p>}
              </li>
            ))}
          </ol>
        </div>
      )}
    </div>
  );
}
