# SupaDeck

Made by Kestrane, a DMJ Group company.

A private Android admin app for your Supabase project: overview and insights, table browser,
users, a read-only SQL runner and (optionally) logs.

```
Android app  --(your admin login)-->  admin-api Edge Function  -->  Postgres / Auth / Management API
   (no secrets)                        (holds all privileged keys)
```

The app never contains your `service_role` key or a Supabase access token. It signs in as you,
and the Edge Function checks that your user id is on the admin list before doing anything.

## 1. Deploy the backend (once)

**Option A — GitHub Actions (recommended, no local setup):**

Add three repo secrets under **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value | Where to get it |
| --- | --- | --- |
| `SUPABASE_ACCESS_TOKEN` | a personal access token | supabase.com/dashboard/account/tokens → Generate new token |
| `SUPABASE_PROJECT_REF` | your project ref | the `<ref>` in `https://<ref>.supabase.co`, or Dashboard → Settings → General |
| `ADMIN_USER_IDS` | your user UUID (comma-separate several) | Dashboard → Authentication → Users → copy the **User UID** |

Then run the **Deploy Supabase Edge Function** workflow from the Actions tab (or just push a change under `supabase/functions/`). It deploys `admin-api` and sets its `ADMIN_USER_IDS` and `MGMT_ACCESS_TOKEN` secrets for you — the same access token doubles as the Management API token used by the Logs tab.

- The admin account needs **email + password** sign-in. If you normally use OAuth or magic links, create a dedicated admin user in the dashboard with a password.
- Recommended: Authentication → Sign In / Providers → turn off "Allow new users to sign up".

**Option B — Supabase CLI locally:**

```bash
# from the repo root, logged in and linked to your project
supabase functions deploy admin-api --no-verify-jwt
supabase secrets set ADMIN_USER_IDS=<your-user-uuid>
supabase secrets set MGMT_ACCESS_TOKEN=<personal-access-token>   # optional, enables the Logs tab
```

`--no-verify-jwt` is intentional: the function validates the session itself and rejects anyone who isn't on `ADMIN_USER_IDS`.

## 2. Build the app

Option A: open the `android/` folder in Android Studio and press Run.
Gradle downloads what it needs; if Studio complains about the wrapper, let it use its bundled Gradle 8.9.

Option B (no computer needed): push this repo to GitHub. The workflow in `.github/workflows/build-apk.yml`
builds a debug APK on every push to `main` that touches `android/`, and you can also start it by hand
from the Actions tab. Open the run, download the `kestrane-supadeck-apk` artifact and install the APK.

To get a permanent download link, push a version tag:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

The workflow then attaches the APK to a GitHub Release. Android will ask you to allow installs from your browser or file manager the first time.

The app's version name and version code come from the tag itself — no manual editing of `build.gradle.kts` needed. A push to `main` without a tag builds a `0.1.0-dev.<run>+<sha>` version for testing.

## 3. First launch

Enter your Project URL (`https://<ref>.supabase.co`), the anon / publishable key, and the admin
email and password. The URL and public key are stored encrypted with the Android Keystore,
along with the session tokens.

## What each tab does

| Tab | What it shows | Where the data comes from |
| --- | --- | --- |
| Overview | user counts, sign-ups per day, DB size, connections, cache hit, largest tables, storage buckets | SQL inside the function |
| Tables | every table and view in every schema, row browser with paging, column schema, RLS status | `pg_catalog` + `select` |
| Users | search, details, ban / unban | `auth.users` + Auth Admin API |
| SQL | read-only queries, up to 500 rows | transaction set to `READ ONLY`, 15 s timeout |
| Logs | edge, postgres, auth and function logs from the last hour | Management API (needs `MGMT_ACCESS_TOKEN`) |

## Security notes

- Only user ids in `ADMIN_USER_IDS` get any data; everyone else gets 403.
- The row browser hides password hashes and token columns of `auth.*` tables.
- SQL runner: one statement, read-only transaction. A lost phone still means your session is on it,
  so keep a screen lock and use Sign out or revoke the session from the dashboard if it goes missing.
- Ban / unban are the only write actions. Adding more means adding an action in `index.ts`.

SupaDeck is an independent tool and is not affiliated with or endorsed by Supabase.
