/** Task status, priority and approval status names. */
export const en = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  IN_REVIEW: 'In review',
  DONE: 'Done',
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  URGENT: 'Urgent',
  priorityOf: '{priority} priority',
  PENDING: 'Pending',
  CHANGES_REQUESTED: 'Changes requested',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
};

export const km: Record<keyof typeof en, string> = {
  TODO: 'ត្រូវធ្វើ',
  IN_PROGRESS: 'កំពុងធ្វើ',
  IN_REVIEW: 'កំពុងពិនិត្យ',
  DONE: 'រួចរាល់',
  LOW: 'ទាប',
  MEDIUM: 'មធ្យម',
  HIGH: 'ខ្ពស់',
  URGENT: 'បន្ទាន់',
  priorityOf: 'អាទិភាព{priority}',
  PENDING: 'កំពុងរង់ចាំ',
  CHANGES_REQUESTED: 'បានស្នើឱ្យកែ',
  APPROVED: 'បានអនុម័ត',
  REJECTED: 'បានបដិសេធ',
  WITHDRAWN: 'បានដកវិញ',
};
