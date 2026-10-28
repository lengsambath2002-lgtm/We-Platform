'use client';

import { Download, Upload } from 'lucide-react';
import { toast } from 'sonner';

import { PersonAvatar } from '@/components/common/person';
import { ErrorState } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Skeleton } from '@/components/ui/skeleton';
import { tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type DocActivity, downloadDocument, useDocument } from '@/lib/api/documents';
import { ago, bytes, day } from '@/lib/format';

import { fileIcon } from './document-table';

const s = (v: unknown) => (typeof v === 'string' ? v : typeof v === 'number' ? String(v) : '');

/** A plain sentence for a document's history entry, in the current language. */
function describe(a: DocActivity): string {
  const d = a.data;
  switch (a.verb) {
    case 'document.uploaded': return tr('documents.actUploaded');
    case 'document.versioned': return tr('documents.actVersioned', { version: s(d.version) });
    case 'document.renamed': return tr('documents.actRenamed', { from: s(d.from), to: s(d.to) });
    case 'document.described': return tr('documents.actDescribed');
    case 'document.moved': return tr('documents.actMoved', { folder: s(d.to) || tr('documents.topLevel') });
    case 'document.deleted': return tr('documents.actDeleted');
    case 'document.restored': return tr('documents.actRestored');
    default: return tr('documents.actOther');
  }
}

/** A file's details: description, every version (each downloadable) and its history. */
export function DocumentSheet({ org, id, onOpenChange, onNewVersion }: {
  org: string; id: string | null; onOpenChange: (v: boolean) => void; onNewVersion: (doc: { id: string; name: string }) => void;
}) {
  const { t } = useT();
  const q = useDocument(org, id);
  const doc = q.data?.document;
  const Icon = fileIcon(doc?.contentType ?? null);
  const get = (version?: number) => downloadDocument(org, id!, version).catch((e) => toast.error(errorMessage(e)));

  return (
    <Sheet open={!!id} onOpenChange={onOpenChange}>
      <SheetContent className="w-full gap-0 overflow-y-auto sm:max-w-md">
        <SheetHeader className="border-b pr-12">
          <SheetTitle className="flex items-start gap-2 break-words"><Icon className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden />{doc?.name ?? t('documents.details')}</SheetTitle>
          <SheetDescription className="break-words">
            {q.data ? [t('documents.allDocuments'), ...q.data.path.map((p) => p.name)].join(' / ') : t('documents.details')}
          </SheetDescription>
        </SheetHeader>
        {q.error ? <ErrorState error={q.error} retry={() => q.refetch()} className="m-4" /> : !q.data || !doc ? (
          <div className="space-y-3 p-4" aria-busy="true" aria-label={t('common.loading')}><Skeleton className="h-16" /><Skeleton className="h-40" /></div>
        ) : (
          <div className="space-y-6 p-4">
            <p className="whitespace-pre-wrap break-words text-sm text-muted-foreground">{doc.description || t('documents.noDescription')}</p>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" onClick={() => get()}><Download className="size-4" />{t('documents.download')}</Button>
              <Button size="sm" variant="outline" onClick={() => onNewVersion(doc)}><Upload className="size-4" />{t('documents.newVersion')}</Button>
            </div>
            <section aria-labelledby="versions-h" className="space-y-2">
              <h3 id="versions-h" className="text-sm font-semibold">{t('documents.versionsTitle')}</h3>
              <ul className="divide-y rounded-lg border">
                {q.data.versions.map((v) => (
                  <li key={v.version} className="flex items-start gap-3 px-3 py-2.5">
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2 text-sm font-medium">
                        {t('documents.versionN', { version: v.version })}
                        {v.current && <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">{t('documents.current')}</span>}
                      </span>
                      <span className="block truncate text-xs text-muted-foreground">{v.fileName} · {bytes(v.size)}</span>
                      <span className="block text-xs text-muted-foreground" title={day(v.createdAt)}>
                        {t('documents.uploadedBy', { name: v.uploadedBy?.name ?? t('documents.someone') })} · {ago(v.createdAt)}
                      </span>
                      {v.note && <span className="mt-1 block whitespace-pre-wrap break-words text-sm">{v.note}</span>}
                    </span>
                    <Button variant="ghost" size="icon" className="size-9 shrink-0" aria-label={t('documents.downloadVersion', { version: v.version })} onClick={() => get(v.version)}>
                      <Download className="size-4" />
                    </Button>
                  </li>
                ))}
              </ul>
            </section>
            <section aria-labelledby="history-h" className="space-y-2">
              <h3 id="history-h" className="text-sm font-semibold">{t('documents.history')}</h3>
              <ol className="space-y-3">
                {q.data.activity.map((a) => (
                  <li key={a.id} className="flex gap-2 text-sm">
                    <PersonAvatar person={a.actor} className="mt-0.5 shrink-0" />
                    <span className="min-w-0 break-words">
                      <span className="font-medium">{a.actor?.name ?? t('documents.someone')}</span> {describe(a)}
                      <span className="block text-xs text-muted-foreground" title={day(a.at)}>{ago(a.at)}</span>
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
