'use client';

import { MailX, Send, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { InviteDialog, ROLE_TEXT } from '@/components/org/invite-dialog';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { accountApi, type SentInvitation } from '@/lib/api/account';
import { errorMessage } from '@/lib/api/client';
import { orgApi, useInvitations, useMembers, useOrgMutation } from '@/lib/api/queries';
import type { OrgRole } from '@/lib/api/types';
import { useSession } from '@/lib/auth/session';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';

const ROLES: OrgRole[] = ['OWNER', 'ADMIN', 'MEMBER', 'GUEST'];

export default function MembersAdmin() {
  const { slug, can, org } = useOrg();
  const { user } = useSession();
  const { t } = useT();
  const allowed = can('org.members.manage');
  const members = useMembers(slug);
  const invitations = useInvitations(slug, allowed);
  const [inviting, setInviting] = useState(false);
  const update = useOrgMutation(slug, ({ id, body }: { id: string; body: Record<string, unknown> }) => orgApi.updateMember(slug, id, body));
  const revoke = useOrgMutation(slug, (id: string) => orgApi.revokeInvitation(slug, id));
  const resend = useOrgMutation(slug, (id: string) => accountApi.resendInvitation(slug, id));
  const [resent, setResent] = useState<SentInvitation | null>(null);
  if (!allowed) return <PermissionDenied />;
  const isOwner = org.role === 'OWNER';
  const change = (id: string, body: Record<string, unknown>, done: string) =>
    update.mutateAsync({ id, body }).then(() => toast.success(done)).catch((e) => toast.error(errorMessage(e)));

  return (
    <>
      <PageHeader title={t('admin.membersTitle')} subtitle={t('admin.membersSubtitle')}
        actions={<Button size="sm" onClick={() => setInviting(true)}><UserPlus className="size-4" />{t('org.invite')}</Button>} />
      {members.error ? <ErrorState error={members.error} retry={() => members.refetch()} /> : members.isPending ? <ListSkeleton /> : (
        <div className="overflow-hidden rounded-lg border bg-card">
          <Table>
            <TableHeader>
              <TableRow><TableHead>{t('admin.colPerson')}</TableHead><TableHead className="w-40">{t('admin.colRole')}</TableHead><TableHead className="hidden md:table-cell">{t('admin.colJoined')}</TableHead><TableHead className="w-28 text-right">{t('admin.colAccess')}</TableHead></TableRow>
            </TableHeader>
            <TableBody>
              {members.data.map((m) => {
                const me = m.userId === user?.id;
                const locked = me || (m.role === 'OWNER' && !isOwner);
                return (
                  <TableRow key={m.membershipId} className={m.status === 'SUSPENDED' ? 'opacity-60' : undefined}>
                    <TableCell>
                      <span className="flex items-center gap-3">
                        <PersonAvatar person={m} className="size-8" />
                        <span className="min-w-0"><span className="block truncate text-sm font-medium">{m.name}{me && ` ${t('common.you')}`}</span><span className="block truncate text-xs text-muted-foreground">{m.email}</span></span>
                      </span>
                    </TableCell>
                    <TableCell>
                      {locked ? <span className="text-sm">{ROLE_TEXT[m.role]?.label}</span> : (
                        <Select value={m.role} onValueChange={(r) => change(m.membershipId, { role: r }, t('admin.roleChanged', { name: m.name, role: ROLE_TEXT[r]?.label.toLowerCase() ?? r }))}>
                          <SelectTrigger size="sm" className="w-full" aria-label={t('admin.roleOf', { name: m.name })}><SelectValue /></SelectTrigger>
                          <SelectContent>{ROLES.filter((r) => isOwner || r !== 'OWNER').map((r) => <SelectItem key={r} value={r}>{ROLE_TEXT[r]!.label}</SelectItem>)}</SelectContent>
                        </Select>
                      )}
                    </TableCell>
                    <TableCell className="hidden text-sm text-muted-foreground md:table-cell">{day(m.joinedAt)}</TableCell>
                    <TableCell className="text-right">
                      {!locked && (m.status === 'ACTIVE'
                        ? <Button variant="ghost" size="sm" className="text-destructive" onClick={() => change(m.membershipId, { status: 'SUSPENDED' }, t('admin.suspended', { name: m.name }))}>{t('admin.suspend')}</Button>
                        : <Button variant="ghost" size="sm" onClick={() => change(m.membershipId, { status: 'ACTIVE' }, t('admin.restored', { name: m.name }))}>{t('admin.restore')}</Button>)}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
      <dl className="mt-4 grid gap-2 text-xs text-muted-foreground sm:grid-cols-2">
        {ROLES.map((r) => <div key={r}><dt className="inline font-medium text-foreground">{ROLE_TEXT[r]!.label}</dt> — <dd className="inline">{ROLE_TEXT[r]!.hint}</dd></div>)}
      </dl>

      <h2 className="mb-3 mt-8 text-base font-semibold">{t('admin.pendingInvitations')}</h2>
      {invitations.error ? <ErrorState error={invitations.error} retry={() => invitations.refetch()} /> : invitations.isPending ? <ListSkeleton rows={2} /> : invitations.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('admin.noPendingInvitations')}</p>
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {invitations.data.map((i) => (
            <li key={i.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <span className="min-w-0 flex-1"><span className="block truncate text-sm">{i.email}</span><span className="text-xs text-muted-foreground">{t('admin.invitationMeta', { role: ROLE_TEXT[i.role]?.label ?? i.role, date: day(i.expiresAt) })}</span></span>
              <Button variant="ghost" size="sm" aria-label={t('settings.resendOf', { email: i.email })} disabled={resend.isPending}
                onClick={() => resend.mutateAsync(i.id).then((r) => {
                  // Without email the new link (the old one stopped working) must be copied from the dialog.
                  if (r.emailed) toast.success(t('settings.resent', { email: r.email })); else setResent(r);
                }).catch((e) => toast.error(errorMessage(e)))}>
                <Send className="size-4" />{t('settings.resend')}
              </Button>
              <Button variant="ghost" size="sm" onClick={() => revoke.mutateAsync(i.id).then(() => toast.success(t('admin.invitationRevoked'))).catch((e) => toast.error(errorMessage(e)))}>
                <MailX className="size-4" />{t('admin.revoke')}
              </Button>
            </li>
          ))}
        </ul>
      )}
      <InviteDialog org={slug} open={inviting} onOpenChange={setInviting} />
      <InviteDialog org={slug} open={!!resent} onOpenChange={(v) => { if (!v) setResent(null); }} resent={resent} />
    </>
  );
}
