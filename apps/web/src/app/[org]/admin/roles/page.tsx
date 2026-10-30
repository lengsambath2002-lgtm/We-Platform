'use client';

import { KeyRound, Pencil, Plus, Trash2, UserPlus, X } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useMembers, useOrgMutation } from '@/lib/api/queries';
import { type CatalogueEntry, type CustomRole, type RolesView, rolesApi, useRoles } from '@/lib/api/roles';
import { unitTree, useStructure } from '@/lib/api/structure';
import { useOrg } from '@/lib/org-context';

const ORG = '__org__';
const alert = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;
const key = (code: string) => code.replace(/\./g, '_');
const permLabel = (code: string) => tr(`roles.perm_${key(code)}` as MessageKey);
const permHint = (code: string) => tr(`roles.permHint_${key(code)}` as MessageKey);
const groupOf = (e: CatalogueEntry) => e.module ?? 'core';

/** Owners and Admins: a role's name, description and permissions, grouped by module. */
function RoleDialog({ org, view, role, onClose }: { org: string; view: RolesView; role: CustomRole | null; onClose: () => void }) {
  const { t } = useT();
  const [name, setName] = useState(role?.name ?? '');
  const [description, setDescription] = useState(role?.description ?? '');
  const [perms, setPerms] = useState<Set<string>>(new Set(role?.permissions ?? []));
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => rolesApi.save(org, role?.id ?? null, body));
  const groups = [...new Set(view.catalogue.map(groupOf))];
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
        <DialogHeader><DialogTitle>{role ? t('roles.editRole') : t('roles.newRole')}</DialogTitle><DialogDescription>{t('roles.roleBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          <div className="space-y-1.5"><Label htmlFor="role-name">{t('roles.name')}</Label><Input id="role-name" autoFocus maxLength={60} placeholder="Branch HR" value={name} onChange={(e) => setName(e.target.value)} /></div>
          <div className="space-y-1.5"><Label htmlFor="role-desc">{t('roles.description')}</Label><Textarea id="role-desc" rows={2} maxLength={300} value={description} onChange={(e) => setDescription(e.target.value)} /></div>
          <fieldset className="space-y-3">
            <legend className="text-sm font-medium">{t('roles.permissions')}</legend>
            {groups.map((g) => (
              <div key={g} className="space-y-2">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{tr(`roles.group_${g}` as MessageKey)}</p>
                {view.catalogue.filter((e) => groupOf(e) === g).map((e) => (
                  <label key={e.code} htmlFor={`perm-${e.code}`} className="flex cursor-pointer items-start gap-3 rounded-md border p-3 hover:bg-muted/50">
                    <Checkbox id={`perm-${e.code}`} className="mt-0.5" checked={perms.has(e.code)}
                      onCheckedChange={(on) => { const n = new Set(perms); if (on) n.add(e.code); else n.delete(e.code); setPerms(n); }} />
                    <span className="min-w-0">
                      <span className="block text-sm font-medium">{permLabel(e.code)}
                        {!e.available && <span className="ml-2 text-xs font-normal text-muted-foreground">({t('roles.moduleOff')})</span>}</span>
                      <span className="block text-xs text-muted-foreground">{permHint(e.code)}{e.scoped && ` · ${t('roles.scopable')}`}</span>
                    </span>
                  </label>
                ))}
              </div>
            ))}
          </fieldset>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!name.trim()) { setErr(t('roles.errName')); return; }
            if (perms.size === 0) { setErr(t('roles.errPermissions')); return; }
            setErr(null);
            try {
              await save.mutateAsync({ name: name.trim(), description: description.trim() || null, permissions: [...perms] });
              toast.success(t('roles.saved'));
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Owners and Admins: grants a role to a member, organisation-wide or (for a scopable role) for one unit and below. */
function GrantDialog({ org, role, onClose }: { org: string; role: CustomRole; onClose: () => void }) {
  const { t } = useT();
  const members = useMembers(org);
  const structure = useStructure(org, role.scopable);
  const [member, setMember] = useState('');
  const [unit, setUnit] = useState(ORG);
  const [err, setErr] = useState<string | null>(null);
  const grant = useOrgMutation(org, (body: { membershipId: string; unitId: string | null }) => rolesApi.grant(org, role.id, body));
  const eligible = (members.data ?? []).filter((m) => m.role === 'MEMBER' && m.status === 'ACTIVE');
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('roles.grantTitle', { name: role.name })}</DialogTitle><DialogDescription>{t('roles.grantBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="grant-member">{t('roles.member')}</Label>
            <Select value={member} onValueChange={setMember}>
              <SelectTrigger id="grant-member" className="w-full"><SelectValue placeholder={t('roles.chooseMember')} /></SelectTrigger>
              <SelectContent>{eligible.map((m) => <SelectItem key={m.membershipId} value={m.membershipId}>{m.name}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          {role.scopable && (
            <div className="space-y-1.5">
              <Label htmlFor="grant-scope">{t('roles.scope')}</Label>
              <Select value={unit} onValueChange={setUnit}>
                <SelectTrigger id="grant-scope" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={ORG}>{t('roles.wholeOrg')}</SelectItem>
                  {unitTree(structure.data?.units ?? []).map(({ unit: u, depth }) => <SelectItem key={u.id} value={u.id}>{'— '.repeat(depth)}{u.name}</SelectItem>)}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{t('roles.scopeHint')}</p>
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={grant.isPending} onClick={async () => {
            if (!member) { setErr(t('roles.chooseMember')); return; }
            setErr(null);
            try {
              const g = await grant.mutateAsync({ membershipId: member, unitId: unit === ORG ? null : unit });
              toast.success(t('roles.granted', { role: role.name, name: g.person.name }));
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('roles.grant')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RoleCard({ org, view, role, edit, grant }: { org: string; view: RolesView; role: CustomRole; edit: () => void; grant: () => void }) {
  const { t } = useT();
  const remove = useOrgMutation(org, () => rolesApi.remove(org, role.id));
  const revoke = useOrgMutation(org, (id: string) => rolesApi.revoke(org, id));
  const holders = view.grants.filter((g) => g.roleId === role.id);
  return (
    <section aria-label={role.name} className="space-y-3 rounded-lg border bg-card p-4">
      <div className="flex flex-wrap items-start gap-2">
        <div className="min-w-0 flex-1">
          <h2 className="text-base font-semibold">{role.name}</h2>
          {role.description && <p className="text-sm text-muted-foreground">{role.description}</p>}
        </div>
        <Button variant="outline" size="sm" onClick={grant}><UserPlus className="size-4" />{t('roles.grant')}</Button>
        <Button variant="ghost" size="icon" aria-label={t('roles.editRoleOf', { name: role.name })} onClick={edit}><Pencil className="size-4" /></Button>
        <Button variant="ghost" size="icon" className="text-destructive" aria-label={t('common.delete')} onClick={() => {
          if (window.confirm(t('roles.deleteConfirm', { name: role.name }))) remove.mutateAsync(undefined).then(() => toast.success(t('roles.deleted'))).catch((e) => toast.error(errorMessage(e)));
        }}><Trash2 className="size-4" /></Button>
      </div>
      <ul className="flex flex-wrap gap-1.5" aria-label={t('roles.permissions')}>
        {role.permissions.map((p) => <li key={p} className="rounded-full bg-muted px-2 py-0.5 text-xs">{permLabel(p)}</li>)}
      </ul>
      <div>
        <p className="mb-1 text-xs text-muted-foreground">{t('roles.holders', { count: holders.length })}</p>
        {holders.length === 0 ? <p className="text-sm text-muted-foreground">{t('roles.noHolders')}</p> : (
          <ul className="divide-y rounded-md border">{holders.map((g) => (
            <li key={g.id} className="flex items-center gap-3 px-3 py-2">
              <PersonAvatar person={{ name: g.person.name, avatarUrl: g.person.avatarUrl }} className="size-7" />
              <span className="min-w-0 flex-1"><span className="block truncate text-sm font-medium">{g.person.name}</span>
                <span className="block truncate text-xs text-muted-foreground">{g.unitName ? t('roles.inUnit', { unit: g.unitName }) : t('roles.orgWide')}</span></span>
              <Button variant="ghost" size="icon" aria-label={t('roles.revokeOf', { role: role.name, name: g.person.name })}
                onClick={() => revoke.mutateAsync(g.id).then(() => toast.success(t('roles.revoked'))).catch((e) => toast.error(errorMessage(e)))}><X className="size-4" /></Button>
            </li>
          ))}</ul>
        )}
      </div>
    </section>
  );
}

/** Custom roles: named sets of permissions granted to members, organisation-wide or for one unit (Owners and Admins). */
export default function RolesAdmin() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const allowed = can('org.roles.manage');
  const { data, error, isPending, refetch } = useRoles(slug, allowed);
  const [editing, setEditing] = useState<CustomRole | 'new' | null>(null);
  const [granting, setGranting] = useState<CustomRole | null>(null);
  if (!allowed) return <PermissionDenied />;
  return (
    <>
      <PageHeader title={t('roles.title')} subtitle={t('roles.subtitle')}
        actions={<Button size="sm" onClick={() => setEditing('new')}><Plus className="size-4" />{t('roles.newRole')}</Button>} />
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">{t('roles.intro')}</p>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={3} />
        : data.roles.length === 0 ? <EmptyState icon={KeyRound} title={t('roles.noRoles')} body={t('roles.noRolesBody')} /> : (
          <div className="grid gap-4 xl:grid-cols-2">
            {data.roles.map((r) => <RoleCard key={r.id} org={slug} view={data} role={r} edit={() => setEditing(r)} grant={() => setGranting(r)} />)}
          </div>
        )}
      {data && editing && <RoleDialog org={slug} view={data} role={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      {granting && <GrantDialog org={slug} role={granting} onClose={() => setGranting(null)} />}
    </>
  );
}
