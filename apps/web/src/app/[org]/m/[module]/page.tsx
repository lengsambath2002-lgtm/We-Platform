'use client';

import { Blocks, Construction } from 'lucide-react';
import Link from 'next/link';
import { notFound, redirect, useParams } from 'next/navigation';

import { MODULE_ICON } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import { useOrg } from '@/lib/org-context';

const PLANNED: Record<string, string[]> = {
  business: ['Customers and contacts', 'Quotes, sales orders and invoices', 'Purchasing and suppliers', 'Expenses and simple finance reports'],
  education: ['Students and guardians', 'Classes, timetables and attendance', 'Assessments and report cards', 'Fees and payments'],
  retail: ['Online shop and point of sale (VirtualBiz)', 'Products, stock and suppliers', 'Orders, returns and receipts'],
  hr: ['Employee records', 'Leave requests (using the approval engine)', 'Attendance and payroll inputs'],
  service: ['Bookings and schedules', 'Work orders and field visits', 'Assets and maintenance'],
};

/** Honest page for an industry module that isn't built yet: what it will do, nothing fake. */
export default function ModulePage() {
  const { module } = useParams<{ module: string }>();
  const { slug, modules, can } = useOrg();
  const m = modules.find((x) => x.key === module);
  if (!m) notFound();
  if (m.key === 'projects') redirect(`/${slug}/projects`);
  const Icon = MODULE_ICON[m.key] ?? Blocks;

  return (
    <div className="mx-auto max-w-2xl py-6">
      <div className="flex items-center gap-3">
        <span className="grid size-12 place-items-center rounded-lg bg-primary/10 text-primary"><Icon className="size-6" /></span>
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{m.nameEn}</h1>
          <p className="text-sm text-muted-foreground" lang="km">{m.nameKm}</p>
        </div>
      </div>
      <div className="mt-6 rounded-lg border bg-card p-5">
        <p className="flex items-center gap-2 font-medium"><Construction className="size-4 text-warning" />Not available yet</p>
        <p className="mt-1 text-sm text-muted-foreground">This module is planned. It will use the shared core you already have — people, roles, approvals, calendar and notifications. Planned first:</p>
        <ul className="mt-3 list-disc space-y-1 pl-5 text-sm">{(PLANNED[m.key] ?? []).map((x) => <li key={x}>{x}</li>)}</ul>
      </div>
      <div className="mt-4 flex gap-2">
        <Button asChild variant="outline"><Link href={`/${slug}/overview`}>Back to overview</Link></Button>
        {can('org.modules.manage') && <Button asChild variant="ghost"><Link href={`/${slug}/admin/modules`}>Manage modules</Link></Button>}
      </div>
    </div>
  );
}

