package app.recordkit.fin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// 알림 글자 읽기 시험. 아래 글자는 지어낸 예시다(실제 알림·금액·이름 아님). 안 잡히는 실제 알림이 나오면 금액·이름을 바꿔 여기에 더한다.
public class FinParseTest {
    private static void hit(String title, String text, String kind, long amount, String name) {
        FinParse.Hit h = FinParse.parse(title, text);
        assertNotNull(title + " / " + text, h);
        assertEquals(title + " / " + text, kind, h.kind);
        assertEquals(title + " / " + text, amount, h.amount);
        if (name != null) assertEquals(title + " / " + text, name, h.name);
    }

    @Test public void 카드_결제() {
        hit("토스뱅크", "별다방 5,600원 결제 잔액 123,456원", "지출", 5600, "별다방");
        hit("결제 완료", "동네마트에서 12,300원 결제했어요", "지출", 12300, "동네마트");
        hit("신한카드 승인", "홍*동님 15,000원 일시불 11/16 12:30 한식당 누적 250,000원", "지출", 15000, null);
        hit("1588-0000", "[Web발신] 가나카드 승인 4,500원 11/16 09:10 편의점", "지출", 4500, "편의점");
        // 결제 알림에 혜택·적립 문구가 붙어도 버리지 않는다(2026-11-20 토스 5만원 결제가 안 잡힘)
        hit("토스페이", "동네마트 50,000원 결제 완료 · 혜택 받기", "지출", 50000, null);
        hit("토스", "50,000원 결제했어요. 포인트 500원 적립 예정", "지출", 50000, null);
    }

    @Test public void 통장_입금_출금() {
        hit("입금 30,000원", "김철수 → 내 통장 잔액 1,030,000원", "수입", 30000, "김철수");
        hit("신한은행", "출금 50,000원 110-***-123456 관리비 잔액 200,000원", "지출", 50000, "관리비");
        hit("입출금 알림", "입금 1,000,000원 주식회사가나 잔액 2,000,000원", "수입", 1000000, "주식회사가나");
        hit("토스", "잔액 70,000원 · 출금 3,000원 버스", "지출", 3000, "버스");
    }

    @Test public void 보냄_받음() {
        hit("토스", "김영희님이 10,000원을 보냈어요", "수입", 10000, "김영희");
        hit("토스", "김영희님에게 10,000원을 보냈어요", "지출", 10000, "김영희");
        hit("카카오페이", "이민수님에게 송금 받았어요 20,000원", "수입", 20000, "이민수");
        hit("카카오페이", "3만원 결제 완료 치킨집", "지출", 30000, "치킨집");
        hit("카카오페이", "3만 5,000원 결제 완료", "지출", 35000, "");
    }

    @Test public void 안_잡는_것() {
        assertNull(FinParse.parse("(광고) 토스", "지금 5,000원 받았어요 이벤트"));
        assertNull(FinParse.parse("신한카드", "승인취소 15,000원 한식당"));
        assertNull(FinParse.parse("토스", "내일 50,000원 출금 예정이에요"));
        assertNull(FinParse.parse("토스", "잔액이 부족해 출금 실패 30,000원"));
        assertNull(FinParse.parse("신한은행", "인증번호 123456"));
        assertNull(FinParse.parse("토스", "이번 달 카드값을 확인해 보세요"));
        assertNull(FinParse.parse("토스", "잔액 70,000원"));
        assertNull(FinParse.parse("", ""));
        assertNull(FinParse.parse("황금 복권", "3만원 이상 써서 받았어요. 바로 긁어보세요."));
    }

    @Test public void 이름에_번호가_안_남는다() {
        FinParse.Hit h = FinParse.parse("신한은행", "출금 50,000원 110-123-456789 1234-56**-****-7890 잔액 200,000원");
        assertNotNull(h);
        assertFalse(h.name, h.name.matches(".*\\d{3,}.*"));
        assertTrue(h.name.length() <= 12);
    }

    @Test public void 어느_앱() {
        assertTrue(FinParse.fromMoneyApp("viva.republica.toss", "토스"));
        assertTrue(FinParse.fromMoneyApp("com.shinhan.sbanking", "신한"));
        assertTrue(FinParse.fromMoneyApp("com.kakaopay.app", "카카오페이"));
        assertTrue(FinParse.fromMoneyApp("com.kakao.talk", "카카오페이"));
        assertFalse(FinParse.fromMoneyApp("com.kakao.talk", "가족방"));
        assertTrue(FinParse.fromMoneyApp("com.samsung.android.messaging", "1588-0000"));
        assertFalse(FinParse.fromMoneyApp("com.samsung.android.messaging", "김영희"));
        assertFalse(FinParse.fromMoneyApp("com.instagram.android", "인스타"));
    }
}
