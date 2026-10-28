'use client';

import { MailX, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { InviteDialog, ROLE_TEXT } from '@/components/org/invite-dialog';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
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
  const allowed = can('org.members.manage');
  const members = useMembers(slug);
  const invitations = useInvitations(slug, allowed);
  const [inviting, setInviting] = useState(false);
  const update = useOrgMutation(slug, ({ id, body }: { id: string; body: Record<string, unknown> }) => orgApi.updateMember(slug, id, body));
  const revoke = useOrgMutation(slug, (id: string) => orgApi.revokeInvitation(slug, id));
  if (!allowed) return <PermissionDenied />;
  const isOwner = org.role === 'OWNER';
  const change = (id: string, body: Record<string, unknown>, done: string) =>
    update.mutateAsync({ id, body }).then(() => toast.success(done)).catch((e) => toast.error(errorMessage(e)));

  return (
    <>
      <PageHeader title="Members & roles" subtitle="Roles decide what people can do. Only owners can make or remove owners."
        actions={<Button size="sm" onClick={() => setInviting(true)}><UserPlus className="size-4" />Invite</Button>} />
      {members.error ? <ErrorState error={members.error} retry={() => members.refetch()} /> : members.isPending ? <ListSkeleton /> : (
        <div className="overflow-hidden rounded-lg border bg-card">
          <Table>
            <TableHeader>
              <TableRow><TableHead>Person</TableHead><TableHead className="w-40">Role</TableHead><TableHead className="hidden md:table-cell">Joined</TableHead><TableHead className="w-28 text-right">Access</TableHead></TableRow>
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
                        <span className="min-w-0"><span className="block truncate text-sm font-medium">{m.name}{me && ' (you)'}</span><span className="block truncate text-xs text-muted-foreground">{m.email}</span></span>
                      </span>
                    </TableCell>
                    <TableCell>
                      {locked ? <span className="text-sm">{ROLE_TEXT[m.role]?.label}</span> : (
                        <Select value={m.role} onValueChange={(r) => change(m.membershipId, { role: r }, `${m.name} is now ${ROLE_TEXT[r]?.label.toLowerCase()}`)}>
                          <SelectTrigger size="sm" className="w-full" aria-label={`Role of ${m.name}`}><SelectValue /></SelectTrigger>
                          <SelectContent>{ROLES.filter((r) => isOwner || r !== 'OWNER').map((r) => <SelectItem key={r} value={r}>{ROLE_TEXT[r]!.label}</SelectItem>)}</SelectContent>
                        </Select>
                      )}
                    </TableCell>
                    <TableCell className="hidden text-sm text-muted-foreground md:table-cell">{day(m.joinedAt)}</TableCell>
                    <TableCell className="text-right">
                      {!locked && (m.status === 'ACTIVE'
                        ? <Button variant="ghost" size="sm" className="text-destructive" onClick={() => change(m.membershipId, { status: 'SUSPENDED' }, `${m.name} suspended`)}>Suspend</Button>
                        : <Button variant="ghost" size="sm" onClick={() => change(m.membershipId, { status: 'ACTIVE' }, `${m.name} restored`)}>Restore</Button>)}
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

      <h2 className="mb-3 mt-8 text-base font-semibold">Pending invitations</h2>
      {invitations.error ? <ErrorState error={invitations.error} retry={() => invitations.refetch()} /> : invitations.isPending ? <ListSkeleton rows={2} /> : invitations.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">No pending invitations.</p>
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {invitations.data.map((i) => (
            <li key={i.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <span className="min-w-0 flex-1"><span className="block truncate text-sm">{i.email}</span><span className="text-xs text-muted-foreground">{ROLE_TEXT[i.role]?.label} · expires {day(i.expiresAt)}</span></span>
              <Button variant="ghost" size="sm" onClick={() => revoke.mutateAsync(i.id).then(() => toast.success('Invitation revoked')).catch((e) => toast.error(errorMessage(e)))}>
                <MailX className="size-4" />Revoke
              </Button>
            </li>
          ))}
        </ul>
      )}
      <InviteDialog org={slug} open={inviting} onOpenChange={setInviting} />
    </>
  );
}
