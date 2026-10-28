import { METHOD_LABEL } from '@/components/business/ui';
import { PRIORITY_LABEL, STATUS_LABEL } from '@/components/common/badges';
import { PersonAvatar } from '@/components/common/person';
import { tr } from '@/i18n/core';
import type { PaymentMethod } from '@/lib/api/business';
import type { ActivityItem, Priority, TaskStatus } from '@/lib/api/types';
import { ago, day } from '@/lib/format';
import { usd } from '@/lib/money';

const s = (v: unknown) => (typeof v === 'string' ? v : '');
const status = (v: unknown) => STATUS_LABEL[v as TaskStatus] ?? s(v);
const priority = (v: unknown) => (PRIORITY_LABEL[v as Priority] ?? s(v)).toLowerCase();
const PROJECT_STATUS = ['ACTIVE', 'ON_HOLD', 'DONE', 'ARCHIVED'] as const;
const projectStatus = (v: unknown) => (PROJECT_STATUS.includes(v as never) ? tr(`projects.${v as (typeof PROJECT_STATUS)[number]}`) : s(v));
const ORG_ROLES = ['OWNER', 'ADMIN', 'MEMBER', 'GUEST'] as const;
const role = (v: unknown) => (ORG_ROLES.includes(v as never) ? tr(`org.role_${v as (typeof ORG_ROLES)[number]}`) : s(v));

/**
 * A plain sentence for an activity entry, in the current language (task history, Overview, audit log).
 * `moduleName` names a module key for organisation entries.
 */
export function describe(a: ActivityItem, moduleName: (key: string) => string = (k) => k): string {
  const d = a.data;
  switch (a.verb) {
    case 'task.created': return tr('tasks.actCreated', { key: s(d.key), title: s(d.title) });
    case 'task.subtask.added': return tr('tasks.actSubtaskAdded', { key: s(d.key) });
    case 'task.commented': return tr('tasks.actCommented', { excerpt: s(d.excerpt) });
    case 'task.attached': return tr('tasks.actAttached', { name: s(d.name) });
    case 'task.detached': return tr('tasks.actDetached');
    case 'task.deleted': return tr('tasks.actDeleted', { key: s(d.key), title: s(d.title) });
    case 'task.updated': {
      const parts: string[] = [];
      const st = d.status as { from?: string; to?: string } | undefined;
      if (st) parts.push(tr('tasks.actMoved', { from: status(st.from), to: status(st.to) }));
      if (d.assignee) parts.push((d.assignee as { to: string }).to === 'none' ? tr('tasks.actUnassigned') : tr('tasks.actAssigneeChanged'));
      if (d.priority) parts.push(tr('tasks.actPriority', { priority: priority((d.priority as { to: string }).to) }));
      if (d.dueDate) parts.push((d.dueDate as { to: string }).to === 'none' ? tr('tasks.actDueRemoved') : tr('tasks.actDueSet', { date: day(s((d.dueDate as { to: string }).to)) }));
      if (d.title) parts.push(tr('tasks.actRenamed'));
      if (d.description) parts.push(tr('tasks.actDescription'));
      return parts.join(', ') || tr('tasks.actUpdated');
    }
    case 'project.created': return tr('tasks.actProjectCreated', { name: s(d.name) });
    case 'project.updated':
      if (d.status === 'ARCHIVED') return tr('collab.actProjectArchived');
      if (d.status) return tr('collab.actProjectStatus', { status: projectStatus(d.status) });
      return tr('tasks.actProjectUpdated');
    case 'project.deleted': return tr('collab.actProjectDeleted', { key: s(d.key), name: s(d.name) });
    case 'task.comment.edited': return tr('collab.actCommentEdited', { excerpt: s(d.excerpt) });
    case 'task.comment.deleted': return tr('collab.actCommentDeleted');
    case 'document.uploaded': return tr('documents.actUploaded');
    case 'document.versioned': return tr('documents.actVersioned', { version: String(d.version ?? '') });
    case 'document.renamed': return tr('documents.actRenamed', { from: s(d.from), to: s(d.to) });
    case 'document.described': return tr('documents.actDescribed');
    case 'document.moved': return tr('documents.actMoved', { folder: s(d.to) || tr('documents.topLevel') });
    case 'document.deleted': return tr('documents.actDeleted');
    case 'document.restored': return tr('documents.actRestored');
    case 'folder.created': return tr('documents.actFolderCreated');
    case 'folder.renamed': return tr('documents.actFolderRenamed', { from: s(d.from), to: s(d.to) });
    case 'folder.moved': return tr('documents.actFolderMoved', { folder: s(d.to) || tr('documents.topLevel') });
    case 'folder.deleted': return tr('documents.actFolderDeleted');
    case 'folder.restored': return tr('documents.actFolderRestored');
    case 'event.created': return tr('collab.actEventCreated', { title: s(d.title) });
    case 'event.updated': return tr('collab.actEventUpdated', { title: s(d.title) });
    case 'event.deleted': return tr('collab.actEventDeleted', { title: s(d.title) });
    case 'org.updated': return tr('collab.actOrgUpdated', { name: s(d.name) });
    case 'org.module.enabled': return tr('collab.actModuleOn', { module: moduleName(s(d.module)) });
    case 'org.module.disabled': return tr('collab.actModuleOff', { module: moduleName(s(d.module)) });
    case 'org.member.updated':
      if (d.role) return tr('collab.actMemberRole', { name: s(d.name), role: role(d.role) });
      if (d.status === 'SUSPENDED') return tr('collab.actMemberSuspended', { name: s(d.name) });
      if (d.status === 'ACTIVE') return tr('collab.actMemberReactivated', { name: s(d.name) });
      return tr('collab.actMemberUpdated', { name: s(d.name) });
    case 'org.member.invited': return tr('collab.actMemberInvited', { email: s(d.email), role: role(d.role) });
    case 'org.invitation.revoked': return tr('collab.actInvitationRevoked', { email: s(d.email) });
    case 'org.member.joined': return tr('collab.actMemberJoined', { role: role(d.role) });
    case 'org.invitation.resent': return tr('collab.actInvitationResent', { email: s(d.email) });
    case 'org.member.left': return tr('collab.actMemberLeft');
    case 'retail.shop.linked': return tr('retail.actLinked', { name: s(d.name) });
    case 'retail.shop.unlinked': return d.reason === 'moved' ? tr('retail.actMoved', { name: s(d.name) }) : tr('retail.actUnlinked', { name: s(d.name) });
    case 'org.deleted': return tr('collab.actOrgDeleted', { name: s(d.name) });
    case 'org.team.created': return tr('collab.actTeamCreated', { name: s(d.name) });
    case 'org.team.updated': return tr('collab.actTeamUpdated', { name: s(d.name) });
    case 'org.team.deleted': return tr('collab.actTeamDeleted', { name: s(d.name) });
    case 'project.members': return tr('tasks.actProjectMembers');
    case 'leave.requested': return tr('hr.actRequested', { days: tr('hr.days', { count: Number(d.days ?? 0) }), type: s(d.type), from: day(s(d.start)), to: day(s(d.end)) });
    case 'leave.cancelled': return d.reason ? tr('hr.actCancelledWithReason', { reason: s(d.reason) }) : tr('hr.actCancelled');
    case 'leave.balance.adjusted': return tr('hr.actAdjusted', { type: s(d.type), year: String(d.year ?? ''), days: Number(d.days) > 0 ? `+${s(d.days)}` : s(d.days), reason: s(d.reason) });
    case 'employee.updated': return tr('hr.actProfileUpdated', { name: s(d.name) });
    case 'leave_type.created': return tr('hr.actTypeCreated', { name: s(d.name) });
    case 'leave_type.updated': return tr('hr.actTypeUpdated', { name: s(d.name) });
    case 'holiday.added': return tr('hr.actHolidayAdded', { name: s(d.name), date: day(s(d.date)) });
    case 'holiday.removed': return tr('hr.actHolidayRemoved', { name: s(d.name), date: day(s(d.date)) });
    case 'holiday.imported': return tr('hr.actHolidaysImported', { year: String(d.year ?? ''), count: Number(d.added ?? 0) });
    case 'business.customer.created': return tr('business.actCustomerCreated', { name: s(d.name) });
    case 'business.customer.updated': return tr('business.actCustomerUpdated', { name: s(d.name) });
    case 'business.customer.archived': return tr('business.actCustomerArchived', { name: s(d.name) });
    case 'business.customer.restored': return tr('business.actCustomerRestored', { name: s(d.name) });
    case 'business.product.created': return tr('business.actProductCreated', { name: s(d.name), price: usd(s(d.price)) });
    case 'business.product.updated': return tr('business.actProductUpdated', { name: s(d.name), price: usd(s(d.price)) });
    case 'business.quote.created': return tr('business.actQuoteCreated', { number: s(d.number), customer: s(d.customer), total: usd(s(d.total)) });
    case 'business.quote.updated': return tr('business.actQuoteUpdated', { number: s(d.number), total: usd(s(d.total)) });
    case 'business.quote.deleted': return tr('business.actQuoteDeleted', { number: s(d.number) });
    case 'business.quote.sent': return tr('business.actQuoteSent', { number: s(d.number) });
    case 'business.quote.accepted': return tr('business.actQuoteAccepted', { number: s(d.number) });
    case 'business.quote.declined': return tr('business.actQuoteDeclined', { number: s(d.number) });
    case 'business.quote.converted': return tr('business.actQuoteConverted', { number: s(d.number) });
    case 'business.invoice.created': return d.quote ? tr('business.actInvoiceFromQuote', { quote: s(d.quote), total: usd(s(d.total)) })
      : tr('business.actInvoiceCreated', { customer: s(d.customer), total: usd(s(d.total)) });
    case 'business.invoice.updated': return tr('business.actInvoiceUpdated', { total: usd(s(d.total)) });
    case 'business.invoice.deleted': return tr('business.actInvoiceDeleted', { total: usd(s(d.total)) });
    case 'business.invoice.issued': return tr('business.actInvoiceIssued', { number: s(d.number), total: usd(s(d.total)), due: d.due ? day(s(d.due)) : '' });
    case 'business.invoice.payment': return tr('business.actPayment', { amount: usd(s(d.amount)), method: METHOD_LABEL[d.method as PaymentMethod] ?? s(d.method) });
    case 'business.invoice.paid': return tr('business.actInvoicePaid', { number: s(d.number) });
    case 'business.invoice.payment_removed': return tr('business.actPaymentRemoved', { amount: usd(s(d.amount)) });
    case 'business.invoice.voided': return tr('business.actInvoiceVoided', { number: s(d.number), reason: s(d.reason) });
    case 'business.category.created': return tr('business.actCategoryCreated', { name: s(d.name) });
    case 'business.category.updated': return tr('business.actCategoryUpdated', { name: s(d.name) });
    case 'business.settings.updated': return tr('business.actSettingsUpdated');
    case 'business.expense.created': return tr('business.actExpenseCreated', { amount: usd(s(d.amount)), category: s(d.category) });
    case 'business.expense.updated': return tr('business.actExpenseUpdated', { amount: usd(s(d.amount)) });
    case 'business.expense.submitted': return tr('business.actExpenseSubmitted', { amount: usd(s(d.amount)) });
    case 'business.expense.self_approved': return tr('business.actExpenseSelfApproved');
    case 'business.expense.deleted': return tr('business.actExpenseDeleted', { amount: usd(s(d.amount)) });
    case 'business.expense.paid': return tr('business.actExpensePaid', { amount: usd(s(d.amount)) });
    case 'business.expense.receipt': return tr('business.actExpenseReceipt', { name: s(d.name) });
    case 'approval.requested': return tr('tasks.actApprovalRequested', { workflow: s(d.workflow) });
    case 'approval.step.approved': return tr('tasks.actStepApproved');
    case 'approval.approved': return tr('tasks.actApproved');
    case 'approval.rejected': return d.comment ? tr('tasks.actRejectedWithComment', { comment: s(d.comment) }) : tr('tasks.actRejected');
    case 'approval.changes_requested': return tr('tasks.actChangesRequested', { comment: s(d.comment) });
    case 'approval.resubmitted': return tr('tasks.actResubmitted');
    case 'approval.withdrawn': return tr('tasks.actWithdrawn');
    default: return a.verb;
  }
}

export function ActivityTimeline({ items, empty }: { items: ActivityItem[]; empty?: string }) {
  if (items.length === 0) return <p className="py-4 text-sm text-muted-foreground">{empty ?? tr('tasks.noActivity')}</p>;
  return (
    <ol className="relative space-y-4 border-l pl-5">
      {items.map((a) => (
        <li key={a.id} className="relative">
          <span className="absolute -left-[29px] top-0.5"><PersonAvatar person={a.actor} className="size-5" /></span>
          <p className="text-sm"><span className="font-medium">{a.actor?.name ?? tr('tasks.system')}</span> {describe(a)}</p>
          <p className="text-xs text-muted-foreground">{ago(a.at)}</p>
        </li>
      ))}
    </ol>
  );
}
