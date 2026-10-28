'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Archive, ArchiveRestore, Trash2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { collabApi } from '@/lib/api/collab';
import { keys, orgApi, useOrgMutation } from '@/lib/api/queries';
import type { Project } from '@/lib/api/types';

/** Archived = read-only; a lead or admin may restore it. */
export function ArchivedBanner({ org, project }: { org: string; project: Project }) {
  const { t } = useT();
  const restore = useOrgMutation(org, () => orgApi.updateProject(org, project.key, { status: 'ACTIVE' }));
  if (project.status !== 'ARCHIVED') return null;
  return (
    <div role="status" className="mb-4 flex flex-wrap items-center gap-3 rounded-md border bg-muted px-3 py-2 text-sm text-muted-foreground">
      <Archive className="size-4 shrink-0" />
      <span className="min-w-0 flex-1">{t('collab.archivedBanner')}</span>
      {project.canManage && (
        <Button size="sm" variant="outline" disabled={restore.isPending}
          onClick={() => restore.mutateAsync(undefined).then(() => toast.success(t('collab.restored'))).catch((e) => toast.error(errorMessage(e)))}>
          <ArchiveRestore className="size-4" />{t('collab.restore')}
        </Button>
      )}
    </div>
  );
}

/** Project settings: archive / restore, and delete for good after typing the project key. */
export function ProjectDangerZone({ org, project }: { org: string; project: Project }) {
  const { t } = useT();
  const router = useRouter();
  const qc = useQueryClient();
  const [confirm, setConfirm] = useState<'archive' | 'delete' | null>(null);
  const [typed, setTyped] = useState('');
  const archived = project.status === 'ARCHIVED';
  const setStatus = useOrgMutation(org, (status: string) => orgApi.updateProject(org, project.key, { status }));
  const remove = useMutation({
    mutationFn: () => collabApi.deleteProject(org, project.key),
    onSuccess: () => {
      router.replace(`/${org}/projects`);
      qc.removeQueries({ queryKey: keys.project(org, project.key) });
      void qc.invalidateQueries({ queryKey: ['org', org] });
    },
  });
  const tasks = Object.values(project.taskCounts).reduce((a, b) => a + (b ?? 0), 0);

  if (!project.canManage) {
    return <p className="max-w-[720px] rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">{t('collab.onlyLeadDanger')}</p>;
  }
  const close = () => { setConfirm(null); setTyped(''); };
  return (
    <section aria-labelledby="danger-h" className="max-w-[720px] rounded-lg border border-destructive/40 bg-card">
      <h2 id="danger-h" className="border-b px-4 py-3 text-sm font-semibold sm:px-6">{t('collab.dangerTitle')}</h2>
      <div className="flex flex-col gap-3 border-b px-4 py-4 sm:flex-row sm:items-center sm:px-6">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium">{t(archived ? 'collab.restoreTitle' : 'collab.archiveTitle')}</p>
          <p className="text-sm text-muted-foreground">{t(archived ? 'collab.restoreBody' : 'collab.archiveBody')}</p>
        </div>
        {archived ? (
          <Button variant="outline" disabled={setStatus.isPending}
            onClick={() => setStatus.mutateAsync('ACTIVE').then(() => toast.success(t('collab.restored'))).catch((e) => toast.error(errorMessage(e)))}>
            <ArchiveRestore className="size-4" />{t('collab.restore')}
          </Button>
        ) : (
          <Button variant="outline" onClick={() => setConfirm('archive')}><Archive className="size-4" />{t('collab.archiveTitle')}</Button>
        )}
      </div>
      <div className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:px-6">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium">{t('collab.deleteTitle')}</p>
          <p className="text-sm text-muted-foreground">{t('collab.deleteBody')}</p>
        </div>
        <Button variant="outline" className="border-destructive/50 text-destructive hover:bg-destructive/10 hover:text-destructive" onClick={() => setConfirm('delete')}>
          <Trash2 className="size-4" />{t('collab.deleteTitle')}
        </Button>
      </div>

      <AlertDialog open={confirm === 'archive'} onOpenChange={(v) => !v && close()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('collab.archiveConfirmTitle', { name: project.name })}</AlertDialogTitle>
            <AlertDialogDescription>{t('collab.archiveConfirmBody')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={() => setStatus.mutateAsync('ARCHIVED').then(() => toast.success(t('collab.archived'))).catch((e) => toast.error(errorMessage(e)))}>
              {t('collab.archiveTitle')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={confirm === 'delete'} onOpenChange={(v) => !v && close()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('collab.deleteConfirmTitle', { name: project.name })}</AlertDialogTitle>
            <AlertDialogDescription>{t('collab.deleteConfirmBody', { count: tasks })}</AlertDialogDescription>
          </AlertDialogHeader>
          <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); }}>
            <Label htmlFor="confirm-key">{t('collab.typeKey', { key: project.key })}</Label>
            <Input id="confirm-key" value={typed} onChange={(e) => setTyped(e.target.value.toUpperCase())} autoComplete="off" className="font-mono" />
          </form>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <Button className="bg-destructive text-white hover:bg-destructive/90" disabled={typed !== project.key || remove.isPending}
              onClick={() => remove.mutateAsync().then(() => toast.success(t('collab.deleted', { key: project.key }))).catch((e) => toast.error(errorMessage(e)))}>
              {remove.isPending ? t('collab.deleting') : t('collab.deleteTitle')}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}
