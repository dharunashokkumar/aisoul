# AiSoul — Implementation Plan

> **Status:** living document, version 0.6 — 2026-07-18. Companion to [SPEC.md](SPEC.md); decisions logged in [DECISIONS.md](DECISIONS.md). v0.6.0 (code 5): publishing paused for a feature wave — chat rendering v2, thinking shimmer, message action row, live tool cards, editable PROMPT.md (now system-prompt **head**, D-034), no forced continuity/cursor (D-034), widget proposal inbox + add-widget gallery, embeddings recall, launcher widget (D-027…D-034).
> **M0–M5 implemented and user-verified on device** (D-012…D-026): onboarding interview, memory + recall, distill pass v2, agent loop with 7 tools + permission gate, toolbox + terminal, chat history, dashboard home + widget DSL, backup (Drive round-trip verified live 2026-07-17: connect → back up → archive in Drive → restore; report flow verified), compliance surfaces done. v0.5.0 (code 4), 54 unit tests green, **first R8 release build green** (10 MB, toolbox/JNI/assets intact, debug-signing fallback). OAuth Android client live in `aisoul-502608`; privacy policy live at dharun.dev. **Now in M6:** upload keystore → Play Console setup → internal track → 12×14 closed test (O-5) → production (O-7 remainder: Play App Signing SHA-1 second OAuth client + consent screen out of Testing).

---

## 1. Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x | Platform default; coroutines/Flow fit streaming + agent loop |
| UI | Jetpack Compose + Material 3 (heavily themed to DESIGN.md) | Dynamic DSL-driven rendering is natural in Compose; spring animations built in |
| Min / target SDK | 26 / 35 | 26 covers ~97% of devices; 35 is the current Play requirement for new apps |
| ABIs | `arm64-v8a`, `x86_64` (emulator) | v7a deferred; add only if user demand appears |
| Serialization | kotlinx.serialization | DSL parsing with strict unknown-field rejection |
| HTTP / SSE | OkHttp (+ okhttp-sse) | One client for provider streaming, `fetch` tool, Drive REST |
| Persistence | Plain files (authoritative) + SQLite FTS4 index (rebuildable) + Jetpack DataStore (settings) | Files ARE the product (SPEC §3); DB is only a disposable index |
| Background | WorkManager | Distill pass, widget refresh, scheduled backup |
| Crypto | Android Keystore (key wrap) + AES-256-GCM; Argon2id via libsodium binding for backup KDF | No deprecated `security-crypto` dependency |
| DI | Hilt (or manual — decide at M0; app is small enough for manual) | Keep it boring |
| Build | Single `:app` module + `:toolbox` (binary packaging only) | No premature modularization |

**No backend. No Firebase. No analytics SDK. No crash reporter in v1** (Play Console's built-in ANR/crash reporting only).

---

## 2. Architecture

```
ui/            Compose screens: onboarding, chat, dashboard, files, memory,
               terminal, settings. Thin; observe ViewModels.
agent/         AgentRuntime: turn loop, tool registry, PermissionGate,
               system-prompt assembly.
providers/     ProviderClient interface + Anthropic / OpenAI / Gemini /
               OpenAICompat adapters. Unified internal message & tool schema;
               each adapter maps to its wire format + SSE parsing.
harness/       HarnessStore: file CRUD under /harness, file watching,
               frontmatter parsing, memory recall (keyword ranking v1),
               FTS indexer.
widgets/       WidgetSpec (kotlinx.serialization, strict), validator,
               capability freezer, Compose renderer, RefreshWorker,
               per-source history store (for sparklines).
toolbox/       ToolboxRunner: ProcessBuilder exec, env setup, timeouts,
               output caps, applet symlink bootstrap.
backup/        Archiver (zip), Crypto (Argon2id + AES-GCM), DriveClient
               (REST via OkHttp), SafExporter, BackupWorker.
vault/         KeyVault: API keys wrapped by Keystore AES key, never in
               backups, never logged.
```

Data flow for one agent turn:

```
user msg → HarnessStore.systemPrompt(PROMPT head, SOUL, USER, SUMMARY,
MEMORY index, recalled, note, journal, time — no CURSOR) →
ProviderClient.stream() → tool_use? → PermissionGate.check(action) →
[approval sheet if needed] → tool executes → result (trusted/untrusted) →
back to model → … → final text → chat JSONL appended → idle timer →
DistillWorker enqueued (memory ops + journal + activity + title; no cursor)
```

---

## 3. Provider adapters

- One internal schema: `Message(role, content: List<Part>)` where `Part` is text / tool-call / tool-result; `ToolDef(name, description, jsonSchema)`.
- Adapters translate to: Anthropic Messages API (`tool_use`/`tool_result` blocks), OpenAI Chat Completions with `tools` (the compat path is why we use Chat Completions rather than Responses), Gemini `generateContent` with `functionDeclarations`.
- SSE parsing per provider; unified `Flow<StreamEvent>` (text-delta, tool-call-started, tool-call-args-delta, done, error).
- Key validation at onboarding = cheapest possible real call (e.g. 1-token completion), surfacing provider error messages verbatim.
- Default model IDs live in one constants file **and** are user-editable free text — the app must never break just because a provider renamed models.

---

## 4. Agent runtime & lifecycle

- Turn runs in a coroutine scoped to a **foreground service** started when a turn begins and stopped when it ends (notification: "aisoul is working — tap to open, swipe to cancel"). FGS type: `specialUse` with the required manifest `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declaration ("user-initiated AI agent turn"); most turns are seconds-long, but this survives the user switching apps mid-turn.
- Hard limits: 20 tool iterations/turn, per-tool timeout, global turn timeout (5 min default), cancel always kills the process tree of any running command.
- PermissionGate is a suspend point: `check()` either returns instantly (allowed / rule matched) or suspends while an approval sheet is shown. If the app is backgrounded, the notification says "aisoul needs approval to continue."

---

## 5. Toolbox packaging (the tricky Android bit)

- Static binaries are packaged as fake native libs: `src/main/jniLibs/arm64-v8a/libbusybox.so`, `libcurl_exe.so`, `libjq.so` (same for x86_64).
- **Critical gradle setting:** `packaging { jniLibs { useLegacyPackaging = true } }` — with app bundles, native libs are otherwise kept compressed inside the APK and never extracted, and there is nothing on disk to exec. Legacy packaging extracts them to `applicationInfo.nativeLibraryDir`, which is read-only and **exec is allowed from there** (this is the standard, Play-compliant technique).
- First-run bootstrap: create `filesDir/bin/` full of **symlinks** (`ls`, `grep`, `sh`, … → `libbusybox.so`; `curl` → `libcurl_exe.so`). Symlinks in writable storage pointing at read-only binaries are executable (the W^X check applies to the target). Busybox dispatches by `argv[0]`.
- `ping`: exec `/system/bin/ping` directly (present and app-executable on Android; busybox ping requires raw sockets and would fail unprivileged).
- Exec environment: `cwd=/harness/workspace`, `HOME` likewise, `PATH=filesDir/bin:/system/bin`, minimal env vars, `TMPDIR` inside sandbox. stdout/stderr capped (64 KB, tail-truncated with a marker), default timeout 30 s, `Process.destroyForcibly()` on cancel/timeout.
- Binary provenance: build busybox/curl/jq from source in CI (or vendor well-known static builds with recorded checksums); document versions + licenses (busybox is GPLv2 — ship license text + written offer for source; curl/jq licenses bundled in the licenses screen).

---

## 6. Widget engine

- `WidgetSpec` decoded with `ignoreUnknownKeys = false` — unknown key/type = invalid spec = quiet error card, never partial render.
- **Capability freeze:** at approval, the validator extracts the capability set (exact URLs, exact commands, file paths) and stores it beside the spec with a content hash. At render/refresh, execution is only permitted for capabilities in the frozen set; any spec change invalidates the hash → re-approval sheet.
- Rendering: one `@Composable WidgetCard(spec, state)` mapping DSL components to themed composables. Sizes map to fixed heights; dashboard is a `LazyColumn` (one-ish card per viewport, per DESIGN.md §3).
- Refresh: one periodic `WidgetRefreshWorker` (15-min floor — matches WorkManager's own minimum) walks due widgets; on-open refresh runs immediately in-process. Source fetch/exec reuse ToolboxRunner and the OkHttp client with the widget's frozen capabilities, not the chat permission flow.
- Sparkline history: per-source ring buffer (last 96 numeric samples) in a small file next to the widget spec.
- Extractors: minimal JSONPath subset implemented by hand (`$.a.b[0]` only), `regex:` (single capture group, 1 s eval cap), `lines:`.

---

## 7. Harness store & memory

- All writes atomic (temp file + rename). A `FileObserver` refreshes UI and marks the FTS index dirty.
- Frontmatter: simple YAML subset parser (key: value, no nesting needed).
- Recall v1: score `memories/*` by keyword overlap between the user message and each file's `name` + `description`; take top 5, include bodies. Embeddings are roadmap.
- DistillWorker: enqueued when a conversation idles 10 min or closes; one cheap-model call with a strict JSON-output contract (`operations: [{op, slug, content…}]`); malformed output → drop silently (never corrupt the harness); `delete` ops and SOUL/USER edits go to the approval queue.

---

## 8. Backup implementation

- Archive: deterministic zip of `/harness` (excluding `workspace/` temp) → Argon2id(passphrase, per-archive random salt) → AES-256-GCM (random nonce). Header: magic + format version + salt + KDF params + nonce.
- Drive: Credential Manager (Sign in with Google) + `AuthorizationClient` for the `drive.file` scope; Drive REST v3 via OkHttp (multipart upload, list-by-appProperties, delete-oldest-beyond-10). The Google Java Drive client library is deliberately avoided (heavy, brings its own HTTP stack).
- BackupWorker: daily periodic + a debounced one-shot enqueued on harness writes (30 min quiet window); constraints: charging not required, Wi-Fi toggleable.
- Restore: download → decrypt → show tree summary (file counts, newest timestamps) → typed confirmation → swap directories atomically (rename old harness to `.pre-restore`, keep one generation).
- SAF path shares the exact same archive format (`CREATE_DOCUMENT` / `OPEN_DOCUMENT`).

**OAuth prerequisites (start EARLY — see §13):** Google Cloud project, OAuth consent screen (external), `drive.file` scope declared, privacy policy URL live, brand verification if Google asks. `drive.file` is non-sensitive so full security review is not expected, but consent-screen review still takes days-to-weeks.

---

## 9. Key vault

- Random AES-256 key generated in Android Keystore (`setUserAuthenticationRequired(false)` — keys must work for background distill/refresh); provider API keys AES-GCM-wrapped with it, stored in DataStore.
- Never serialized into backups, never in logs, masked in UI (`sk-…f3a2`), deletable per provider.
- On restore to a new device the user re-enters keys — stated plainly in restore UI.

---

## 10. Design-system implementation

- One `AiSoulTheme` implementing DESIGN.md tokens exactly: surfaces 0–3, hairline borders, the 7 type roles (Satoshi via Fontshare + Inter, bundled), 4dp spacing scale, radius set, the single spring (`dampingRatio 0.75f, stiffness 380f`) as a shared `AnimationSpec`.
- Shared modifiers: `pressable()` (0.97 scale + haptic `CONTEXT_CLICK`), `staggeredEntrance(index)`, count-up number composable for `data-hero` / stat values.
- Lint-by-review: no raw dp/sp/color literals in screens — tokens only (DESIGN.md §8 is the checklist).
- Widget-birth signature animation lives with the dashboard; orchestrated once per approval, honors reduced-motion (falls back to 150 ms fade).

---

## 11. Threat model (v1)

| Threat | Mitigation |
|---|---|
| API keys at rest | Keystore-wrapped (§9), excluded from backups |
| Backup in Drive read by third party | Client-side Argon2id + AES-GCM; Google never sees plaintext |
| Prompt injection via fetched web content | Tool results tagged untrusted; system prompt treats fetched content as data; every side-effecting action still passes the permission gate regardless of why the model asked |
| Malicious/hallucinated widget spec | Strict schema, capability freeze at human approval, no code execution path exists |
| Shell abuse | App-UID sandbox (no root, no other apps' data reachable), timeouts, output caps, approval gate, revocable allowlist |
| Data exfiltration via `fetch`/`curl` to attacker host | New hosts require approval in careful/standard modes; allowlist visible in settings |
| Memory poisoning (distill writes junk) | Memory feed is fully visible + per-item delete; SOUL/USER edits and deletions always require approval |

---

## 12. Testing

- **Unit:** DSL parse/validate/freeze (golden files, malicious cases), permission-gate matrix (§6 SPEC table as a parameterized test), provider adapters against recorded SSE fixtures, archive round-trip + wrong-passphrase, JSONPath/regex extractors.
- **Instrumented:** ToolboxRunner on emulator (x86_64 binaries make this possible), harness file ops, Keystore wrap/unwrap.
- **Manual device matrix:** one low-end arm64 (Android 8–10), one mid (12–13), one current (15+); dark-only design reviewed on OLED + LCD.
- **Dogfooding IS the closed test:** the 12-tester requirement (§13) doubles as the real QA pass.

---

## 13. Milestones & launch plan

| Milestone | Contents | Exit criteria |
|---|---|---|
| **M0 — skeleton** ✅ *(built + verified on device 2026-07-16)* | Project setup, theme/tokens, provider adapters, streaming chat, key vault | Streamed conversation with all 4 provider types on device — **user-verified with a real key on a real phone** |
| **M1 — harness** ✅ *(built 2026-07-16)* | File store, onboarding + soul interview, system-prompt assembly, file browser/editor, distill pass + memory feed | Fresh install → interview → SOUL/USER written → next chat provably uses them — **code complete + tests green; on-device verification pending (reinstall or clear data to see onboarding)** |
| **M2 — agent** ✅ *(built 2026-07-17)* | Tool registry, permission gate + approval sheets, toolbox packaging, terminal screen | Agent runs `ping`/`curl` with approvals; allowlist works; cancel kills processes — **code complete + tests green; on-device verification pending** (FGS deferred to M5, D-018/O-6) |
| **M3 — widgets** ✅ *(built 2026-07-17)* | DSL parser/validator/freezer, renderer, `propose_widget`, refresh worker, widget birth animation | Server-status demo: agent proposes from conversation → approve → live widget refreshing — **code complete + tests green; on-device verification pending** |
| **M4 — backup** ✅ *(built 2026-07-17, D-024)* | Crypto, archiver, Drive OAuth + REST, scheduled backup, restore, SAF fallback | Full backup → wipe → restore on second device (minus keys) — **code complete + archive round-trip/wrong-passphrase/zip-slip tests green; on-device + two-device verification pending (needs the GCP Android OAuth client live)** |
| **M5 — compliance & polish** ✅ *(built 2026-07-17, D-025)* | Report-response flow, agent FGS (O-6), data-safety mapping, privacy policy page, licenses screen, empty states, reduced-motion, perf pass | DESIGN.md §9 checklist passes on every screen; Play pre-launch report clean — **code complete; pre-launch report runs at M6** |
| **M6 — launch** | Closed test → production | See below |

**Launch sequence (personal Play account rules):**

1. During M4: create Google Cloud project + OAuth consent screen and submit for review (longest external dependency — do not leave to the end). Put privacy policy on GitHub Pages.
2. End of M5: internal testing track (self), then **closed testing with ≥12 testers for 14 continuous days** — recruit from friends + r/androidapps + X/Mastodon; the free-at-launch decision exists partly to make this easy.
3. Apply for production access, answering Play's closed-test questionnaire honestly.
4. Production rollout: staged 20% → 100%; store listing copy written in the app's own voice (lowercase, terse — it IS the marketing).

## 14. Risks

| Risk | Handling |
|---|---|
| Google OAuth verification drags | Start in M4; SAF backup path ships regardless, so Drive can even miss the launch without blocking it |
| Play reviewer flags the terminal | Framing: fixed developer-utility toolbox, all AI use human-approved, binaries ship in-APK (no code download). Prepared reviewer note + demo video |
| Provider API drift | Adapters isolated; model IDs user-editable; compat base-URL is the universal escape hatch |
| Busybox GPLv2 obligations | License screen + source offer; curl/jq are permissive |
| Battery complaints (refresh/distill) | WorkManager defaults, 15-min refresh floor, Wi-Fi toggle, visible "last refreshed" honesty |
| Solo-dev scope creep | SPEC §1 non-goals are binding; anything new goes to SPEC §13 roadmap, not into v1 |


333220536955-8n6h40oss5n0qhhf4fuoiqj84j3h5pe9.apps.googleusercontent.com