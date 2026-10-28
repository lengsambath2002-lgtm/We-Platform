'use client';

import { Folder, RotateCcw, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { docApi, type TrashItem, useTrash } from '@/lib/api/documents';
import { useOrgMutation } from '@/lib/api/queries';
import { ago, bytes, day } from '@/lib/format';

import { fileIcon } from './document-table';

/** What the caller deleted or added and someone deleted (admins: everything), with Restore. */
export function TrashList({ org }: { org: string }) {
  const { t } = useT();
  const trash = useTrash(org);
  const restore = useOrgMutation(org, (i: TrashItem) => (i.kind === 'FOLDER' ? docApi.restoreFolder(org, i.id) : docApi.restore(org, i.id)));

  if (trash.error) return <ErrorState error={trash.error} retry={() => trash.refetch()} />;
  if (trash.isPending) return <ListSkeleton />;
  if (trash.data.length === 0) return <EmptyState icon={Trash2} title={t('documents.trashEmptyTitle')} body={t('documents.trashEmptyBody')} />;
  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">{t('documents.trashHint')}</p>
      <ul className="divide-y rounded-lg border bg-card">
        {trash.data.map((i) => {
          const Icon = i.kind === 'FOLDER' ? Folder : fileIcon(i.contentType);
          return (
            <li key={i.id} className="flex items-center gap-3 px-3 py-2.5">
              <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{i.name}</span>
                <span className="block truncate text-xs text-muted-foreground" title={day(i.deletedAt)}>
                  {i.kind === 'FOLDER' ? t('documents.folderKind') : bytes(i.size ?? 0)} · {t('documents.deletedBy', { when: ago(i.deletedAt), name: i.deletedBy?.name ?? t('documents.someone') })}
                </span>
              </span>
              <Button variant="outline" size="sm" disabled={restore.isPending} onClick={() => restore.mutateAsync(i)
                .then(() => toast.success(t('documents.restored'))).catch((e) => toast.error(errorMessage(e)))}>
                <RotateCcw className="size-4" /><span className="hidden sm:inline">{t('documents.restore')}</span>
                <span className="sr-only sm:hidden">{t('documents.restore')}</span>
              </Button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
