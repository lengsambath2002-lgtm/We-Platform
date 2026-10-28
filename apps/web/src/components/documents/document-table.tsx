'use client';

import {
  Download, File, FileArchive, FileImage, FileSpreadsheet, FileText, Folder, FolderInput, History, MoreHorizontal, Pencil,
  Trash2, Upload, type LucideIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';

import { PersonAvatar } from '@/components/common/person';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import type { DocFile, DocFolder, FolderRef } from '@/lib/api/documents';
import { ago, bytes, day } from '@/lib/format';

export type DocAction =
  | { kind: 'rename-folder' | 'move-folder' | 'delete-folder'; folder: DocFolder }
  | { kind: 'download' | 'edit' | 'move' | 'version' | 'details' | 'delete'; doc: DocFile };

/** An icon for a content type (no thumbnails: files are only served to signed-in members). */
export function fileIcon(type: string | null): LucideIcon {
  if (!type) return File;
  if (type.startsWith('image/')) return FileImage;
  if (type.includes('spreadsheet') || type.includes('excel') || type === 'text/csv') return FileSpreadsheet;
  if (type === 'application/zip') return FileArchive;
  if (type === 'application/pdf' || type.startsWith('text/') || type.includes('word') || type.includes('presentation')) return FileText;
  return File;
}

/**
 * Folders first, then files. Phones show the name with size and date under it; wider screens add
 * columns. Every row action sits in the "…" menu; what the server would refuse isn't offered.
 */
export function DocumentTable({ folders, documents, paths, onOpenFolder, onAction }: {
  folders: DocFolder[];
  documents: DocFile[];
  /** Search results: where each file is. */
  paths?: Record<string, FolderRef[]>;
  onOpenFolder: (id: string) => void;
  onAction: (a: DocAction) => void;
}) {
  const { t } = useT();
  return (
    <div className="overflow-hidden rounded-lg border bg-card">
      <Table className="table-fixed">
        <TableHeader>
          <TableRow>
            <TableHead>{t('documents.colName')}</TableHead>
            <TableHead className="hidden w-24 text-right sm:table-cell">{t('documents.colSize')}</TableHead>
            <TableHead className="hidden w-36 md:table-cell">{t('documents.colUpdated')}</TableHead>
            <TableHead className="hidden w-44 lg:table-cell">{t('documents.colBy')}</TableHead>
            <TableHead className="w-12"><span className="sr-only">{t('common.edit')}</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {folders.map((f) => (
            <TableRow key={f.id} className="cursor-pointer" onClick={() => onOpenFolder(f.id)}>
              <TableCell>
                <button type="button" className="flex w-full min-w-0 items-center gap-3 text-left" aria-label={t('documents.openFolder', { name: f.name })}
                  onClick={(e) => { e.stopPropagation(); onOpenFolder(f.id); }}>
                  <Folder className="size-5 shrink-0 fill-primary/15 text-primary" aria-hidden />
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{f.name}</span>
                    <span className="block truncate text-xs text-muted-foreground sm:hidden">{t('documents.items', { count: f.items })} · {ago(f.updatedAt)}</span>
                  </span>
                </button>
              </TableCell>
              <TableCell className="hidden text-right text-xs tabular text-muted-foreground sm:table-cell">{t('documents.items', { count: f.items })}</TableCell>
              <TableCell className="hidden text-sm text-muted-foreground md:table-cell" title={day(f.updatedAt)}>{ago(f.updatedAt)}</TableCell>
              <TableCell className="hidden lg:table-cell"><By person={f.createdBy} /></TableCell>
              <TableCell onClick={(e) => e.stopPropagation()}>
                {f.canManage && (
                  <RowMenu name={f.name}>
                    <DropdownMenuItem onSelect={() => onAction({ kind: 'rename-folder', folder: f })}><Pencil className="size-4" />{t('documents.rename')}</DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => onAction({ kind: 'move-folder', folder: f })}><FolderInput className="size-4" />{t('documents.moveTo')}</DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => onAction({ kind: 'delete-folder', folder: f })}><Trash2 className="size-4" />{t('documents.delete')}</DropdownMenuItem>
                  </RowMenu>
                )}
              </TableCell>
            </TableRow>
          ))}
          {documents.map((d) => {
            const Icon = fileIcon(d.contentType);
            const path = paths?.[d.id];
            return (
              <TableRow key={d.id} className="cursor-pointer" onClick={() => onAction({ kind: 'details', doc: d })}>
                <TableCell>
                  <button type="button" className="flex w-full min-w-0 items-center gap-3 text-left" onClick={(e) => { e.stopPropagation(); onAction({ kind: 'details', doc: d }); }}>
                    <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden />
                    <span className="min-w-0">
                      <span className="flex min-w-0 items-center gap-2">
                        <span className="truncate font-medium">{d.name}</span>
                        {d.version > 1 && <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">{t('documents.versionBadge', { version: d.version })}</span>}
                      </span>
                      {path && <span className="block truncate text-xs text-muted-foreground">{t('documents.location', { path: path.length ? path.map((p) => p.name).join(' / ') : t('documents.topLevel') })}</span>}
                      <span className="block truncate text-xs text-muted-foreground sm:hidden">{bytes(d.size)} · {ago(d.updatedAt)}</span>
                    </span>
                  </button>
                </TableCell>
                <TableCell className="hidden text-right text-xs tabular text-muted-foreground sm:table-cell">{bytes(d.size)}</TableCell>
                <TableCell className="hidden text-sm text-muted-foreground md:table-cell" title={day(d.updatedAt)}>{ago(d.updatedAt)}</TableCell>
                <TableCell className="hidden lg:table-cell"><By person={d.updatedBy ?? d.uploadedBy} /></TableCell>
                <TableCell onClick={(e) => e.stopPropagation()}>
                  <RowMenu name={d.name}>
                    <DropdownMenuItem onSelect={() => onAction({ kind: 'download', doc: d })}><Download className="size-4" />{t('documents.download')}</DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => onAction({ kind: 'details', doc: d })}><History className="size-4" />{t('documents.versions')}</DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => onAction({ kind: 'version', doc: d })}><Upload className="size-4" />{t('documents.newVersion')}</DropdownMenuItem>
                    {d.canManage && (
                      <>
                        <DropdownMenuItem onSelect={() => onAction({ kind: 'edit', doc: d })}><Pencil className="size-4" />{t('documents.editDetails')}</DropdownMenuItem>
                        <DropdownMenuItem onSelect={() => onAction({ kind: 'move', doc: d })}><FolderInput className="size-4" />{t('documents.moveTo')}</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => onAction({ kind: 'delete', doc: d })}><Trash2 className="size-4" />{t('documents.delete')}</DropdownMenuItem>
                      </>
                    )}
                  </RowMenu>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

function RowMenu({ name, children }: { name: string; children: ReactNode }) {
  const { t } = useT();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="size-9" aria-label={t('documents.rowActions', { name })}><MoreHorizontal className="size-4" /></Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">{children}</DropdownMenuContent>
    </DropdownMenu>
  );
}

function By({ person }: { person: DocFile['uploadedBy'] }) {
  if (!person) return <span className="text-xs text-muted-foreground">—</span>;
  return <span className="flex min-w-0 items-center gap-2"><PersonAvatar person={person} /><span className="truncate text-sm">{person.name}</span></span>;
}
