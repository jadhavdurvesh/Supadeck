# SupaDeck

Made by Kestrane, a DMJ Group company.

A private Android admin app for your Supabase project: overview and insights, table browser,
users, and a SQL runner. There is nothing to deploy — the app talks straight to Supabase.

```
Android app  --(personal access token)-->  Supabase Management API  -->  your project
```

## 1. Get a personal access token

1. Go to [supabase.com/dashboard/account/tokens](https://supabase.com/dashboard/account/tokens)
   and generate a new token.
2. Copy it — it starts with `sbp_` and is shown only once.

**This token is a master credential for your whole Supabase account** — every project, billing
included — not just this one project's database. Treat it like your Supabase password:

- Don't share this APK or your token with anyone you wouldn't hand your Supabase login to.
- If your phone is lost or the token leaks, revoke it immediately from the same tokens page.
- The SQL tab runs queries read-only (enforced by Supabase itself), but the app still has full
  read access to every table, every schema, and your user list.

## 2. Get the app

Either build it yourself in Android Studio (`android/` folder, press Run), or push this repo to
GitHub and let `.github/workflows/build-apk.yml` build it for you:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The workflow attaches a `Kestrane-SupaDeck-<version>.apk` to a GitHub Release on that tag —
download it to your phone and install it (Android will ask you to allow installs from your
browser or file manager the first time).

## 3. Connect

Open the app and enter:

- **Project URL** — `https://<ref>.supabase.co`
- **Personal access token** — the `sbp_...` token from step 1

That's it. Both are stored encrypted on-device via the Android Keystore and never sent anywhere
except `api.supabase.com` and your own project URL.

## What each tab does

| Tab | What it shows | How |
| --- | --- | --- |
| Overview | user counts, sign-ups per day, DB size, connections, cache hit, largest tables, storage buckets | SQL via the Management API |
| Tables | every table and view in every schema, row browser with paging, column schema, RLS status | same |
| Users | search, details, ban / unban | reads and updates `auth.users` directly |
| SQL | your own queries, run **read-only** | Management API, `read_only: true` |
| Logs | edge, postgres, auth and function logs from the last hour | Management API logs endpoint |

## Why no backend?

An earlier version of this app used a Supabase Edge Function as a proxy, so the powerful key
never left a server. This version trades that isolation for simplicity, on request: one token,
no deployment, works the moment you install it. If you'd rather have that separation back —
useful if several people will use the app, or you want an audit point between the app and your
data — say so and it can be rebuilt that way.
