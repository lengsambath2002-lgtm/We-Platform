# Email

Business Platform's emails follow VirtualBiz's: one shared layout (`com.businessplatform.mail.Email`: table layout and
inline styles for mail apps, plus a plain-text part), every email written **in English, then in Khmer**, with the
subject in the reader's language (`MailService.letter`).

| Email | Sent by | Button |
| --- | --- | --- |
| Invitation to an organisation | `OrgService` | Accept the invitation |
| Account created by a people import ("choose a password") | `AccountService.provision` | Choose a password |
| Password reset | `AccountService` | Choose a new password |
| Password changed | `AccountService` | Reset your password |
| Payslip ready | `PayrollService` | View your payslip |
| **Every notification** (tasks, mentions, approvals, events …) | `NotificationService` | Open in Business Platform |

## Notification emails

Every notification also goes out by email, in the background once the change is saved. In **Notifications →
settings**, each person chooses per kind, and per organisation, whether it comes **in the app** and **by email**
(`notification_mute`, `notification_email_off`; `PUT /api/orgs/{org}/notifications/preferences` with `muted` and
`emailOff`). Invitations, password emails and payslips are not notifications and always go.

## Settings

| Setting | Default | |
| --- | --- | --- |
| `PLATFORM_MAIL_HOST` | empty | empty: emails are only logged (`LoggingMailer`) |
| `PLATFORM_MAIL_PORT`, `PLATFORM_MAIL_SECURITY` | `starttls` | `starttls` (587), `ssl` (465) or `none` (Mailpit) |
| `PLATFORM_MAIL_USERNAME`, `PLATFORM_MAIL_PASSWORD` | empty | a real provider needs them |
| `PLATFORM_MAIL_FROM` | `Business Platform <no-reply@localhost>` | the From address |
| `PLATFORM_MAIL_ONLY_TO` | empty | outside production: only these addresses or domains get real email; the rest are logged |
| `PLATFORM_MAIL_NEVER_TO` | `.test,.example,.invalid,.localhost,example.com,example.org,example.net` | never emailed, whatever else is set |

Emails go out **after** the change that caused them is saved: a rolled-back change sends nothing, and a mail server
that is down never undoes a change (the failure is logged). `/actuator/health` shows `mail` (whether the SMTP server
answers) when mail is configured.

### Never email

Addresses, domains (`example.com`) or domain endings (`.test`) that never get an email. By default the reserved test
domains: made-up accounts (people imports, demos, tests: `…@kosign.test`, `…@e2e.test`) would otherwise bounce back
into the sending mailbox and can get it flagged.

## Sending through Gmail

Gmail sends from your own address (about 500 emails a day on a personal account):

1. Turn on 2-Step Verification (https://myaccount.google.com/security), then create an **App Password**
   (https://myaccount.google.com/apppasswords). Never use the account password.
2. In `.env` (git-ignored):

   ```sh
   PLATFORM_MAIL_HOST=smtp.gmail.com
   PLATFORM_MAIL_PORT=587
   PLATFORM_MAIL_SECURITY=starttls
   PLATFORM_MAIL_USERNAME=you@gmail.com
   PLATFORM_MAIL_PASSWORD=<the 16-letter app password>
   PLATFORM_MAIL_FROM="Business Platform <you@gmail.com>"   # must be the Gmail address
   PLATFORM_MAIL_ONLY_TO=you@gmail.com                      # outside production
   ```

## Locally: Mailpit

Mailpit catches every email; nothing reaches a real inbox. Open http://localhost:8025.

- On a Mac: `brew install mailpit && brew services start mailpit`.
- With Docker: `docker compose up mailpit`.

Then in `.env`: `PLATFORM_MAIL_HOST=localhost`, `PLATFORM_MAIL_PORT=1025`, `PLATFORM_MAIL_SECURITY=none`, and leave
`PLATFORM_MAIL_ONLY_TO` empty.
