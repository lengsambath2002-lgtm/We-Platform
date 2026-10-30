'use client';

import { Network } from 'lucide-react';
import Link from 'next/link';

import { PageHeader } from '@/components/common/page-header';
import { Button } from '@/components/ui/button';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { useT } from '@/i18n/provider';
import { type ChartNode, useOrgChart } from '@/lib/api/hr';
import { useOrg } from '@/lib/org-context';

function Branch({ node, byManager, slug, depth }: { node: ChartNode; byManager: Map<string, ChartNode[]>; slug: string; depth: number }) {
  const reports = byManager.get(node.person.id) ?? [];
  return (
    <li>
      <Link href={`/${slug}/hr/people/${node.person.id}`} className="inline-flex max-w-full items-center gap-3 rounded-md border bg-card px-3 py-2 hover:border-primary/40">
        <PersonAvatar person={node.person} className="size-8" />
        <span className="min-w-0">
          <span className="block truncate text-sm font-medium">{node.person.name}</span>
          <span className="block truncate text-xs text-muted-foreground">{[node.title, node.department].filter(Boolean).join(' · ')}</span>
        </span>
      </Link>
      {reports.length > 0 && depth < 20 && (
        <ul className="ml-5 mt-2 space-y-2 border-l pl-4">
          {reports.map((r) => <Branch key={r.person.id} node={r} byManager={byManager} slug={slug} depth={depth + 1} />)}
        </ul>
      )}
    </li>
  );
}

/** Who reports to whom, from the managers set on people's profiles. */
export default function OrgChart() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = useOrgChart(slug);
  if (error) return <ErrorState error={error} retry={() => refetch()} />;
  if (isPending) return <ListSkeleton rows={6} />;
  const byManager = new Map<string, ChartNode[]>();
  data.forEach((n) => { if (n.managerId) byManager.set(n.managerId, [...(byManager.get(n.managerId) ?? []), n]); });
  const top = data.filter((n) => !n.managerId);
  return (
    <>
      <PageHeader title={t('hr.orgChartTitle')} subtitle={t('hr.orgChartSubtitle')}
        actions={can('org.structure.view') && <Button variant="outline" size="sm" asChild><Link href={`/${slug}/structure`}><Network className="size-4" />{t('structure.title')}</Link></Button>} />
      {data.length === 0 ? <EmptyState icon={Network} title={t('hr.nobodyToShow')} /> : (
        <ul className="space-y-3" aria-label={t('hr.orgChartTitle')}>
          {top.map((n) => <Branch key={n.person.id} node={n} byManager={byManager} slug={slug} depth={0} />)}
        </ul>
      )}
    </>
  );
}
