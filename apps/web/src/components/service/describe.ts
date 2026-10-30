import { PRIORITY_LABEL } from '@/components/common/badges';
import { tr } from '@/i18n/core';
import type { ActivityItem, Priority } from '@/lib/api/types';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';

import { ASSET_LABEL, TICKET_LABEL } from './ui';

const s = (v: unknown) => (typeof v === 'string' ? v : '');
const ticket = (v: unknown) => TICKET_LABEL[v as keyof typeof TICKET_LABEL] ?? s(v);
const role = (v: unknown) => (v === 'DISPATCHER' ? tr('service.roleDispatcher') : v === 'TECHNICIAN' ? tr('service.roleTechnician') : s(v));

/** A plain sentence for a Service & Operations activity entry, in the current language (history, audit log). */
export function describeService(a: ActivityItem): string {
  const d = a.data;
  switch (a.verb) {
    case 'service.ticket.created': return tr('service.actTicketCreated', { number: s(d.number), title: s(d.title) });
    case 'service.ticket.updated': return d.priority ? tr('service.actTicketPriority', { priority: PRIORITY_LABEL[(d.priority as { to: Priority }).to] ?? '' })
      : tr('service.actTicketUpdated');
    case 'service.ticket.status': return tr('service.actTicketStatus', { from: ticket(d.from), to: ticket(d.to) });
    case 'service.ticket.reopened': return tr('service.actTicketReopened');
    case 'service.ticket.assigned': return tr('service.actTicketAssigned', { name: s(d.name) });
    case 'service.ticket.unassigned': return tr('service.actTicketUnassigned');
    case 'service.ticket.commented': return tr('service.actTicketCommented', { excerpt: s(d.excerpt) });
    case 'service.ticket.attached': return tr('service.actTicketAttached', { name: s(d.name) });
    case 'service.ticket.detached': return tr('service.actTicketDetached', { name: s(d.name) });
    case 'service.ticket.workorder': return tr('service.actTicketWorkOrder', { number: s(d.workOrder) });
    case 'service.workorder.created': return d.ticket ? tr('service.actWoFromTicket', { number: s(d.number), ticket: s(d.ticket) })
      : tr('service.actWoCreated', { number: s(d.number), title: s(d.title) });
    case 'service.workorder.generated': return tr('service.actWoGenerated', { number: s(d.number), date: d.due ? day(s(d.due)) : '' });
    case 'service.workorder.updated': return tr('service.actWoUpdated', { total: usd(s(d.total)) });
    case 'service.workorder.scheduled': return tr('service.actWoScheduled', { date: d.start ? day(s(d.start)) : '' });
    case 'service.workorder.self_approved': return tr('service.actWoSelfApproved');
    case 'service.workorder.started': return tr('service.actWoStarted');
    case 'service.workorder.resumed': return tr('service.actWoResumed');
    case 'service.workorder.held': return tr('service.actWoHeld', { reason: s(d.reason) });
    case 'service.workorder.checked': return tr('service.actWoChecked', { label: s(d.label) });
    case 'service.workorder.unchecked': return tr('service.actWoUnchecked', { label: s(d.label) });
    case 'service.workorder.lines': return tr('service.actWoLines', { total: usd(s(d.total)) });
    case 'service.workorder.completed': return tr('service.actWoCompleted', { name: s(d.signedBy) });
    case 'service.workorder.cancelled': return tr('service.actWoCancelled', { reason: s(d.reason) });
    case 'service.customer.created': return tr('service.actCustomerCreated', { name: s(d.name) });
    case 'service.customer.updated': return tr('service.actCustomerUpdated', { name: s(d.name) });
    case 'service.customer.archived': return tr('service.actCustomerArchived', { name: s(d.name) });
    case 'service.customer.restored': return tr('service.actCustomerRestored', { name: s(d.name) });
    case 'service.site.added': return tr('service.actSiteAdded', { name: s(d.name) });
    case 'service.site.removed': return tr('service.actSiteRemoved', { name: s(d.name) });
    case 'service.asset.created': return tr('service.actAssetCreated', { name: s(d.name) });
    case 'service.asset.updated': return tr('service.actAssetUpdated', { name: s(d.name), status: ASSET_LABEL[d.status as keyof typeof ASSET_LABEL] ?? s(d.status) });
    case 'service.plan.created': return tr('service.actPlanCreated', { title: s(d.title), date: d.due ? day(s(d.due)) : '' });
    case 'service.plan.updated': return tr('service.actPlanUpdated', { title: s(d.title) });
    case 'service.staff.added': return tr('service.actStaffAdded', { name: s(d.name), role: role(d.role) });
    case 'service.staff.updated': return d.active === false ? tr('service.actStaffOff', { name: s(d.name) }) : tr('service.actStaffUpdated', { name: s(d.name), role: role(d.role) });
    case 'service.settings.updated': return tr('service.actSettingsUpdated');
    case 'service.sla.updated': return tr('service.actSlaUpdated');
    default: return a.verb;
  }
}
