-- 기업 장부 (2026-11-03 룡 기준: 장부 2개, 세 사람 모두 보고 고침, 영역 = 청소·포멘토·그린주의·오레진, 경비는 공통)
-- 슈파베이스 「룡룡룡」 SQL 편집기에서 그대로 실행. 두 번 돌려도 안전(이미 있으면 건너뜀).
alter table public.fin_items add column if not exists area text not null default '';
alter table public.fin_entries add column if not exists created_by uuid;
-- 같은 항목 이름이 영역마다 있으므로 고유키에 영역을 더한다
alter table public.fin_items drop constraint if exists fin_items_book_id_kind_middle_detail_key;
alter table public.fin_items drop constraint if exists fin_items_book_id_kind_middle_detail_area_key;
alter table public.fin_items add constraint fin_items_book_id_kind_middle_detail_area_key unique (book_id,kind,middle,detail,area);
create table if not exists public.fin_fixed (
  id uuid primary key default gen_random_uuid(),
  book_id uuid not null references public.fin_books(id),
  item_id uuid not null references public.fin_items(id),
  name text not null, day int, amount bigint not null, sort int not null default 0, active boolean not null default true
);
alter table public.fin_fixed enable row level security;

insert into public.fin_books(name) select '기업' where not exists (select 1 from public.fin_books where name='기업');
insert into public.fin_book_members(book_id, work_member_id)
select b.id, bm.work_member_id from public.fin_books b, (select distinct work_member_id from public.fin_book_members) bm
where b.name='기업' on conflict do nothing;

do $$
declare b uuid; a text; s int := 0;
begin
  select id into b from public.fin_books where name='기업';
  if exists (select 1 from public.fin_items where book_id=b) then return; end if;
  -- 영역 붙는 항목: 매출·재료비·인건비
  foreach a in array array['청소','포멘토','그린주의','오레진'] loop
    s := s+10;
    insert into public.fin_items(book_id,kind,bucket,middle,detail,area,sort) values
      (b,'수입','사업소득','매출','',a,s),
      (b,'지출','재료비','재료비','',a,100+s),
      (b,'지출','인건비','인건비(프리)','',a,100+s+1),
      (b,'지출','인건비','인건비(일용직)','',a,100+s+2);
  end loop;
  -- 공통 수입
  insert into public.fin_items(book_id,kind,bucket,middle,detail,sort) values
    (b,'수입','기타','기타소득','',50),(b,'수입','기타','환급','',51);
  -- 공통 경비 (기업예산안 작업중.xlsx 분류 그대로)
  insert into public.fin_items(book_id,kind,bucket,middle,detail,sort) values
    (b,'지출','운영경비','월세','',201),(b,'지출','운영경비','식비','',202),(b,'지출','운영경비','차량 유지비','',203),
    (b,'지출','운영경비','수수료','',204),(b,'지출','운영경비','대출 이자','',205),(b,'지출','운영경비','소모품/잡비','',206),(b,'지출','운영경비','경조사','',207),
    (b,'지출','세금','부가세','',301),(b,'지출','세금','종소세','',302),(b,'지출','세금','원천세','',303),
    (b,'지출','세금','자동차세','',304),(b,'지출','세금','고용/산재','',305),(b,'지출','세금','환경개선','',306),(b,'지출','세금','국민연금','',307),
    (b,'지출','소득공제 저축','노란우산','',401),
    (b,'지출','헌금','십일조','',501),(b,'지출','헌금','공제 헌금','',502),(b,'지출','헌금','비공제 헌금','',503),
    (b,'지출','가계 및 기타','왕기','',601),(b,'지출','가계 및 기타','생활비','',602),(b,'지출','가계 및 기타','빚갚기','',603),
    (b,'지출','가계 및 기타','보너스','',604),(b,'지출','가계 및 기타','과태료','',605);
  -- 월 고정 경비 (「월 고정지출」 시트. 명애 헌금은 9월이 끝이라 뺌, 국민연금 0 뺌. 2026-11-03 룡 확인)
  insert into public.fin_fixed(book_id,item_id,name,day,amount,sort)
  select b,(select id from public.fin_items where book_id=b and middle=m),n,d,amt,o from (values
    ('월세',5,290000,'월세',1),('왕기회비',10,300000,'왕기',2),('대출 이자',10,17083,'대출 이자',3),
    ('카카오',12,990,'수수료',4),('건강보험',21,57130,'수수료',5),('세무기장',25,55000,'수수료',6),
    ('노란우산',25,50000,'노란우산',7),('차량보험',30,120000,'차량 유지비',8)) v(n,d,amt,m,o);
end $$;

-- 장부 고르기: p_book 없으면 첫 장부(가정) = 기존 동작 그대로
create or replace function public.fin_book(p_token text, p_book uuid default null) returns uuid
language plpgsql stable security definer set search_path to 'public','extensions' as $$
declare w work_members := fin_member(p_token); v uuid;
begin
  if p_book is null then
    select bm.book_id into v from fin_book_members bm join fin_books b on b.id=bm.book_id
     where bm.work_member_id=w.id order by b.created_at limit 1;
  else
    select bm.book_id into v from fin_book_members bm where bm.work_member_id=w.id and bm.book_id=p_book;
  end if;
  if v is null then raise exception 'auth'; end if;
  return v;
end $$;

create or replace function public.fin_me(p_token text) returns json
language plpgsql stable security definer set search_path to 'public','extensions' as $$
declare w work_members := fin_member(p_token);
begin
  return json_build_object('name',w.name,'must_change_pw',coalesce(w.must_change_pw,false),
    'books',(select json_agg(json_build_object('id',b.id,'name',b.name) order by b.created_at)
             from fin_book_members bm join fin_books b on b.id=bm.book_id where bm.work_member_id=w.id));
end $$;

create or replace function public.fin_load(p_token text, p_month date, p_book uuid default null) returns jsonb
language plpgsql security definer set search_path to '' as $$
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
    'items',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'kind',kind,'bucket',bucket,'middle',middle,'detail',detail,'area',area,'rate',rate,'sort',sort) order by sort),'[]') from public.fin_items where book_id=b and active),
    'budgets',(select coalesce(jsonb_object_agg(item_id,amount),'{}') from public.fin_budgets where book_id=b and month=m),
    'entries',(select coalesce(jsonb_agg(jsonb_build_object('id',e.id,'date',e.entry_date,'item_id',e.item_id,'amount',e.amount,'memo',e.memo,'by',w.name,'fixed',e.source_id) order by e.entry_date desc,e.created_at desc),'[]')
               from public.fin_entries e left join public.work_members w on w.id=e.created_by
               where e.book_id=b and e.deleted_at is null and e.entry_date>=m and e.entry_date<(m+interval '1 month')),
    'fixed',(select coalesce(jsonb_agg(jsonb_build_object('id',f.id,'name',f.name,'day',f.day,'amount',f.amount,'item_id',f.item_id,
               'paid',(select e.id from public.fin_entries e where e.book_id=b and e.deleted_at is null and e.source_id='fixed:'||f.id||':'||to_char(m,'YYYY-MM') limit 1)) order by f.sort),'[]')
             from public.fin_fixed f where f.book_id=b and f.active),
    'usage',(select coalesce(jsonb_object_agg(item_id,n),'{}') from (select item_id,count(*) n from public.fin_entries where book_id=b and deleted_at is null and entry_date>=current_date-90 group by item_id) u)
  );
end $$;

create or replace function public.fin_add(p_token text, p_date date, p_item uuid, p_amount bigint, p_memo text, p_book uuid default null, p_source text default null) returns uuid
language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); v uuid; w public.work_members := public.fin_member(p_token);
begin
  if not exists (select 1 from public.fin_items where id=p_item and book_id=b) then raise exception '없는 항목입니다'; end if;
  insert into public.fin_entries(book_id,entry_date,item_id,amount,memo,created_by,source_id)
  values (b,p_date,p_item,p_amount,coalesce(p_memo,''),w.id,p_source) returning id into v;
  return v;
end $$;

-- 고치기·지우기는 그 기록이 속한 장부의 구성원이면 된다
create or replace function public.fin_update(p_token text, p_id uuid, p_date date, p_item uuid, p_amount bigint, p_memo text) returns void
language plpgsql security definer set search_path to '' as $$
declare b uuid;
begin
  select book_id into b from public.fin_entries where id=p_id;
  b := public.fin_book(p_token,b);
  if not exists (select 1 from public.fin_items where id=p_item and book_id=b) then raise exception '없는 항목입니다'; end if;
  update public.fin_entries set entry_date=p_date,item_id=p_item,amount=p_amount,memo=coalesce(p_memo,''),updated_at=now()
   where id=p_id and book_id=b;
end $$;

create or replace function public.fin_delete(p_token text, p_id uuid, p_restore boolean default false) returns void
language plpgsql security definer set search_path to '' as $$
declare b uuid;
begin
  select book_id into b from public.fin_entries where id=p_id;
  b := public.fin_book(p_token,b);
  update public.fin_entries set deleted_at=case when p_restore then null else now() end,updated_at=now()
   where id=p_id and book_id=b;
end $$;

create or replace function public.fin_set_budget(p_token text, p_month date, p_item uuid, p_amount bigint, p_book uuid default null) returns void
language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book);
begin
  if not exists (select 1 from public.fin_items where id=p_item and book_id=b) then raise exception '없는 항목입니다'; end if;
  insert into public.fin_budgets(book_id,month,item_id,amount)
  values (b,date_trunc('month',p_month)::date,p_item,p_amount)
  on conflict (book_id,month,item_id) do update set amount=excluded.amount;
end $$;

-- 확인
select (select count(*) from public.fin_items where area<>'') 영역항목, (select count(*) from public.fin_fixed) 고정경비, (select count(*) from public.fin_book_members) 구성원줄;

-- 2026-11-04 사람별 기록 영역(결제권). 기록은 자기 영역만, 보기는 모두 (룡: 아내 가정·오레진·기업 공통, 룡 청소·포멘토·그린주의)
create table if not exists public.fin_scopes (
  work_member_id uuid not null references public.work_members(id),
  book_id uuid not null references public.fin_books(id),
  area text not null default '',
  primary key (work_member_id, book_id, area)
);
alter table public.fin_scopes enable row level security;
insert into public.fin_scopes(work_member_id, book_id, area)
select m.id, b.id, v.area from (values
  ('예쁜영이','가정',''),('예쁜영이','기업','오레진'),('예쁜영이','기업',''),
  ('깐깐룡이','기업','청소'),('깐깐룡이','기업','포멘토'),('깐깐룡이','기업','그린주의')) v(nick,book,area)
join public.work_members m on m.login_id=v.nick join public.fin_books b on b.name=v.book
on conflict do nothing;
-- fin_me는 scopes를 함께 돌려주고, fin_add는 그 영역에 권한이 있을 때만 넣는다(실제 함수는 DB에 적용됨).

-- 2026-11-06 오레진 수당(3.3% 떼고 받음). 화면이 받은 돈으로 세전·뗀 세금을 계산한다
insert into public.fin_items(book_id, kind, bucket, middle, detail, sort, active, area)
select b.id,'수입','사업소득','수당','',39,true,'오레진' from public.fin_books b where b.name='기업'
on conflict do nothing;

-- 2026-11-06 룡: 깐깐룡이에게 가정 장부 기록 권한도
insert into public.fin_scopes(work_member_id, book_id, area)
select m.id, b.id, '' from public.work_members m, public.fin_books b where m.login_id='깐깐룡이' and b.name='가정'
on conflict do nothing;

-- 2026-11-06 룡: 기업은 룡·아내가 메인으로 관리 → 둘 다 기업 모든 영역(공통·청소·포멘토·그린주의·오레진) 기록 권한
insert into public.fin_scopes(work_member_id, book_id, area)
select m.id, b.id, a.area from public.work_members m cross join public.fin_books b
cross join (select distinct area from public.fin_items i join public.fin_books bb on bb.id=i.book_id where bb.name='기업') a
where m.login_id in ('깐깐룡이','예쁜영이') and b.name='기업'
on conflict do nothing;
