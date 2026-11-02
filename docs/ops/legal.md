# Terms of Service and Privacy Policy

`/terms` and `/privacy` (English and Khmer) are written for this product: per-member plans, invoices due in 7 days,
the 14-day grace period, owners exporting data, account deletion, 30-day backups, the one sign-in cookie. Text:
`apps/web/src/components/legal/content.ts`. **Have a lawyer in Cambodia review them before launch** (in particular
liability, governing law, and the e-commerce and consumer-protection laws of 2019).

- Who runs the service comes from `LEGAL_NAME`, `LEGAL_ADDRESS`, `LEGAL_EMAIL` (`NEXT_PUBLIC_LEGAL_*`, compiled into
  the web app).
- Sign-up says "By creating an account you agree to the Terms of Service and the Privacy Policy", and the API records
  the version and time on the account (`app_user.terms_version`, `terms_accepted_at`).
- To change them: edit the text, set `LEGAL_VERSION` in `content.ts` and `PLATFORM_LEGAL_VERSION` to the date the new
  version takes effect, and tell owners at least 30 days before (the Terms promise this).
