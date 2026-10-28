'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Check, Copy } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { type Locale, tr, translate } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { Invitation } from '@/lib/api/types';

const makeSchema = (l: Locale) => z.object({ email: z.string().trim().email(translate(l, 'org.emailInvalid')), role: z.enum(['ADMIN', 'MEMBER', 'GUEST']) });
type Values = z.infer<ReturnType<typeof makeSchema>>;

/** Role label and hint, read in the current language on each access. */
export const ROLE_TEXT: Record<string, { label: string; hint: string }> = Object.fromEntries(
  (['OWNER', 'ADMIN', 'MEMBER', 'GUEST'] as const).map((r) => [r, {
    get label() { return tr(`org.role_${r}`); },
    get hint() { return tr(`org.role_${r}_hint`); },
  }]),
);

/**
 * Creates an invitation and shows its one-time link to copy. The platform does not send email yet,
 * so the link is how the person joins; it only works for the invited address.
 */
export function InviteDialog({ org, open, onOpenChange }: { org: string; open: boolean; onOpenChange: (v: boolean) => void }) {
  const [created, setCreated] = useState<Invitation | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { t, locale } = useT();
  const schema = useMemo(() => makeSchema(locale), [locale]);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', role: 'MEMBER' } });
  const invite = useOrgMutation(org, (v: Values) => orgApi.invite(org, v.email, v.role));
  const close = (v: boolean) => { if (!v) { setCreated(null); setCopied(false); setError(null); form.reset(); } onOpenChange(v); };

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{created ? t('org.inviteReadyTitle') : t('org.inviteTitle')}</DialogTitle>
          <DialogDescription>
            {created ? t('org.inviteReadyBody', { email: created.email }) : t('org.inviteBody')}
          </DialogDescription>
        </DialogHeader>
        {created ? (
          <div className="space-y-3">
            <div className="flex gap-2">
              <Input readOnly value={created.link ?? ''} aria-label={t('org.inviteLink')} onFocus={(e) => e.target.select()} className="font-mono text-xs" />
              <Button variant="outline" onClick={async () => { await navigator.clipboard.writeText(created.link ?? ''); setCopied(true); }}>
                {copied ? <Check className="size-4" /> : <Copy className="size-4" />}{copied ? t('org.copied') : t('org.copy')}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">{t('org.linkShownOnce')}</p>
            <DialogFooter><Button onClick={() => close(false)}>{t('common.done')}</Button></DialogFooter>
          </div>
        ) : (
          <Form {...form}>
            <form noValidate className="space-y-4" onSubmit={form.handleSubmit(async (v) => {
              setError(null);
              try { setCreated(await invite.mutateAsync(v)); } catch (e) { setError(applyServerErrors(form, e)); }
            })}>
              {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
              <FormField control={form.control} name="email" render={({ field }) => (
                <FormItem><FormLabel>{t('org.email')}</FormLabel><FormControl><Input type="email" autoFocus autoComplete="off" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="role" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('org.role')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>{(['ADMIN', 'MEMBER', 'GUEST'] as const).map((r) => <SelectItem key={r} value={r}>{ROLE_TEXT[r]!.label}</SelectItem>)}</SelectContent>
                  </Select>
                  <p className="text-xs text-muted-foreground">{ROLE_TEXT[form.watch('role')]!.hint}</p>
                </FormItem>
              )} />
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => close(false)}>{t('common.cancel')}</Button>
                <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? t('common.creating') : t('org.createInvitation')}</Button>
              </DialogFooter>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  );
}
