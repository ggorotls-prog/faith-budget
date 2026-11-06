-- 2026-11-06 룡: 고정비(가정·기업)에 날짜·어디로 내는지(계좌)·추가/삭제, 기업 지출은 경비/경비 아님 구분
-- 계좌 값은 앱에서 사람이 직접 넣는다(이 파일·위키에는 적지 않는다).

alter table public.fin_fixed add column if not exists account text not null default '';
alter table public.fin_items add column if not exists expense boolean not null default true;

-- 기업: 경비로 처리하지 않는 지출(룡: 예전엔 헌금들이 그랬다). 처음 값은 헌금·가계 및 기타만. 나머지는 룡 확인 후 고친다.
update public.fin_items i set expense=false from public.fin_books b
 where b.id=i.book_id and b.name='기업' and i.kind='지출' and i.bucket in ('헌금','가계 및 기타');

-- 가정 고정 지출: 예전에 화면이 항목에서 만들던 목록(매일 쓰는 것 빼고 예산 있는 것)을 고정비 표로 옮긴다.
-- id = 항목 id로 둬서 예전 「냈어요」 표시(fixed:항목id:달)와 이어진다. 날짜는 비움(= 첫 화면에 안 뜸), 금액 0 = 그달 예산대로.
insert into public.fin_fixed(id, book_id, item_id, name, day, amount, sort, active)
select i.id, i.book_id, i.id, case when i.detail<>'' then i.middle||' · '||i.detail else i.middle end, null, 0, i.sort, true
from public.fin_items i join public.fin_books b on b.id=i.book_id
where b.name='가정' and i.kind='지출' and i.active
  and i.middle not in ('식비','아침식비','교통비','생필품','기타지출','교육비')
  and not (i.middle='가족관리비' and i.detail in ('의료비','의류/미용비','아이들유지비','간식비'))
  and (i.rate is not null or exists (select 1 from public.fin_budgets g where g.item_id=i.id and g.month=date '2026-11-01' and g.amount>0))
on conflict (id) do nothing;

-- 고정비 저장(새로 = p_id 비움). 그 항목 영역에 기록 권한이 있어야 한다
create or replace function public.fin_fixed_save(p_token text, p_book uuid, p_id uuid, p_name text, p_item uuid, p_day int, p_amount bigint, p_account text)
returns uuid language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); w public.work_members := public.fin_member(p_token); a text; v uuid;
begin
  select area into a from public.fin_items where id=p_item and book_id=b;
  if a is null then raise exception '없는 항목입니다'; end if;
  if not exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=a) then raise exception '이 영역은 기록 권한이 없어요'; end if;
  if p_day is not null and (p_day<1 or p_day>31) then raise exception '날짜는 1~31일'; end if;
  if p_id is null then
    insert into public.fin_fixed(book_id,item_id,name,day,amount,sort,account)
    values (b,p_item,coalesce(nullif(trim(p_name),''),'고정비'),p_day,coalesce(p_amount,0),(select coalesce(max(sort),0)+1 from public.fin_fixed where book_id=b),coalesce(p_account,''))
    returning id into v;
  else
    update public.fin_fixed set item_id=p_item,name=coalesce(nullif(trim(p_name),''),name),day=p_day,amount=coalesce(p_amount,0),account=coalesce(p_account,'')
     where id=p_id and book_id=b returning id into v;
    if v is null then raise exception '없는 고정비입니다'; end if;
  end if;
  return v;
end $$;

-- 고정비 지우기 = 끄기(지난 기록은 그대로)
create or replace function public.fin_fixed_delete(p_token text, p_book uuid, p_id uuid)
returns void language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); w public.work_members := public.fin_member(p_token); a text;
begin
  select i.area into a from public.fin_fixed f join public.fin_items i on i.id=f.item_id where f.id=p_id and f.book_id=b;
  if a is null then raise exception '없는 고정비입니다'; end if;
  if not exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=a) then raise exception '이 영역은 기록 권한이 없어요'; end if;
  update public.fin_fixed set active=false where id=p_id and book_id=b;
end $$;

-- fin_load: 항목에 expense, 고정비에 account 더함(나머지는 그대로)
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
    'entries',(select coalesce(jsonb_agg(jsonb_build_object('id',e.id,'date',e.entry_date,'item_id',e.item_id,'amount',e.amount,'memo',e.memo,'by',w.name,'fixed',e.source_id) order by e.entry_date desc,e.created_at desc),'[]')
               from public.fin_entries e left join public.work_members w on w.id=e.created_by
               where e.book_id=b and e.deleted_at is null and e.entry_date>=m and e.entry_date<(m+interval '1 month')),
    'fixed',(select coalesce(jsonb_agg(jsonb_build_object('id',f.id,'name',f.name,'day',f.day,'item_id',f.item_id,'account',f.account,'loan',f.principal is not null,
               'amount',case when f.principal is null then f.amount
                 else floor(f.principal*f.rate/100*(d.due-greatest((d.due-interval '1 month')::date,f.loan_start))/365)::bigint end,
               'paid',(select e.id from public.fin_entries e where e.book_id=b and e.deleted_at is null and e.source_id='fixed:'||f.id||':'||to_char(m,'YYYY-MM') limit 1)) order by f.sort),'[]')
             from public.fin_fixed f cross join lateral (select (m+(coalesce(f.day,1)-1))::date due) d
             where f.book_id=b and f.active and (f.principal is null or d.due>f.loan_start)),
    'usage',(select coalesce(jsonb_object_agg(item_id,n),'{}') from (select item_id,count(*) n from public.fin_entries where book_id=b and deleted_at is null and entry_date>=current_date-90 group by item_id) u)
  );
end $$;

-- 확인
select b.name, count(*) 고정비, count(f.day) 날짜있음 from public.fin_fixed f join public.fin_books b on b.id=f.book_id where f.active group by 1;

grant execute on function public.fin_fixed_save(text,uuid,uuid,text,uuid,int,bigint,text) to anon;
grant execute on function public.fin_fixed_delete(text,uuid,uuid) to anon;

-- 2026-11-06 룡: 고정비 설정은 장부 구성원 누구나(공통 월세를 룡이 못 고치던 것). fin_fixed_save·fin_fixed_delete에서 영역 권한 검사를 뺐다(DB에 적용됨). 냈어요 기록은 fin_add가 계속 영역 권한을 본다.

-- 2026-11-06 룡: 십일조·봉헌물·이웃 나눔은 고정 금액이 아니라 수입이 들어올 때마다 낸다 → 가정 고정비에서 뺌
update public.fin_fixed f set active=false from public.fin_items i, public.fin_books b
where i.id=f.item_id and b.id=f.book_id and b.name='가정' and i.rate is not null and f.active;
