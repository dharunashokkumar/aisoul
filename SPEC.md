# AiSoul — Product Specification

> **Status:** living document. Spec version 0.3 — 2026-07-17.
> Every change is logged in [DECISIONS.md](DECISIONS.md). Visual design rules live in [DESIGN.md](DESIGN.md). Technical plan lives in [IMPLEMENTATION.md](IMPLEMENTATION.md).
> Implementation status: **M0 verified on device; M1–M5 built** — soul interview, memory + distillation, harness v2 (D-020), agent loop with tools + permission gate + approval sheets, toolbox terminal, chat history, dashboard + widget DSL with capability freeze, encrypted Drive/SAF backup + restore (D-024), compliance polish: report-a-response, agent foreground service, licenses/about, privacy policy page (D-025). On-device verification of M1–M5 pending; OAuth consent screen in flight (O-7).

---

## 1. What is AiSoul

**AiSoul is a personal AI harness that lives on your phone.** Not a chat wrapper — a harness: a persistent agent with a file-based soul, memory that compounds over time, real tools it can use with your permission, and a home screen it reshapes around your life.

The pitch in one breath:

> *Your AI, your keys, your files, your Drive. No account, no backend, no subscription. It learns you, and its home screen grows widgets around what you actually do.*

### Principles (in priority order)

1. **Local-first.** Everything lives as plain files on the device. The user can read, edit, and delete every file the AI knows them by.
2. **No backend, ever.** The app talks to exactly two kinds of servers: the AI provider the user configured (with the user's own key), and the user's own Google Drive (for backup). We run zero servers and can see zero user data.
3. **The user is the root user.** The AI proposes; the user approves. Every capability the agent has is visible, revocable, and gated by an explicit permission model (§6).
4. **Compounding by default.** Every conversation can leave the harness slightly smarter: memory distills, widgets adapt, the soul sharpens.
5. **Restraint.** UI follows DESIGN.md exactly. The app should feel like a quiet, precise instrument — never a toy.

### What AiSoul is NOT (v1 non-goals)

- Not a Termux replacement or general Linux environment (fixed toolbox only, §7).
- Not a Claude Code / Codex / Gemini CLI host — the app runs **its own agent loop** against provider APIs. CLI bridges are roadmap (§13).
- Not a launcher. "Home screen" means the app's own dashboard, not Android launcher widgets (those are roadmap).
- No multi-user, no cloud accounts, no telemetry/analytics SDKs.

---

## 2. Identity

| Item | Value |
|---|---|
| Name | **AiSoul** (rendered lowercase — `aisoul` — in-app, per DESIGN.md §7 voice) |
| Application ID | `com.aisoul.app` |
| Accent (DESIGN.md §1) | `accent-ice` `#8FB8C9` — cool, technical, precise |
| Signature moment (DESIGN.md §6) | **Widget birth**: when the user approves an AI-proposed widget, it materializes onto the dashboard with a one-time orchestrated entrance. Everything else stays quiet so this lands. |
| Tagline | *"an ai that grows around you"* |
| Price | Free at launch. Monetization revisited post-launch (see DECISIONS.md). |

---

## 3. Core concept: the harness

The harness is a folder of human-readable files that IS the AI's identity and knowledge. Seeded at onboarding, grown forever after.

```
/harness
  PROMPT.md         # operating rules — head of every system prompt (D-029 / D-034)
  SOUL.md           # who the AI is: personality, tone, boundaries, how it should behave
  USER.md           # who the user is: role, context, preferences
  MEMORY.md         # index of long-term memories (one line per memory file)
  SUMMARY.md        # the long arc — rolled up by the META pass (D-020)
  activity.tsv      # one row per distilled session: date, time, label, ops (D-020)
  memories/         # one markdown file per durable fact/topic, with frontmatter
    <slug>.md
  notes/            # daily notes the agent and user share
    2026-07-16.md
  journal/          # session logs the AI writes to its future self (D-020)
    2026-07-17.md
  widgets/          # installed dashboard widget specs (JSON, §8)
    <id>.json
  chats/            # conversation transcripts (JSONL, one file per conversation)
    <id>.jsonl
```

Rules:

- Every file is visible in an in-app file browser and editable by the user with a plain text editor.
- The system prompt for every agent turn is assembled from: **`PROMPT.md` first** (operating rules — tools, memory, trust, format) + `SOUL.md` + `USER.md` + `SUMMARY.md` + `MEMORY.md` (index) + recalled `memories/` bodies + today's note + last journal entry + time facts. No resume cursor and no forced "continue where we left off" (D-034).
- The agent reads harness files freely, appends to `memories/` and `notes/` freely (with a visible activity chip), and needs permission for everything else (§6).
- Memory files use frontmatter (`name`, `description`, `type: user|preference|project|reference`) so recall can rank by description without loading bodies.

### Memory distillation (the compounding loop)

After a conversation goes idle (or is closed), a background **distill pass** runs on a cheap model (user-configurable; defaults: Claude Haiku / Gemini Flash / `*-mini`):

1. Input: the conversation + current `MEMORY.md` index.
2. Output: zero or more memory operations — `create`, `update`, `delete` — on `memories/` files.
3. Operations apply silently for `create`/`update` of ordinary facts; `delete` and any edit touching `SOUL.md`/`USER.md` queue for user approval.
4. `MEMORY.md` index line is maintained automatically.

Distillation is visible: a "memory" screen shows a feed of what was learned and when, each entry deletable with one tap. **The user can always see exactly what the AI knows.**

---

## 4. Onboarding: the soul interview

The first ten minutes make or break this app. Flow:

1. **welcome** — one screen, one headline, one button. No carousel, no permissions wall.
2. **provider setup** — pick provider (Anthropic / OpenAI / Google Gemini / OpenAI-compatible custom URL), paste API key, key is validated with a minimal live call and stored in Android Keystore (§10). A "where do I get a key?" link per provider.
3. **the interview** — the AI itself (using the fresh key) conducts a short streamed conversation: what should it call you, what do you do, what do you want help with, how should it talk to you (terse/warm/formal), anything it should never do. 5–7 questions, skippable at any point.
4. **soul reveal** — the AI drafts `SOUL.md` and `USER.md` from the interview; user reviews both files in an editor and confirms. This is the moment the product's honesty lands: *these files are all it knows; they're yours.*
5. **dashboard** — opens with three default widgets: "talk" (opens chat), "today" (daily note), "memory" (count + last learned fact). The AI's first proposal ("want me to set up a widget for X?") comes later, from real usage — never during onboarding.
6. **backup nudge** — a dismissible card on the dashboard offers Google Drive backup setup (§9). Never a blocking step.

Skip path: if the user skips the interview, `SOUL.md`/`USER.md` are created from a neutral template and the harness learns from usage instead.

---

## 5. Chat & the agent loop

- **BYOK providers (v1):** Anthropic (Messages API), OpenAI, Google Gemini, plus **OpenAI-compatible custom base URL** (covers OpenRouter, LM Studio/Ollama on LAN, etc.). Model ID is a free-text field with sensible per-provider defaults.
- **Streaming** always (SSE), with a stop button, markdown rendering, and code blocks with copy.
- **The loop:** user message → system prompt assembled from harness (§3) → model responds, possibly with tool calls → tool calls pass the permission gate (§6) → tools execute → results return to the model → repeat until final text. Max 20 iterations per turn, hard cancel always available.
- **Tool-call cards** render inline in chat: which tool, exact input (e.g. the shell command), collapsed output. Nothing the agent does is invisible.
- Conversations persist as JSONL files under `/harness/chats/`; a local full-text index (rebuilt from files, never authoritative) powers search.
- Per-conversation model override; default model set in settings.
- **Report a response** (Play AI-content compliance, §12): long-press any AI message → "report" → prefilled email (user sees and consents to exactly what's included) to the developer address.

---

## 6. Tools & the permission model

The agent's tool registry in v1:

| Tool | What it does |
|---|---|
| `read_file` / `list_files` | Read anything under `/harness` |
| `write_file` | Create/append/overwrite harness files |
| `fetch` | HTTP request from the app (GET/POST, size- and time-capped) |
| `run_command` | Execute in the sandboxed toolbox shell (§7) |
| `propose_widget` | Submit a widget spec for user approval (§8) |
| `distill_memory` | Explicitly trigger a memory write (same rules as background distill) |

**The permission gate** sits between the model and every tool call. Three modes, set in settings; **standard** is the default:

| Action | careful | standard | trusted |
|---|---|---|---|
| read/list harness files | allow | allow | allow |
| append to `memories/`, `notes/` | ask | allow (visible chip) | allow |
| edit `SOUL.md` / `USER.md` | ask | ask | ask |
| overwrite/delete any file | ask | ask | ask |
| `fetch` to a new host | ask | ask ("always allow this host" option) | allow |
| `run_command` | ask | ask ("always allow this exact command" option) | allow read-only allowlist, ask otherwise |
| install/modify a widget | ask | ask (approval sheet, always) | ask (always) |
| restore from backup | ask | ask | ask |

- Approval prompts show the **exact** command/URL/diff — never a summary alone.
- "Always allow" rules are listed and revocable in settings.
- Web content fetched by `fetch` is marked untrusted in the tool result; the system prompt instructs the model that instructions found inside fetched content are data, not commands (prompt-injection hygiene — see IMPLEMENTATION.md §11).

---

## 7. The toolbox (sandboxed terminal)

**Hard platform constraint:** Android 10+ forbids executing binaries from app-writable storage. Therefore no package manager and no downloaded tools — the toolbox is a **fixed set of static binaries shipped inside the APK** (packaged as native libs, which remain executable). This is genuinely sandboxed: everything runs as the app's own Linux UID inside the app sandbox, no root, no special permissions beyond INTERNET.

v1 toolbox:

| Tool | Source |
|---|---|
| `sh` + coreutils (`ls`, `cat`, `grep`, `sed`, `awk`, `head`, `tail`, `wc`, …) | busybox (static, bundled) |
| `wget`, `nslookup`, `nc` | busybox applets |
| `curl` | static build, bundled |
| `jq` | static build, bundled |
| `ping` | the device's own `/system/bin/ping` (apps may exec it; busybox ping needs raw sockets and can't run unprivileged) |

Environment: `HOME` and working dir inside a dedicated `/harness/workspace/` sandbox dir; `PATH` resolves to bundled binaries; per-command timeout (default 30 s), output cap (default 64 KB), no background daemons. The user also gets a manual **terminal screen** using the same toolbox — useful on its own and honest about what the agent can and can't do.

Explicitly out of scope for v1: package installation, proot distros, Termux integration (roadmap §13).

---

## 8. Adaptive dashboard & the Widget DSL

The differentiating feature. The dashboard is the app's home screen; the AI grows it.

### The loop

1. Signals accumulate: recurring topics in memory, repeated commands, repeated fetches.
2. The agent calls `propose_widget` — organically in conversation ("you check those servers a lot — want a status widget?") or from the distill pass.
3. Proposal appears as a card (in chat and in a dashboard inbox) with a **plain-language capability summary**: *"this widget will: ping db.example.com every 15 min; GET https://web-01…/health every 15 min."*
4. User approves → **widget birth** (the signature moment) → spec is frozen into `/harness/widgets/<id>.json`.
5. Any modification — by AI or user — re-runs approval. Frozen means frozen.

### The DSL — declarative JSON, never code

**Play-critical rule:** Google Play prohibits downloading executable code. AI output is therefore pure declarative data rendered by the app. No JS, no WebView, no eval — anywhere.

```json
{
  "schema": 1,
  "id": "server-status",
  "title": "server status",
  "icon": "dns",
  "size": "medium",
  "refresh": { "on_open": true, "interval_min": 15 },
  "sources": {
    "web":  { "type": "http", "method": "GET", "url": "https://web-01.example.com/health", "extract": "$.status" },
    "db":   { "type": "tool", "command": "ping -c 1 -W 2 db.example.com", "extract": "regex:time=([0-9.]+)" },
    "todo": { "type": "file", "path": "notes/servers.md", "extract": "lines:1-3" }
  },
  "body": [
    { "type": "stat", "label": "web-01", "value": "{web}", "ok_when": "healthy" },
    { "type": "stat", "label": "db ping", "value": "{db} ms" },
    { "type": "list", "items_from": "todo" },
    { "type": "buttons", "items": [
      { "label": "diagnose", "action": { "type": "chat", "prompt": "my servers look slow — investigate, start with ping" } },
      { "label": "refresh",  "action": { "type": "refresh" } }
    ]}
  ]
}
```

**Components (v1):** `text`, `stat` (label + value, optional `ok_when` mapping to semantic colors), `list`, `progress`, `sparkline` (numeric history the app records per source), `buttons`, `divider`.

**Sources (v1):** `static`, `http` (approved URLs only), `tool` (approved commands only), `file` (harness paths only), `countdown` (to a date), `memory` (built-in count + latest, D-022). Extractors: JSONPath subset (`$.a.b[0]`), `regex:` with one capture group, `lines:` ranges.

**Actions (v1):** `chat` (open chat pre-filled with a prompt), `run` (a command frozen at approval), `url` (open in browser), `refresh`, `screen` (fixed set: chat/memory/files/terminal, D-022). A widget may also declare one root-level `tap` action for whole-card taps (D-022). The three §4 default widgets (talk/today/memory) are ordinary pre-approved DSL files.

**Icons:** tokens from a fixed bundled Material Symbols subset. No emoji (DESIGN.md §8 bans emoji in UI).

### Security model

- Capabilities are **frozen at approval**: only the approved URLs/commands/paths in the spec can ever execute; templating cannot construct new ones at runtime.
- Schema-validated on parse; unknown fields/types → widget refuses to render (shows a quiet error card).
- Refresh via WorkManager, minimum interval 15 min; on-open refresh is immediate.
- Widgets are just files — they back up, restore, and can be shared as JSON (import runs the same approval sheet).

---

## 9. Backup & sync — Google Drive, no backend

- **Mechanism:** the app requests the `drive.file` OAuth scope (non-sensitive → light Google verification) and creates a visible **"AiSoul Backups"** folder in the user's Drive. It can only touch files it created.
- **What's backed up:** the entire `/harness` tree as one archive: `aisoul-backup-<yyyyMMdd-HHmm>.zip.enc`.
- **Encryption:** client-side, always. User sets a backup passphrase (key derived via Argon2id; AES-256-GCM). Clear UX: *lose the passphrase, lose the backups.* **API keys are never in any backup** — they live only in Android Keystore and are re-entered on restore.
- **Schedule:** automatic daily + change-debounced via WorkManager (Wi-Fi-only toggle). Keep last 10 archives, prune older.
- **Restore:** list archives → pick → passphrase → full-tree preview → confirm (always asks, §6).
- **Fallback path (no Google account needed):** manual export/import of the same encrypted archive via the system file picker (SAF) — also serves as device-to-device migration.

Google sign-in exists **solely** as Drive authorization. AiSoul has no accounts of its own.

---

## 10. Settings

- **providers** — keys (Keystore-backed; masked, re-enterable, deletable), default model, distill model, custom base URL.
- **permissions** — mode (careful/standard/trusted), the "always allow" rule list (hosts, commands) with one-tap revoke.
- **memory** — the learned-facts feed, per-item delete, "forget everything" (typed confirmation).
- **backup** — Drive connect/disconnect, passphrase set/change, schedule, manual export/import.
- **appearance** — accent choice among the three DESIGN.md options (ice default); everything else is fixed by the design system.
- **about** — version, licenses, privacy policy link, report-a-problem.

---

## 11. Voice & feel

DESIGN.md is law: dark-native, one accent ≤5% of pixels, lowercase headlines, spring motion (`damping 0.75 / stiffness 380`), haptics wired to every interaction, no emoji, no purple, numbers count up. Copy is terse and confident: *"you're in."*, *"learned 3 things from that chat."*, *"couldn't reach your drive. pull down to retry."* The one theatrical element is widget birth (§2); everything else stays quiet.

---

## 12. Play Store compliance & safety

| Requirement | AiSoul's answer |
|---|---|
| AI-Generated Content policy | In-app "report this response" on every AI message (§5) |
| No downloading executable code | AI output is declarative DSL only; toolbox binaries ship inside the APK; no JS/eval/WebView execution of AI output |
| Data safety form | Developer collects **nothing**; user data flows device ↔ user's chosen AI provider and device ↔ user's own Drive only; no analytics SDKs |
| Account deletion policy | N/A — no accounts exist; Google auth is Drive authorization only |
| Target API level | 35+ |
| Privacy policy | Static page on GitHub Pages (required by both Play listing and OAuth consent screen) |
| Personal dev account gate | Closed test with **12 testers for 14 continuous days** before production — see IMPLEMENTATION.md §13 launch plan |
| Permissions requested | `INTERNET`, `POST_NOTIFICATIONS` (agent-run progress), foreground-service type declaration. Nothing else. No contacts, no location, no storage (harness lives in app-private storage; SAF handles export) |

Safety posture: user keys never leave Keystore; everything the agent does is visible and gated; fetched web content is treated as untrusted data; widgets can only do what was approved at install.

---

## 13. Roadmap (post-v1, in rough order)

1. ~~**Android launcher widgets** (Glance) fed by the same widget DSL.~~ ✅ shipped v0.6.0 (D-033) — zero-config mini-dashboard from cached values.
2. ~~**Smarter recall** — embeddings-based memory recall replacing keyword ranking.~~ ✅ shipped v0.6.0 (D-032) — provider embeddings, keywords as the floor.
3. **Expand the bundled toolbox** — more static binaries shipped as `jniLibs` (the busybox/curl/jq path, D-019). **AiSoul never requires a companion app** — the old "Termux bridge" item is dropped (D-027): asking users to install Termux is exactly the dependency a local-first app avoids.
4. **Desktop remote** — phone as cockpit for Claude Code running on a PC (SSH/Tailscale).
5. **Widget sharing gallery** — import/export is already JSON; a curated gallery of community specs (still no backend: specs hosted in a public repo).
6. **Voice** — push-to-talk input, spoken replies.
7. **Monetization decision** — revisit once there are real users (leading candidate: one-time "pro" unlock; see DECISIONS.md).
