-- ============================================================
-- 个人流水记账 App - Supabase 数据库初始化脚本
-- 使用方式：Supabase Dashboard -> SQL Editor -> 新建查询 -> 粘贴执行
-- 说明：全部业务表启用 RLS，策略统一为 auth.uid() = user_id
-- ============================================================

-- gen_random_uuid() 依赖 pgcrypto（Supabase 默认已启用）
create extension if not exists "pgcrypto";

-- ------------------------------------------------------------
-- 1. 分类表
-- ------------------------------------------------------------
create table if not exists public.categories (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references auth.users (id) on delete cascade,
    name        varchar(50) not null,
    icon_key    varchar(30) not null default 'other',
    type        int not null default 0 check (type in (0, 1, 2)),
    is_default  boolean not null default false,
    created_at  timestamptz not null default now()
);

create index if not exists categories_user_idx on public.categories (user_id);

-- ------------------------------------------------------------
-- 2. 流水记录表
-- ------------------------------------------------------------
create table if not exists public.records (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references auth.users (id) on delete cascade,
    type        int not null check (type in (0, 1)),
    amount      numeric(12, 2) not null check (amount > 0),
    category_id uuid not null references public.categories (id) on delete restrict,
    record_date date not null,
    record_time timestamptz not null default now(),
    remark      text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- 分页、日历与统计查询均以 (user_id, 日期/时间) 为条件
create index if not exists records_user_time_idx on public.records (user_id, record_time desc);
create index if not exists records_user_date_idx on public.records (user_id, record_date);
create index if not exists records_category_idx on public.records (category_id);

-- ------------------------------------------------------------
-- 3. 月度预算表
-- ------------------------------------------------------------
create table if not exists public.month_budget (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null references auth.users (id) on delete cascade,
    year          int not null check (year between 2000 and 2100),
    month         int not null check (month between 1 and 12),
    budget_amount numeric(12, 2) not null check (budget_amount > 0),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint month_budget_user_year_month_key unique (user_id, year, month)
);

create index if not exists month_budget_user_idx
    on public.month_budget (user_id, year desc, month desc);

-- ------------------------------------------------------------
-- 4. updated_at 自动维护
-- ------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists records_set_updated_at on public.records;
create trigger records_set_updated_at
    before update on public.records
    for each row execute function public.set_updated_at();

drop trigger if exists month_budget_set_updated_at on public.month_budget;
create trigger month_budget_set_updated_at
    before update on public.month_budget
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 5. 业务约束：流水只能使用本人分类
-- ------------------------------------------------------------
create or replace function public.check_record_category_owner()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if not exists (
        select 1
        from public.categories c
        where c.id = new.category_id
          and c.user_id = new.user_id
    ) then
        raise exception '分类不存在或不属于当前用户';
    end if;
    return new;
end;
$$;

drop trigger if exists records_check_category on public.records;
create trigger records_check_category
    before insert or update on public.records
    for each row execute function public.check_record_category_owner();

-- ------------------------------------------------------------
-- 6. 业务约束：内置分类不可删除
-- ------------------------------------------------------------
create or replace function public.protect_default_category()
returns trigger
language plpgsql
as $$
begin
    if old.is_default then
        raise exception '内置分类不可删除';
    end if;
    return old;
end;
$$;

drop trigger if exists categories_protect_default on public.categories;
create trigger categories_protect_default
    before delete on public.categories
    for each row execute function public.protect_default_category();

-- ------------------------------------------------------------
-- 7. 行级安全策略（RLS）
-- ------------------------------------------------------------
alter table public.categories enable row level security;
alter table public.records enable row level security;
alter table public.month_budget enable row level security;

-- categories
drop policy if exists categories_select_own on public.categories;
create policy categories_select_own on public.categories
    for select using (auth.uid() = user_id);

drop policy if exists categories_insert_own on public.categories;
create policy categories_insert_own on public.categories
    for insert with check (auth.uid() = user_id);

drop policy if exists categories_update_own on public.categories;
create policy categories_update_own on public.categories
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists categories_delete_own on public.categories;
create policy categories_delete_own on public.categories
    for delete using (auth.uid() = user_id);

-- records
drop policy if exists records_select_own on public.records;
create policy records_select_own on public.records
    for select using (auth.uid() = user_id);

drop policy if exists records_insert_own on public.records;
create policy records_insert_own on public.records
    for insert with check (auth.uid() = user_id);

drop policy if exists records_update_own on public.records;
create policy records_update_own on public.records
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists records_delete_own on public.records;
create policy records_delete_own on public.records
    for delete using (auth.uid() = user_id);

-- month_budget
drop policy if exists month_budget_select_own on public.month_budget;
create policy month_budget_select_own on public.month_budget
    for select using (auth.uid() = user_id);

drop policy if exists month_budget_insert_own on public.month_budget;
create policy month_budget_insert_own on public.month_budget
    for insert with check (auth.uid() = user_id);

drop policy if exists month_budget_update_own on public.month_budget;
create policy month_budget_update_own on public.month_budget
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists month_budget_delete_own on public.month_budget;
create policy month_budget_delete_own on public.month_budget
    for delete using (auth.uid() = user_id);

-- ------------------------------------------------------------
-- 8. 新用户注册时自动创建内置分类
-- ------------------------------------------------------------
create or replace function public.create_default_categories()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.categories (user_id, name, icon_key, type, is_default) values
        (new.id, '餐饮', 'restaurant', 0, true),
        (new.id, '交通', 'transport',  0, true),
        (new.id, '购物', 'shopping',   0, true),
        (new.id, '住房', 'housing',    0, true),
        (new.id, '工资', 'salary',     1, true),
        (new.id, '红包', 'gift',       1, true),
        (new.id, '兼职', 'parttime',   1, true);
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.create_default_categories();

-- ------------------------------------------------------------
-- 9. 已存在但缺少内置分类的账号（可选，首次部署时执行一次）
-- ------------------------------------------------------------
insert into public.categories (user_id, name, icon_key, type, is_default)
select u.id, v.name, v.icon_key, v.type, true
from auth.users u
cross join (
    values
        ('餐饮', 'restaurant', 0),
        ('交通', 'transport',  0),
        ('购物', 'shopping',   0),
        ('住房', 'housing',    0),
        ('工资', 'salary',     1),
        ('红包', 'gift',       1),
        ('兼职', 'parttime',   1)
) as v(name, icon_key, type)
where not exists (
    select 1
    from public.categories c
    where c.user_id = u.id
      and c.is_default = true
      and c.name = v.name
);

-- ============================================================
-- 验证 RLS 是否生效（可选）：
-- 1. 使用两个不同账号分别登录 App，确认看不到对方账单；
-- 2. 在 SQL Editor 中执行 select * from public.records;（service_role 会绕过 RLS，
--    请改用 anon key + 用户 JWT 的客户端验证）。
-- ============================================================
