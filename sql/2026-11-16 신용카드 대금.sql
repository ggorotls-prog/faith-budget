-- 신용카드 대금(2026-11-16 룡: 「다음 달 카드대금을 알아야 해」). 가정 장부의 지출에 쓴다
-- 기록에 「카드로 냄」(card_months: 비면 계좌·체크, 1 = 일시불, 2 이상 = 할부 개월 수)을 표시하고,
-- 장부마다 결제일(card_day)과 시작 금액(card_start = 카드사 앱의 결제예정금액, card_start_pay = 그 금액이 나가는 날,
-- card_start_on = 넣은 날. 그 전의 카드 기록은 시작 금액에 들어 있다고 보고 세지 않는다)을 둔다.
-- 언제 얼마가 나가는지는 화면(index.html)이 결제일별 이용 기간으로 계산한다. 금액은 이 파일에 없다.

alter table public.fin_entries add column if not exists card_months int check (card_months between 1 and 36);
alter table public.fin_books add column if not exists card_day int check (card_day between 1 and 31);
alter table public.fin_books add column if not exists card_start bigint not null default 0;
alter table public.fin_books add column if not exists card_start_on date;
alter table public.fin_books add column if not exists card_start_pay date;

-- 기록 넣기: p_card를 더한다(옛 8칸짜리는 지우고 9칸짜리 하나로. 옛 화면은 p_card 없이 불러도 그대로 된다)
drop function if exists public.fin_add(text, date, uuid, bigint, text, uuid, text, uuid);
create or replace function public.fin_add(p_token text, p_date date, p_item uuid, p_amount bigint, p_memo text, p_book uuid default null, p_source text default null, p_fund uuid default null, p_card int default null)
 returns uuid language plpgsql security definer set search_path to ''
as $function$
declare b uuid := public.fin_book(p_token,p_book); v uuid; w public.work_members := public.fin_member(p_token); a text;
begin
  select area into a from public.fin_items where id=p_item and book_id=b;
  if a is null then raise exception '없는 항목입니다'; end if;
  if not exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=a) then raise exception '이 영역은 기록 권한이 없어요'; end if;
  if p_fund is not null and not exists (select 1 from public.fin_funds where id=p_fund and book_id=b) then raise exception '없는 줄입니다'; end if;
  if p_card is not null and (p_card<1 or p_card>36) then raise exception '할부는 1~36개월'; end if;
  insert into public.fin_entries(book_id,entry_date,item_id,amount,memo,created_by,source_id,fund_id,card_months)
  values (b,p_date,p_item,p_amount,coalesce(p_memo,''),w.id,p_source,p_fund,p_card) returning id into v;
  return v;
end $function$;
grant execute on function public.fin_add(text, date, uuid, bigint, text, uuid, text, uuid, int) to anon;

-- 이미 적은 기록에 카드 표시를 붙이거나 뗀다(p_card null = 계좌·체크)
create or replace function public.fin_entry_card(p_token text, p_id uuid, p_card int)
 returns void language plpgsql security definer set search_path to ''
as $function$
declare b uuid;
begin
  select book_id into b from public.fin_entries where id=p_id;
  b := public.fin_book(p_token,b);
  if p_card is not null and (p_card<1 or p_card>36) then raise exception '할부는 1~36개월'; end if;
  update public.fin_entries set card_months=p_card,updated_at=now() where id=p_id and book_id=b;
end $function$;
grant execute on function public.fin_entry_card(text, uuid, int) to anon;

-- 카드 설정: 결제일·시작 금액·그 금액이 나가는 날. 그 장부 구성원 누구나(고정비 설정과 같음)
create or replace function public.fin_card_set(p_token text, p_book uuid, p_day int, p_start bigint, p_start_pay date)
 returns void language plpgsql security definer set search_path to ''
as $function$
declare b uuid := public.fin_book(p_token,p_book); old_start bigint; old_pay date;
begin
  if p_day is not null and (p_day<1 or p_day>31) then raise exception '결제일은 1~31일'; end if;
  select card_start,card_start_pay into old_start,old_pay from public.fin_books where id=b;
  update public.fin_books set card_day=p_day, card_start=coalesce(p_start,0), card_start_pay=p_start_pay,
    -- 시작 금액이나 나가는 날을 바꿨을 때만 「넣은 날」을 오늘로(결제일만 바꾸면 그대로)
    card_start_on=case when coalesce(p_start,0)=0 then null
                       when coalesce(p_start,0)<>coalesce(old_start,0) or p_start_pay is distinct from old_pay or card_start_on is null
                         then (now() at time zone 'Asia/Seoul')::date
                       else card_start_on end
   where id=b;
end $function$;
grant execute on function public.fin_card_set(text, uuid, int, bigint, date) to anon;

-- 장부 읽기: 기록마다 card, 장부의 card 설정과 카드 기록(지난 37달, 할부가 길어도 남은 달을 세려고)
create or replace function public.fin_load(p_token text, p_month date, p_book uuid default null)
 returns jsonb language plpgsql security definer set search_path to ''
as $function$
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
    'entries',(select coalesce(jsonb_agg(jsonb_build_object('id',e.id,'date',e.entry_date,'item_id',e.item_id,'amount',e.amount,'memo',e.memo,'by',w.name,'fixed',e.source_id,'fund',e.fund_id,'card',e.card_months) order by e.entry_date desc,e.created_at desc),'[]')
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
    'usage',(select coalesce(jsonb_object_agg(item_id,n),'{}') from (select item_id,count(*) n from public.fin_entries where book_id=b and deleted_at is null and entry_date>=current_date-90 group by item_id) u),
    'card',(select jsonb_build_object('day',k.card_day,'start',k.card_start,'start_on',k.card_start_on,'start_pay',k.card_start_pay,
               'list',(select coalesce(jsonb_agg(jsonb_build_object('date',e.entry_date,'amount',e.amount,'months',e.card_months) order by e.entry_date),'[]')
                       from public.fin_entries e join public.fin_items i on i.id=e.item_id
                       where e.book_id=b and e.deleted_at is null and e.card_months is not null and i.kind='지출'
                         and e.entry_date>=current_date-interval '37 months'
                         and (k.card_start_on is null or e.entry_date>=k.card_start_on)))
            from public.fin_books k where k.id=b)
  );
end $function$;


-- ── 2026-11-18 검수 반영 ──
-- 「넣기 전·후」를 날짜가 아니라 시각으로 가른다(card_start_at). 그 전에 적은 기록은 화면이 시작 금액의 결제 달까지만 빼고 뒤 회차는 센다.
-- 시작 금액이 바뀔 때만 넣은 시각을 고친다(결제일만 바꾸면 그대로). 카드 표시는 지출 항목에만 남긴다.
alter table public.fin_books add column if not exists card_start_at timestamptz;
update public.fin_books set card_start_at = card_start_on::timestamp at time zone 'Asia/Seoul' where card_start_at is null and card_start_on is not null;

create or replace function public.fin_card_set(p_token text, p_book uuid, p_day int, p_start bigint, p_start_pay date)
 returns void language plpgsql security definer set search_path to ''
as $function$
declare b uuid := public.fin_book(p_token,p_book); old_start bigint;
begin
  if p_day is not null and (p_day<1 or p_day>31) then raise exception '결제일은 1~31일'; end if;
  select card_start into old_start from public.fin_books where id=b;
  update public.fin_books set card_day=p_day, card_start=coalesce(p_start,0),
    card_start_pay=case when coalesce(p_start,0)=0 then null else p_start_pay end,
    card_start_at=case when coalesce(p_start,0)=0 then null
                       when coalesce(p_start,0)<>coalesce(old_start,0) or card_start_at is null then now()
                       else card_start_at end
   where id=b;
end $function$;

create or replace function public.fin_add(p_token text, p_date date, p_item uuid, p_amount bigint, p_memo text, p_book uuid default null, p_source text default null, p_fund uuid default null, p_card int default null)
 returns uuid language plpgsql security definer set search_path to ''
as $function$
declare b uuid := public.fin_book(p_token,p_book); v uuid; w public.work_members := public.fin_member(p_token); a text; k text;
begin
  select area,kind into a,k from public.fin_items where id=p_item and book_id=b;
  if a is null then raise exception '없는 항목입니다'; end if;
  if not exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=a) then raise exception '이 영역은 기록 권한이 없어요'; end if;
  if p_fund is not null and not exists (select 1 from public.fin_funds where id=p_fund and book_id=b) then raise exception '없는 줄입니다'; end if;
  if p_card is not null and (p_card<1 or p_card>36) then raise exception '할부는 1~36개월'; end if;
  insert into public.fin_entries(book_id,entry_date,item_id,amount,memo,created_by,source_id,fund_id,card_months)
  values (b,p_date,p_item,p_amount,coalesce(p_memo,''),w.id,p_source,p_fund,case when k='지출' then p_card end) returning id into v;
  return v;
end $function$;

create or replace function public.fin_entry_card(p_token text, p_id uuid, p_card int)
 returns void language plpgsql security definer set search_path to ''
as $function$
declare b uuid; k text;
begin
  select e.book_id,i.kind into b,k from public.fin_entries e join public.fin_items i on i.id=e.item_id where e.id=p_id;
  b := public.fin_book(p_token,b);
  if p_card is not null and (p_card<1 or p_card>36) then raise exception '할부는 1~36개월'; end if;
  update public.fin_entries set card_months=case when k='지출' then p_card end,updated_at=now() where id=p_id and book_id=b;
end $function$;

-- fin_load의 card 칸: start_at과 기록한 시각(at)을 같이 보내고, 날짜로 자르지 않는다(나머지 본문은 위와 같다)
-- (DB에는 fin_load 본문에서 start_on → start_at, list에 'at'(created_at) 더하기, card_start_on 날짜 조건 빼기로 적용함)
