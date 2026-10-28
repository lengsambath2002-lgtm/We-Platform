'use client';

import { ChevronRight, FolderPlus, Home, Search, Trash2, Upload, X } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';

import { ConfirmDelete, MoveDialog, NameDialog, NewVersionDialog } from '@/components/documents/dialogs';
import { DocumentSheet } from '@/components/documents/document-sheet';
import { type DocAction, DocumentTable } from '@/components/documents/document-table';
import { TrashList } from '@/components/documents/trash-list';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useT } from '@/i18n/provider';
import { ApiError, errorMessage } from '@/lib/api/client';
import { docApi, downloadDocument, type FolderRef, MAX_UPLOAD, useDocSearch, useFolderContents } from '@/lib/api/documents';
import { useOrgMutation } from '@/lib/api/queries';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

function Documents() {
  const { t } = useT();
  const { slug, org, can } = useOrg();
  const router = useRouter();
  const params = useSearchParams();
  const folderId = params.get('folder');
  const q = params.get('q') ?? '';
  const trash = params.get('view') === 'trash';
  const allowed = can('documents.view');

  const go = (next: { folder?: string | null; q?: string; trash?: boolean }) => {
    const sp = new URLSearchParams();
    if (next.folder) sp.set('folder', next.folder);
    if (next.q) sp.set('q', next.q);
    if (next.trash) sp.set('view', 'trash');
    const s = sp.toString();
    router.push(`/${slug}/documents${s ? `?${s}` : ''}`);
  };

  // The search box updates the URL after a short pause, so results are linkable and Back works.
  const [term, setTerm] = useState(q);
  useEffect(() => setTerm(q), [q]);
  useEffect(() => {
    if (term.trim() === q) return;
    const id = setTimeout(() => {
      const sp = new URLSearchParams();
      if (term.trim()) sp.set('q', term.trim());
      else if (folderId) sp.set('folder', folderId);
      router.replace(`/${slug}/documents${sp.size ? `?${sp}` : ''}`);
    }, 300);
    return () => clearTimeout(id);
  }, [term, q, folderId, slug, router]);

  const contents = useFolderContents(slug, folderId, allowed && !q && !trash);
  const search = useDocSearch(slug, allowed && !trash ? q : '');

  // Uploads: several files one after another, from the button or dropped anywhere on the page.
  const input = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<{ done: number; count: number } | null>(null);
  const [drag, setDrag] = useState(false);
  const uploadOne = useOrgMutation(slug, (f: File) => docApi.upload(slug, f, folderId));
  const canUpload = allowed && can('documents.upload') && !q && !trash && !!contents.data;
  const upload = async (list: FileList | null) => {
    const files = Array.from(list ?? []);
    if (!files.length) return;
    let ok = 0;
    for (const [i, f] of files.entries()) {
      setProgress({ done: i + 1, count: files.length });
      if (f.size > MAX_UPLOAD) { toast.error(t('documents.tooLarge', { name: f.name })); continue; }
      try { await uploadOne.mutateAsync(f); ok++; } catch (e) { toast.error(`${f.name}: ${errorMessage(e)}`); }
    }
    setProgress(null);
    if (ok) toast.success(t('documents.uploaded', { count: ok }));
  };

  // Dialogs and the details sheet.
  const [action, setAction] = useState<DocAction | null>(null);
  const [newFolder, setNewFolder] = useState(false);
  const [details, setDetails] = useState<string | null>(null);
  const [versionOf, setVersionOf] = useState<{ id: string; name: string } | null>(null);
  const close = (v: boolean) => { if (!v) setAction(null); };
  const mutate = useOrgMutation(slug, (fn: () => Promise<unknown>) => fn());
  const onAction = (a: DocAction) => {
    if (a.kind === 'details') setDetails(a.doc.id);
    else if (a.kind === 'version') setVersionOf(a.doc);
    else if (a.kind === 'download') downloadDocument(slug, a.doc.id).catch((e) => toast.error(errorMessage(e)));
    else setAction(a);
  };

  const here = contents.data?.folder?.name ?? t('documents.allDocuments');
  const path: FolderRef[] = contents.data?.path ?? [];

  if (!allowed) {
    return (<><PageHeader title={t('documents.title')} /><PermissionDenied /></>);
  }

  const body = () => {
    if (trash) return <TrashList org={slug} />;
    if (q) {
      if (search.error) return <ErrorState error={search.error} retry={() => search.refetch()} />;
      if (search.isPending) return <ListSkeleton />;
      if (search.data.length === 0) return <EmptyState icon={Search} title={t('documents.noMatchTitle', { q })} body={t('documents.noMatchBody')} />;
      return <DocumentTable folders={[]} documents={search.data.map((h) => h.document)}
        paths={Object.fromEntries(search.data.map((h) => [h.document.id, h.path]))} onOpenFolder={(id) => go({ folder: id })} onAction={onAction} />;
    }
    if (contents.error) {
      const gone = contents.error instanceof ApiError && contents.error.status === 404;
      return gone ? (
        <EmptyState icon={Search} title={t('common.notFoundTitle')} body={t('documents.folderGone')}
          action={<Button size="sm" variant="outline" onClick={() => go({})}>{t('documents.backToLibrary')}</Button>} />
      ) : <ErrorState error={contents.error} retry={() => contents.refetch()} />;
    }
    if (contents.isPending) return <ListSkeleton />;
    const { folders, documents } = contents.data;
    if (folders.length === 0 && documents.length === 0) {
      return (
        <EmptyState icon={folderId ? FolderPlus : Upload} title={t(folderId ? 'documents.folderEmptyTitle' : 'documents.emptyTitle')}
          body={t(folderId ? 'documents.folderEmptyBody' : 'documents.emptyBody')}
          action={canUpload ? <Button size="sm" onClick={() => input.current?.click()}><Upload className="size-4" />{t('documents.upload')}</Button> : undefined} />
      );
    }
    return <DocumentTable folders={folders} documents={documents} onOpenFolder={(id) => go({ folder: id })} onAction={onAction} />;
  };

  return (
    <div className="relative min-w-0"
      onDragOver={(e) => { if (canUpload && e.dataTransfer.types.includes('Files')) { e.preventDefault(); setDrag(true); } }}
      onDragLeave={(e) => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDrag(false); }}
      onDrop={(e) => { if (!canUpload) return; e.preventDefault(); setDrag(false); void upload(e.dataTransfer.files); }}>
      <PageHeader title={t('documents.title')} subtitle={t('documents.subtitle', { org: org.name })}
        actions={!trash && (
          <>
            {canUpload && <Button size="sm" variant="outline" onClick={() => setNewFolder(true)}><FolderPlus className="size-4" />{t('documents.newFolder')}</Button>}
            {canUpload && (
              <Button size="sm" disabled={!!progress} onClick={() => input.current?.click()}>
                <Upload className="size-4" />{progress ? t('documents.uploading', progress) : t('documents.upload')}
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={() => go({ trash: true })}><Trash2 className="size-4" />{t('documents.trash')}</Button>
          </>
        )} />
      <input ref={input} type="file" multiple hidden onChange={(e) => { void upload(e.target.files); e.target.value = ''; }} />

      {trash ? (
        <div className="mb-4 flex items-center gap-2">
          <Button size="sm" variant="ghost" className="-ml-2" asChild><Link href={`/${slug}/documents`}><Home className="size-4" />{t('documents.backToLibrary')}</Link></Button>
          <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
          <span className="text-sm font-medium">{t('documents.trash')}</span>
        </div>
      ) : (
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <nav aria-label={t('documents.breadcrumb')} className="min-w-0">
            {q ? <p className="text-sm text-muted-foreground">{t('documents.search')}</p> : (
              <ol className="flex flex-wrap items-center gap-1 text-sm">
                <li>
                  {folderId ? <Link href={`/${slug}/documents`} className="flex items-center gap-1.5 rounded px-1 py-0.5 text-muted-foreground hover:text-foreground hover:underline"><Home className="size-4" aria-hidden />{t('documents.allDocuments')}</Link>
                    : <span aria-current="page" className="flex items-center gap-1.5 px-1 py-0.5 font-medium"><Home className="size-4" aria-hidden />{t('documents.allDocuments')}</span>}
                </li>
                {path.map((p, i) => (
                  <li key={p.id} className="flex min-w-0 items-center gap-1">
                    <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                    {i === path.length - 1 ? <span aria-current="page" className="max-w-[16rem] truncate px-1 py-0.5 font-medium">{p.name}</span>
                      : <Link href={`/${slug}/documents?folder=${p.id}`} className="max-w-[12rem] truncate rounded px-1 py-0.5 text-muted-foreground hover:text-foreground hover:underline">{p.name}</Link>}
                  </li>
                ))}
              </ol>
            )}
          </nav>
          <div className="relative w-full sm:w-72">
            <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
            <Input type="search" value={term} onChange={(e) => setTerm(e.target.value)} placeholder={t('documents.search')} aria-label={t('documents.search')} className="pl-8 pr-8" />
            {term && (
              <button type="button" className="absolute right-1.5 top-1.5 grid size-6 place-items-center rounded text-muted-foreground hover:text-foreground" aria-label={t('common.clear')} onClick={() => setTerm('')}>
                <X className="size-4" />
              </button>
            )}
          </div>
        </div>
      )}

      {body()}
      {canUpload && !q && <p className="mt-3 text-xs text-muted-foreground">{t('documents.fileTypes')}</p>}

      {drag && (
        <div className={cn('pointer-events-none absolute inset-0 z-20 grid place-items-center rounded-lg border-2 border-dashed border-primary bg-primary/5')}>
          <p className="rounded-md bg-background px-4 py-2 text-sm font-medium shadow-sm">{t('documents.dropHere', { folder: here })}</p>
        </div>
      )}

      <NameDialog open={newFolder} onOpenChange={setNewFolder} title={t('documents.newFolder')} body={t('documents.newFolderIn', { folder: here })}
        label={t('documents.folderName')} initial="" submitLabel={t('common.create')}
        save={async (v) => { await mutate.mutateAsync(() => docApi.createFolder(slug, v.name, folderId)); toast.success(t('documents.folderCreated')); }} />
      {action?.kind === 'rename-folder' && (
        <NameDialog open onOpenChange={close} title={t('documents.renameFolder')} label={t('documents.folderName')} initial={action.folder.name}
          submitLabel={t('common.save')}
          save={async (v) => { await mutate.mutateAsync(() => docApi.renameFolder(slug, action.folder.id, v.name)); toast.success(t('documents.renamed')); }} />
      )}
      {action?.kind === 'edit' && (
        <NameDialog open onOpenChange={close} title={t('documents.editDocument')} label={t('documents.docName')} initial={action.doc.name}
          description={action.doc.description} max={255} submitLabel={t('common.save')}
          save={async (v) => { await mutate.mutateAsync(() => docApi.update(slug, action.doc.id, v)); toast.success(t('documents.saved')); }} />
      )}
      {action && 'doc' in action && action.kind === 'move' && (
        <MoveDialog org={slug} open onOpenChange={close} name={action.doc.name} currentFolderId={action.doc.folderId}
          move={(to) => mutate.mutateAsync(() => docApi.move(slug, action.doc.id, to))} />
      )}
      {action && 'folder' in action && action.kind === 'move-folder' && (
        <MoveDialog org={slug} open onOpenChange={close} name={action.folder.name} currentFolderId={action.folder.parentId}
          movingFolderId={action.folder.id} move={(to) => mutate.mutateAsync(() => docApi.moveFolder(slug, action.folder.id, to))} />
      )}
      {action?.kind === 'delete-folder' && (
        <ConfirmDelete open onOpenChange={close} title={t('documents.deleteFolderTitle', { name: action.folder.name })} body={t('documents.deleteFolderBody')}
          confirm={() => mutate.mutateAsync(() => docApi.deleteFolder(slug, action.folder.id))} />
      )}
      {action?.kind === 'delete' && (
        <ConfirmDelete open onOpenChange={close} title={t('documents.deleteFileTitle', { name: action.doc.name })} body={t('documents.deleteFileBody')}
          confirm={() => mutate.mutateAsync(() => docApi.remove(slug, action.doc.id))} />
      )}
      <NewVersionDialog org={slug} doc={versionOf} open={!!versionOf} onOpenChange={(v) => { if (!v) setVersionOf(null); }} />
      <DocumentSheet org={slug} id={details} onOpenChange={(v) => { if (!v) setDetails(null); }} onNewVersion={(d) => setVersionOf(d)} />
    </div>
  );
}

export default function Page() {
  return <Suspense><Documents /></Suspense>;
}
