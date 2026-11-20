package app.recordkit.fin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 은행·카드 알림 글자에서 「수입|지출 · 금액 · 짧은 이름」만 뽑는다(2026-11-16 룡: 알림이 뜨면 「기록할까요?」).
// 안드로이드 없이 도는 순수 자바(시험은 src/test). 계좌번호·카드번호·잔액은 읽지 않고 버린다.
// 실제 알림 글자를 보고 만든 것이 아니라 일반 규칙이다. 안 잡히는 알림이 나오면 그 글자를 시험에 더하고 여기를 고친다.
public final class FinParse {
    private FinParse() {}

    public static final class Hit {
        public final String kind;   // "수입" | "지출"
        public final long amount;
        public final String name;   // 가게·보낸 사람(없으면 "")
        Hit(String kind, long amount, String name) { this.kind = kind; this.amount = amount; this.name = name; }
    }

    // 돈 알림을 보내는 앱인가. 앱 이름표(패키지)에 은행·카드·페이 낱말이 들어 있으면 본다.
    private static final String[] MONEY = {"toss", "bank", "card", "pay", "shinhan", "kbstar", "nonghyup", "nh.smart",
            "woori", "hana", "ibk", "wallet", "epost", "kfcc", "naverfin"};
    private static final Pattern SMS_SENDER = Pattern.compile("^[\\d\\-+ ()]+$");
    private static final Pattern TALK_SENDER = Pattern.compile("카카오페이|카드|은행|뱅크|페이|금고|신협|증권");

    public static boolean fromMoneyApp(String pkg, String title) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        String t = title == null ? "" : title.trim();
        // 문자: 저장 안 된 번호(대표번호)에서 온 것만. 아는 사람이 보낸 「5만원 입금했어」는 안 본다
        if (p.equals("com.samsung.android.messaging") || p.equals("com.google.android.apps.messaging"))
            return SMS_SENDER.matcher(t).matches();
        // 카톡: 카카오페이·카드사·은행 알림톡만(대화방은 안 본다)
        if (p.equals("com.kakao.talk")) return TALK_SENDER.matcher(t).find();
        if (p.equals("com.android.shell")) return true;   // 가상 폰 시험(adb로 띄운 알림)
        return isMoneyPkg(p);
    }

    /** 은행·카드·페이 앱인가(앱 이름표에 그 낱말이 있나). 「은행·카드 알림 켜기」 창의 앱 목록도 이걸 쓴다 */
    public static boolean isMoneyPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String m : MONEY) if (p.contains(m)) return true;
        return false;
    }

    // 버리는 것은 「돈이 안 움직였다」가 분명한 글만(2026-11-20 룡: 「다 열고 버릴 걸 추려 나가자」).
    // 「혜택·이벤트·적립 예정」처럼 진짜 결제 알림에도 붙는 낱말로는 버리지 않는다. 잘못 잡힌 알림이 나오면 그 문구만 여기에 더한다.
    private static final Pattern SKIP = Pattern.compile(
            "\\(광고\\)|\\[광고\\]|취소|거절|실패|잔액\\s*부족|인증번호|인증 번호"
                    + "|(?:입금|출금|결제|이체|송금|납부|인출)\\s*(?:될\\s*)?예정"
                    + "|원\\s*이상|복권");   // 「황금 복권 · 3만원 이상 써서 받았어요」(2026-11-20 토스 광고, 두 번 잡혀서 「복권」도 더함)
    // 12,300원 · 5000원 · 3만원 · 3만 5,000원
    private static final Pattern WON = Pattern.compile(
            "(?<![\\d,.])(?:(\\d{1,4})만\\s*(?:(\\d{1,3}(?:,\\d{3})?|\\d{1,4})\\s*)?|(\\d{1,3}(?:,\\d{3})+|\\d{1,9})\\s*)원");
    private static final Pattern NOT_THIS = Pattern.compile("(잔액|잔고|누적|한도|남은|포인트|적립|캐시백|수수료|총)\\s*[:：]?\\s*$");
    private static final String[] IN = {"입금", "받았", "들어왔", "환급", "환불"};
    private static final String[] OUT = {"출금", "결제", "승인", "이체", "송금", "보냈", "나갔", "사용", "납부", "인출"};
    private static final Pattern SENT_TO_ME = Pattern.compile("님이\\s.{0,24}보냈");

    // 앱 알림에는 보통 빈칸 대신 「줄바꿈 없는 빈칸」(U+00A0) 같은 글자가 섞여 온다. 자바 \s는 이걸 빈칸으로 안 본다.
    private static final Pattern ODD_SPACE = Pattern.compile("[\\u00a0\\u1680\\u2000-\\u200b\\u202f\\u205f\\u3000\\ufeff]");
    private static String clean(String v) { return v == null ? "" : ODD_SPACE.matcher(v).replaceAll(" "); }

    public static Hit parse(String title, String text) {
        title = clean(title);
        text = clean(text);
        String s =((title == null ? "" : title) + " \n " + (text == null ? "" : text)).replace("입출금", " ");
        if (s.trim().isEmpty() || SKIP.matcher(s).find()) return null;

        long amount = 0;
        Matcher m = WON.matcher(s);
        while (m.find()) {
            String before = s.substring(Math.max(0, m.start() - 10), m.start());
            if (NOT_THIS.matcher(before).find()) continue;
            long v;
            if (m.group(1) != null) {
                v = Long.parseLong(m.group(1)) * 10000L;
                if (m.group(2) != null) v += Long.parseLong(m.group(2).replace(",", ""));
            } else v = Long.parseLong(m.group(3).replace(",", ""));
            if (v > 0) { amount = v; break; }
        }
        if (amount <= 0) return null;

        String kind = kindOf(s);
        if (kind == null) return null;
        return new Hit(kind, amount, nameOf(title, text));
    }

    private static String kindOf(String s) {
        if (SENT_TO_ME.matcher(s).find()) return "수입";           // 「○○님이 1만원을 보냈어요」
        if (s.contains("받았") || s.contains("들어왔")) return "수입";   // 「송금 받았어요」
        int in = first(s, IN), out = first(s, OUT);
        if (in < 0 && out < 0) return null;
        if (out < 0) return "수입";
        if (in < 0) return "지출";
        return in < out ? "수입" : "지출";
    }

    private static int first(String s, String[] words) {
        int best = -1;
        for (String w : words) { int i = s.indexOf(w); if (i >= 0 && (best < 0 || i < best)) best = i; }
        return best;
    }

    // 이름: 금액·번호·날짜·은행 낱말을 다 걷어 내고 남은 첫 낱말 한두 개(12자까지). 엄청 간단하게만(룡).
    private static final Pattern JUNK = Pattern.compile(
            "(잔액|잔고|누적|한도)\\s*[:：]?\\s*[\\d,]+\\s*원?|\\[Web발신\\]|\\d{1,4}만\\s*[\\d,]*\\s*원[을이은]?|[\\d,]+\\s*원[을이은]?"
                    + "|\\d{1,2}[/.]\\d{1,2}|\\d{1,2}:\\d{2}|[\\d*\\-]{4,}");
    private static final String[] STOP = {"입금", "출금", "결제", "승인", "이체", "송금", "완료", "일시불", "잔액", "누적", "통장", "계좌",
            "카드", "은행", "뱅크", "토스", "신한", "카카오", "국민", "농협", "우리", "하나", "기업", "됐어요", "했어요", "보냈", "받았",
            "들어왔", "나갔", "되었습니다", "됐습니다", "했습니다", "내역", "알림", "안내", "체크", "신용", "사용", "납부", "인출", "해외", "국내",
            "SOL", "KB", "NH", "IBK", "원"};
    private static final Pattern TAIL = Pattern.compile("(님이|님에게|님께|님으로부터|님|에게|에서|으로부터|으로|로부터)$");
    private static final Pattern HAS_LETTER = Pattern.compile("[가-힣A-Za-z]");
    private static final Pattern ONLY_MARKS =Pattern.compile("^[\\d*\\-.~]*$");

    static String nameOf(String title, String text) {
        String n = pick(text);
        if (n.isEmpty()) n = pick(title);
        return n;
    }

    private static String pick(String s) {
        if (s == null) return "";
        s = JUNK.matcher(s.replace("입출금", " ")).replaceAll(" ");
        List<String> keep = new ArrayList<>();
        for (String tok : s.split("[\\s,·|/()\\[\\]:：!?。.]+")) {
            String t = TAIL.matcher(tok.trim()).replaceAll("");
            if (t.isEmpty() || ONLY_MARKS.matcher(t).matches() || !HAS_LETTER.matcher(t).find() || t.equals("내")) continue;
            boolean stop = false;
            for (String w : STOP) if (t.contains(w)) { stop = true; break; }
            if (stop) continue;
            keep.add(t);
            if (keep.size() == 2) break;
        }
        String n = String.join(" ", keep);
        return n.length() > 12 ? n.substring(0, 12) : n;
    }
}
