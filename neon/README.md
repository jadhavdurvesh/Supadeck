# NeonDeck

Made by Kestrane, a DMJ Group company.

A private Android client for your Neon Postgres database: overview and insights, a full table
browser across every schema, and a SQL runner. Neon is just Postgres, so there's no proxy, no
Management API, no token to generate — the app connects with a standard connection string,
exactly like any desktop Postgres client (TablePlus, DBeaver, psql) would.

```
Android app  --(postgresql:// connection string, TLS)-->  your Neon database
```

## 1. Get your connection string

Neon dashboard → your project → **Connection Details**. Copy the string that looks like:

```
postgresql://alex:AbCdEf123456@ep-cool-darkness-123456.us-east-2.aws.neon.tech/neondb?sslmode=require
```

That string **is** your database password — anyone with it can read and write everything in that
database. Treat it accordingly:

- Don't share this APK alongside your connection string.
- If it leaks, reset the role's password from the Neon dashboard (Roles → your role → Reset password).
- The SQL tab runs every query inside a genuine Postgres read-only transaction — enforced by the
  database engine itself, not just the app — so accidental writes from that tab aren't possible.

## 2. Get the app

Build it yourself in Android Studio (`neon/` folder), or push this repo to GitHub and tag a
release — `.github/workflows/build-neondeck-apk.yml` builds it for you:

```bash
git tag neon-v1.0.0 && git push origin neon-v1.0.0
```

(Note the `neon-v` prefix — that's what keeps NeonDeck's releases separate from SupaDeck's `v*`
tags in this same repo.) The workflow attaches `Kestrane-NeonDeck-<version>.apk` to a GitHub
Release on that tag.

## 3. Connect

Open the app, paste the connection string, tap Connect. It's stored encrypted on-device via the
Android Keystore and used only to talk to your database host directly.

**First connection after a while idle can take a few seconds** — Neon suspends compute when
nothing's using it, and waking it back up isn't instant.

## What each tab does

| Tab | What it shows | How |
| --- | --- | --- |
| Overview | database size, connections, cache hit rate, largest tables, every database on this branch | `pg_catalog` / `pg_stat_*` queries |
| Tables | every table and view in every schema, row browser with paging, column schema, RLS status | same |
| SQL | your own queries, run **read-only** | a real Postgres read-only transaction |

No Users or Logs tab — Neon doesn't bundle an Auth or Storage system the way Supabase does, so
there's no `auth.users` table or request-log schema to show. If you add your own users table,
it'll show up under Tables like any other table.
