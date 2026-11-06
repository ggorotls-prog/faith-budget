-- 2026-11-06 룡: 시트 「10월」 → 「사역비&교육비&빚」 탭을 앱으로. 지출하면 그 줄 잔액이 줄고, 이달 계획의 합이 그 항목 예산이 된다.
-- 칸: 구분(항목) · 납부처/갚을 곳(name) · 최종납부일(due) · 시작 금액/채무액(start) · 만들 때 잔액(balance0) · 이달 계획(monthly) · 상환 계획/목적(plan) · 이자(interest)
create table if not exists public.fin_funds (
  id uuid primary key default gen_random_uuid(),
  book_id uuid not null references public.fin_books(id),
  item_id uuid not null references public.fin_items(id),
  name text not null,
  due text not null default '',
  start bigint not null default 0,
  balance0 bigint not null default 0,
  monthly bigint not null default 0,
  plan text not null default '',
  interest text not null default '',
  sort int not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now()
);
alter table public.fin_funds enable row level security;
alter table public.fin_entries add column if not exists fund_id uuid references public.fin_funds(id);

-- 시트 값(2026-11-06 시트 「10월」에서 읽음). 잔액은 시트의 지금 잔액 = 만들 때 잔액, 이후 앱 기록이 빼 나간다
insert into public.fin_funds(book_id,item_id,name,due,start,balance0,monthly,plan,sort)
select b.id, i.id, v.name, v.due, v.start, v.bal, v.monthly, v.plan, v.sort
-- 금액은 공개 저장소라 0으로 바꿈. 실제 값은 backup\sql\ 사본(저장소 밖)에 있다 (2026-11-06)
from (values ('사역비','키르기스스탄','26년 11월',0,0,0,'',1),
             ('사역비','MY5K 펀딩','',0,0,0,'',2),
             ('교육비','충성 등록금','27년 2월',0,0,0,'',3)) v(item,name,due,start,bal,monthly,plan,sort)
join public.fin_books b on b.name='가정'
join public.fin_items i on i.book_id=b.id and i.middle=v.item and i.detail=''
where not exists (select 1 from public.fin_funds f where f.book_id=b.id and f.name=v.name);

-- 저장·끄기: 장부 구성원 누구나(고정비와 같음)
create or replace function public.fin_fund_save(p_token text, p_book uuid, p_id uuid, p_item uuid, p_name text, p_due text, p_start bigint, p_balance bigint, p_monthly bigint, p_plan text, p_interest text)
returns uuid language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); v uuid; paid bigint;
begin
  if not exists (select 1 from public.fin_items where id=p_item and book_id=b and kind='지출') then raise exception '없는 항목입니다'; end if;
  if p_id is null then
    insert into public.fin_funds(book_id,item_id,name,due,start,balance0,monthly,plan,interest,sort)
    values (b,p_item,coalesce(nullif(trim(p_name),''),'새 줄'),coalesce(p_due,''),coalesce(p_start,0),coalesce(p_balance,p_start,0),coalesce(p_monthly,0),coalesce(p_plan,''),coalesce(p_interest,''),
            (select coalesce(max(sort),0)+1 from public.fin_funds where book_id=b))
    returning id into v;
  else
    -- 화면에서 고친 「지금 잔액」 = 만들 때 잔액 − 앱에서 낸 것 → 만들 때 잔액을 거꾸로 맞춘다
    select coalesce(sum(amount),0) into paid from public.fin_entries where fund_id=p_id and deleted_at is null;
    update public.fin_funds set item_id=p_item,name=coalesce(nullif(trim(p_name),''),name),due=coalesce(p_due,''),start=coalesce(p_start,0),
      balance0=coalesce(p_balance,balance0-paid)+paid,monthly=coalesce(p_monthly,0),plan=coalesce(p_plan,''),interest=coalesce(p_interest,'')
     where id=p_id and book_id=b returning id into v;
    if v is null then raise exception '없는 줄입니다'; end if;
  end if;
  return v;
end $$;
create or replace function public.fin_fund_delete(p_token text, p_book uuid, p_id uuid)
returns void language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book);
begin
  update public.fin_funds set active=false where id=p_id and book_id=b;
  if not found then raise exception '없는 줄입니다'; end if;
end $$;

-- fin_add: 어느 줄에서 나간 돈인지(p_fund)도 받는다. 예전 7칸 함수는 지운다(같은 이름 두 개면 PostgREST가 못 고름)
drop function if exists public.fin_add(text,date,uuid,bigint,text,uuid,text);
create or replace function public.fin_add(p_token text, p_date date, p_item uuid, p_amount bigint, p_memo text, p_book uuid default null, p_source text default null, p_fund uuid default null)
returns uuid language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); v uuid; w public.work_members := public.fin_member(p_token); a text;
begin
  select area into a from public.fin_items where id=p_item and book_id=b;
  if a is null then raise exception '없는 항목입니다'; end if;
  if not exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=a) then raise exception '이 영역은 기록 권한이 없어요'; end if;
  if p_fund is not null and not exists (select 1 from public.fin_funds where id=p_fund and book_id=b) then raise exception '없는 줄입니다'; end if;
  insert into public.fin_entries(book_id,entry_date,item_id,amount,memo,created_by,source_id,fund_id)
  values (b,p_date,p_item,p_amount,coalesce(p_memo,''),w.id,p_source,p_fund) returning id into v;
  return v;
end $$;
grant execute on function public.fin_add(text,date,uuid,bigint,text,uuid,text,uuid) to anon;
grant execute on function public.fin_fund_save(text,uuid,uuid,uuid,text,text,bigint,bigint,bigint,text,text) to anon;
grant execute on function public.fin_fund_delete(text,uuid,uuid) to anon;

-- fin_load: 사역비·교육비·빚 줄(funds)과 기록의 fund 더함
create or replace function public.fin_load(p_token text, p_month date, p_book uuid default null)
returns jsonb language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); m date := date_trunc('month',p_month)::date; prev date;
begin
  if not exists (select 1 from public.fin_budgets where book_id=b and month=m) then
    select max(month) into prev from public.fin_budgets where book_id=b and month<m;
    if prev is not null then
      insert into public.fin_budgets(book_id,month,item_id,amount)
      select book_id,m,item_id,amount from public.fin_budgets where book_id=b and month=prev;
    end if;
  end if;
  return jsonb_build_object(
    'book',(select name from public.fin_books where id=b),
    'items',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'kind',kind,'bucket',bucket,'middle',middle,'detail',detail,'area',area,'rate',rate,'sort',sort,'expense',expense) order by sort),'[]') from public.fin_items where book_id=b and active),
    'budgets',(select coalesce(jsonb_object_agg(item_id,amount),'{}') from public.fin_budgets where book_id=b and month=m),
    'entries',(select coalesce(jsonb_agg(jsonb_build_object('id',e.id,'date',e.entry_date,'item_id',e.item_id,'amount',e.amount,'memo',e.memo,'by',w.name,'fixed',e.source_id,'fund',e.fund_id) order by e.entry_date desc,e.created_at desc),'[]')
               from public.fin_entries e left join public.work_members w on w.id=e.created_by
               where e.book_id=b and e.deleted_at is null and e.entry_date>=m and e.entry_date<(m+interval '1 month')),
    'fixed',(select coalesce(jsonb_agg(jsonb_build_object('id',f.id,'name',f.name,'day',f.day,'item_id',f.item_id,'account',f.account,'loan',f.principal is not null,
               'amount',case when f.principal is null then f.amount
                 else floor(f.principal*f.rate/100*(d.due-greatest((d.due-interval '1 month')::date,f.loan_start))/365)::bigint end,
               'paid',(select e.id from public.fin_entries e where e.book_id=b and e.deleted_at is null and e.source_id='fixed:'||f.id||':'||to_char(m,'YYYY-MM') limit 1)) order by f.sort),'[]')
             from public.fin_fixed f cross join lateral (select (m+(coalesce(f.day,1)-1))::date due) d
             where f.book_id=b and f.active and (f.principal is null or d.due>f.loan_start)),
    'funds',(select coalesce(jsonb_agg(jsonb_build_object('id',f.id,'item_id',f.item_id,'name',f.name,'due',f.due,'start',f.start,'monthly',f.monthly,'plan',f.plan,'interest',f.interest,
               'balance',f.balance0-coalesce((select sum(e.amount) from public.fin_entries e where e.fund_id=f.id and e.deleted_at is null),0),
               'paid',coalesce((select sum(e.amount) from public.fin_entries e where e.fund_id=f.id and e.deleted_at is null and e.entry_date>=m and e.entry_date<(m+interval '1 month')),0)) order by f.sort),'[]')
             from public.fin_funds f where f.book_id=b and f.active),
    'usage',(select coalesce(jsonb_object_agg(item_id,n),'{}') from (select item_id,count(*) n from public.fin_entries where book_id=b and deleted_at is null and entry_date>=current_date-90 group by item_id) u)
  );
end $$;
