/**
 * Industry modules: what each does (`about_<key>`) and what is planned first (`planned_<key>`,
 * one item per line — split on '\n'). Also the "not available yet" module page.
 */
export const en = {
  about_projects: 'Projects, tasks with a board and list, subtasks, comments, attachments and approvals.',
  about_business: 'Customers, sales, purchasing and finance for any company.',
  about_education: 'Students, classes, attendance, grades and fees.',
  about_retail: 'Shop, point of sale, stock and orders (VirtualBiz joins here).',
  about_hr: 'Employees, leave, attendance and payroll.',
  about_service: 'Bookings, work orders, field service and assets.',
  planned_business: 'Customers and contacts\nQuotes, sales orders and invoices\nPurchasing and suppliers\nExpenses and simple finance reports',
  planned_education: 'Students and guardians\nClasses, timetables and attendance\nAssessments and report cards\nFees and payments',
  planned_retail: 'Online shop and point of sale (VirtualBiz)\nProducts, stock and suppliers\nOrders, returns and receipts',
  planned_hr: 'Employee records\nLeave requests (using the approval engine)\nAttendance and payroll inputs',
  planned_service: 'Bookings and schedules\nWork orders and field visits\nAssets and maintenance',
  notAvailable: 'Not available yet',
  plannedBody: 'This module is planned. It will use the shared core you already have — people, roles, approvals, calendar and notifications. Planned first:',
  backToOverview: 'Back to overview',
  manageModules: 'Manage modules',
};

export const km: Record<keyof typeof en, string> = {
  about_projects: 'គម្រោង កិច្ចការជាផ្ទាំង និងបញ្ជី កិច្ចការរង មតិយោបល់ ឯកសារភ្ជាប់ និងការអនុម័ត។',
  about_business: 'អតិថិជន ការលក់ ការទិញ និងហិរញ្ញវត្ថុ សម្រាប់ក្រុមហ៊ុនគ្រប់ប្រភេទ។',
  about_education: 'សិស្ស ថ្នាក់រៀន វត្តមាន ពិន្ទុ និងថ្លៃសិក្សា។',
  about_retail: 'ហាង ការលក់នៅបញ្ជរ (POS) ស្តុក និងការបញ្ជាទិញ (VirtualBiz ចូលរួមនៅទីនេះ)។',
  about_hr: 'បុគ្គលិក ការឈប់សម្រាក វត្តមាន និងប្រាក់បៀវត្សរ៍។',
  about_service: 'ការកក់ ការងារបញ្ជា សេវាកម្មនៅទីតាំង និងទ្រព្យសម្បត្តិ។',
  planned_business: 'អតិថិជន និងទំនាក់ទំនង\nសម្រង់តម្លៃ ការបញ្ជាលក់ និងវិក្កយបត្រ\nការទិញ និងអ្នកផ្គត់ផ្គង់\nការចំណាយ និងរបាយការណ៍ហិរញ្ញវត្ថុសាមញ្ញ',
  planned_education: 'សិស្ស និងអាណាព្យាបាល\nថ្នាក់រៀន កាលវិភាគ និងវត្តមាន\nការវាយតម្លៃ និងព្រឹត្តិបត្រពិន្ទុ\nថ្លៃសិក្សា និងការបង់ប្រាក់',
  planned_retail: 'ហាងអនឡាញ និងការលក់នៅបញ្ជរ (VirtualBiz)\nផលិតផល ស្តុក និងអ្នកផ្គត់ផ្គង់\nការបញ្ជាទិញ ការប្រគល់ត្រឡប់ និងបង្កាន់ដៃ',
  planned_hr: 'កំណត់ត្រាបុគ្គលិក\nសំណើសុំឈប់សម្រាក (ប្រើប្រព័ន្ធអនុម័ត)\nទិន្នន័យវត្តមាន និងប្រាក់បៀវត្សរ៍',
  planned_service: 'ការកក់ និងកាលវិភាគ\nការងារបញ្ជា និងការចុះទីតាំង\nទ្រព្យសម្បត្តិ និងការថែទាំ',
  notAvailable: 'មិនទាន់មាននៅឡើយ',
  plannedBody: 'ម៉ូឌុលនេះស្ថិតក្នុងគម្រោង។ វានឹងប្រើមូលដ្ឋានរួមដែលអ្នកមានរួចហើយ — មនុស្ស តួនាទី ការអនុម័ត ប្រតិទិន និងការជូនដំណឹង។ អ្វីដែលគ្រោងធ្វើមុនគេ៖',
  backToOverview: 'ត្រឡប់ទៅទិដ្ឋភាពទូទៅ',
  manageModules: 'គ្រប់គ្រងម៉ូឌុល',
};
