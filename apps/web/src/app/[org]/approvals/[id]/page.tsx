'use client';

import { ChevronRight, ExternalLink } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';

import { RecordKind } from '@/components/approvals/record-kind';
import { ApprovalSteps } from '@/components/approvals/approval-steps';
import { DecisionBar } from '@/components/approvals/decision-bar';
import { ApprovalBadge } from '@/components/common/badges';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useApproval } from '@/lib/api/queries';
import { ago, day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

export default function ApprovalPage() {
  const { slug } = useOrg();
  const { t } = useT();
  const { id } = useParams<{ id: string }>();
  const { data, error, isPending, refetch } = useApproval(slug, id);
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton />;
  const s = data.summary;

  return (
    <div className="mx-auto max-w-4xl">
      <nav aria-label={t('approvals.breadcrumb')} className="mb-2 flex items-center gap-1 text-sm text-muted-foreground">
        <Link href={`/${slug}/approvals`} className="hover:text-foreground">{t('approvals.title')}</Link><ChevronRight className="size-3.5" /><span>{t('approvals.request')}</span>
      </nav>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight">{s.title}</h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <ApprovalBadge status={s.status} /> {data.workflowName} · {t('approvals.requestedBy', { name: s.requester.name, date: day(s.createdAt) })}
          </p>
        </div>
        <Button asChild variant="outline" size="sm"><Link href={s.recordLink.startsWith('/') ? s.recordLink : `/${slug}/${s.recordLink}`}><ExternalLink className="size-4" />{s.recordType === 'task' ? t('approvals.openTask') : s.recordType === 'leave' ? t('approvals.openLeave') : s.recordType === 'expense' ? t('approvals.openExpense') : t('approvals.openRecord', { type: s.recordType })}</Link></Button>
      </div>
      <div className="grid gap-6 md:grid-cols-[1fr_280px]">
        <div className="space-y-6">
          {data.text && (
            <section className="rounded-lg border bg-card p-4">
              <h2 className="mb-2 text-sm font-semibold">{t('approvals.noteFrom', { name: s.requester.name })}</h2>
              <p className="whitespace-pre-wrap text-sm">{data.text}</p>
            </section>
          )}
          <section className="rounded-lg border bg-card p-4">
            <h2 className="mb-4 text-sm font-semibold">{t('approvals.steps')}</h2>
            <ApprovalSteps detail={data} />
          </section>
        </div>
        <aside className="space-y-3">
          <div className="rounded-lg border bg-card p-4 text-sm">
            <p className="text-xs text-muted-foreground">{t('approvals.lastChange')}</p><p>{ago(s.updatedAt)}</p>
            <p className="mt-3 text-xs text-muted-foreground">{t('approvals.module')}</p><p><RecordKind moduleKey={s.moduleKey} recordType={s.recordType} /></p>
          </div>
          <DecisionBar org={slug} detail={data} />
        </aside>
      </div>
    </div>
  );
}
