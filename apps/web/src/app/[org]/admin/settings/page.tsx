'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { PageHeader } from '@/components/common/page-header';
import { PermissionDenied } from '@/components/common/states';
import { applyServerErrors } from '@/components/forms/server-errors';
import { LeaveOrgDialog } from '@/components/org/leave-org-dialog';
import {
  AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { accountApi, useLeaveOrDeleteOrg } from '@/lib/api/account';
import { errorMessage } from '@/lib/api/client';
import { keys, useMembers } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';

const makeSchema = (t: ReturnType<typeof useT>['t']) => z.object({ name: z.string().trim().min(2, t('settings.nameMin')).max(120) });
type Values = z.infer<ReturnType<typeof makeSchema>>;

function DeleteOrgDialog({ org, open, onOpenChange }: { org: { slug: string; name: string }; open: boolean; onOpenChange: (v: boolean) => void }) {
  const { t } = useT();
  const router = useRouter();
  const del = useLeaveOrDeleteOrg();
  const [typed, setTyped] = useState('');
  const [error, setError] = useState<string | null>(null);
  const matches = typed.trim() === org.name.trim();
  const close = (v: boolean) => { if (!v) { setTyped(''); setError(null); } onOpenChange(v); };
  return (
    <AlertDialog open={open} onOpenChange={close}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('settings.deleteTitle', { org: org.name })}</AlertDialogTitle>
          <AlertDialogDescription>{t('settings.deleteBody', { org: org.name })}</AlertDialogDescription>
        </AlertDialogHeader>
        <form id="delete-org" className="space-y-2" onSubmit={async (e) => {
          e.preventDefault();
          if (!matches) { setError(t('settings.deleteNameMismatch')); return; }
          setError(null);
          try {
            await del.mutateAsync({ org: org.slug, action: 'delete', name: typed.trim() });
            toast.success(t('settings.deleted', { org: org.name }));
            router.replace('/');
          } catch (err) { setError(errorMessage(err)); }
        }}>
          <Label htmlFor="delete-confirm">{t('settings.deleteConfirmLabel', { name: org.name })}</Label>
          <Input id="delete-confirm" autoComplete="off" value={typed} onChange={(e) => setTyped(e.target.value)} aria-invalid={!!error} />
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        </form>
        <AlertDialogFooter>
          <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
          <Button type="submit" form="delete-org" variant="destructive" disabled={!matches || del.isPending}>
            {t(del.isPending ? 'settings.deleting' : 'settings.deleteButton')}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

/** Admins: rename; everyone here: leave; owners: delete (name typed to confirm). */
export default function OrgSettings() {
  const { slug, org, can } = useOrg();
  const { t } = useT();
  const qc = useQueryClient();
  const router = useRouter();
  const schema = useMemo(() => makeSchema(t), [t]);
  const form = useForm<Values>({ resolver: zodResolver(schema), values: { name: org.name } });
  const [error, setError] = useState<string | null>(null);
  const [leaving, setLeaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const isOwner = org.role === 'OWNER';
  // To tell the last owner up front that they can't leave (the API refuses it anyway).
  const members = useMembers(slug);
  const [host, setHost] = useState('');
  useEffect(() => setHost(window.location.host), []);
  const lastOwner = isOwner && (members.data?.filter((m) => m.role === 'OWNER' && m.status === 'ACTIVE').length ?? 2) <= 1;
  if (!can('org.settings.manage')) return <PermissionDenied />;

  return (
    <>
      <PageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} />
      <div className="max-w-2xl space-y-4">
        <section className="rounded-lg border bg-card p-4 sm:p-6" aria-labelledby="general-title">
          <h2 id="general-title" className="mb-4 text-base font-semibold">{t('settings.general')}</h2>
          <Form {...form}>
            <form noValidate className="space-y-5" onSubmit={form.handleSubmit(async (v) => {
              setError(null);
              try {
                await accountApi.renameOrg(slug, v.name);
                await Promise.all([qc.invalidateQueries({ queryKey: ['org', slug] }), qc.invalidateQueries({ queryKey: keys.orgs })]);
                toast.success(t('settings.renamed'));
              } catch (e) { setError(applyServerErrors(form, e)); }
            })}>
              {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
              <FormField control={form.control} name="name" render={({ field }) => (
                <FormItem><FormLabel>{t('settings.orgName')}</FormLabel><FormControl><Input autoComplete="organization" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormItem>
                <FormLabel htmlFor="org-slug">{t('settings.webAddress')}</FormLabel>
                <div className="flex items-center rounded-md border bg-muted/50 text-sm">
                  <span className="shrink-0 py-2 pl-3 text-muted-foreground">{host}/</span>
                  <Input id="org-slug" value={slug} readOnly className="min-w-0 flex-1 border-0 bg-transparent pl-0 shadow-none focus-visible:ring-0" />
                </div>
                <FormDescription>{t('settings.webAddressHint')}</FormDescription>
              </FormItem>
              <Button type="submit" disabled={!form.formState.isDirty || form.formState.isSubmitting}>{t(form.formState.isSubmitting ? 'common.saving' : 'common.save')}</Button>
            </form>
          </Form>
        </section>

        <section className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card p-4 sm:p-6" aria-labelledby="leave-title">
          <div className="min-w-0 flex-1">
            <h2 id="leave-title" className="text-base font-semibold">{t('settings.leaveSection')}</h2>
            <p className="text-sm text-muted-foreground">{lastOwner ? t('settings.lastOwnerHint') : t('settings.leaveSectionBody', { org: org.name })}</p>
          </div>
          <Button variant="outline" disabled={lastOwner} onClick={() => setLeaving(true)}>{t('settings.leave')}</Button>
        </section>

        <section className="rounded-lg border border-destructive/40 bg-card p-4 sm:p-6" aria-labelledby="delete-title">
          <h2 id="delete-title" className="text-base font-semibold text-destructive">{t('settings.deleteSection')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('settings.deleteBody', { org: org.name })}</p>
          {isOwner
            ? <Button variant="destructive" className="mt-4" onClick={() => setDeleting(true)}>{t('settings.deleteButton')}</Button>
            : <p className="mt-3 text-sm font-medium">{t('settings.ownersOnlyDelete')}</p>}
        </section>
      </div>
      <LeaveOrgDialog org={{ slug, name: org.name }} open={leaving} onOpenChange={setLeaving} onLeft={() => router.replace('/')} />
      {isOwner && <DeleteOrgDialog org={{ slug, name: org.name }} open={deleting} onOpenChange={setDeleting} />}
    </>
  );
}
