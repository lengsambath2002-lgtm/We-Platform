import type { FieldValues, Path, UseFormReturn } from 'react-hook-form';

import { ApiError, errorMessage } from '@/lib/api/client';

/** Puts the API's field errors on the form's fields; returns a message for anything else. */
export function applyServerErrors<T extends FieldValues>(form: UseFormReturn<T>, e: unknown): string | null {
  if (e instanceof ApiError && e.fields.length) {
    const names = Object.keys(form.getValues());
    let unplaced = false;
    for (const f of e.fields) {
      if (names.includes(f.field)) form.setError(f.field as Path<T>, { message: f.message });
      else unplaced = true;
    }
    return unplaced ? e.fields[0]!.message : null;
  }
  return errorMessage(e);
}
