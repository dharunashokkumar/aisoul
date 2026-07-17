# The complete beginner's guide to shipping AiSoul

Everything confusing, explained once: how apps get signed, how the Play Store
pipeline works, what "sending an update" actually does, and how websites work.
Read top to bottom once; after that you'll only ever need the checklists.

---

## Part 1 — Signing & keystores (the "wax seal" system)

**The problem it solves:** when a phone installs "AiSoul update v2", how does
it know the update really came from *you* and not an attacker? Answer: every
APK is stamped with a cryptographic signature, like a wax seal only you can
make.

- A **keystore** is a small password-protected file (e.g. `aisoul-upload.jks`)
  containing your secret signing key. That's the seal.
- The **SHA-1 fingerprint** you pasted into Google Cloud is like a photo of
  the seal — it lets Google recognize "yes, this app was stamped by that key."
- **Debug key**: Android Studio auto-generates one on every dev machine
  (`~/.android/debug.keystore`, password "android"). It's for development
  only — Play won't accept it. That's what your current builds use.
- **Upload key**: the real one YOU create once (that keytool command from
  before). You sign every Play upload with it, forever.
- **Play App Signing**: when you first upload, Google generates a *third* key
  (the "app signing key") and re-signs your app with it before delivering to
  users. You keep the upload key, Google keeps the signing key. Benefit: if
  you ever lose your upload key, Google can reset it. This is also why Drive
  needs a SECOND OAuth client later — Play-delivered builds carry Google's
  seal, not yours, so its SHA-1 is different.

**The one rule:** back up `aisoul-upload.jks` and its password somewhere that
survives your laptop dying. Losing it is recoverable (Play can reset), but
it's a support-ticket headache you don't want.

**Why two build types exist:**
| | debug build | release build |
|---|---|---|
| signed with | auto debug key | your upload key |
| code | as written | minified/shrunk by R8 (22 MB → 10 MB) |
| for | your phone, development | Play Store |

---

## Part 2 — The Play Store pipeline (rings of people)

Publishing isn't one button. Your app moves outward through **tracks** —
think of them as rings of increasingly many people:

```
you alone  →  people you invite  →  the whole world
(internal)     (closed testing)      (production)
```

- **Internal testing** — up to 100 emails you list, updates go live in
  minutes with no review. This is "does the Play-delivered build even work?"
- **Closed testing** — invited testers via an email list or a link. Normal
  review applies (hours to a couple of days).
- **Open testing** — anyone can opt in from the store page. Optional; you
  can skip it.
- **Production** — the actual store listing everyone sees.

**The 12×14 rule (applies to you):** personal developer accounts created
after Nov 2023 must run a closed test with **at least 12 testers
continuously opted-in for 14 days** before Google unlocks the production
ring. It's Google's spam filter — proof a real human tested a real app.
The clock runs as long as 12+ people stay opted in; you can push fixes
during it without resetting anything. This is the single longest step in
the whole process, which is why the plan is to recruit friends +
r/androidapps when the time comes.

**A build is promoted, not rebuilt:** the exact `.aab` file you tested in
internal gets *promoted* to closed, then to production. You never rebuild
between rings — that would invalidate the testing.

---

## Part 3 — What "sending an update" actually means

This is the part that confused you; it's genuinely simple:

1. You change code.
2. Bump two numbers in `app/build.gradle.kts`:
   - `versionCode` 4 → 5 (invisible counter; **must** go up every upload,
     can never go down — Play rejects reused or lower numbers)
   - `versionName` "0.5.0" → "0.6.0" (the human-readable one users see)
3. Build: `./gradlew :app:bundleRelease` → new `.aab`.
4. Play Console → the track you want → **Create new release** → drag the
   `.aab` in → write 2 lines of release notes → roll out.
5. Google reviews it (minutes for internal, ~hours–2 days for production),
   then phones **auto-update** over the following days. Users do nothing.

**What survives an update:** everything. The harness files, settings, keys —
app updates never touch app data. Only *uninstalling* deletes data (that's
also why Drive backup matters).

**Staged rollout:** for production you can release to 20% of users first,
watch crash stats for a day, then go 100% — or halt if something's wrong.
Halting stops NEW updates; people who already got it keep it.

**Typical update rhythm for you later:**
```
fix bugs → bump versions → bundleRelease → upload to internal →
try it on your phone → promote same build to production (staged 20%) →
next day, 100%
```

---

## Part 4 — Websites, demystified in five sentences

1. A website is just **files in a folder on a computer that's always on**
   (a "server"), plus a **domain name** (dharun.dev) that tells browsers
   which computer to ask.
2. When someone opens `https://dharun.dev/projects/AiSoul/privacy`, the
   server looks for a file at that path and sends its text back; the
   browser draws it.
3. **`index.html` is nothing magical** — it's just the filename servers
   serve *by default* when a URL points at a folder instead of a file
   (`dharun.dev/` really serves `dharun.dev/index.html`).
4. "Hosting" = renting/borrowing that always-on computer. For plain files
   (like a privacy page) it's free everywhere: GitHub Pages, Cloudflare
   Pages, Netlify — you upload files, they serve them. You already have
   this working, which is why your privacy URL is live.
5. The only maintenance: when `docs/privacy.html` changes in this project,
   re-upload it to your site so the live page matches. That's it. No
   servers to run, nothing to break.

Your app deliberately needs **zero** website beyond that one page — no
backend, no accounts, no API. The page exists only because Play and
Google's OAuth screen require a public privacy policy URL.

---

## Part 5 — Where AiSoul stands right now

**Done:** M0–M5 built and verified on your phone (chat + tools + widgets +
Drive backup round-trip + report flow). OAuth client live. Privacy page
live. Release build (R8) green. Release signing wired — it auto-uses
`keystore.properties` when that file exists.

**Deliberately NOT done (waiting until you say "publish"):**
- Upload keystore creation (Part 1) — 5 minutes when needed
- Play Console setup — `DATA_SAFETY.md` holds every form answer
- The 12×14 closed test (Part 2)
- Second OAuth client for Play's signing key (Part 1 explains why)

Nothing about continuing development conflicts with publishing later; every
update just bumps `versionCode` when the day comes. Build features freely.
