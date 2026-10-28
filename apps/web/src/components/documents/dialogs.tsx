'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Check, Folder, Home } from 'lucide-react';
import { type FormEvent, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { docApi, type FolderOption, MAX_UPLOAD, useFolderOptions } from '@/lib/api/documents';
import { useOrgMutation } from '@/lib/api/queries';
import { bytes } from '@/lib/format';
import { cn } from '@/lib/utils';

type T = ReturnType<typeof useT>['t'];

const nameSchema = (t: T, max: number) => z.object({
  name: z.string().trim().min(1, t('documents.errName')).max(max),
  description: z.string().max(2000),
});
type NameValues = z.infer<ReturnType<typeof nameSchema>>;

/**
 * One small form for "new folder", "rename folder" and "rename or describe a file". `save` gets the
 * trimmed values; field errors from the API land on their fields.
 */
export function NameDialog({ open, onOpenChange, title, body, label, initial, description, max = 120, submitLabel, save }: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  title: string;
  body?: string;
  label: string;
  initial: string;
  /** Show the description field, starting with this text. */
  description?: string | null;
  max?: number;
  submitLabel: string;
  save: (v: NameValues) => Promise<unknown>;
}) {
  const { t } = useT();
  const schema = useMemo(() => nameSchema(t, max), [t, max]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<NameValues>({ resolver: zodResolver(schema), values: { name: initial, description: description ?? '' } });
  const onSubmit = form.handleSubmit(async (v) => {
    setError(null);
    try { await save({ name: v.name.trim(), description: v.description.trim() }); onOpenChange(false); } catch (e) { setError(applyServerErrors(form, e)); }
  });
  return (
    <Dialog open={open} onOpenChange={(v) => { setError(null); onOpenChange(v); }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {body ? <DialogDescription>{body}</DialogDescription> : <DialogDescription className="sr-only">{title}</DialogDescription>}
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
            <FormField control={form.control} name="name" render={({ field }) => (
              <FormItem><FormLabel>{label}</FormLabel><FormControl><Input autoFocus maxLength={max} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            {description !== undefined && (
              <FormField control={form.control} name="description" render={({ field }) => (
                <FormItem><FormLabel>{t('documents.description')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Textarea rows={3} maxLength={2000} {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.saving') : submitLabel}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

/** Folder picker for "Move to…": the whole tree, indented; a folder can't go into itself or below. */
export function MoveDialog({ org, open, onOpenChange, name, currentFolderId, movingFolderId, move }: {
  org: string;
  open: boolean;
  onOpenChange: (v: boolean) => void;
  name: string;
  currentFolderId: string | null;
  movingFolderId?: string;
  move: (folderId: string | null) => Promise<unknown>;
}) {
  const { t } = useT();
  const options = useFolderOptions(org, open);
  const [target, setTarget] = useState<string | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const rows = useMemo(() => flatten(options.data ?? [], movingFolderId), [options.data, movingFolderId]);
  const chosen = target === undefined ? currentFolderId : target;

  const submit = async () => {
    setBusy(true);
    try { await move(chosen); toast.success(t('documents.moved')); onOpenChange(false); } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
  };

  const row = (id: string | null, label: string, depth: number, Icon = Folder) => (
    <li key={id ?? 'top'}>
      <button type="button" onClick={() => setTarget(id)} aria-pressed={chosen === id}
        className={cn('flex min-h-10 w-full items-center gap-2 rounded-md px-2 text-left text-sm hover:bg-muted', chosen === id && 'bg-primary/10 text-primary')}
        style={{ paddingLeft: `${8 + depth * 16}px` }}>
        <Icon className="size-4 shrink-0" aria-hidden />
        <span className="min-w-0 flex-1 truncate">{label}</span>
        {id === currentFolderId && <span className="shrink-0 text-xs text-muted-foreground">{t('documents.currentFolder')}</span>}
        {chosen === id && <Check className="size-4 shrink-0" aria-hidden />}
      </button>
    </li>
  );

  return (
    <Dialog open={open} onOpenChange={(v) => { setTarget(undefined); onOpenChange(v); }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="break-words pr-6">{t('documents.moveTitle', { name })}</DialogTitle>
          <DialogDescription>{t('documents.moveBody')}</DialogDescription>
        </DialogHeader>
        {options.error ? <p role="alert" className="text-sm text-destructive">{errorMessage(options.error)}</p> : (
          <ul className="max-h-72 space-y-0.5 overflow-y-auto rounded-md border p-1" aria-busy={options.isPending}>
            {row(null, t('documents.allDocuments'), 0, Home)}
            {rows.map((r) => row(r.id, r.name, r.depth + 1))}
          </ul>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
          <Button type="button" disabled={busy || chosen === currentFolderId || options.isPending} onClick={submit}>{t('documents.move')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Depth-first with depths, leaving out `exclude` and everything below it. */
function flatten(all: FolderOption[], exclude?: string) {
  const byParent = new Map<string | null, FolderOption[]>();
  for (const f of all) byParent.set(f.parentId, [...(byParent.get(f.parentId) ?? []), f]);
  const out: (FolderOption & { depth: number })[] = [];
  const walk = (parent: string | null, depth: number) => {
    for (const f of byParent.get(parent) ?? []) {
      if (f.id === exclude) continue;
      out.push({ ...f, depth });
      if (depth < 50) walk(f.id, depth + 1);
    }
  };
  walk(null, 0);
  return out;
}

/** A new version of a file, with an optional note on what changed. */
export function NewVersionDialog({ org, doc, open, onOpenChange }: {
  org: string; doc: { id: string; name: string } | null; open: boolean; onOpenChange: (v: boolean) => void;
}) {
  const { t } = useT();
  const [file, setFile] = useState<File | null>(null);
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const upload = useOrgMutation(org, (v: { file: File; note: string }) => docApi.newVersion(org, doc!.id, v.file, v.note));
  const close = (v: boolean) => { if (!v) { setFile(null); setNote(''); setError(null); } onOpenChange(v); };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!file) { setError(t('documents.errFile')); return; }
    if (file.size > MAX_UPLOAD) { setError(t('documents.tooLarge', { name: file.name })); return; }
    try {
      const d = await upload.mutateAsync({ file, note });
      toast.success(t('documents.versionUploaded', { version: d.version }));
      close(false);
    } catch (err) { setError(errorMessage(err)); }
  };

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="break-words pr-6">{t('documents.newVersionTitle', { name: doc?.name ?? '' })}</DialogTitle>
          <DialogDescription>{t('documents.newVersionBody')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
          <div className="space-y-2">
            <label className="flex cursor-pointer items-center gap-3 rounded-md border border-dashed px-3 py-3 text-sm hover:bg-muted/50">
              <span className="shrink-0 rounded-md border bg-background px-2.5 py-1 font-medium">{t('documents.chooseFile')}</span>
              <span className="min-w-0 truncate text-muted-foreground">{file ? `${file.name} · ${bytes(file.size)}` : t('documents.noFileChosen')}</span>
              <input type="file" className="sr-only" onChange={(e) => { setFile(e.target.files?.[0] ?? null); setError(null); }} />
            </label>
            <p className="text-xs text-muted-foreground">{t('documents.fileTypes')}</p>
          </div>
          <div className="space-y-2">
            <label htmlFor="version-note" className="text-sm font-medium">{t('documents.versionNote')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></label>
            <Textarea id="version-note" rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={upload.isPending}>{upload.isPending ? t('documents.uploading', { done: 1, count: 1 }) : t('documents.upload')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function ConfirmDelete({ open, onOpenChange, title, body, confirm }: {
  open: boolean; onOpenChange: (v: boolean) => void; title: string; body: string; confirm: () => Promise<unknown>;
}) {
  const { t } = useT();
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="break-words">{title}</AlertDialogTitle>
          <AlertDialogDescription>{body}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
          <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={async () => {
            try { await confirm(); toast.success(t('documents.deletedToTrash')); } catch (e) { toast.error(errorMessage(e)); }
          }}>{t('documents.delete')}</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
