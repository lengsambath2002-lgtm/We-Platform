'use client';

import { Download, Paperclip, Trash2, Upload } from 'lucide-react';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { errorMessage, getAccessToken } from '@/lib/api/client';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { FileRef } from '@/lib/api/types';
import { bytes } from '@/lib/format';

const MAX = 20 * 1024 * 1024;

/** Downloads through the API with the bearer token (attachments are never public URLs). */
async function download(url: string, name: string) {
  const res = await fetch(url, { headers: { Authorization: `Bearer ${getAccessToken() ?? ''}` }, credentials: 'include' });
  if (!res.ok) throw new Error(res.status === 404 ? 'That file is gone' : "Couldn't download the file");
  const href = URL.createObjectURL(await res.blob());
  const a = Object.assign(document.createElement('a'), { href, download: name });
  a.click();
  URL.revokeObjectURL(href);
}

export function Attachments({ org, taskId, files, canEdit }: { org: string; taskId: string; files: FileRef[]; canEdit: boolean }) {
  const input = useRef<HTMLInputElement>(null);
  const [drag, setDrag] = useState(false);
  const attach = useOrgMutation(org, (f: File) => orgApi.attach(org, taskId, f));
  const detach = useOrgMutation(org, (id: string) => orgApi.detach(org, taskId, id));

  const upload = async (list: FileList | null) => {
    for (const f of Array.from(list ?? [])) {
      if (f.size > MAX) { toast.error(`${f.name} is larger than 20 MB`); continue; }
      try { await attach.mutateAsync(f); } catch (e) { toast.error(`${f.name}: ${errorMessage(e)}`); }
    }
  };

  return (
    <section aria-labelledby="files-h" className="space-y-3">
      <h2 id="files-h" className="flex items-center gap-2 text-base font-semibold"><Paperclip className="size-4" />Attachments</h2>
      {files.length > 0 && (
        <ul className="divide-y rounded-lg border bg-card">
          {files.map((f) => (
            <li key={f.id} className="flex items-center gap-3 px-3 py-2">
              <span className="min-w-0 flex-1"><span className="block truncate text-sm">{f.name}</span><span className="text-xs text-muted-foreground">{bytes(f.size)}</span></span>
              <Button variant="ghost" size="icon" aria-label={`Download ${f.name}`}
                onClick={() => download(`/api/orgs/${org}/tasks/${taskId}/attachments/${f.id}`, f.name).catch((e) => toast.error(errorMessage(e)))}>
                <Download className="size-4" />
              </Button>
              {canEdit && (
                <Button variant="ghost" size="icon" aria-label={`Remove ${f.name}`} onClick={() => detach.mutateAsync(f.id).catch((e) => toast.error(errorMessage(e)))}>
                  <Trash2 className="size-4" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit ? (
        <div onDragOver={(e) => { e.preventDefault(); setDrag(true); }} onDragLeave={() => setDrag(false)}
          onDrop={(e) => { e.preventDefault(); setDrag(false); void upload(e.dataTransfer.files); }}
          className={`flex flex-col items-center gap-2 rounded-lg border border-dashed px-4 py-5 text-center text-sm ${drag ? 'border-primary bg-primary/5' : ''}`}>
          <p className="text-muted-foreground">Drop files here, or</p>
          <Button variant="outline" size="sm" disabled={attach.isPending} onClick={() => input.current?.click()}>
            <Upload className="size-4" />{attach.isPending ? 'Uploading…' : 'Choose files'}
          </Button>
          <p className="text-xs text-muted-foreground">PDF, images, Office documents, text · up to 20 MB</p>
          <input ref={input} type="file" multiple hidden onChange={(e) => { void upload(e.target.files); e.target.value = ''; }} />
        </div>
      ) : files.length === 0 && <p className="text-sm text-muted-foreground">No attachments.</p>}
    </section>
  );
}
