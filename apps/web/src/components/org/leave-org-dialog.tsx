'use client';

import { useState } from 'react';
import { toast } from 'sonner';

import {
  AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { useLeaveOrDeleteOrg } from '@/lib/api/account';
import { errorMessage } from '@/lib/api/client';

/** Confirms leaving an organisation; the API refuses the last owner with a message shown here. */
export function LeaveOrgDialog({ org, open, onOpenChange, onLeft }: {
  org: { slug: string; name: string }; open: boolean; onOpenChange: (v: boolean) => void; onLeft?: () => void;
}) {
  const { t } = useT();
  const leave = useLeaveOrDeleteOrg();
  const [error, setError] = useState<string | null>(null);
  const close = (v: boolean) => { if (!v) setError(null); onOpenChange(v); };
  return (
    <AlertDialog open={open} onOpenChange={close}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('settings.leaveTitle', { org: org.name })}</AlertDialogTitle>
          <AlertDialogDescription>{t('settings.leaveBody')}</AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
          <Button variant="destructive" disabled={leave.isPending} onClick={async () => {
            setError(null);
            try {
              await leave.mutateAsync({ org: org.slug, action: 'leave' });
              toast.success(t('settings.left', { org: org.name }));
              close(false);
              onLeft?.();
            } catch (e) { setError(errorMessage(e)); }
          }}>{t(leave.isPending ? 'settings.leaving' : 'settings.leave')}</Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
