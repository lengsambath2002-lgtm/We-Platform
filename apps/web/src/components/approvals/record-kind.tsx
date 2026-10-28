'use client';

import { moduleName } from '@/components/shell/nav';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useOrg } from '@/lib/org-context';

/** "Projects & Tasks · Task" in the current language; unknown codes are shown as they are. */
export function RecordKind({ moduleKey, recordType }: { moduleKey: string; recordType: string }) {
  const { modules } = useOrg();
  const m = modules.find((x) => x.key === moduleKey);
  const type = tr(`approvals.recordType_${recordType}` as MessageKey);
  return <>{m ? moduleName(m) : moduleKey} · {type.startsWith('approvals.') ? recordType : type}</>;
}
