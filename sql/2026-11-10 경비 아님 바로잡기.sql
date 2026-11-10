-- 2026-11-10 룡 「원칙대로」: 사업 경비(필요경비)가 아닌 것을 기업 장부에서 「경비 아님」으로.
-- 종소세 = 소득세는 경비가 안 됨 / 국민연금(본인) = 경비가 아니라 소득공제 / 노란우산 = 저축, 경비가 아니라 소득공제.
-- 부가세는 그대로 둔다(경비는 아니지만 앱 이익 = 매출 − 부가세 − 경비 계산에 필요, 옛 시트와 같음).
update public.fin_items i set expense=false
  from public.fin_books b
 where b.id=i.book_id and b.name='기업' and i.kind='지출'
   and ((i.bucket='세금' and i.middle in ('종소세','국민연금')) or i.bucket='소득공제 저축')
returning i.bucket, i.middle, i.detail, i.expense;
