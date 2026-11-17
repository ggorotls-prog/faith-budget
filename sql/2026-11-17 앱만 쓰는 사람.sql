-- 2026-11-17 룡 「다 패치해」: 앱만 쓰는 사람이 혼자 쓸 수 있게
-- ① 항목 관리(설정 → 항목 관리): 내 장부 항목 보기 · 더하기 · 이름 바꾸기 · 숨기기/다시 보이기
-- ② 「기록할까요?」에서 같은 가게로 전에 기록한 항목을 한 번에(fin_last_item)
-- 아직 적용 안 함. 적용 전에도 화면은 안내 글을 띄우고 깨지지 않는다.
-- 칸 추가 없음: 숨기기는 이미 있는 fin_items.active(기본 true)를 쓴다. fin_load가 active만 보내므로 숨긴 항목은 기록 화면에서 빠진다.

-- 계산 규칙이 이름으로 찾는 항목은 이름 바꾸기·숨기기를 막는다(가정: 십일조·봉헌물 비율, 수입 십일조 버튼, 이월, 식비 주차 / 기업: 매출·수당·부가세·십일조 넣을 것).
create or replace function public.fin_item_locked(i public.fin_items)
returns boolean language sql immutable set search_path to '' as $$
  select i.rate is not null
      or i.middle in ('이월','십일조(10%)','근로소득','미지정공급','사업소득','매출','수당','부가세','십일조')
      or i.bucket = '사업소득'
      or coalesce(i.detail,'') ~ '주차$'
$$;
revoke execute on function public.fin_item_locked(public.fin_items) from public, anon, authenticated;

-- 항목 목록(숨긴 것 포함). 내가 기록 권한(fin_scopes)이 있는 영역 것만. 금액은 안 보낸다
create or replace function public.fin_item_list(p_token text, p_book uuid)
returns jsonb language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); w public.work_members := public.fin_member(p_token);
begin
  return (select coalesce(jsonb_agg(jsonb_build_object('id',i.id,'kind',i.kind,'bucket',i.bucket,'middle',i.middle,'detail',i.detail,'area',i.area,
            'active',i.active,'locked',public.fin_item_locked(i),
            'tied',exists(select 1 from public.fin_fixed f where f.item_id=i.id and f.active) or exists(select 1 from public.fin_funds f where f.item_id=i.id and f.active))
          order by i.kind desc, i.area, i.sort),'[]'::jsonb)
    from public.fin_items i
   where i.book_id=b and exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=i.area));
end $$;

-- 항목 더하기(p_id 비움) / 이름 바꾸기(p_id). 그 영역에 기록 권한이 있어야 한다.
-- 바꾸기는 중분류·소분류 이름만(대분류·종류·영역은 그대로: 지난 기록의 뜻이 안 바뀌게)
create or replace function public.fin_item_save(p_token text, p_book uuid, p_id uuid, p_kind text, p_area text, p_bucket text, p_middle text, p_detail text)
returns uuid language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); w public.work_members := public.fin_member(p_token);
  it public.fin_items; v uuid; m text := nullif(trim(coalesce(p_middle,'')),''); d text := trim(coalesce(p_detail,''));
begin
  if m is null then raise exception '이름을 적어 주세요'; end if;
  if length(m)>20 or length(d)>20 then raise exception '이름은 20자까지'; end if;
  if p_id is null then
    if p_kind not in ('수입','지출') then raise exception '수입·지출을 골라 주세요'; end if;
    if nullif(trim(coalesce(p_bucket,'')),'') is null then raise exception '대분류를 골라 주세요'; end if;
    if not exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=coalesce(p_area,'')) then raise exception '이 영역은 기록 권한이 없어요'; end if;
    -- 표의 겹침 막기(unique)가 대분류를 안 보므로 여기서도 대분류를 안 본다(영어 오류가 화면에 안 나가게)
    select * into it from public.fin_items where book_id=b and kind=p_kind and area=coalesce(p_area,'') and middle=m and coalesce(detail,'')=d limit 1;
    if it.id is not null then
      if it.active then raise exception '같은 항목이 이미 있어요'; end if;
      update public.fin_items set active=true where id=it.id;   -- 숨긴 같은 항목이 있으면 다시 보이게
      return it.id;
    end if;
    insert into public.fin_items(book_id,kind,bucket,middle,detail,sort,active,area,expense)
    values (b,p_kind,trim(p_bucket),m,d,(select coalesce(max(sort),0)+1 from public.fin_items where book_id=b),true,coalesce(p_area,''),
            coalesce((select x.expense from public.fin_items x where x.book_id=b and x.area=coalesce(p_area,'') and x.bucket=trim(p_bucket) order by x.sort limit 1),true))  -- 경비 구분은 같은 대분류 항목을 따라간다
    returning id into v;
    return v;
  end if;
  select * into it from public.fin_items where id=p_id and book_id=b;
  if it.id is null then raise exception '없는 항목입니다'; end if;
  if not exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=it.area) then raise exception '이 영역은 기록 권한이 없어요'; end if;
  if public.fin_item_locked(it) then raise exception '이 항목은 계산에 쓰여서 이름을 바꿀 수 없어요'; end if;
  if exists (select 1 from public.fin_items where book_id=b and id<>it.id and kind=it.kind and area=it.area and middle=m and coalesce(detail,'')=d)
    then raise exception '같은 이름이 이미 있어요'; end if;
  update public.fin_items set middle=m, detail=d where id=it.id;
  return it.id;
end $$;

-- 숨기기(p_hide true) / 다시 보이기(false). 지우지 않는다: 지난 기록은 그대로 남는다
create or replace function public.fin_item_hide(p_token text, p_book uuid, p_id uuid, p_hide boolean)
returns void language plpgsql security definer set search_path to '' as $$
declare b uuid := public.fin_book(p_token,p_book); w public.work_members := public.fin_member(p_token); it public.fin_items;
begin
  select * into it from public.fin_items where id=p_id and book_id=b;
  if it.id is null then raise exception '없는 항목입니다'; end if;
  if not exists (select 1 from public.fin_scopes s where s.work_member_id=w.id and s.book_id=b and s.area=it.area) then raise exception '이 영역은 기록 권한이 없어요'; end if;
  if coalesce(p_hide,true) then
    if public.fin_item_locked(it) then raise exception '이 항목은 계산에 쓰여서 숨길 수 없어요'; end if;
    if exists (select 1 from public.fin_fixed f where f.item_id=it.id and f.active) or exists (select 1 from public.fin_funds f where f.item_id=it.id and f.active)
      then raise exception '고정비나 사역비·교육비·빚 줄에서 쓰는 항목이에요. 그 줄을 먼저 지워 주세요'; end if;
  end if;
  update public.fin_items set active=not coalesce(p_hide,true) where id=it.id;
end $$;

-- 같은 가게(알림의 짧은 이름 = 기록 메모)로 내가 전에 기록한 항목 하나. 지금 기록 권한이 있고 숨기지 않은 항목만. 없으면 null
create or replace function public.fin_last_item(p_token text, p_name text)
returns jsonb language plpgsql stable security definer set search_path to '' as $$
declare w public.work_members := public.fin_member(p_token); n text := nullif(trim(coalesce(p_name,'')),''); r jsonb;
begin
  if n is null then return null; end if;
  select jsonb_build_object('book',e.book_id,'item',e.item_id) into r
    from public.fin_entries e join public.fin_items i on i.id=e.item_id and i.active
    join public.fin_scopes s on s.work_member_id=w.id and s.book_id=e.book_id and s.area=i.area
   where e.created_by=w.id and e.deleted_at is null and trim(e.memo)=n
   order by e.created_at desc limit 1;
  return r;
end $$;

grant execute on function public.fin_item_list(text,uuid) to anon;
grant execute on function public.fin_item_save(text,uuid,uuid,text,text,text,text,text) to anon;
grant execute on function public.fin_item_hide(text,uuid,uuid,boolean) to anon;
grant execute on function public.fin_last_item(text,text) to anon;
