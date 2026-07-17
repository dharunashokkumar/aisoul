# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**AiSoul** (`com.aisoul.app`) — a local-first AI harness for Android. Single-module Kotlin/Compose app, minSdk 26 / target 35, no backend, no Firebase, no analytics. The user's phone talks to exactly two kinds of servers: the AI provider they configured (their own API key) and their own Google Drive.

## The workflow rule that overrides everything else

The four root docs are living documents that compound across sessions, and changes flow in one direction:

1. **DECISIONS.md first** — every change of substance gets a dated D-### entry (append-only, newest at bottom) explaining what and why.
2. Then update **SPEC.md** (product behavior) and/or **IMPLEMENTATION.md** (technical plan + milestone status).
3. **DESIGN.md is law**, not guidance — see "Design system" below.

Read DECISIONS.md before proposing changes; open questions live at its bottom (O-###). DATA_SAFETY.md is the Play Console data-safety form answer sheet — update it if data flows change.

## Commands

```
./gradlew :app:assembleDebug                 # debug APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest             # all JVM unit tests
./gradlew :app:testDebugUnitTest --tests "com.aisoul.app.backup.*"   # one package
./gradlew :app:testDebugUnitTest --tests "*.ArchiverTest"            # one class
./gradlew :app:assembleRelease               # minified R8 build (10 MB); signs with
                                             # keystore.properties if present, else debug key
./gradlew :app:bundleRelease                 # AAB for Play upload (needs keystore.properties)
```

**Windows build gotcha (recurring):** `Unable to delete directory … app\build\…` means a stale daemon holds a lock. Fix: `./gradlew --stop; taskkill //F //IM java.exe; rm -rf app/build` and rebuild. SDK lives at `%LOCALAPPDATA%\Android\Sdk` (see `local.properties`).

`keystore.properties` (repo root, gitignored) holds upload-keystore path/passwords; never commit it or any `.jks`.

## Architecture — the big picture

**Manual DI:** `di/AppContainer.kt` constructs the entire object graph (D-012, no Hilt). Everything reaches it via `(application as AiSoulApp).container` — workers and services included. Screens take `container` directly.

**Files are the product (SPEC §3):** `filesDir/harness/` is the single source of truth — SOUL.md, USER.md, MEMORY.md index, memories/, notes/, journal/, chats/ (JSONL), widgets/ (JSON specs), plus harness-v2 state (CURSOR.md, SUMMARY.md, activity.tsv — D-020). `HarnessStore` owns all file I/O (atomic temp+rename writes); `MemoryStore` owns memories + recall (keyword overlap v1). Anything derived (indexes, widget refresh caches) must be rebuildable from files. Both stores expose an `onMutation` hook that slides the 30-min debounced backup.

**One agent turn:** `ChatViewModel.send` → system prompt assembled by `HarnessStore.systemPrompt()` (soul + user + summary + memory index + recalled + cursor + today's note + journal tail + time facts + conduct) → `AgentRuntime.runTurn` streams provider events, executes tools through `PermissionGate` → gate either allows instantly or **suspends on a `CompletableDeferred`** while the approval sheet renders (`gate.pending` StateFlow) → tool results ride back in USER-role messages as `tool_result` parts. `AgentTurnService` (FGS `specialUse`) brackets the turn so it survives app switching. 20-iteration cap per turn.

**Providers:** one internal schema (`providers/Model.kt`: `ChatMessage`/`Part`/`ToolDef`/`StreamEvent`); four adapters (Anthropic, OpenAI, Gemini, OpenAI-compat with custom base URL) each own their wire format + SSE parsing. Model IDs are user-editable free text — never hard-fail on unknown models.

**Widget DSL (SPEC §8):** declarative JSON only, strict decode (`ignoreUnknownKeys = false`), never code. **Capability freeze:** SHA-256 of the exact spec at human approval (`widgets/.approvals.json`); any edit → hash mismatch → re-approval; refresh executes only frozen URLs/commands/paths, never the chat permission flow.

**Toolbox (the Android trick, IMPLEMENTATION §5):** busybox/curl/jq ship as fake native libs `jniLibs/<abi>/lib*.so` with `useLegacyPackaging = true` so they land executable in `nativeLibraryDir`; `filesDir/bin/` symlinks dispatch by argv[0]. `nativeLibraryDir` moves on every install — `ToolboxRunner` re-verifies symlinks via `Os.readlink` at bootstrap, never trusts persisted paths.

**Backup (D-024):** `backup/` — deterministic zip of /harness (sorted entries, excludes `workspace/`) → Argon2id (argon2kt) + AES-256-GCM with an `AISOULBK` header carrying KDF params. Drive via Play Services `AuthorizationClient` (matched by package+SHA-1 — **no client ID in code**) + hand-rolled Drive REST over OkHttp. Restore swaps directories keeping one `.pre-restore` generation. The `Kdf` interface exists so JVM tests run the format with a fake — keep it that way.

**Secrets:** `vault/KeyVault` wraps with an Android Keystore AES key; provider keys and the backup passphrase live wrapped in the vault DataStore. Never in backups, never in logs, never in archives.

## Design system — DESIGN.md is law

- **No raw dp/sp/color literals in screens.** Tokens only: colors from `ui/theme/Color.kt`, spacing from `Space`, shapes from `Shape.kt`, the 7 type roles from `LocalAiSoulTypography`.
- One spring for all motion: `aiSoulSpring()` (0.75 / 380); fades via `fadeSpec()`. Honor reduced motion through the existing shared modifiers (`staggeredEntrance`, `pressable`, `rememberReducedMotion`).
- Every tappable uses `pressable()` (scale + haptic). Copy is lowercase, terse, no emoji anywhere in UI.
- Icons are hand-drawn `ImageVector`s in `ui/common/Icons.kt` — no icon library.

## Hard-won device lessons (don't re-learn these)

- Android's ICU regex rejects unescaped `}`/`]` that the desktop JVM tolerates — always escape closing braces/brackets in `Regex`; JVM unit tests cannot catch this class of bug.
- Tool `execute()` implementations own their dispatcher — blocking I/O wraps in `withContext(Dispatchers.IO)` (agent flow is collected on the main thread).
- `jsonPrimitive.content` stringifies JSON null as `"null"` — filter `JsonNull` when reading nullable wire fields.
- Never render `e.message` without a fallback.
