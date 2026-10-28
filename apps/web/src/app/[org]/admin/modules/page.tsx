'use client';

import { Blocks } from 'lucide-react';
import Link from 'next/link';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PermissionDenied } from '@/components/common/states';
import { MODULE_ICON } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

const ABOUT: Record<string, string> = {
  projects: 'Projects, tasks with a board and list, subtasks, comments, attachments and approvals.',
  business: 'Customers, sales, purchasing and finance for any company.',
  education: 'Students, classes, attendance, grades and fees.',
  retail: 'Shop, point of sale, stock and orders (VirtualBiz joins here).',
  hr: 'Employees, leave, attendance and payroll.',
  service: 'Bookings, work orders, field service and assets.',
};

export default function ModulesAdmin() {
  const { slug, can, modules } = useOrg();
  const toggle = useOrgMutation(slug, ({ key, on }: { key: string; on: boolean }) => orgApi.setModule(slug, key, on));
  if (!can('org.modules.manage')) return <PermissionDenied />;

  return (
    <>
      <PageHeader title="Modules" subtitle="Switch on what this organisation uses. Switching off hides a module; its data is kept." />
      <ul className="grid gap-3 md:grid-cols-2">
        {modules.map((m) => {
          const Icon = MODULE_ICON[m.key] ?? Blocks;
          return (
            <li key={m.key} className="flex gap-4 rounded-lg border bg-card p-4">
              <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary"><Icon className="size-5" /></span>
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 font-medium">
                  {m.nameEn} <span className="text-sm font-normal text-muted-foreground" lang="km">{m.nameKm}</span>
                  {!m.available && <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] font-normal text-muted-foreground">Coming soon</span>}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">{ABOUT[m.key] ?? ''}</p>
                <div className="mt-3 flex items-center gap-2">
                  {m.available ? (
                    <Button size="sm" variant={m.enabled ? 'outline' : 'default'} disabled={toggle.isPending}
                      onClick={() => toggle.mutateAsync({ key: m.key, on: !m.enabled }).then(() => toast.success(`${m.nameEn} ${m.enabled ? 'switched off' : 'switched on'}`)).catch((e) => toast.error(errorMessage(e)))}>
                      {m.enabled ? 'Switch off' : 'Switch on'}
                    </Button>
                  ) : <Button size="sm" variant="outline" asChild><Link href={`/${slug}/m/${m.key}`}>What&apos;s planned</Link></Button>}
                  {m.enabled && <span className="text-xs text-success">On</span>}
                </div>
              </div>
            </li>
          );
        })}
      </ul>
    </>
  );
}
