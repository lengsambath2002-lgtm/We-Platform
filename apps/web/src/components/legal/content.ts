// Terms of Service and Privacy Policy, in English and Khmer. A starting point written for this product: have a
// lawyer in Cambodia review both before launch. Bump LEGAL_VERSION (and the API's PLATFORM_LEGAL_VERSION) on changes.

/** The version people accept at sign-up (the date it took effect). */
export const LEGAL_VERSION = '2026-11-02';

/** Who runs the service; set NEXT_PUBLIC_LEGAL_* when deploying. */
export const OPERATOR = {
  name: process.env.NEXT_PUBLIC_LEGAL_NAME || 'Business Platform',
  address: process.env.NEXT_PUBLIC_LEGAL_ADDRESS || 'Phnom Penh, Kingdom of Cambodia',
  email: process.env.NEXT_PUBLIC_LEGAL_EMAIL || '',
};

export interface Section {
  title: string;
  body: string[];
}

export interface LegalDoc {
  title: string;
  lead: string;
  sections: Section[];
}

type Docs = Record<'terms' | 'privacy', LegalDoc>;

const contact = (km: boolean) => OPERATOR.email
  ? (km ? `អ៊ីមែល ${OPERATOR.email} ឬផ្ញើលិខិតទៅ ${OPERATOR.name}, ${OPERATOR.address}។` : `Email ${OPERATOR.email} or write to ${OPERATOR.name}, ${OPERATOR.address}.`)
  : (km ? `សរសេរទៅ ${OPERATOR.name}, ${OPERATOR.address}។` : `Write to ${OPERATOR.name}, ${OPERATOR.address}.`);

export const en: Docs = {
  terms: {
    title: 'Terms of Service',
    lead: `These terms are an agreement between you and ${OPERATOR.name} (“we”) about using Business Platform. By creating an account or using the service you accept them. If you use it for an organisation, you accept them for that organisation too.`,
    sections: [
      { title: '1. Your account', body: [
        'Give your real name and an email address you use. Keep your password to yourself; we recommend two-step sign-in. You are responsible for what is done with your account.',
        'One account is for one person. You must be at least 18, or old enough to sign contracts where you live.',
      ] },
      { title: '2. Organisations', body: [
        'An organisation (a company, school or shop) belongs to the customer that created it. Its primary owner decides who the owners and admins are; admins invite members and choose what each can do.',
        'Owners and admins can see and manage the work of the organisation, including records about members such as HR and payroll records, according to the roles they set.',
      ] },
      { title: '3. Plans, invoices and payment', body: [
        'Paid plans are priced per member (guests are free), per month or per year, in US dollars, as shown on the Plans page. New organisations get a 14-day trial of Standard; afterwards they move to Free unless a plan is chosen.',
        'We issue an invoice at the start of each period, payable within 7 days by bank transfer or KHQR. If it is not paid, the subscription becomes past due; 14 days later the organisation moves to Free, and modules outside Free are locked until it pays. Its data is kept.',
        'Prices exclude taxes that apply by law. A plan changed or cancelled during a period runs to the end of that period; we do not refund partial periods unless the law requires it.',
      ] },
      { title: '4. Your data', body: [
        'What you and your organisation put into Business Platform stays yours. We use it only to run the service for you, as described in the Privacy Policy.',
        'Owners can download all of their organisation’s data at any time (Settings → Export all data). A deleted organisation disappears at once; ask us to erase its data for good.',
      ] },
      { title: '5. Acceptable use', body: [
        'Do not use the service to break the law, to send spam, to harm others or their data, to store content you have no right to, to test our security without written permission, or to resell the service without an agreement with us.',
      ] },
      { title: '6. The service', body: [
        'We work to keep the service running and your data safe, with daily backups, but we provide it “as is” and cannot promise it will never be interrupted. We may change or improve features; we will tell owners in advance about changes that take something important away.',
      ] },
      { title: '7. Suspension and ending', body: [
        'We may suspend an account or organisation that breaks these terms or does not pay, and will tell you why. You can stop at any time: delete your account from your account page, or delete your organisation from its settings.',
      ] },
      { title: '8. Liability', body: [
        'As far as the law allows, we are not liable for indirect losses such as lost profits, and our total liability for any claim is limited to what the organisation paid us in the 12 months before it. Nothing here limits liability that cannot be limited by law.',
      ] },
      { title: '9. Changes to these terms', body: [
        'We will tell you by email or in the app at least 30 days before new terms take effect. If you keep using the service afterwards, you accept them.',
      ] },
      { title: '10. Law and contact', body: [
        'These terms are governed by the laws of the Kingdom of Cambodia, and disputes go to the courts of Phnom Penh, unless the law where you live gives you other rights.',
        contact(false),
      ] },
    ],
  },
  privacy: {
    title: 'Privacy Policy',
    lead: `How ${OPERATOR.name} (“we”) handles personal information in Business Platform. For the records an organisation keeps about its people (HR, payroll, tasks), the organisation decides how they are used and we process them on its behalf; for your account itself, we are responsible.`,
    sections: [
      { title: '1. What we collect', body: [
        'Account: your name, email, language, photo if you add one, and your password as a one-way hash (we never see it).',
        'Security: the devices you sign in on (browser, IP address, times), failed sign-ins, and two-step sign-in settings (the authenticator key is stored encrypted).',
        'Your work: what you and your organisation enter, such as tasks, documents, approvals, HR and payroll records, and uploaded files.',
        'Billing: the plan, invoices, and the payment references you give us. We never receive card or bank passwords.',
      ] },
      { title: '2. Why we use it', body: [
        'To run the service, keep accounts safe, send the emails you need (invitations, password resets, notifications you can turn off per kind), bill organisations, and meet legal duties. We do not sell personal information or use it for advertising.',
      ] },
      { title: '3. Who sees it', body: [
        'People in your organisation, according to the roles its owners and admins set. Our platform administrators only when needed to support or protect the service; every time they open an organisation or account it is logged.',
        'Service providers who help us run it: hosting and storage, our email provider, and banks for payments. Apps you connect, such as VirtualBiz single sign-on, get only what you consent to. We disclose information to authorities only when the law requires it.',
      ] },
      { title: '4. How long we keep it', body: [
        'Account information is kept while the account exists. When you delete your account, your sign-in, email, photo and devices are erased; organisations keep the records they need as your employer, such as your name on tasks and in HR and payroll.',
        'Signed-in devices expire after 30 days of not being used. Backups are kept for 30 days and then deleted.',
      ] },
      { title: '5. How we protect it', body: [
        'Encrypted connections (HTTPS), hashed passwords, optional two-step sign-in, lockout after repeated wrong passwords, permission checks on every request, encrypted authenticator keys, and logs of administrator access.',
      ] },
      { title: '6. Your choices', body: [
        'Correct your name and language, choose which notifications you receive by email, see and sign out your devices, and delete your account, all from your account page. Owners can export all of their organisation’s data. For records your organisation keeps about you, ask its administrators.',
      ] },
      { title: '7. Cookies', body: [
        'We use one cookie, to keep you signed in, and your browser’s storage for preferences such as theme and language. No advertising or tracking cookies.',
      ] },
      { title: '8. Changes and contact', body: [
        'We will tell you before important changes to this policy take effect.',
        contact(false),
      ] },
    ],
  },
};

export const km: Docs = {
  terms: {
    title: 'លក្ខខណ្ឌប្រើប្រាស់',
    lead: `លក្ខខណ្ឌទាំងនេះជាកិច្ចព្រមព្រៀងរវាងអ្នក និង ${OPERATOR.name} („យើង“) អំពីការប្រើប្រាស់ Business Platform។ ដោយការបង្កើតគណនី ឬប្រើប្រាស់សេវាកម្ម អ្នកយល់ព្រមនឹងលក្ខខណ្ឌទាំងនេះ។ ប្រសិនបើអ្នកប្រើសម្រាប់ស្ថាប័នមួយ អ្នកក៏យល់ព្រមជំនួសស្ថាប័ននោះដែរ។`,
    sections: [
      { title: '១. គណនីរបស់អ្នក', body: [
        'សូមផ្ដល់ឈ្មោះពិត និងអាសយដ្ឋានអ៊ីមែលដែលអ្នកប្រើ។ រក្សាពាក្យសម្ងាត់ជាការសម្ងាត់ យើងណែនាំឱ្យប្រើការចូលពីរជំហាន។ អ្នកទទួលខុសត្រូវចំពោះអ្វីដែលធ្វើដោយគណនីរបស់អ្នក។',
        'គណនីមួយសម្រាប់មនុស្សម្នាក់។ អ្នកត្រូវមានអាយុយ៉ាងតិច ១៨ ឆ្នាំ ឬគ្រប់អាយុអាចចុះកិច្ចសន្យាតាមច្បាប់ដែលអ្នករស់នៅ។',
      ] },
      { title: '២. ស្ថាប័ន', body: [
        'ស្ថាប័ន (ក្រុមហ៊ុន សាលា ឬហាង) ជាកម្មសិទ្ធិរបស់អតិថិជនដែលបានបង្កើតវា។ ម្ចាស់ចម្បងសម្រេចថាអ្នកណាជាម្ចាស់ និងអ្នកគ្រប់គ្រង។ អ្នកគ្រប់គ្រងអញ្ជើញសមាជិក និងជ្រើសរើសអ្វីដែលម្នាក់ៗអាចធ្វើបាន។',
        'ម្ចាស់ និងអ្នកគ្រប់គ្រងអាចមើល និងគ្រប់គ្រងការងាររបស់ស្ថាប័ន រួមទាំងកំណត់ត្រាអំពីសមាជិក ដូចជាកំណត់ត្រាធនធានមនុស្ស និងប្រាក់បៀវត្សរ៍ តាមតួនាទីដែលពួកគេកំណត់។',
      ] },
      { title: '៣. គម្រោង វិក្កយបត្រ និងការបង់ប្រាក់', body: [
        'គម្រោងបង់ប្រាក់គិតតម្លៃក្នុងមួយសមាជិក (ភ្ញៀវឥតគិតថ្លៃ) ប្រចាំខែ ឬប្រចាំឆ្នាំ ជាដុល្លារអាមេរិក ដូចបង្ហាញនៅទំព័រគម្រោង។ ស្ថាប័នថ្មីទទួលបានការសាកល្បង Standard រយៈពេល ១៤ ថ្ងៃ បន្ទាប់មកប្ដូរទៅ Free លុះត្រាតែជ្រើសរើសគម្រោង។',
        'យើងចេញវិក្កយបត្រនៅដើមរយៈពេលនីមួយៗ ត្រូវបង់ក្នុងរយៈពេល ៧ ថ្ងៃ តាមការផ្ទេរប្រាក់ ឬ KHQR។ ប្រសិនបើមិនបង់ ការជាវក្លាយជាហួសកាលកំណត់។ ១៤ ថ្ងៃក្រោយមក ស្ថាប័នប្ដូរទៅ Free ហើយម៉ូឌុលដែលនៅក្រៅ Free ត្រូវចាក់សោរហូតដល់បង់ប្រាក់។ ទិន្នន័យនៅរក្សាទុក។',
        'តម្លៃមិនរួមបញ្ចូលពន្ធដែលអនុវត្តតាមច្បាប់។ គម្រោងដែលប្ដូរ ឬលុបចោលក្នុងរយៈពេលមួយ បន្តដល់ចុងរយៈពេលនោះ។ យើងមិនសងប្រាក់វិញសម្រាប់រយៈពេលមិនពេញ លើកលែងតែច្បាប់តម្រូវ។',
      ] },
      { title: '៤. ទិន្នន័យរបស់អ្នក', body: [
        'អ្វីដែលអ្នក និងស្ថាប័នរបស់អ្នកបញ្ចូលក្នុង Business Platform នៅតែជារបស់អ្នក។ យើងប្រើវាសម្រាប់តែដំណើរការសេវាកម្មជូនអ្នក ដូចបានពិពណ៌នាក្នុងគោលការណ៍ឯកជនភាព។',
        'ម្ចាស់អាចទាញយកទិន្នន័យទាំងអស់របស់ស្ថាប័នបានគ្រប់ពេល (ការកំណត់ → នាំចេញទិន្នន័យទាំងអស់)។ ស្ថាប័នដែលបានលុបនឹងបាត់ភ្លាមៗ សូមស្នើយើងឱ្យលុបទិន្នន័យរបស់វាជាអចិន្ត្រៃយ៍។',
      ] },
      { title: '៥. ការប្រើប្រាស់ដែលអាចទទួលយកបាន', body: [
        'កុំប្រើសេវាកម្មដើម្បីបំពានច្បាប់ ផ្ញើសារឥតបានការ ធ្វើបាបអ្នកដទៃ ឬទិន្នន័យរបស់ពួកគេ រក្សាទុកមាតិកាដែលអ្នកគ្មានសិទ្ធិ សាកល្បងសុវត្ថិភាពរបស់យើងដោយគ្មានការអនុញ្ញាតជាលាយលក្ខណ៍អក្សរ ឬលក់បន្តសេវាកម្មដោយគ្មានកិច្ចព្រមព្រៀងជាមួយយើង។',
      ] },
      { title: '៦. សេវាកម្ម', body: [
        'យើងខិតខំរក្សាសេវាកម្មឱ្យដំណើរការ និងទិន្នន័យរបស់អ្នកឱ្យមានសុវត្ថិភាព ដោយមានការបម្រុងទុកប្រចាំថ្ងៃ ប៉ុន្តែយើងផ្ដល់វា „តាមស្ថានភាពជាក់ស្ដែង“ ហើយមិនអាចសន្យាថាវានឹងមិនរំខានទេ។ យើងអាចផ្លាស់ប្ដូរ ឬកែលម្អមុខងារ ហើយនឹងប្រាប់ម្ចាស់ជាមុនអំពីការផ្លាស់ប្ដូរដែលដកយកអ្វីសំខាន់ចេញ។',
      ] },
      { title: '៧. ការផ្អាក និងការបញ្ចប់', body: [
        'យើងអាចផ្អាកគណនី ឬស្ថាប័នដែលបំពានលក្ខខណ្ឌទាំងនេះ ឬមិនបង់ប្រាក់ ហើយនឹងប្រាប់អ្នកពីមូលហេតុ។ អ្នកអាចឈប់បានគ្រប់ពេល៖ លុបគណនីរបស់អ្នកពីទំព័រគណនី ឬលុបស្ថាប័នពីការកំណត់របស់វា។',
      ] },
      { title: '៨. ការទទួលខុសត្រូវ', body: [
        'តាមដែលច្បាប់អនុញ្ញាត យើងមិនទទួលខុសត្រូវចំពោះការខាតបង់ដោយប្រយោល ដូចជាការបាត់បង់ប្រាក់ចំណេញ ហើយការទទួលខុសត្រូវសរុបរបស់យើងសម្រាប់ការទាមទារណាមួយ មិនលើសពីអ្វីដែលស្ថាប័នបានបង់ឱ្យយើងក្នុងរយៈពេល ១២ ខែមុននោះទេ។ គ្មានអ្វីនៅទីនេះកំណត់ការទទួលខុសត្រូវដែលច្បាប់មិនអនុញ្ញាតឱ្យកំណត់ទេ។',
      ] },
      { title: '៩. ការផ្លាស់ប្ដូរលក្ខខណ្ឌ', body: [
        'យើងនឹងប្រាប់អ្នកតាមអ៊ីមែល ឬក្នុងកម្មវិធី យ៉ាងតិច ៣០ ថ្ងៃមុនលក្ខខណ្ឌថ្មីចូលជាធរមាន។ ប្រសិនបើអ្នកបន្តប្រើសេវាកម្មបន្ទាប់ពីនោះ អ្នកយល់ព្រមនឹងវា។',
      ] },
      { title: '១០. ច្បាប់ និងទំនាក់ទំនង', body: [
        'លក្ខខណ្ឌទាំងនេះស្ថិតក្រោមច្បាប់នៃព្រះរាជាណាចក្រកម្ពុជា ហើយវិវាទត្រូវដោះស្រាយនៅតុលាការរាជធានីភ្នំពេញ លើកលែងតែច្បាប់ដែលអ្នករស់នៅផ្ដល់សិទ្ធិផ្សេងទៀតដល់អ្នក។',
        contact(true),
      ] },
    ],
  },
  privacy: {
    title: 'គោលការណ៍ឯកជនភាព',
    lead: `របៀបដែល ${OPERATOR.name} („យើង“) គ្រប់គ្រងព័ត៌មានផ្ទាល់ខ្លួនក្នុង Business Platform។ សម្រាប់កំណត់ត្រាដែលស្ថាប័នរក្សាទុកអំពីបុគ្គលិករបស់ខ្លួន (ធនធានមនុស្ស ប្រាក់បៀវត្សរ៍ កិច្ចការ) ស្ថាប័នសម្រេចពីរបៀបប្រើប្រាស់ ហើយយើងដំណើរការជំនួសវា។ សម្រាប់គណនីរបស់អ្នកផ្ទាល់ យើងជាអ្នកទទួលខុសត្រូវ។`,
    sections: [
      { title: '១. អ្វីដែលយើងប្រមូល', body: [
        'គណនី៖ ឈ្មោះ អ៊ីមែល ភាសា រូបថតប្រសិនបើអ្នកបន្ថែម និងពាក្យសម្ងាត់ជាសញ្ញាសម្ងាត់មួយផ្លូវ (យើងមិនដែលឃើញវាទេ)។',
        'សុវត្ថិភាព៖ ឧបករណ៍ដែលអ្នកចូល (កម្មវិធីរុករក អាសយដ្ឋាន IP ពេលវេលា) ការចូលមិនជោគជ័យ និងការកំណត់ការចូលពីរជំហាន (សោផ្ទៀងផ្ទាត់ត្រូវបានរក្សាទុកដោយអ៊ិនគ្រីប)។',
        'ការងាររបស់អ្នក៖ អ្វីដែលអ្នក និងស្ថាប័នបញ្ចូល ដូចជាកិច្ចការ ឯកសារ ការអនុម័ត កំណត់ត្រាធនធានមនុស្ស និងប្រាក់បៀវត្សរ៍ និងឯកសារដែលបានផ្ទុកឡើង។',
        'វិក្កយបត្រ៖ គម្រោង វិក្កយបត្រ និងលេខយោងការបង់ប្រាក់ដែលអ្នកផ្ដល់ឱ្យយើង។ យើងមិនដែលទទួលពាក្យសម្ងាត់កាត ឬធនាគារទេ។',
      ] },
      { title: '២. មូលហេតុដែលយើងប្រើវា', body: [
        'ដើម្បីដំណើរការសេវាកម្ម រក្សាសុវត្ថិភាពគណនី ផ្ញើអ៊ីមែលដែលអ្នកត្រូវការ (ការអញ្ជើញ ការកំណត់ពាក្យសម្ងាត់ឡើងវិញ ការជូនដំណឹងដែលអ្នកអាចបិទតាមប្រភេទ) ចេញវិក្កយបត្រជូនស្ថាប័ន និងបំពេញកាតព្វកិច្ចផ្លូវច្បាប់។ យើងមិនលក់ព័ត៌មានផ្ទាល់ខ្លួន ឬប្រើវាសម្រាប់ការផ្សាយពាណិជ្ជកម្មទេ។',
      ] },
      { title: '៣. អ្នកណាឃើញវា', body: [
        'មនុស្សក្នុងស្ថាប័នរបស់អ្នក តាមតួនាទីដែលម្ចាស់ និងអ្នកគ្រប់គ្រងកំណត់។ អ្នកគ្រប់គ្រងវេទិការបស់យើង តែនៅពេលចាំបាច់ដើម្បីគាំទ្រ ឬការពារសេវាកម្ម រាល់ពេលដែលពួកគេបើកស្ថាប័ន ឬគណនីណាមួយ ត្រូវបានកត់ត្រាទុក។',
        'អ្នកផ្ដល់សេវាដែលជួយយើងដំណើរការ៖ ការបង្ហោះ និងការផ្ទុក អ្នកផ្ដល់សេវាអ៊ីមែល និងធនាគារសម្រាប់ការបង់ប្រាក់។ កម្មវិធីដែលអ្នកភ្ជាប់ ដូចជាការចូលតែមួយ VirtualBiz ទទួលបានតែអ្វីដែលអ្នកយល់ព្រម។ យើងបង្ហាញព័ត៌មានដល់អាជ្ញាធរ តែនៅពេលច្បាប់តម្រូវ។',
      ] },
      { title: '៤. រយៈពេលរក្សាទុក', body: [
        'ព័ត៌មានគណនីត្រូវបានរក្សាទុកដរាបណាគណនីនៅមាន។ នៅពេលអ្នកលុបគណនី ការចូល អ៊ីមែល រូបថត និងឧបករណ៍របស់អ្នកត្រូវបានលុប។ ស្ថាប័នរក្សាទុកកំណត់ត្រាដែលពួកគេត្រូវការក្នុងនាមជានិយោជក ដូចជាឈ្មោះរបស់អ្នកលើកិច្ចការ និងក្នុងធនធានមនុស្ស និងប្រាក់បៀវត្សរ៍។',
        'ឧបករណ៍ដែលបានចូលផុតកំណត់បន្ទាប់ពី ៣០ ថ្ងៃដែលមិនប្រើ។ ការបម្រុងទុកត្រូវបានរក្សាទុក ៣០ ថ្ងៃ ហើយបន្ទាប់មកលុបចោល។',
      ] },
      { title: '៥. របៀបដែលយើងការពារវា', body: [
        'ការតភ្ជាប់ដែលបានអ៊ិនគ្រីប (HTTPS) ពាក្យសម្ងាត់ជាសញ្ញាសម្ងាត់ ការចូលពីរជំហានជាជម្រើស ការចាក់សោបន្ទាប់ពីពាក្យសម្ងាត់ខុសច្រើនដង ការពិនិត្យសិទ្ធិលើគ្រប់សំណើ សោផ្ទៀងផ្ទាត់ដែលបានអ៊ិនគ្រីប និងកំណត់ហេតុនៃការចូលប្រើរបស់អ្នកគ្រប់គ្រង។',
      ] },
      { title: '៦. ជម្រើសរបស់អ្នក', body: [
        'កែឈ្មោះ និងភាសា ជ្រើសរើសការជូនដំណឹងដែលអ្នកទទួលតាមអ៊ីមែល មើល និងចាកចេញពីឧបករណ៍ ហើយលុបគណនីរបស់អ្នក ទាំងអស់ពីទំព័រគណនី។ ម្ចាស់អាចនាំចេញទិន្នន័យទាំងអស់របស់ស្ថាប័ន។ សម្រាប់កំណត់ត្រាដែលស្ថាប័នរក្សាទុកអំពីអ្នក សូមសួរអ្នកគ្រប់គ្រងរបស់វា។',
      ] },
      { title: '៧. ខូគី', body: [
        'យើងប្រើខូគីមួយ ដើម្បីរក្សាអ្នកឱ្យនៅចូល និងកន្លែងផ្ទុករបស់កម្មវិធីរុករកសម្រាប់ចំណូលចិត្ត ដូចជារូបរាង និងភាសា។ គ្មានខូគីផ្សាយពាណិជ្ជកម្ម ឬតាមដានទេ។',
      ] },
      { title: '៨. ការផ្លាស់ប្ដូរ និងទំនាក់ទំនង', body: [
        'យើងនឹងប្រាប់អ្នកមុនការផ្លាស់ប្ដូរសំខាន់ៗចំពោះគោលការណ៍នេះចូលជាធរមាន។',
        contact(true),
      ] },
    ],
  },
};
