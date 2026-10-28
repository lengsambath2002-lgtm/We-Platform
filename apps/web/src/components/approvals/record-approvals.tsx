'use client';

import { ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

import { ApprovalBadge } from '@/components/common/badges';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useApproval, useRecordApprovals } from '@/lib/api/queries';
import { ago } from '@/lib/format';

import { ApprovalSteps } from './approval-steps';
import { DecisionBar } from './decision-bar';
import { RequestApprovalDialog } from './request-approval-dialog';

const OPEN = ['PENDING', 'CHANGES_REQUESTED'];

/** Approval state of one record: the open request with its steps and actions, earlier requests, and "Request approval". */
export function RecordApprovals({ org, recordId, canRequest }: { org: string; recordId: string; canRequest: boolean }) {
  const { data = [], error, isPending, refetch } = useRecordApprovals(org, recordId);
  const open = data.find((a) => OPEN.includes(a.status));
  const detail = useApproval(org, open?.id ?? '');
  const [asking, setAsking] = useState(false);

  return (
    <section aria-labelledby="appr-h" className="space-y-3 rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 id="appr-h" className="flex items-center gap-2 text-sm font-semibold"><ShieldCheck className="size-4" />Approval</h2>
        {open && <ApprovalBadge status={open.status} />}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={1} /> : (
        <>
          {open && detail.data && (
            <>
              <ApprovalSteps detail={detail.data} />
              <DecisionBar org={org} detail={detail.data} />
              <Link href={`/${org}/approvals/${open.id}`} className="block text-xs text-primary hover:underline">Open the request</Link>
            </>
          )}
          {!open && (
            <p className="text-sm text-muted-foreground">{data.length ? 'No open request.' : 'Not sent for approval.'}</p>
          )}
          {!open && canRequest && <Button size="sm" variant="outline" className="w-full" onClick={() => setAsking(true)}>Request approval</Button>}
          {data.filter((a) => a.id !== open?.id).length > 0 && (
            <ul className="space-y-1 border-t pt-3">
              {data.filter((a) => a.id !== open?.id).map((a) => (
                <li key={a.id} className="flex items-center justify-between gap-2 text-xs">
                  <Link href={`/${org}/approvals/${a.id}`} className="text-muted-foreground hover:text-foreground hover:underline">Requested {ago(a.createdAt)}</Link>
                  <ApprovalBadge status={a.status} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      <RequestApprovalDialog org={org} moduleKey="projects" recordType="task" recordId={recordId} open={asking} onOpenChange={setAsking} />
    </section>
  );
}
