# VirtualBiz ↔ Business Platform: single sign-on and shop links

Status: **Accepted, 2026-10-28.** VirtualBiz is the platform's Retail & Commerce module (VirtualBiz
ADR-0002). This contract replaces the WeGroupware-based draft in VirtualBiz `docs/platform/`: the
identity provider is this platform (`apps/api`), not WeGroupware.

## Roles

- **Business Platform** is the identity provider (OpenID Connect-style, authorization code + PKCE).
- **VirtualBiz** is a registered client (confidential: client id + secret, fixed redirect URIs).
- Merchants and their staff use SSO; marketplace customers keep VirtualBiz's own sign-in.

## 1. Discovery and keys (platform)

`GET {PLATFORM}/api/oidc/.well-known/openid-configuration`

```json
{
  "issuer": "{PLATFORM}",
  "authorization_endpoint": "{PLATFORM}/oauth/authorize",
  "token_endpoint": "{PLATFORM}/api/oidc/token",
  "jwks_uri": "{PLATFORM}/api/oidc/jwks",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code"],
  "code_challenge_methods_supported": ["S256"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post"],
  "scopes_supported": ["openid", "email", "profile", "orgs"]
}
```

`{PLATFORM}` is the platform's public web origin (`PLATFORM_APP_URL`); the web app proxies `/api/*`
to the API, so every URL above is on that one origin.

`GET {PLATFORM}/api/oidc/jwks` returns the RS256 public key(s) (`kty`, `kid`, `use=sig`, `alg=RS256`,
`n`, `e`). The private key is `PLATFORM_OIDC_SIGNING_KEY` (PKCS#8 PEM, base64-encoded); without it a
key is generated at start-up **only when `PLATFORM_ENV=local`**, and production refuses to start.

## 2. Authorize (browser → platform web)

`GET {PLATFORM}/oauth/authorize?response_type=code&client_id=…&redirect_uri=…&scope=openid%20email%20profile%20orgs&state=…&nonce=…&code_challenge=…&code_challenge_method=S256[&prompt=none]`

- `redirect_uri` must exactly match one registered for the client; otherwise the page shows an
  error and **does not redirect**. Unknown `client_id`: same.
- `state`, `nonce` (≤ 255 chars) and `code_challenge` (S256) are required.
- Not signed in → the platform's sign-in page, then back here. First time for this client → a
  consent screen ("VirtualBiz wants to sign you in as {name} ({email}) and see your organisations and
  roles"); approval is remembered per user + client (revocable on the platform account page).
- `prompt=none`: never shows UI; if not signed in or no consent yet, redirects with
  `error=login_required` / `error=consent_required`.
- Success: `302 {redirect_uri}?code=…&state=…`. Denied: `{redirect_uri}?error=access_denied&state=…`.
- The code is random (≥ 32 bytes), stored hashed, single use, valid **60 seconds**, bound to client,
  redirect URI, user, `code_challenge` and `nonce`. A reused code revokes nothing else but is refused.

## 3. Token (VirtualBiz server → platform API)

`POST {PLATFORM}/api/oidc/token` (`application/x-www-form-urlencoded`), client authentication with
HTTP Basic (`client_id:client_secret`) or `client_id` + `client_secret` in the body:

`grant_type=authorization_code&code=…&redirect_uri=…&code_verifier=…`

Response `200`:

```json
{ "id_token": "<JWT>", "token_type": "Bearer", "expires_in": 300 }
```

Errors (RFC 6749 §5.2), status 400 (401 for `invalid_client`): `{"error": "invalid_request" | "invalid_client" | "invalid_grant" | "unsupported_grant_type", "error_description": "…"}`.
`invalid_grant` covers unknown/used/expired code, redirect URI mismatch and a failed PKCE check.

### ID token (RS256, header `kid`)

| Claim | Value |
|---|---|
| `iss` | `{PLATFORM}` |
| `aud` | the client id |
| `sub` | platform user id (UUID) — **the only stable identity key** |
| `iat`, `exp` | `exp = iat + 300 s` |
| `auth_time` | when the user signed in to the platform |
| `nonce` | from the authorize request |
| `email` | lower-case |
| `email_verified` | `false` (the platform does not verify email today; never link on email) |
| `name` | display name |
| `locale` | `en` or `km` |
| `orgs` | `[{"id": uuid, "slug": "…", "name": "…", "role": "OWNER"\|"ADMIN"\|"MEMBER"\|"GUEST"}]` — active memberships only |

The client must check signature (JWKS, cache by `kid`, refetch on unknown `kid`), `iss`, `aud`,
`exp` (≤ 60 s clock skew) and `nonce`.

## 4. Shop links (VirtualBiz server → platform API, client credentials)

A shop (VirtualBiz business) can be linked to one platform organisation; an organisation can have
several shops. Authenticated with HTTP Basic `client_id:client_secret`.

- `PUT {PLATFORM}/api/integrations/virtualbiz/links/{businessId}`
  body `{"orgId": uuid, "businessName": "…", "linkedBy": "<platform sub>"}` →
  `200 {"orgId", "businessId", "businessName", "linkedAt"}`. The platform checks the organisation
  exists and `linkedBy` is an **active OWNER or ADMIN** of it (else `403 {"error":"forbidden"}`);
  re-linking the same shop to another organisation moves it. Recorded in the platform audit log.
- `DELETE {PLATFORM}/api/integrations/virtualbiz/links/{businessId}` → `204`.

## 5. On the platform: the Retail & Commerce module

- The `retail` module becomes AVAILABLE when `PLATFORM_VIRTUALBIZ_URL` is set (VirtualBiz web
  origin); admins switch it on per organisation.
- `/[org]/retail` lists the organisation's linked shops with **Open shop** →
  `{VIRTUALBIZ}/platform/open?business={businessId}`; admins also get **Connect a shop** →
  `{VIRTUALBIZ}/platform/connect?org={orgId}`, and can unlink.

## 6. On VirtualBiz

- Config: `VBIZ_PLATFORM_URL` (= `{PLATFORM}`), `VBIZ_PLATFORM_CLIENT_ID`,
  `VBIZ_PLATFORM_CLIENT_SECRET`. Without them the feature is off (button hidden, endpoints answer
  `NOT_CONFIGURED`). Redirect URI: `{VBIZ_APP_URL}/platform/callback` (a web page, so the session
  cookie is set same-origin).
- **Start**: the backend creates the `state`, `nonce` and PKCE verifier, keeps them server-side
  (hashed ticket, 10 minutes, with the intent: `signin` | `open` (+ business) | `connect` (+ org) and
  a sanitised `next`), and returns the authorize URL.
- **Complete** (`/platform/callback` page posts `code` + `state` to the backend): exchange the code,
  verify the ID token, then resolve the user:
  1. A VirtualBiz identity `(PLATFORM, sub)` exists → that user.
  2. Else a VirtualBiz user with the same email exists → **do not link**; answer `LINK_REQUIRED` with
     a link ticket. The person signs in to VirtualBiz the usual way (password/Google/code, two-step
     if on), then confirms the link; only then is `(PLATFORM, sub)` stored.
  3. Else create a VirtualBiz user from `name`, `email`, `locale` and store the identity.
  Sign-in goes through the normal path (suspension check, two-step challenge if the user turned it on).
- **Staff access follows the platform** for shops linked to an organisation in the token:
  OWNER/ADMIN → MANAGER, MEMBER → STAFF, GUEST → no access. Rows created this way are marked as
  platform-managed; they are updated on every SSO sign-in and deactivated when the person is no
  longer in that organisation. A shop's real OWNER row and manually added staff are never changed.
- **Connect** (`intent=connect`): after SSO, the person picks one of the shops they **own** in
  VirtualBiz and an organisation from the token where they are OWNER/ADMIN; VirtualBiz stores
  `business.platform_org_id` and calls the link API. **Disconnect** clears it and calls DELETE.
- **Open** (`intent=open`): after SSO, the shop becomes the active business and the seller (or
  provider) dashboard opens.

## Security notes

- Secrets: the client secret is stored only as a hash on the platform (`PLATFORM_OIDC_CLIENTS`
  config: id, secret hash, redirect URIs, display name) and only in env on VirtualBiz.
- Codes, link tickets and state are single use, short-lived and stored hashed.
- No account is ever linked by email alone; `sub` is the key.
- Every step is logged without tokens or codes; links and unlinks are audited on both sides.
