'use client';

import { BadgeCheck, Inbox, type LucideIcon, Send } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

import { RecordKind } from '@/components/approvals/record-kind';
import { ApprovalBadge } from '@/components/common/badges';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { useApprovals } from '@/lib/api/queries';
import { ago } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

const BOXES = {
  inbox: { label: 'approvals.boxInbox', icon: Inbox, empty: 'approvals.inboxEmpty', body: 'approvals.inboxEmptyBody' },
  sent: { label: 'approvals.boxSent', icon: Send, empty: 'approvals.sentEmpty', body: 'approvals.sentEmptyBody' },
  all: { label: 'approvals.boxAll', icon: BadgeCheck, empty: 'approvals.allEmpty', body: 'approvals.allEmptyBody' },
} as const satisfies Record<string, { label: MessageKey; icon: LucideIcon; empty: MessageKey; body: MessageKey }>;
type Box = keyof typeof BOXES;

function Approvals() {
  const { slug } = useOrg();
  const { t } = useT();
  const router = useRouter();
  const params = useSearchParams();
  const box: Box = (params.get('box') as Box) in BOXES ? (params.get('box') as Box) : 'inbox';
  const { data = [], error, isPending, refetch } = useApprovals(slug, box);
  const b = BOXES[box];

  return (
    <>
      <PageHeader title={t('approvals.title')} subtitle={t('approvals.subtitle')} />
      <Tabs value={box} onValueChange={(v) => router.replace(`/${slug}/approvals${v === 'inbox' ? '' : `?box=${v}`}`)}>
        <TabsList className="mb-4">
          {(Object.keys(BOXES) as Box[]).map((k) => {
            const Icon = BOXES[k].icon;
            return <TabsTrigger key={k} value={k}><Icon className="size-4" />{t(BOXES[k].label)}</TabsTrigger>;
          })}
        </TabsList>
      </Tabs>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton /> : data.length === 0 ? (
        <EmptyState icon={b.icon} title={t(b.empty)} body={t(b.body)} />
      ) : (
        <ul className="divide-y overflow-hidden rounded-lg border bg-card">
          {data.map((a) => (
            <li key={a.id}>
              <Link href={`/${slug}/approvals/${a.id}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-3 hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{a.title}</span>
                  <span className="text-xs text-muted-foreground">{a.requester.name} · {ago(a.createdAt)} · {t('approvals.stepOf', { step: Math.min(a.currentStep, a.steps), steps: a.steps })}</span>
                </span>
                <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground"><RecordKind moduleKey={a.moduleKey} recordType={a.recordType} /></span>
                <ApprovalBadge status={a.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

export default function Page() {
  return <Suspense><Approvals /></Suspense>;
}
