I read the core paths (agent loop, gate, stores, toolbox, crypto, providers, widgets) and ran the unit suite — **all tests pass**. This is genuinely well-built: the probe-don't-assume toolbox, the hash-freeze on widget capabilities, and the `Kdf` seam for JVM-testable crypto are all better than typical. Findings below are ranked by what I'd actually fix.

---

## Bugs

### 1. A throw before the stream starts permanently bricks the chat
`ui/chat/ChatViewModel.kt:92-119`

`send()` sets `isStreaming = true` synchronously, then launches. But `.catch {}` is a Flow operator — it only covers `runTurn`. Everything before it is unprotected:

```kotlin
val client = container.providerFactory.create(provider, key ?: "", baseUrl)
val recalled = runCatching { container.recall.recall(trimmed) }.getOrDefault(emptyList())
val system = container.harness.systemPrompt(recalled)   // ← not wrapped
```

`systemPrompt()` does ~8 file reads. `providerFactory.create` can throw on a malformed compat base URL. `settings.first()` can throw. Any of those escapes to `viewModelScope` → uncaught → **crash**, and if it doesn't crash the process, `isStreaming` stays `true` forever, so `send()` early-returns on every subsequent message and the FGS notification stays pinned. `recall` is defensively wrapped; its neighbours aren't.

Fix: wrap the whole launch body in `try/catch`, and call `finalizeTurn()` in a `finally`.

### 2. `find` is on the read-only allowlist
`agent/GatePolicy.kt:17`

In TRUSTED mode `isReadOnly` blocks `|;&><\`$` and then checks the first word. But:

```
find . -delete
find . -exec rm {} +
```

contain none of the blocked characters and start with `find` → **`Decision.ALLOW`, no prompt**. `find` is the one entry on that list that carries its own exec/delete verbs. `GatePolicyTest` covers pipes and chains but not this. Drop `find`, or special-case `-exec`/`-delete`/`-execdir`/`-ok`.

### 3. Restore trusts KDF parameters from the archive header
`backup/BackupCrypto.kt:78-85`

`memoryKiB`, `iterations`, `parallelism` are read straight from an untrusted header and handed to Argon2. A corrupt or hostile `.aisoulbk` declaring 2 GB of memory OOM-kills the app on restore, before any authentication happens — the GCM tag is only checked *after* the KDF runs. Clamp on read (e.g. memory ≤ 256 MiB, iterations ≤ 10, parallelism ≤ 4) and throw `BadArchiveException` outside those bounds.

### 4. PROMPT.md self-edits look like any other file write
`agent/Tools.kt:141`

PROMPT.md is the head of every system prompt (D-034) — it *is* the guardrails. `SOUL.md`/`USER.md` get a distinct `EditSoulUser` action; PROMPT.md falls through to `OverwriteFile`, which the sheet renders as plain **"write PROMPT.md"** (`ApprovalSheet.kt:171`) — visually identical to writing `notes/scratch.md`. A model rewriting its own operating rules should be the loudest prompt in the app, not the quietest. Add PROMPT.md to the `EditSoulUser` branch (or add a dedicated action).

### 5. `unwrap` throws `IndexOutOfBounds` on a malformed blob
`vault/KeyVault.kt:51`

```kotlin
val (iv, ct) = blob.split(":", limit = 2).let { it[0] to it[1] }
```

No colon → `it[1]` throws, and it's not `runCatching`-wrapped at this level. A truncated DataStore write turns into a hard crash on every app launch that touches a key. Return `null` and let callers treat it as "no key set".

### 6. Deleting every widget resurrects the defaults
`widgets/WidgetStore.kt:198`

`ensureDefaults()` bails only if the dir is non-empty *or* the registry is non-empty. `remove()` clears both. So a user who deletes all three default widgets gets them back on next launch, with no way to say no. Persist a `defaults_seeded` flag instead of inferring from emptiness.

### 7. `resolve()` uses a bare prefix match
`harness/HarnessStore.kt:61`

```kotlin
require(file.canonicalPath.startsWith(root.canonicalPath))
```

`/data/.../harness-backup` satisfies `startsWith("/data/.../harness")`. Not currently exploitable — no sibling dir starts with `harness`, and `writeBlocked` catches `..` at the tool layer — but this is the last-line defense and it's one character from correct: `startsWith(root.canonicalPath + File.separator)`.

---

## Architecture-level observations

**Every file write enqueues WorkManager.** `AppContainer.kt:144-153` wires `onMutation` to a coroutine that reads two DataStore flows and calls `BackupWorker.debounce`. `writeAtomic` fires it, and so does `rebuildIndex` — which itself calls `writeAtomic`. During one agent turn you get an enqueue per message, per memory upsert, per index rebuild. The debounce is real but it's happening *after* the expensive part. Coalesce in-process (a `MutableSharedFlow` + `debounce(30_000)` collected once in `appScope`) rather than round-tripping WorkManager on every write.

**No lock around read-modify-write on shared files.** `MemoryStore.queueDelete`/`removePending` and `WidgetStore.updateRegistry` all read → mutate → write. `DistillWorker` and `WidgetRefreshWorker` run on WorkManager threads concurrently with a live chat turn. Individual writes are atomic, but the read-modify-write pairs aren't — a lost update to `.approvals.json` would silently un-approve a widget. A single `Mutex` per store would close it.

**`AgentTurnService.onStartCommand` leaks a collector per turn.** `AgentTurnService.kt:42` launches a new `gate.pending` collector every time `start()` is called, and `start()` is called per `send()`. `finalizeTurn` stops the service each turn so it mostly self-cleans, but `retry()` → `send()` on an already-running service stacks them. Guard with a `started` flag.

**`persist()` captures `chatId` at execution, not at call.** `ChatViewModel.kt:212` — `newChat()` reassigns `chatId` while a queued persist coroutine is still pending, so a message can land in the wrong transcript file. Pass `chatId` as a parameter.

**The DoH privacy tradeoff is documented but the summary isn't.** D-037 (DECISIONS.md:205) is admirably honest that Cloudflare resolves `run_command` hostnames. But CLAUDE.md:7 and SPEC.md:20 both still say "exactly two kinds of servers" unqualified. The reasoning (user-initiated shell traffic ≠ app traffic) holds, but I'd carry a half-sentence caveat into SPEC §2 so the binding doc matches the decision log.

---

## Smaller things

- `AgentRuntime.kt:75` — `if (text.isNotBlank())` silently drops whitespace-only assistant text. Harmless today, but it means a model that emits only a newline produces an empty `parts` list and an early `TurnDone`.
- `WidgetValidator.kt:99-107` — `checkTemplates` runs on `component.text` and `component.value` but not `label`, `empty`, or `items_from`. Unknown `{source}` refs there render literally instead of failing validation.
- `Sse.kt:29` — `.trim()` on the data payload is more aggressive than the SSE spec (one leading space). Safe here because every payload is JSON, but it'd break the moment a provider streams raw text.
- `HarnessStore.writeAtomic` never `fsync`s before rename. On a phone that's a real (if rare) power-loss window where the rename lands and the content doesn't.

---

## What I'd do first

Items **1, 2, and 3** — those are the ones with real consequences (permanent UI lock, an unprompted `rm` in trusted mode, and a DoS on the restore path). All three are small, localized diffs.

Want me to write the fixes? If so I'll do the DECISIONS.md entry first per the workflow rule — probably one D-### covering the gate/PROMPT.md hardening, since 2 and 4 are the same argument about what deserves a prompt.