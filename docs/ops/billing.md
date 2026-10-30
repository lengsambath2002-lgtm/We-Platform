# Billing: plans, invoices and payment

Organisations pay per member (guests are free), monthly or yearly. Plans and prices are edited by super admins in
**Platform → Plans**; see `docs/02-architecture.md` for how plans are enforced.

## The life of an invoice

1. **Issued** when an Owner (or someone with the billing role) chooses a paid plan, and again at each renewal
   (`SubscriptionJob`, daily). Seats are the active non-guest members at that moment. Earlier open invoices are
   voided. The Owners get an email with a link to the invoice.
2. **Paid by the customer**: bank transfer to the account on the invoice, or by scanning its KHQR code with any
   Cambodian banking app. They then press **We paid** with the bank's transfer reference; super admins get an email.
3. **Confirmed by a super admin** in **Platform → Invoices**, after checking the money is in the bank: *Record
   payment* (method and reference). The Owners get a receipt by email. *Void* cancels an invoice with a reason.
4. **Not paid**: after the due date (`PLATFORM_BILLING_DUE_DAYS`, default 7) the subscription is *past due* and the
   members see a banner; after the grace period (`PLATFORM_BILLING_GRACE_DAYS`, default 14) the organisation moves
   to Free (modules outside Free are locked, data is kept) and the invoice is voided.

## Configuration

| Variable | What it is |
| --- | --- |
| `PLATFORM_BILLING_SELLER_NAME`, `_SELLER_ADDRESS`, `_SELLER_TAX_ID` | The seller printed on invoices (VAT TIN when registered) |
| `PLATFORM_BILLING_BANK_NAME`, `_BANK_ACCOUNT_NAME`, `_BANK_ACCOUNT_NUMBER` | Where customers transfer to |
| `PLATFORM_BILLING_BAKONG_ACCOUNT_ID` | A Bakong account (`name@bank`); when set, every invoice carries a KHQR code for its exact amount in USD |
| `PLATFORM_BILLING_MERCHANT_CITY` | City in the KHQR code (default Phnom Penh) |
| `PLATFORM_BILLING_DUE_DAYS`, `PLATFORM_BILLING_GRACE_DAYS` | Days to pay, then days before moving to Free |

## Not automatic yet

Payments are confirmed by a person. Confirming them automatically needs merchant credentials: the Bakong Open API
(checking a KHQR transaction by its MD5) or ABA PayWay (a checkout and its callback). Either can call the same
`PlatformInvoiceService.record` once the bank confirms. Invoices are in USD; a VAT invoice in KHR for the tax office
is the accountant's job.
