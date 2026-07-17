# AiSoul — Decision Log

> Append-only. Newest at the bottom. Every entry: date, decision, why, and what it replaced (if anything).
> This is the compounding record — when SPEC.md or IMPLEMENTATION.md changes, the reason lands here first.

---

## 2026-07-16 — Project inception

**D-001 — Product concept locked.** A local-first personal AI harness for Android, inspired by OpenClaw: file-based soul + compounding memory, BYOK model APIs, agent loop with real tools, AI-adaptive dashboard, Google Drive backup, zero backend.
*Why:* differentiated vs. the flood of chat wrappers; privacy story is real, not marketing.

**D-002 — Name: AiSoul.** Application ID `com.aisoul.app`. Rendered lowercase (`aisoul`) in-app per DESIGN.md voice.
*Note:* package ID is not domain-verified by Google, but grab the `aisoul.app` domain if branding sticks — needed anyway for the privacy-policy page looking respectable.

**D-003 — Terminal: built-in toolbox only (v1).** Static busybox + curl + jq shipped in the APK as native libs; system `/system/bin/ping`. No Termux dependency, no package manager.
*Why:* Android 10+ W^X forbids executing downloaded binaries; bundled binaries are the only Play-compliant path. Termux bridge → roadmap.
*User choice, 2026-07-16.*

**D-004 — AI engine: own agent loop only (v1).** App implements its own tool-use loop against provider APIs. No hosting of Claude Code / Codex / Gemini CLI on-device.
*Why:* CLIs-under-Termux is fragile (Node under proot, process death, battery) and can't be a Play Store product's foundation. Termux CLI bridge and desktop-remote → roadmap.
*User choice, 2026-07-16.*

**D-005 — Backup: Drive `drive.file` folder.** Visible "AiSoul Backups" folder; non-sensitive scope (light OAuth verification); automatic scheduled backup; SAF export/import as accountless fallback. Client-side encryption always; API keys never in backups.
*User choice, 2026-07-16.*

**D-006 — Pricing: fully free at launch.** No IAP in v1. Revisit after real users exist; leading candidate is a one-time "pro" unlock.
*Why:* BYOK means near-zero marginal cost, and free maximizes the pool for the mandatory 12-tester/14-day closed test.
*User choice, 2026-07-16.*

**D-007 — Providers (v1):** Anthropic, OpenAI, Google Gemini, plus OpenAI-compatible custom base URL (OpenRouter / LAN Ollama / LM Studio). Model IDs are user-editable free text with per-provider defaults.
*Why:* compat URL is a cheap win covering the long tail; free-text models survive provider renames.

**D-008 — Widgets are declarative JSON DSL, never code.** Components/sources/actions per SPEC §8; capabilities frozen at human approval; strict schema, no JS/WebView/eval path for AI output anywhere in the app.
*Why:* Play prohibits downloading executable code; DSL is also the safer and more reviewable design regardless.

**D-009 — Design system: the pre-existing DESIGN.md is law.** Accent: `accent-ice`. Signature moment: **widget birth** (approval → orchestrated entrance on the dashboard). No emoji in UI → widget icons come from a bundled Material Symbols subset.

**D-010 — Stack:** Kotlin + Jetpack Compose, minSdk 26 / target 35, arm64-v8a + x86_64, plain files authoritative + rebuildable FTS index + DataStore, OkHttp everywhere (provider SSE, fetch tool, Drive REST — no Google Drive Java client), WorkManager for background, Keystore-wrapped keys, Argon2id + AES-256-GCM backups.

**D-011 — Process/spec workflow.** SPEC.md and IMPLEMENTATION.md are living documents that compound across chat sessions; changes are justified here first. Discussion precedes writing; **no implementation until the user explicitly says to start.**

---

## 2026-07-16 — Implementation begins (M0)

**D-012 — Implementation started; manual DI (resolves O-3).** User gave the go-ahead; M0 skeleton built in `Desktop\Harness` as a single-module Gradle project (`:app`). DI is a hand-rolled `AppContainer` on `Application` — the app is small enough, keep it boring. Hilt reconsidered only if the graph ever hurts.

**D-013 — M0 technical choices (details within D-010's frame):**
- *Toolchain:* AGP 8.7.3, Gradle 8.9, Kotlin 2.1.0, compose-bom 2024.12.01; SDK command-line tools installed to `%LOCALAPPDATA%\Android\Sdk`.
- *Key validation = GET models list* (`/v1/models`, `/models`, `/v1beta/models`) instead of a 1-token completion: free, uniform across all four provider types, and OpenAI-compat servers (Ollama/LM Studio/OpenRouter) all implement it.
- *No token cap on OpenAI-compat wire requests:* `max_tokens` vs `max_completion_tokens` drifts by model; omitting it entirely is the compat-safe path. Anthropic (requires it) and Gemini get `max_tokens`/`maxOutputTokens` = 4096.
- *Fonts:* Satoshi-Variable + InterVariable bundled as variable fonts (weight 400–650 via `FontVariation`); Satoshi's own weights skip 600, the variable axis is the only clean way to honor DESIGN.md's w600. License texts vendored in `/licenses`.
- *Icons:* tiny hand-drawn `ImageVector` set (arrow, stop, plus, back, copy, check) instead of material-icons — keeps the look ours and the APK small. The widget-DSL Material Symbols subset (SPEC §8) is a separate M3 concern.
- *Neutral SOUL.md/USER.md templates seed at first launch* (SPEC §4 skip path); the interview replaces them in M1. Chats already persist as JSONL under `/harness/chats/` from M0.

**D-014 — M0 scope note.** Chat history browsing, the soul interview, memory recall/distillation, tools, and the permission gate are deliberately NOT in M0 (they are M1/M2). The provider event schema already carries tool-call events so M2 plugs in without rework. The M0 system prompt is SOUL.md + USER.md + MEMORY.md index only.

---

## 2026-07-16 — M0 verified on device; M1 built

**M0 exit criteria closed.** User installed the debug APK on their phone: provider setup + key validation worked, chat streams. (Verified with the user's provider; the other adapters share the same wire-tested plumbing.)

**D-015 — Soul interview mechanics (resolves O-1).** The interview is a system prompt, not hardcoded questions: the model asks 5–7 questions one at a time (call-you / days / help-with / tone / never-do, optionally good-day / changing), each under 40 words, wraps early on curt answers, and signals completion with the exact marker `"that's all i need"`. UI offers "skip" always and "that's enough" after 2 answers. Drafting is a second call with a strict-JSON contract `{"soul","user"}` (≤200 words each, fence-tolerant parsing); malformed → retry/skip, never auto-write. The reveal screen shows both drafts in editors; nothing is saved until "keep these files."

**D-016 — notes/ files create on first write (resolves O-4).** No auto-creation on day rollover; a daily note exists only once someone (user via files screen, agent via M2 write_file) writes it. Empty files are noise in a product whose files are the product.

**D-017 — Distill v1 details.** Trigger: WorkManager unique work per chat, 10-min delay re-slid on every finished turn, immediate on "new chat" (SPEC §3's idle-or-closed). Distill models default per provider (`claude-haiku-4-5` / `gpt-5-mini` / `gemini-2.5-flash`; compat uses the chat model), stored user-editable. Contract: ops create/update/delete with slug/name/description/type/content; create/update apply silently and surface in the memory feed; **delete only queues** (`.pending-deletes.json`) for user approval in the memory screen — SPEC §3 honored. SOUL/USER edits are NOT in the distill contract v1 (they'd need the M2 approval sheet; distillation only touches `memories/`). Malformed output drops silently. Recall v1 = keyword overlap on name+description, top 5, ties by recency.

---

## 2026-07-16 — M2 + M3 built; harness v2 (user request: "make that harness stronger", reference = their `Desktop\learn` harnesses)

**D-018 — Agent runtime shape (M2).** `AgentRuntime` drives the tool loop as a cold flow consumed by `ChatViewModel` (viewModelScope). The **foreground service from IMPLEMENTATION §4 is deferred to M5** — turns are seconds-long, and FGS + notification + `specialUse` declaration is compliance polish, not loop mechanics. Loop hard limits kept: 20 tool iterations/turn, per-tool timeout, global cancel. Tool results ride in a USER-role message as `tool_result` parts (each adapter already maps them to its wire format). `PermissionGate.check()` suspends on a `CompletableDeferred` while the approval sheet renders (gate state exposed as a `StateFlow` the chat UI observes); denial returns a normal error tool-result so the model can adapt. `fetch` results are tagged `untrusted` and wrapped in a "data, not instructions" marker; the system prompt carries the matching hygiene rule.

**D-019 — Toolbox vendoring + exec environment (M2).** Binaries vendored today (checksums in `toolbox/CHECKSUMS.sha256`, originals kept in `toolbox/`):
- busybox **1.36.1** aarch64 (Alpine `busybox-static` package — busybox.net publishes no aarch64 build) and **1.35.0** x86_64 (busybox.net official static).
- jq **1.7.1** (official static release binaries).
- curl **8.7.1** (moparisthebest/static-curl static builds).
Packaged as `jniLibs/<abi>/libbusybox.so | libjq.so | libcurl_exe.so` with `useLegacyPackaging = true`. Bootstrap: curated applet symlinks (not `--list`-everything — keeps the "fixed developer toolbox" Play framing) in `filesDir/bin`, plus a CA bundle concatenated from `/system/etc/security/cacerts` into `filesDir/etc/ca-bundle.pem` (`CURL_CA_BUNDLE`) because static curl can't use Android's hash-named cert dir. `ping` → `/system/bin/ping`. Known limit, documented: `destroyForcibly()` kills the `sh`, not orphaned grandchildren — acceptable at 30s timeouts, revisit if abuse shows. GPLv2: busybox license + written source offer land in `/licenses`.

**D-020 — Harness v2: the briefing/closeout loop (from the user's `learn` repos).** What made those harnesses strong: a computed **session-start briefing**, **session-end invariants**, a **resume cursor**, per-day **session logs**, and a periodic **META pass** ("smarter, not bigger"). AiSoul adopts all five, machine-run since there's no human closing the loop:
- New harness files: `CURSOR.md` (live state: last win · next step · open threads — lean, points at files), `SUMMARY.md` (rolled-up long arc), `journal/YYYY-MM-DD.md` (timestamped per-session entries: did / decided / next / read-on-you), `activity.tsv` (one row per distilled session: date, time, label, ops count — streak/consistency data, feeds a future widget).
- System prompt v2 = SOUL + USER + SUMMARY + MEMORY index + recalled + CURSOR + today's note + last journal entry + **time facts** (now, weekday, time since last session) + continuity instruction ("greet like you remember — reference the cursor, never generic") + tool conduct + injection hygiene.
- Distill contract v2 adds `log`, `cursor`, `activity`, `title` fields beside `operations` (all optional; malformed → that field drops silently).
- **META pass**: every 12 distills (counter in DataStore), same worker runs a second call over MEMORY index + SUMMARY + journal tail; output = memory ops (merge/prune — deletes still queue for approval) + a rewritten SUMMARY.md. Memory gets smarter, not just bigger.

**D-021 — Chat history view (user request).** `chats/*.jsonl` listed newest-first with title, relative time, message count. Titles come from distill (`title` field, stored in `chats/.titles.json`), falling back to the first user message. Tap resumes the conversation (transcript reloads into the live loop); delete = confirm dialog, removes the JSONL. Route: `chat?chatId=` optional arg.

**D-022 — M3 widget engine choices.**
- DSL per SPEC §8 with strict decode (`ignoreUnknownKeys = false`) + validator; two v1 additions logged here: a root-level optional `tap` action (whole-card tap — the "talk" default widget is a door, not a form) and source type `memory` + action type `screen` (fixed enum: chat/memory/files/terminal) so the three SPEC §4 default widgets (talk / today / memory) are themselves ordinary DSL files seeded pre-approved — the defaults dogfood the engine.
- Capability freeze: SHA-256 of the exact spec JSON at approval, registry in `widgets/.approvals.json` (also carries `born` for the one-time birth animation). Hash mismatch (any edit) → quiet re-approval card; refresh refuses to execute.
- Refresh: 15-min periodic worker walks due widgets; on-open refresh immediate; last values + `lastRefreshAt` cached in `widgets/.state/` so the dashboard renders instantly and honestly ("last refreshed n min ago"). Sparkline history: last 96 numeric samples per source in `widgets/.history/`.
- Extractors: hand-rolled JSONPath subset (`$.a.b[0]`), `regex:` (first capture group, pattern+input length caps instead of an eval timer), `lines:N[-M]`.
- Widget `tool`/`http` sources execute through ToolboxRunner/OkHttp against the frozen capability set only — never the chat permission flow (IMPLEMENTATION §6).
- Proposal inbox on the dashboard is deferred; v1 proposals ride the chat approval sheet (which shows the plain-language capability summary). Resolves **O-2** partially: golden examples shipped = the three defaults + server-status in tests.

**D-023 — Navigation reshape (SPEC §4 step 5 lands).** Dashboard becomes the home screen once onboarded (was: chat). Chat opens via the talk widget; files/settings hang off the dashboard; history/new off chat; terminal + permissions off settings. Version → 0.4.0 (versionCode 3) covering M2+M3.

---

## 2026-07-17 — M4 + M5 built

**D-024 — M4 backup implementation choices.**
- *Archive format v1* (`backup/BackupCrypto.kt`): `AISOULBK` magic + format version + kdf id + Argon2id params (m/t/p) + 16B salt + 12B nonce, then AES-256-GCM over a zip of `/harness` with entries sorted by path (identical trees → identical zips). Excluded: `workspace/`, `*.tmp`. Dot-registries (`widgets/.approvals.json`, `chats/.titles.json`) DO travel, so approvals and titles survive restore. Params (64 MiB / t=3 / p=2) ride in the header, so they can be raised later without breaking old archives.
- *KDF binding:* **argon2kt** (thin JNI over reference argon2, per-abi .so) instead of a libsodium binding — lazysodium drags JNA for one primitive. `Kdf` is an interface; JVM tests exercise the whole format with a SHA-256 fake.
- *Drive auth:* Play Services **`AuthorizationClient` alone** — no Credential Manager sign-in step. We never need identity, only a `drive.file` access token; Google matches the app to the GCP **Android OAuth client by package name + signing SHA-1** (no client id appears in code). Connected-account email comes from `drive/v3/about`. Silent auth in workers; a stale grant shows "reconnect" instead of popping consent.
- *Drive REST* stays hand-rolled over OkHttp (D-010): ensure folder "AiSoul Backups", multipart upload with `appProperties`, list newest-first, `alt=media` download, prune beyond 10.
- *Passphrase* is a secret like a provider key: Keystore-wrapped into the vault DataStore (background backups must encrypt without UI). Restore always re-asks it (the archive may come from another device). Min 8 chars.
- *Debounce hook:* `HarnessStore`/`MemoryStore` got an `onMutation` callback; AppContainer slides a 30-min one-shot `BackupWorker` on every write (plus daily periodic; wi-fi-only = UNMETERED constraint, re-scheduled with UPDATE on toggle). WidgetStore is deliberately un-hooked — installs always co-occur with chat writes, and hooking `.state/` refresh writes would slide the debounce forever.
- *Restore:* decrypt → preview (file count, bytes, newest mtime, per-dir tree) → typed "restore" → atomic swap keeping one `.pre-restore` generation → "close aisoul" (`exitProcess`) so everything reopens from the restored files. SAF export/import shares the exact same blob format.

**D-025 — M5 compliance & polish.**
- *Report a response* (SPEC §5/§12): long-press any AI message → sheet shows the **exact text** that will be emailed → `ACTION_SENDTO` to the developer address. Addresses live in `AppLinks.kt`; nothing sends until the user hits send in their own mail app.
- *Agent FGS* (resolves **O-6**): `AgentTurnService`, `specialUse` type with the manifest `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declaration; started by ChatViewModel when a turn begins, stopped in `finalizeTurn`/`newChat`/`onCleared`. Notification (LOW importance, grid mark icon) flips to "aisoul needs approval to continue" while the gate suspends. `POST_NOTIFICATIONS` is requested at the first send — never during onboarding (SPEC §4: no permissions wall).
- *Licenses screen* reads `assets/licenses/` (libraries summary, full Apache-2.0 + GPL-2.0 texts, toolbox versions + busybox source offer, both font licenses). *About card* in settings: version (BuildConfig), privacy policy link, licenses, report-a-problem mailto.
- *Privacy policy* page ready at `docs/privacy.html` (GitHub-Pages-ready, dark, matches voice; includes the Google Limited-Use disclosure OAuth verification looks for). **`AppLinks.PRIVACY_POLICY_URL` must match wherever it's actually hosted.** `DATA_SAFETY.md` maps every Play data-safety form answer.
- *Backup nudge* (SPEC §4 step 6): dismissible dashboard card, shown until Drive is enabled or dismissed.
- Empty states + reduced motion audited: every list screen has an empty state; all entrances/count-ups/birth already route through the shared reduced-motion-aware modifiers.
- Version → **0.5.0 (versionCode 4)**. M4+M5 on-device verification pending.

---

## 2026-07-17 (evening) — M4/M5 verified on device; M6 prep begins

**M4/M5 exit criteria closed by the user on a real phone:** Drive connect → back up now → archive visible in Drive → restore round-trip all working; long-press report flow working. (Second-device migration — restore on *another* phone — still untested; it shares the same code path minus Keystore, so risk is low.)

**D-026 — Release build wiring (M6).**
- Signing: `keystore.properties` at repo root (gitignored, never committed) feeds a `release` signing config; **absent file falls back to debug signing** so minified builds stay installable for local smoke tests without touching the real keystore. Debug SHA-1 = the OAuth client's, so Drive works in fallback-signed release builds too.
- First R8 run ever: **green**. `assembleRelease` = 10.0 MB APK (vs 21.9 debug); toolbox exec binaries, argon2 JNI libs, fonts, and license assets all survive shrinking. Proguard additions: keep argon2kt (JNI), keep native methods, standard okhttp dontwarns (kotlinx.serialization rules were already present).
- Housekeeping: `.gitignore` extended for `keystore.properties`/`*.jks`; stray screenshots removed from repo root; the five root `.md` docs deliberately kept (living docs — not shipped in any build; DATA_SAFETY.md is the Play-form answer sheet).

---

## 2026-07-17 (night) — v0.6.0: publishing paused, feature wave instead

**D-027 — Publishing paused; Termux bridge dropped from the roadmap.** The user chose more updates over M6 launch. Separately, an explicit product decision: **AiSoul will never require a companion app.** The SPEC §13 "Termux bridge" roadmap item is replaced by "expand the bundled toolbox" — more static binaries shipped the same jniLibs way (D-019). Rationale: asking users to install Termux (F-Droid only, its Play build is abandoned) to unlock features is exactly the kind of dependency a local-first product exists to avoid; the busybox/curl/jq toolbox already proves the bundling path works.

**D-028 — Chat rendering v2 (the answer is the product).**
- `MarkdownLite` grows into a real block parser: headings, bullet/numbered lists, tables, `---` rules, `>` quotes, fenced + inline code, **bold**/*italic*/~~strike~~ — still hand-rolled, still no library. Headings map to existing type roles only (h1–h2 → title, h3+ → overline+caps like the app's own section labels); tables render as token-styled rows, never a WebView.
- Light latex-to-unicode pass for `$…$` spans (√, fractions, superscripts, greek) — the model is also *told* (via PROMPT.md, D-029) to write unicode math directly; the converter is a safety net.
- "thinking…" and running-tool labels get a shimmer (gradient sweep, `rememberReducedMotion`-aware) so working-state is visible at a glance.
- Long-press-to-report is replaced by an always-visible action row under each AI message: **copy · retry · report** (report keeps the exact-text email sheet; Play policy unaffected). Retry truncates the transcript back to the last user message and resends it — chats/*.jsonl gets a `rewriteTranscript` (atomic full rewrite); append-only was a convention, not a format.
- Tool cards auto-expand while running with pretty-printed input and a live status line; collapsed cards keep the humanized one-liner. Nothing the agent does is invisible — now visibly in progress, not just visible after.

**D-029 — PROMPT.md: the conduct section becomes a user-editable file.** `harness/PROMPT.md` is seeded with the built-in conduct text + new formatting rules (markdown structure, blank lines between paragraphs, `---` dividers, unicode math, tables). `systemPrompt()` reads it (fallback: built-in). It's an ordinary root file: visible and editable in the files screen, travels in backups. SOUL.md stays identity; PROMPT.md is operating instructions.

**D-030 — Widget proposal inbox (closes the D-022 deferral).** `propose_widget` no longer installs through the chat approval sheet. It validates, then writes the spec to `widgets/.proposals/<id>.json` and tells the model "waiting in the dashboard inbox". The dashboard renders pending proposals (title + frozen-capability summary) with approve/dismiss; approve = `installApproved` (birth animation runs), dismiss = delete. One approval surface instead of two; `GateAction.InstallWidget` is deleted from the gate entirely — proposing is now side-effect-free, so it needs no gate.

**D-031 — Golden widgets ship as an "add widget" gallery (resolves O-2).** Dashboard header gains "add": a sheet with two templates the user fills in and installs themselves (user action = approval): **countdown** (title + yyyy-mm-dd date → `countdown` source + stat) and **habit** (name → `file` source over `notes/habit-<slug>.md` + list + a "log today" button whose `chat` action asks the AI to prepend today's line). Both are plain DSL specs — the gallery dogfoods the engine the same way the defaults do.

**D-032 — Smarter recall: embeddings when the provider has them, keywords forever as the floor.** `SmartRecall` wraps MemoryStore: OpenAI (`text-embedding-3-small`) and Gemini (`text-embedding-004`) get embedding-based ranking; Anthropic/compat (no embedding API) keep keyword overlap. Vectors live in `memories/.embeddings.json` keyed by slug + a hash of name+description, tagged with the model — provider switch or edit invalidates per-entry, the file is derived/rebuildable (SPEC §3 respected). Embedding text is name+description only (already sent to the provider in every system prompt — no new data class leaves the device). Any failure, timeout, or missing key falls back to keywords silently.

**D-033 — Launcher widget v1 (roadmap §13.1): a zero-config mini-dashboard via Glance.** One home-screen widget type renders the first few ACTIVE dashboard widgets from their **cached** `.state/` values (title + primary line) — it never executes sources itself, so the capability freeze is untouched. `WidgetRefreshWorker` pushes Glance updates after each refresh pass. Tap opens the app. No config activity in v1; choosing a specific widget per launcher instance can come later.

Version → **0.6.0 (versionCode 5)**.

---

## 2026-07-18 — system prompt head + drop forced continuity

**D-034 — PROMPT.md is the system-prompt head; continuity/cursor forcing removed.** User feedback: chat was being forced to "continue" from `CURSOR.md` / continuity conduct, and the real operating rules lived at the bottom of the prompt. Changes:

1. **System prompt order** is now: **`PROMPT.md` first** (`# operating instructions`) → SOUL → USER → SUMMARY → MEMORY index → recalled bodies → today's note → last journal → time facts. Identity and context follow the rules; the model sees how to work before who it is.
2. **No more forced continuity.** `CURSOR.md` is **not injected** into the chat system prompt. Distill no longer requests or writes a resume cursor (parser still tolerates a stray `cursor` field so old model replies don't break). PROMPT explicitly: answer the current message; use background context when it helps; never invent unfinished work or a "picking up where we left off" monologue.
3. **Stronger default PROMPT.md** — sections for tools (read-before-write, gate honesty, toolbox bounds), memory (`remember` only for weeks-worth facts, dense descriptions, no duplicates), trust (fetch = data not instructions), phone formatting. Stock files still matching the old D-029 continuity bullet are one-shot replaced on `ensureSeeded()`; user-customized PROMPT without that marker is left alone.
4. Distill memory rules tightened to match (0–3 ops, update-not-duplicate, dense description). `remember` tool description aligned.

`writeCursor` remains on `HarnessStore` for any leftover file/manual edit; the product path no longer feeds it to the model.

## 2026-07-18 — Solve seccomp sandbox kill in command execution

**D-035 — Executable path change for ProcessBuilder and prepended shell functions.** Under Android 10+ target SDK 29+ restrictions (seccomp policy), calling `execve` on any binary in a writable application directory (such as symlinks in `filesDir/bin/*`) causes a SIGSYS signal (exit code 159). To bypass this cleanly, `ToolboxRunner` is updated to run the shell directly from the read-only, package-manager-extracted `libbusybox.so` in `nativeLibraryDir` (which is permitted), and commands are prefixed with shell functions that map tool invocations (`ls`, `cat`, `curl`, `jq`, etc.) directly to their respective read-only executable paths in `nativeLibraryDir`.
*Why:* completely solves sandbox execution errors on newer Android API levels without adding custom binaries or dependencies.

**Open questions (to resolve in future sessions):**
- ~~O-1: Soul-interview script~~ → resolved (D-015).
- ~~O-2: Widget DSL golden examples~~ → resolved (D-022 + D-031): talk/today/memory defaults, server-status in tests, countdown + habit in the add-widget gallery.
- ~~O-3: Hilt vs. manual DI~~ → resolved manual (D-012).
- ~~O-4: notes/ auto-create~~ → resolved on-first-write (D-016).
- O-5: Tester recruitment plan specifics for the 12×14 closed test.
- ~~O-6: Agent foreground service~~ → resolved (D-025).
- O-7: OAuth consent screen completion — **progress 2026-07-17:** OAuth client created in `aisoul-502608` (id `333220536955-…9.apps.googleusercontent.com`; unused in code — AuthorizationClient matches by package+SHA-1); privacy policy live at `https://dharun.dev/projects/AiSoul/privacy` (AppLinks updated to match). Still open: confirm the client is the **Android** type with `com.aisoul.app` + debug SHA-1, add the **release/Play App Signing SHA-1 later**, add the account as a **test user** while the consent screen is in Testing, and verify Drive connect on device.
