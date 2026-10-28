'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Check, Copy } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { orgApi, useOrgMutation } from '@/lib/api/queries';
import type { Invitation } from '@/lib/api/types';

const schema = z.object({ email: z.string().trim().email('Enter an email address'), role: z.enum(['ADMIN', 'MEMBER', 'GUEST']) });
type Values = z.infer<typeof schema>;

export const ROLE_TEXT: Record<string, { label: string; hint: string }> = {
  OWNER: { label: 'Owner', hint: 'Everything, including billing and deleting the organisation' },
  ADMIN: { label: 'Admin', hint: 'Members, modules, workflows and every project' },
  MEMBER: { label: 'Member', hint: 'Projects they can see, calendar, approvals' },
  GUEST: { label: 'Guest', hint: 'Only projects they are added to' },
};

/**
 * Creates an invitation and shows its one-time link to copy. The platform does not send email yet,
 * so the link is how the person joins; it only works for the invited address.
 */
export function InviteDialog({ org, open, onOpenChange }: { org: string; open: boolean; onOpenChange: (v: boolean) => void }) {
  const [created, setCreated] = useState<Invitation | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', role: 'MEMBER' } });
  const invite = useOrgMutation(org, (v: Values) => orgApi.invite(org, v.email, v.role));
  const close = (v: boolean) => { if (!v) { setCreated(null); setCopied(false); setError(null); form.reset(); } onOpenChange(v); };

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{created ? 'Invitation ready' : 'Invite someone'}</DialogTitle>
          <DialogDescription>
            {created ? `Send this link to ${created.email}. It works once, for that email address, until it expires.`
              : 'They join with the role you choose. You can change it later.'}
          </DialogDescription>
        </DialogHeader>
        {created ? (
          <div className="space-y-3">
            <div className="flex gap-2">
              <Input readOnly value={created.link ?? ''} aria-label="Invitation link" onFocus={(e) => e.target.select()} className="font-mono text-xs" />
              <Button variant="outline" onClick={async () => { await navigator.clipboard.writeText(created.link ?? ''); setCopied(true); }}>
                {copied ? <Check className="size-4" /> : <Copy className="size-4" />}{copied ? 'Copied' : 'Copy'}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">The link is shown only now. If it&apos;s lost, revoke the invitation and invite again.</p>
            <DialogFooter><Button onClick={() => close(false)}>Done</Button></DialogFooter>
          </div>
        ) : (
          <Form {...form}>
            <form noValidate className="space-y-4" onSubmit={form.handleSubmit(async (v) => {
              setError(null);
              try { setCreated(await invite.mutateAsync(v)); } catch (e) { setError(applyServerErrors(form, e)); }
            })}>
              {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
              <FormField control={form.control} name="email" render={({ field }) => (
                <FormItem><FormLabel>Email</FormLabel><FormControl><Input type="email" autoFocus autoComplete="off" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="role" render={({ field }) => (
                <FormItem>
                  <FormLabel>Role</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>{(['ADMIN', 'MEMBER', 'GUEST'] as const).map((r) => <SelectItem key={r} value={r}>{ROLE_TEXT[r]!.label}</SelectItem>)}</SelectContent>
                  </Select>
                  <p className="text-xs text-muted-foreground">{ROLE_TEXT[form.watch('role')]!.hint}</p>
                </FormItem>
              )} />
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => close(false)}>Cancel</Button>
                <Button type="submit" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? 'Creating…' : 'Create invitation'}</Button>
              </DialogFooter>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  );
}
