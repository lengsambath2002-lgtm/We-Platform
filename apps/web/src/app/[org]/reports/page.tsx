'use client';

import { FolderKanban } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useMemo } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, PermissionDenied } from '@/components/common/states';
import { ApprovalsReport } from '@/components/reports/approvals';
import { type FilterState, rangeProblem, readFilter, ReportFilters, toReportFilter, writeFilter } from '@/components/reports/filters';
import { OpenWorkReport } from '@/components/reports/open-work';
import { TaskFlowReport } from '@/components/reports/task-flow';
import { WorkloadReport } from '@/components/reports/workload';
import { useT } from '@/i18n/provider';
import { useProjects } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

function Reports() {
  const { t } = useT();
  const { slug, can } = useOrg();
  const router = useRouter();
  const params = useSearchParams();
  const projectsOn = can('projects.view');
  const projects = useProjects(slug);
  const value = useMemo(() => readFilter(new URLSearchParams(params.toString())), [params]);
  // While a custom range is invalid the reports use the default dates (same project and threshold); the inputs say what's wrong.
  const filter = toReportFilter(rangeProblem(value) ? { ...readFilter(new URLSearchParams()), project: value.project, staleDays: value.staleDays } : value);
  const set = (f: FilterState) => {
    const q = writeFilter(f);
    router.replace(`/${slug}/reports${q ? `?${q}` : ''}`, { scroll: false });
  };

  if (!can('reports.view')) {
    return <><PageHeader title={t('reports.title')} /><PermissionDenied /></>;
  }
  return (
    <>
      <PageHeader title={t('reports.title')} subtitle={t('reports.subtitle')} />
      <ReportFilters value={value} onChange={set} projects={projectsOn ? (projects.data ?? []) : undefined} />
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        {projectsOn ? (
          <>
            <div className="min-w-0 xl:col-span-2"><TaskFlowReport org={slug} filter={filter} /></div>
            <OpenWorkReport org={slug} filter={filter} />
            <WorkloadReport org={slug} filter={filter} />
          </>
        ) : (
          <EmptyState className="xl:col-span-2" icon={FolderKanban} title={t('reports.projectsOff')} body={t('reports.projectsOffBody')} />
        )}
        <div className="min-w-0 xl:col-span-2"><ApprovalsReport org={slug} filter={filter} /></div>
      </div>
    </>
  );
}

export default function Page() {
  return <Suspense><Reports /></Suspense>;
}
