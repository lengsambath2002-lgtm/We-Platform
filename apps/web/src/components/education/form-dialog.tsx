'use client';

import type { ReactNode } from 'react';
import type { FieldValues, UseFormReturn } from 'react-hook-form';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form } from '@/components/ui/form';
import { useT } from '@/i18n/provider';
import { cn } from '@/lib/utils';

import { ErrorLine } from './ui';

/** A dialog around a form: title, description, the fields, an error line, Cancel and the submit button. */
export function FormDialog<T extends FieldValues>({ open, onOpenChange, title, description, form, onSubmit, error, submitLabel, wide, children }: {
  open: boolean; onOpenChange: (v: boolean) => void; title: string; description?: string; form: UseFormReturn<T>;
  onSubmit: () => void; error: string | null; submitLabel?: string; wide?: boolean; children: ReactNode;
}) {
  const { t } = useT();
  const busy = form.formState.isSubmitting;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={cn('max-h-[90dvh] overflow-y-auto', wide ? 'sm:max-w-2xl' : 'sm:max-w-lg')}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={(e) => { e.preventDefault(); onSubmit(); }} className="space-y-4" noValidate>
            <ErrorLine error={error} />
            {children}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={busy}>{busy ? t('common.saving') : submitLabel ?? t('common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
