# WORKFLOW.md — read this first, every time

This is the "don't screw it up" guide for anyone (human or AI) touching this
codebase. If you follow it top to bottom you will not break the build, the
docs, or the design. It repeats a few things on purpose. Repetition here is
cheaper than a broken build.

---

## 0. What this app even is (30 seconds)

**AiSoul** (`com.aisoul.app`) is a personal AI that lives entirely on an
Android phone. No servers, no accounts, no backend, no analytics. The phone
talks to only two things: the AI provider the user chose (with the user's own
API key) and the user's own Google Drive (for encrypted backups).

The whole "brain" of the AI is **a folder of plain text files** on the phone
(`filesDir/harness/` — SOUL.md, USER.md, memories, notes, journal, chats,
widgets). Those files ARE the product. Everything else (indexes, caches) can
be rebuilt from them. Never treat the files as a side effect of the code —
the code exists to serve the files.

It is one Android app, written in **Kotlin** with **Jetpack Compose** for the
UI. That's it. One module called `:app`.

---

## 1. The tools & language (what you're working with)

| Thing | What it is | Where |
|---|---|---|
| Kotlin 2.1 | the only language in the app | `app/src/main/java/...` |
| Jetpack Compose | the UI toolkit (no XML layouts) | `ui/` packages |
| Gradle (wrapper) | the build system — run it with `./gradlew` | repo root |
| JUnit 4 | unit tests (run on your computer, no phone needed) | `app/src/test/...` |
| Android SDK | installed at `%LOCALAPPDATA%\Android\Sdk` | see `local.properties` |

Dependencies are declared in **`gradle/libs.versions.toml`** (versions at top,
libraries in the middle) and then referenced in **`app/build.gradle.kts`**.
To add a library you edit BOTH files. Never hardcode a version string in
`build.gradle.kts` — put it in the toml.

There is **no backend code, no server, no database**. If a task sounds like it
needs a server, it's the wrong task for this app — stop and ask.

---

## 2. The golden rules (break these = errors)

These are the mistakes that have actually bitten this project. Memorize them.

1. **Escape closing braces/brackets in every `Regex(...)`.** Android's regex
   engine (ICU) crashes on an unescaped `}` or `]` even though your desktop
   JVM accepts it. Always write `Regex("\\{([a-z]+)\\}")`, never
   `Regex("\{([a-z]+)}")`. JVM unit tests will NOT catch this — it only blows
   up on a real phone. This one crash-looped the whole app once.

2. **Blocking work goes on `Dispatchers.IO`.** Any network call, file read, or
   command execution must be wrapped in `withContext(Dispatchers.IO) { ... }`.
   The agent loop runs on the main thread; a blocking call there throws
   `NetworkOnMainThreadException`. Every tool's `execute()` owns its own
   dispatcher.

3. **Never render `e.message` without a fallback.** Write
   `e.message ?: "something failed"`. Exceptions often have a null message and
   users should never see the word "null".

4. **JSON `null` is a trap.** `jsonPrimitive.content` turns a JSON `null` into
   the string `"null"`. Filter `JsonNull` before reading nullable wire fields.

5. **Design tokens only — no raw numbers in UI.** Never write `16.dp`,
   `Color(0xFF...)`, or `14.sp` in a screen. Use `Space.s16`, colors from
   `ui/theme/Color.kt`, type roles from `LocalAiSoulTypography`, shapes from
   `Shape.kt`. See DESIGN.md — it is law, not advice.

6. **Copy is lowercase, terse, no emoji.** Every user-facing string. "back",
   not "Back". No exclamation marks. This is the app's voice.

7. **Secrets never get committed or logged.** `keystore.properties`, `*.jks`,
   `local.properties`, API keys, the backup passphrase — none of these go into
   git, logs, or backups. `.gitignore` already blocks the files; don't undo it.

---

## 3. The workflow — how to actually make a change

Do these steps **in order**. Don't skip. This is the exact loop that keeps the
build green.

### Step A — EXPLORE (understand before you touch)
- Read **DECISIONS.md** bottom-first: the newest decisions (D-###) and the open
  questions (O-###) at the very bottom tell you what's been decided and why.
- Search for the thing you're changing. Find the file. Read the whole file, not
  just the function. Read the files it imports if they matter.
- Read CLAUDE.md for the architecture map if you're new.
- **Do not edit anything in this step.** You're building a mental model.

### Step B — PLAN (write it down before you write code)
- Decide the smallest change that does the job.
- If it's a change "of substance" (new feature, new behavior, new file), it
  needs a **DECISIONS.md entry first** (see §4). Small bug fixes don't.
- Know which files you'll touch and in what order.

### Step C — EDIT (make the change)
- Match the style of the code already in the file: same naming, same comment
  density, same patterns. Your code should be invisible in a diff review — it
  should look like the person who wrote the file wrote it.
- Obey every golden rule in §2 as you type.
- If you add a user-facing string, keep it lowercase and terse.
- If you add a dependency, edit `libs.versions.toml` AND `build.gradle.kts`.

### Step D — TEST (prove it works without a phone)
- Write or update a unit test if there's logic worth testing (parsers, math,
  policies, file formats — anything pure). Put it in `app/src/test/...`
  mirroring the package.
- Run the tests:
  ```
  ./gradlew :app:testDebugUnitTest
  ```
- One package or one class while iterating (faster):
  ```
  ./gradlew :app:testDebugUnitTest --tests "com.aisoul.app.harness.*"
  ./gradlew :app:testDebugUnitTest --tests "*.MarkdownLiteTest"
  ```

### Step E — VERIFY (make sure it compiles for the phone)
- Compile the real app code:
  ```
  ./gradlew :app:compileDebugKotlin
  ```
- Then build the installable debug APK:
  ```
  ./gradlew :app:assembleDebug
  ```
  Output lands in `app/build/outputs/apk/debug/`.
- Read the compiler errors carefully — Kotlin's messages point at the exact
  line and usually the exact fix (missing import, wrong type, extension not
  imported). Fix and re-run until it's clean.

### Step F — BUILD (the release, when it's a real milestone)
- The minified/shrunk build that would go to the Play Store:
  ```
  ./gradlew :app:assembleRelease
  ```
- This runs R8 (code shrinking). If R8 strips something it shouldn't (usually
  reflection or JNI), you add a keep rule to `app/proguard-rules.pro`. The
  release APK should be ~11 MB; debug is ~22 MB.

### Step G — RECORD & COMMIT
- Update the docs (§4).
- Commit as the user, never with AI attribution (§5).

---

## 4. The docs discipline (this is not optional)

Four living documents at the repo root compound across every session. Changes
flow in ONE direction:

1. **DECISIONS.md FIRST.** Every change of substance gets a dated `D-###` entry
   at the bottom (append-only, newest last) saying WHAT you changed and WHY.
   Open questions live at the very bottom as `O-###`.
2. **Then SPEC.md** (what the product does, for the user) and/or
   **IMPLEMENTATION.md** (the technical plan + milestone status).
3. **DESIGN.md is law** — the visual system. You follow it, you don't edit it
   casually.
4. **DATA_SAFETY.md** is the answer sheet for Google Play's data-safety form —
   update it only if data flows change.

Two more root docs help humans: **CLAUDE.md** (architecture map for AI),
**GUIDE.md** (beginner explainer of shipping), and this **WORKFLOW.md**.

**If you change behavior and don't log it in DECISIONS.md, you did it wrong.**
The next session won't know why the code looks the way it does.

---

## 5. Committing (rules that must not be broken)

- Commit only when the user asks, or when finishing a milestone the user is
  clearly expecting saved.
- **Never** add "Co-Authored-By: Claude" or any AI attribution. Commits are
  authored solely by the user:
  ```
  git -c user.name="dharuna457" -c user.email="dharuna457@gmail.com" commit -m "..."
  ```
- Before committing, run `git status --short` and confirm no secret files
  (`keystore.properties`, `*.jks`, `local.properties`) are staged.
- Push: `git push origin main`. The private repo is
  `dharunashokkumar/aisoul`.

---

## 6. The Windows build gotcha (WILL happen — here's the fix)

On Windows, Gradle sometimes can't delete its own `app/build` directory
because a stale Java daemon is holding a file lock. You'll see:

```
Unable to delete directory 'C:\...\app\build\...'
java.nio.file.AccessDeniedException
```

**The fix (run these three, then rebuild):**
```
./gradlew --stop
taskkill //F //IM java.exe
rm -rf app/build
```
Then run your build command again. This is expected, not a real bug. It
happened twice while building v0.6.0. Don't panic, just clear the lock.

---

## 7. Device-only gotchas (JVM tests can't catch these)

Some bugs only show up on a real phone, never in unit tests. After a
substantial change, do a quick manual smoke test on a device/emulator:

- **Regex braces** (§2 rule 1) — crashes on load, invisible to JVM tests.
- **Native library paths move on every install.** The toolbox binaries
  (busybox/curl/jq) live in `nativeLibraryDir`, which Android relocates on
  every update. Code that persists those paths breaks. `ToolboxRunner`
  re-verifies its symlinks at startup — don't "optimize" that away.
- **Main-thread blocking** (§2 rule 2) — throws only when the call actually
  runs, which a test with a fake might skip.
- **Compose visual/animation issues** — spacing, reduced-motion, theme — just
  look at it on a screen.

---

## 8. Where things live (the map)

```
Harness/                         ← repo root (this is a Gradle project)
├── WORKFLOW.md   ← you are here
├── CLAUDE.md     ← architecture map for AI
├── GUIDE.md      ← beginner shipping guide
├── SPEC.md / IMPLEMENTATION.md / DECISIONS.md / DESIGN.md / DATA_SAFETY.md
├── gradle/libs.versions.toml    ← dependency versions
├── app/
│   ├── build.gradle.kts         ← module config, version numbers, deps
│   ├── proguard-rules.pro       ← R8 keep rules for release builds
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/aisoul/app/
│       │   │   ├── ui/          ← Compose screens (chat, dashboard, ...)
│       │   │   ├── ui/theme/    ← DESIGN tokens: Color, Space, Shape, Type
│       │   │   ├── agent/       ← the AI loop, tools, permission gate
│       │   │   ├── harness/     ← the file store, memory, recall, prompt
│       │   │   ├── widgets/     ← the widget DSL engine + launcher widget
│       │   │   ├── backup/      ← encrypt + Google Drive
│       │   │   ├── providers/   ← Anthropic/OpenAI/Gemini adapters
│       │   │   └── di/AppContainer.kt  ← wires everything together (no Hilt)
│       │   └── res/             ← icons, strings, widget xml
│       └── test/java/...        ← JUnit tests (mirror the main packages)
```

**One place wires everything: `di/AppContainer.kt`.** When you add a new store,
client, or worker, you construct it there and everything else reaches it via
`(application as AiSoulApp).container`. There is no dependency-injection
framework — it's all by hand, on purpose (D-012). Keep it boring.

---

## 9. A worked example (adding a small feature, start to finish)

Say the task is "add a `word_count` tool the AI can call."

1. **Explore:** open `agent/Tools.kt`, read an existing simple tool like
   `ReadFileTool` to copy its shape. Note tools are registered in
   `di/AppContainer.kt`.
2. **Plan:** it's a new capability → it needs a `D-###` in DECISIONS.md. It
   reads a file and counts words; no side effects, so `gateAction` returns
   null (no permission needed).
3. **Edit:** add a `WordCountTool` class in `Tools.kt` matching the style of
   the others. Register it in the tool list in `AppContainer.kt`.
4. **Test:** add `WordCountToolTest` in `app/src/test/.../agent/` and run
   `./gradlew :app:testDebugUnitTest --tests "*.WordCountToolTest"`.
5. **Verify:** `./gradlew :app:compileDebugKotlin` then `:app:assembleDebug`.
6. **Record:** append the `D-###` entry; if it changes what the user can do,
   note it in SPEC.md too.
7. **Commit:** as the user, no AI trailer.

---

## 10. Final checklist before you say "done"

- [ ] Compiles: `./gradlew :app:compileDebugKotlin` is green.
- [ ] Tests pass: `./gradlew :app:testDebugUnitTest` is green.
- [ ] Debug APK builds: `./gradlew :app:assembleDebug`.
- [ ] No raw dp/sp/color literals in any screen you touched.
- [ ] Every `Regex` has escaped closing braces/brackets.
- [ ] Every blocking call is on `Dispatchers.IO`.
- [ ] User-facing strings are lowercase, terse, no emoji.
- [ ] DECISIONS.md has a new entry if you changed behavior.
- [ ] No secrets staged (`git status --short`).
- [ ] Commit is authored as the user, no AI attribution.

If all ten are checked, you're actually done. If any isn't, you're not.
