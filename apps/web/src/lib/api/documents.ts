'use client';

// The document library: types, query hooks and calls (kept apart from queries.ts).
import { useQuery } from '@tanstack/react-query';

import { tr } from '@/i18n/core';

import { getAccessToken, http } from './client';
import type { PersonRef } from './types';

export interface FolderRef {
  id: string;
  name: string;
}

export interface FolderOption extends FolderRef {
  parentId: string | null;
}

export interface DocFolder {
  id: string;
  name: string;
  parentId: string | null;
  items: number;
  createdBy: PersonRef | null;
  createdAt: string;
  updatedAt: string;
  canManage: boolean;
}

export interface DocFile {
  id: string;
  name: string;
  description: string | null;
  folderId: string | null;
  contentType: string;
  size: number;
  version: number;
  uploadedBy: PersonRef | null;
  updatedBy: PersonRef | null;
  createdAt: string;
  updatedAt: string;
  canManage: boolean;
}

export interface FolderContents {
  folder: FolderRef | null;
  path: FolderRef[];
  folders: DocFolder[];
  documents: DocFile[];
  canUpload: boolean;
  canManage: boolean;
}

export interface SearchHit {
  document: DocFile;
  path: FolderRef[];
}

export interface DocVersion {
  version: number;
  fileName: string;
  contentType: string;
  size: number;
  note: string | null;
  uploadedBy: PersonRef | null;
  createdAt: string;
  current: boolean;
}

export interface DocActivity {
  id: string;
  actor: PersonRef | null;
  verb: string;
  data: Record<string, unknown>;
  at: string;
}

export interface DocDetail {
  document: DocFile;
  path: FolderRef[];
  versions: DocVersion[];
  activity: DocActivity[];
}

export interface TrashItem {
  kind: 'FOLDER' | 'DOCUMENT';
  id: string;
  name: string;
  contentType: string | null;
  size: number | null;
  deletedBy: PersonRef | null;
  deletedAt: string;
}

/** Same limit as the API (spring.servlet.multipart). */
export const MAX_UPLOAD = 20 * 1024 * 1024;

const base = (org: string) => `/orgs/${encodeURIComponent(org)}/documents`;

/** Under ['org', org] so useOrgMutation refreshes them. */
export const docKeys = {
  folder: (org: string, id: string | null) => ['org', org, 'documents', 'folder', id ?? 'top'] as const,
  folders: (org: string) => ['org', org, 'documents', 'folders'] as const,
  search: (org: string, q: string) => ['org', org, 'documents', 'search', q] as const,
  detail: (org: string, id: string) => ['org', org, 'documents', 'detail', id] as const,
  trash: (org: string) => ['org', org, 'documents', 'trash'] as const,
};

export const useFolderContents = (org: string, folder: string | null, enabled = true) =>
  useQuery({
    queryKey: docKeys.folder(org, folder),
    queryFn: () => http.get<FolderContents>(`${base(org)}${folder ? `?folder=${encodeURIComponent(folder)}` : ''}`),
    enabled,
  });
export const useFolderOptions = (org: string, enabled = true) =>
  useQuery({ queryKey: docKeys.folders(org), queryFn: () => http.get<FolderOption[]>(`${base(org)}/folders`), enabled });
export const useDocSearch = (org: string, q: string) =>
  useQuery({
    queryKey: docKeys.search(org, q),
    queryFn: () => http.get<SearchHit[]>(`${base(org)}/search?q=${encodeURIComponent(q)}`),
    enabled: q.length > 0,
  });
export const useDocument = (org: string, id: string | null) =>
  useQuery({ queryKey: docKeys.detail(org, id ?? ''), queryFn: () => http.get<DocDetail>(`${base(org)}/${id}`), enabled: !!id });
export const useTrash = (org: string, enabled = true) =>
  useQuery({ queryKey: docKeys.trash(org), queryFn: () => http.get<TrashItem[]>(`${base(org)}/trash`), enabled });

export const docApi = {
  createFolder: (org: string, name: string, parentId: string | null) => http.post<FolderRef>(`${base(org)}/folders`, { name, parentId }),
  renameFolder: (org: string, id: string, name: string) => http.patch<FolderRef>(`${base(org)}/folders/${id}`, { name }),
  moveFolder: (org: string, id: string, folderId: string | null) => http.post<FolderRef>(`${base(org)}/folders/${id}/move`, { folderId }),
  deleteFolder: (org: string, id: string) => http.delete<void>(`${base(org)}/folders/${id}`),
  restoreFolder: (org: string, id: string) => http.post<FolderRef>(`${base(org)}/folders/${id}/restore`),
  upload: (org: string, file: File, folderId: string | null) => {
    const fd = new FormData();
    fd.append('file', file);
    if (folderId) fd.append('folderId', folderId);
    return http.post<DocFile>(base(org), fd);
  },
  newVersion: (org: string, id: string, file: File, note: string) => {
    const fd = new FormData();
    fd.append('file', file);
    if (note.trim()) fd.append('note', note.trim());
    return http.post<DocFile>(`${base(org)}/${id}/versions`, fd);
  },
  update: (org: string, id: string, body: { name?: string; description?: string }) => http.patch<DocFile>(`${base(org)}/${id}`, body),
  move: (org: string, id: string, folderId: string | null) => http.post<DocFile>(`${base(org)}/${id}/move`, { folderId }),
  remove: (org: string, id: string) => http.delete<void>(`${base(org)}/${id}`),
  restore: (org: string, id: string) => http.post<DocFile>(`${base(org)}/${id}/restore`),
};

/** Downloads through the API with the bearer token (files are never public URLs); the API names the file. */
export async function downloadDocument(org: string, id: string, version?: number) {
  const url = `/api${base(org)}/${id}${version ? `/versions/${version}` : ''}/download`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` }, credentials: 'include' });
  if (!res.ok) throw new Error(res.status === 404 ? tr('documents.fileGone') : tr('documents.downloadFailed'));
  const href = URL.createObjectURL(await res.blob());
  const a = Object.assign(document.createElement('a'), { href, download: fileName(res.headers.get('Content-Disposition')) ?? 'download' });
  a.click();
  URL.revokeObjectURL(href);
}

/** The name from `Content-Disposition` (RFC 5987 `filename*` first). */
function fileName(header: string | null) {
  if (!header) return null;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star?.[1]) {
    try { return decodeURIComponent(star[1]); } catch { /* fall through */ }
  }
  return /filename="([^"]+)"/i.exec(header)?.[1] ?? null;
}
