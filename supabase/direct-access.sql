-- ============================================================
-- App 直连模式初始化脚本
-- 使用方式：Supabase Dashboard -> SQL Editor -> 粘贴执行
-- 前置条件：已执行 supabase/schema.sql
--
-- 背景：当前网络的 TLS SNI 会拦截 *.supabase.co 的 HTTPS 接口，
-- App 改为直连 Session Pooler（aws-0-<region>.pooler.supabase.com:5432）。
-- 因此登录/注册不再走 GoTrue，而是在数据库内用 SECURITY DEFINER 函数完成，
-- RLS 策略也由 auth.uid() 改为读取会话变量 public.app_uid()。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 自定义数据库角色
--    连接池用户名形如 app_client.<project-ref>，
--    请把下面的 CHANGEME 替换为随机密码，并与 local.properties 的
--    SUPABASE_DB_PASSWORD 保持一致
-- ------------------------------------------------------------
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'app_client') then
        create role app_client login password 'CHANGEME';
    else
        alter role app_client login password 'CHANGEME';
    end if;
end;
$$;

grant usage on schema public to app_client;

-- ------------------------------------------------------------
-- 2. 会话表：App 登录后保存一次性令牌
-- ------------------------------------------------------------
create table if not exists public.app_sessions (
    token      uuid primary key default gen_random_uuid(),
    user_id    uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default now() + interval '30 days',
    revoked_at timestamptz
);

create index if not exists app_sessions_user_idx on public.app_sessions (user_id);

-- 该表只允许下面的 SECURITY DEFINER 函数访问，不向客户端开放
alter table public.app_sessions enable row level security;
revoke all on public.app_sessions from anon, authenticated;

-- ------------------------------------------------------------
-- 3. 当前用户识别函数：从连接级变量 app.session_token 解析
-- ------------------------------------------------------------
create or replace function public.app_uid()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select s.user_id
    from public.app_sessions s
    where s.token::text = nullif(current_setting('app.session_token', true), '')
      and s.revoked_at is null
      and s.expires_at > now()
    limit 1;
$$;

-- ------------------------------------------------------------
-- 4. 登录：pgcrypto 校验 auth.users 里的 bcrypt 密码，成功后发放令牌
-- ------------------------------------------------------------
create or replace function public.app_login(p_email text, p_password text)
returns uuid
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
    v_user_id uuid;
    v_token   uuid := gen_random_uuid();
begin
    select u.id into v_user_id
    from auth.users u
    where lower(u.email) = lower(trim(p_email))
      and u.encrypted_password is not null
      and u.deleted_at is null
      and crypt(p_password, u.encrypted_password) = u.encrypted_password
    limit 1;

    if v_user_id is null then
        raise exception '邮箱或密码不正确' using errcode = 'P0001';
    end if;

    insert into public.app_sessions (token, user_id, expires_at)
    values (v_token, v_user_id, now() + interval '30 days');

    -- 顺带清理过期或已撤销的旧会话
    delete from public.app_sessions
    where expires_at < now() or revoked_at is not null;

    return v_token;
end;
$$;

-- ------------------------------------------------------------
-- 5. 注册：直接写入 auth.users（邮箱视为已确认），并立即发放令牌
--    auth.users 上的 on_auth_user_created 触发器会自动创建内置分类
-- ------------------------------------------------------------
create or replace function public.app_signup(p_email text, p_password text)
returns uuid
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
    v_user_id uuid := gen_random_uuid();
    v_token   uuid := gen_random_uuid();
    v_email   text := lower(trim(p_email));
begin
    if v_email is null or v_email = '' then
        raise exception '请输入邮箱' using errcode = 'P0001';
    end if;

    if length(p_password) < 6 then
        raise exception '密码至少 6 位' using errcode = 'P0001';
    end if;

    if exists (select 1 from auth.users where lower(email) = v_email) then
        raise exception '该邮箱已注册，请直接登录' using errcode = 'P0001';
    end if;

    insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
        raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
        confirmation_token, recovery_token, email_change, email_change_token_new
    ) values (
        '00000000-0000-0000-0000-000000000000',
        v_user_id,
        'authenticated',
        'authenticated',
        v_email,
        crypt(p_password, gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now(),
        '', '', '', ''
    );

    insert into public.app_sessions (token, user_id, expires_at)
    values (v_token, v_user_id, now() + interval '30 days');

    return v_token;
end;
$$;

-- ------------------------------------------------------------
-- 6. 退出登录：撤销当前连接对应的会话令牌
-- ------------------------------------------------------------
create or replace function public.app_logout()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.app_sessions
       set revoked_at = now()
     where token::text = nullif(current_setting('app.session_token', true), '')
       and revoked_at is null;
end;
$$;

-- ------------------------------------------------------------
-- 7. RLS 策略改为 public.app_uid()（替换 schema.sql 中的 auth.uid() 版本）
-- ------------------------------------------------------------
-- categories
drop policy if exists categories_select_own on public.categories;
create policy categories_select_own on public.categories
    for select using (public.app_uid() = user_id);

drop policy if exists categories_insert_own on public.categories;
create policy categories_insert_own on public.categories
    for insert with check (public.app_uid() = user_id);

drop policy if exists categories_update_own on public.categories;
create policy categories_update_own on public.categories
    for update using (public.app_uid() = user_id) with check (public.app_uid() = user_id);

drop policy if exists categories_delete_own on public.categories;
create policy categories_delete_own on public.categories
    for delete using (public.app_uid() = user_id);

-- records
drop policy if exists records_select_own on public.records;
create policy records_select_own on public.records
    for select using (public.app_uid() = user_id);

drop policy if exists records_insert_own on public.records;
create policy records_insert_own on public.records
    for insert with check (public.app_uid() = user_id);

drop policy if exists records_update_own on public.records;
create policy records_update_own on public.records
    for update using (public.app_uid() = user_id) with check (public.app_uid() = user_id);

drop policy if exists records_delete_own on public.records;
create policy records_delete_own on public.records
    for delete using (public.app_uid() = user_id);

-- month_budget
drop policy if exists month_budget_select_own on public.month_budget;
create policy month_budget_select_own on public.month_budget
    for select using (public.app_uid() = user_id);

drop policy if exists month_budget_insert_own on public.month_budget;
create policy month_budget_insert_own on public.month_budget
    for insert with check (public.app_uid() = user_id);

drop policy if exists month_budget_update_own on public.month_budget;
create policy month_budget_update_own on public.month_budget
    for update using (public.app_uid() = user_id) with check (public.app_uid() = user_id);

drop policy if exists month_budget_delete_own on public.month_budget;
create policy month_budget_delete_own on public.month_budget
    for delete using (public.app_uid() = user_id);

-- ------------------------------------------------------------
-- 8. 权限：只授予业务表 DML 与 4 个认证函数
-- ------------------------------------------------------------
grant select, insert, update, delete
    on public.categories, public.records, public.month_budget
    to app_client;

revoke all on function public.app_uid() from public;
revoke all on function public.app_login(text, text) from public;
revoke all on function public.app_signup(text, text) from public;
revoke all on function public.app_logout() from public;

grant execute on function public.app_uid() to app_client;
grant execute on function public.app_login(text, text) to app_client;
grant execute on function public.app_signup(text, text) to app_client;
grant execute on function public.app_logout() to app_client;

-- ============================================================
-- 执行完成后需要做两件事：
-- 1. 把本文件里的 CHANGEME 换成随机密码（正式使用请重新执行第 1 节）；
-- 2. 在 local.properties 中填写：
--    SUPABASE_DB_HOST=aws-0-<你的区域>.pooler.supabase.com
--    SUPABASE_DB_PORT=5432
--    SUPABASE_DB_NAME=postgres
--    SUPABASE_DB_USER=app_client.<project-ref>
--    SUPABASE_DB_PASSWORD=<上一步的密码>
-- 注意必须使用 5432 会话池：事务池（6543）会在语句之间丢失 app.session_token。
-- ============================================================
