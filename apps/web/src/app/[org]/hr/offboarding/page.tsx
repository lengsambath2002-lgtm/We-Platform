'use client';

import { DoorOpen, ListChecks, Plus, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { DepartureDialog, KIND_LABEL, OffBadge } from '@/components/hr/offboarding-ui';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type TemplateItem, offboardingApi, useOffboardings, useOffTemplate } from '@/lib/api/offboarding';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

function TemplateDialog({ org, onClose }: { org: string; onClose: () => void }) {
  const { t } = useT();
  const { data } = useOffTemplate(org);
  const [items, setItems] = useState<TemplateItem[] | null>(null);
  const list = items ?? data ?? [];
  const save = useOrgMutation(org, (v: TemplateItem[]) => offboardingApi.saveTemplate(org, v));
  const set = (i: number, patch: Partial<TemplateItem>) => setItems(list.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
        <DialogHeader><DialogTitle>{t('offboarding.templateTitle')}</DialogTitle><DialogDescription>{t('offboarding.templateBody')}</DialogDescription></DialogHeader>
        <ul className="space-y-2">{list.map((x, i) => (
          <li key={i} className="flex items-center gap-2">
            <Input aria-label={t('offboarding.step')} maxLength={200} value={x.title} onChange={(e) => set(i, { title: e.target.value })} />
            <Input aria-label={t('offboarding.dueDays')} type="number" className="w-20" min={-90} max={90} value={x.dueDays} onChange={(e) => set(i, { dueDays: Number(e.target.value) })} />
            <Button variant="ghost" size="icon" aria-label={t('common.remove')} onClick={() => setItems(list.filter((_, j) => j !== i))}><Trash2 className="size-4" /></Button>
          </li>
        ))}</ul>
        <p className="text-xs text-muted-foreground">{t('offboarding.dueDaysHint')}</p>
        {list.length < 30 && <Button variant="outline" size="sm" onClick={() => setItems([...list, { title: '', dueDays: 0 }])}><Plus className="size-4" />{t('offboarding.addStep')}</Button>}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={() => save.mutateAsync(list.filter((x) => x.title.trim()).map((x) => ({ title: x.title.trim(), dueDays: Math.max(-90, Math.min(90, x.dueDays || 0)) })))
            .then(() => { toast.success(t('offboarding.templateSaved')); onClose(); }).catch((e) => toast.error(errorMessage(e)))}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Offboarding() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const [open, setOpen] = useState(true);
  const [starting, setStarting] = useState(!!params.get('person'));
  const [editingTemplate, setEditingTemplate] = useState(false);
  const allowed = can('hr.team');
  const { data, error, isPending, refetch } = useOffboardings(slug, open, allowed);
  if (!allowed) return <PermissionDenied />;
  return (
    <>
      <PageHeader title={t('offboarding.title')} subtitle={t('offboarding.subtitle')}
        actions={<>
          {can('hr.manage') && <Button variant="outline" size="sm" onClick={() => setEditingTemplate(true)}><ListChecks className="size-4" />{t('offboarding.templateTitle')}</Button>}
          <Button size="sm" onClick={() => setStarting(true)}><Plus className="size-4" />{t('offboarding.startTitle')}</Button>
        </>} />
      <Tabs value={open ? 'open' : 'closed'} onValueChange={(v) => setOpen(v === 'open')}>
        <TabsList className="mb-4"><TabsTrigger value="open">{t('offboarding.tabOpen')}</TabsTrigger><TabsTrigger value="closed">{t('offboarding.tabClosed')}</TabsTrigger></TabsList>
      </Tabs>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton /> : data.length === 0 ? (
        <EmptyState icon={DoorOpen} title={t(open ? 'offboarding.noneOpen' : 'offboarding.noneClosed')} />
      ) : (
        <ul className="divide-y overflow-hidden rounded-lg border bg-card">
          {data.map((o) => (
            <li key={o.id}>
              <Link href={`/${slug}/hr/offboarding/${o.id}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-3 hover:bg-muted/50">
                <PersonAvatar person={o.person} className="size-8" />
                <span className="min-w-0 flex-1"><span className="block truncate text-sm font-medium">{o.person.name}</span>
                  <span className="text-xs text-muted-foreground">{KIND_LABEL[o.kind]} · {t('offboarding.lastDayOn', { date: day(o.lastDay) })}{o.total > 0 ? ` · ${t('offboarding.progress', { done: o.done, total: o.total })}` : ''}</span></span>
                <OffBadge status={o.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
      {starting && <DepartureDialog org={slug} person={params.get('person') ?? undefined} onClose={() => setStarting(false)} />}
      {editingTemplate && <TemplateDialog org={slug} onClose={() => setEditingTemplate(false)} />}
    </>
  );
}

/** HR: open and closed departures, starting one, and the checklist every departure starts with. */
export default function Page() {
  return <Suspense><Offboarding /></Suspense>;
}
