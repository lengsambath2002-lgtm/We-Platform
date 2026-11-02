'use client';

import { moduleName } from '@/components/shell/nav';
import { useT } from '@/i18n/provider';
import { useOrg } from '@/lib/org-context';

import { RouteTabs } from './route-tabs';

/** HR home: my leave, and for HR managers everyone's leave requests and the workforce at a glance. */
export function HrHomeTabs() {
  const { t } = useT();
  const { can } = useOrg();
  return (
    <RouteTabs tabs={[
      { href: 'hr', label: t('shell.tabMyLeave'), exact: true },
      ...(can('hr.manage') ? [{ href: 'hr/requests', label: t('shell.tabLeaveRequests') }, { href: 'hr/dashboard', label: t('shell.tabWorkforce') }] : []),
    ]} />
  );
}

/** Attendance: mine, and for managers the team's board. */
export function AttendanceTabs() {
  const { t } = useT();
  const { can } = useOrg();
  return <RouteTabs tabs={[{ href: 'hr/attendance', label: t('shell.tabMyAttendance'), exact: true }, ...(can('hr.team') ? [{ href: 'hr/attendance/board', label: t('shell.tabTeam') }] : [])]} />;
}

/** One Reports hub: the workspace reports, and each switched-on app's reports the person may see. */
export function ReportsTabs() {
  const { t } = useT();
  const { can, modules } = useOrg();
  const app = (key: string, perm: string) => {
    const m = modules.find((x) => x.key === key && x.enabled);
    return m && can(perm) ? [{ href: `${key}/reports`, label: moduleName(m) }] : [];
  };
  return (
    <RouteTabs tabs={[
      ...(can('reports.view') ? [{ href: 'reports', label: t('shell.tabWorkspace'), exact: true }] : []),
      ...app('business', 'business.manage'),
      ...app('education', 'education.view'),
      ...app('service', 'service.manage'),
    ]} />
  );
}

/** Purchasing: the overview, and the purchase orders. */
export function PurchasingTabs() {
  const { t } = useT();
  return <RouteTabs tabs={[{ href: 'business/purchasing', label: t('shell.tabOverview') }, { href: 'business/purchase-orders', label: t('retail.navOrders') }]} />;
}

/** Students and their parents and guardians. */
export function StudentsTabs() {
  const { t } = useT();
  return <RouteTabs tabs={[{ href: 'education/students', label: t('education.navStudents'), exact: true }, { href: 'education/parents', label: t('education.navParents') }]} />;
}

/** Exams and grades: setting assessments up, and the gradebook where the scores go. */
export function AssessmentsTabs() {
  const { t } = useT();
  return <RouteTabs tabs={[{ href: 'education/exams', label: t('education.navExams') }, { href: 'education/gradebook', label: t('education.navGradebook') }]} />;
}

/** Growth: performance reviews and training. */
export function GrowthTabs() {
  const { t } = useT();
  return <RouteTabs tabs={[{ href: 'hr/reviews', label: t('hr.navReviews') }, { href: 'hr/training', label: t('hr.navTraining') }]} />;
}
