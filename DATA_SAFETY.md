# Play Console — Data safety form mapping (M5)

> Fill the form exactly as below. The guiding fact: **the developer collects
> nothing and runs no servers**; the only off-device flows are user-configured
> (their AI provider) and user-owned (their Drive), which the form still makes
> us declare because data leaves the device.

## Overview answers

| Form question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Chat text is transmitted off-device to the AI provider the user configures. Play counts off-device transmission as "collection" even when the developer never sees it. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Provider APIs and Drive are HTTPS-only; OkHttp with no cleartext traffic. |
| Do you provide a way for users to request that their data is deleted? | **Yes** | All data is on-device and user-deletable (files screen, "forget everything", uninstall); Drive archives are in the user's own Drive. Link the privacy policy. |

## Data types to declare

### 1. Messages → "Other in-app messages"

- **Collected:** Yes. **Shared:** Yes — with the AI provider the *user* selects (Anthropic / OpenAI / Google / custom endpoint).
- **Processed ephemerally?** Optional to claim; safe to leave unchecked.
- **Required or optional:** Optional (chat only works if the user configures a provider, but the user chooses to send each message).
- **Purposes:** App functionality.
- **NOT** used for advertising/marketing, **not** sold.

### 2. Files and docs (Drive backup)

- **Collected:** Yes (transmitted), **Shared:** No (goes to the user's own Google Drive, not a third party acting for us).
- **Optional:** Yes (backup is off by default and never required).
- **Purpose:** App functionality (backup/restore).
- Note in the free-text box if offered: archives are encrypted on-device with a user passphrase before upload; the developer and Google cannot read them.

### Everything else: **No**

No location, no contacts, no identifiers, no diagnostics, no analytics, no
crash logs (Play's own ANR/crash reporting is exempt — it's Play, not the app),
no advertising data, no health, no financial. API keys never leave the device
except to authenticate with the key's own provider (that is "app
functionality" authentication, not a declarable identifier — do not declare).

## Account deletion question (separate policy section)

- App does **not** allow account creation → answer "My app doesn't allow
  users to create an account", so no deletion URL is required. Google
  sign-in is Drive *authorization* only; no aisoul account exists.

## Related listing prerequisites

- Privacy policy URL (required, both in Play listing and GCP OAuth branding):
  **live at `https://dharun.dev/projects/AiSoul/privacy`** (source: `docs/privacy.html`;
  matches `AppLinks.PRIVACY_POLICY_URL` — keep the two in sync if it ever moves).
- AI-Generated Content policy: in-app reporting exists (long-press any AI
  response → report). Mention this in the app-content questionnaire.
- Target API 35 ✔ (compile/target 35).
- Foreground service disclosure: `specialUse` subtype declared in manifest as
  "user-initiated AI agent turn running tools the user explicitly approved";
  Play Console will ask for a short video/description at review — show a chat
  turn with the notification visible.
