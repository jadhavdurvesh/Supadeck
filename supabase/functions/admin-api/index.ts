// SupaDeck by Kestrane, a DMJ Group company.
// admin-api — one Edge Function that the Android app talks to.
// The service-role key and DB connection live HERE (server side), never in the app.
// Every request must carry the session of a user whose id is listed in ADMIN_USER_IDS.

import { createClient } from "npm:@supabase/supabase-js@2";
import postgres from "npm:postgres@3.4.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const DB_URL = Deno.env.get("SUPABASE_DB_URL")!;
const ADMIN_IDS = (Deno.env.get("ADMIN_USER_IDS") ?? "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
const MGMT_TOKEN = Deno.env.get("MGMT_ACCESS_TOKEN"); // optional, enables the Logs tab
const PROJECT_REF = new URL(SUPABASE_URL).hostname.split(".")[0];

const admin = createClient(SUPABASE_URL, SERVICE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
});
const sql = postgres(DB_URL, { max: 3, prepare: false, idle_timeout: 20 });

const json = (data: unknown, status = 200) =>
  new Response(
    JSON.stringify(data, (_k, v) => (typeof v === "bigint" ? v.toString() : v)),
    { status, headers: { "Content-Type": "application/json" } },
  );

const clamp = (v: unknown, min: number, max: number, dflt: number) => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.min(Math.max(Math.trunc(n), min), max) : dflt;
};

async function columnNames(schema: string, table: string): Promise<string[]> {
  const rows = await sql`
    select a.attname as name
    from pg_attribute a
    join pg_class c on c.oid = a.attrelid
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = ${schema} and c.relname = ${table}
      and c.relkind in ('r','v','m','p') and a.attnum > 0 and not a.attisdropped
    order by a.attnum`;
  return rows.map((r) => r.name as string);
}

// deno-lint-ignore no-explicit-any
const actions: Record<string, (p: any) => Promise<unknown>> = {
  // ---------- Overview / insights ----------
  overview: async () => {
    const [db] = await sql`
      select pg_database_size(current_database())::float8 as db_bytes,
             (select count(*) from pg_stat_activity where datname = current_database())::int as connections,
             version() as version`;
    const [tables] = await sql`
      select count(*)::int as n from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where c.relkind in ('r','p') and n.nspname = 'public'`;
    const [users] = await sql`
      select count(*)::int as total,
             (count(*) filter (where last_sign_in_at > now() - interval '7 days'))::int as active_7d,
             (count(*) filter (where created_at > now() - interval '24 hours'))::int as new_24h
      from auth.users`;
    const signups = await sql`
      select to_char(d, 'MM-DD') as day, coalesce(u.c, 0)::int as count
      from generate_series((current_date - 13)::timestamp, current_date::timestamp, interval '1 day') as d
      left join (select created_at::date as day, count(*) as c from auth.users group by 1) u
             on u.day = d::date
      order by d`;
    const storage = await sql`
      select b.name, count(o.id)::int as objects,
             coalesce(sum((o.metadata->>'size')::bigint), 0)::float8 as bytes
      from storage.buckets b left join storage.objects o on o.bucket_id = b.id
      group by b.name order by bytes desc limit 10`;
    const top_tables = await sql`
      select schemaname as schema, relname as name,
             pg_total_relation_size(relid)::float8 as bytes, n_live_tup::float8 as rows
      from pg_stat_user_tables order by bytes desc limit 8`;
    const [cache] = await sql`
      select round(100 * sum(heap_blks_hit) / nullif(sum(heap_blks_hit) + sum(heap_blks_read), 0), 2)::float8 as cache_hit
      from pg_statio_user_tables`;
    return { db, tables: tables.n, users, signups, storage, top_tables, cache_hit: cache?.cache_hit ?? null };
  },

  // ---------- Tables ----------
  tables: async () => ({
    tables: await sql`
      select n.nspname as schema, c.relname as name,
             case c.relkind when 'r' then 'table' when 'v' then 'view'
                            when 'm' then 'materialized view' when 'p' then 'partitioned table' end as kind,
             c.reltuples::float8 as est_rows, c.relrowsecurity as rls
      from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where c.relkind in ('r','v','m','p')
        and n.nspname not in ('pg_catalog','information_schema') and n.nspname not like 'pg\\_%'
      order by (n.nspname = 'public') desc, n.nspname, c.relname`,
  }),

  columns: async ({ schema, table }) => ({
    columns: await sql`
      select a.attname as name, format_type(a.atttypid, a.atttypmod) as type, not a.attnotnull as nullable
      from pg_attribute a
      join pg_class c on c.oid = a.attrelid
      join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = ${String(schema)} and c.relname = ${String(table)}
        and c.relkind in ('r','v','m','p') and a.attnum > 0 and not a.attisdropped
      order by a.attnum`,
  }),

  rows: async ({ schema, table, limit, offset, order_by, desc }) => {
    schema = String(schema);
    table = String(table);
    const cols = await columnNames(schema, table);
    if (!cols.length) throw new Error("Unknown table");
    // never ship password hashes / tokens to the phone
    const shown = schema === "auth" ? cols.filter((c) => !/(password|token|secret)/i.test(c)) : cols;
    const lim = clamp(limit, 1, 200, 50);
    const off = clamp(offset, 0, 1_000_000_000, 0);
    const order = order_by && cols.includes(order_by)
      ? sql`order by ${sql(order_by)} ${desc ? sql`desc` : sql`asc`}`
      : sql``;
    const data = await sql`
      select ${sql(shown)} from ${sql(schema)}.${sql(table)} ${order}
      limit ${lim + 1} offset ${off}`;
    return { columns: shown, rows: data.slice(0, lim), has_more: data.length > lim };
  },

  // ---------- Users ----------
  users: async ({ q, limit, offset }) => {
    const lim = clamp(limit, 1, 200, 50);
    const off = clamp(offset, 0, 1_000_000_000, 0);
    const term = String(q ?? "").trim();
    const like = `%${term.replace(/[%_\\]/g, "\\$&")}%`;
    const rows = await sql`
      select id, email, phone, created_at, last_sign_in_at,
             (email_confirmed_at is not null) as confirmed, banned_until,
             coalesce(raw_app_meta_data->>'provider', '') as provider,
             raw_user_meta_data as metadata
      from auth.users
      where ${term ? sql`(email ilike ${like} or phone ilike ${like})` : sql`true`}
      order by created_at desc
      limit ${lim + 1} offset ${off}`;
    return { users: rows.slice(0, lim), has_more: rows.length > lim };
  },

  user_ban: async ({ id, ban }) => {
    const { error } = await admin.auth.admin.updateUserById(String(id), {
      ban_duration: ban ? "876000h" : "none",
    });
    if (error) throw error;
    return { ok: true };
  },

  // ---------- SQL runner (read-only) ----------
  sql: async ({ query }) => {
    let text = String(query ?? "").trim().replace(/;\s*$/, "");
    if (!text) throw new Error("Empty query");
    if (text.includes(";")) throw new Error("One statement at a time");
    const started = Date.now();
    const result = await sql.begin("read only", async (tx) => {
      await tx`set local statement_timeout = '15s'`;
      return await tx.unsafe(text);
    });
    return {
      columns: result.columns?.map((c: { name: string }) => c.name) ?? [],
      rows: result.slice(0, 500),
      truncated: result.length > 500,
      ms: Date.now() - started,
    };
  },

  // ---------- Logs (optional, needs MGMT_ACCESS_TOKEN) ----------
  logs: async ({ source, limit }) => {
    if (!MGMT_TOKEN) return { configured: false, rows: [] };
    const allowed = ["edge_logs", "postgres_logs", "auth_logs", "function_logs"];
    const table = allowed.includes(source) ? source : "edge_logs";
    const end = new Date();
    const start = new Date(end.getTime() - 60 * 60 * 1000);
    const query = `select id, timestamp, event_message from ${table} order by timestamp desc limit ${clamp(limit, 1, 100, 50)}`;
    const url = `https://api.supabase.com/v1/projects/${PROJECT_REF}/analytics/endpoints/logs.all?` +
      new URLSearchParams({
        sql: query,
        iso_timestamp_start: start.toISOString(),
        iso_timestamp_end: end.toISOString(),
      });
    const res = await fetch(url, { headers: { Authorization: `Bearer ${MGMT_TOKEN}` } });
    const body = await res.json().catch(() => ({}));
    if (!res.ok || body.error) {
      const err = typeof body.error === "string" ? body.error : body.message ?? `Management API ${res.status}`;
      return { configured: true, rows: [], error: err };
    }
    const rows = (body.result ?? []).map((r: { timestamp: number | string; event_message: string }) => ({
      time: new Date(Number(r.timestamp) / 1000).toISOString(),
      message: r.event_message,
    }));
    return { configured: true, rows };
  },
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return json({ error: "Missing session" }, 401);
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) return json({ error: "Invalid or expired session" }, 401);
  if (!ADMIN_IDS.includes(data.user.id)) return json({ error: "This account is not an admin" }, 403);

  let body: { action?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "Body must be JSON" }, 400);
  }
  const handler = actions[body.action ?? ""];
  if (!handler) return json({ error: `Unknown action: ${body.action}` }, 400);

  try {
    return json(await handler(body));
  } catch (e) {
    return json({ error: (e as Error)?.message ?? String(e) }, 500);
  }
});
