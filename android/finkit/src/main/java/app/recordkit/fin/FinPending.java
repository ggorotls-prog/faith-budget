package app.recordkit.fin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

// 「안 적은 것」: 은행·카드 알림에서 뽑았지만 아직 기록도 「안 함」도 안 한 건(2026-11-16 룡: 알림을 지워도 남아서 치울 때까지 보이게).
// 폰 안에만 둔다(서버에 안 보냄). 든 것 = 종류·금액·짧은 이름·시각뿐. 계좌·카드 번호·잔액은 없다.
public final class FinPending {
    private FinPending() {}

    private static final long SAME_MS = 2 * 60 * 1000L;   // 토스페이 결제 + 통장 출금처럼 같은 돈 알림이 두 번 오면 한 건으로
    private static final int MAX = 50;

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("fin_pending", Context.MODE_PRIVATE); }

    private static JSONArray read(Context c, String key) {
        try { return new JSONArray(sp(c).getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    /** 새 건을 넣는다. 2분 안에 같은 금액이 이미 왔으면(기록했든 안 했든) 넣지 않고 null */
    public static synchronized JSONObject add(Context c, FinParse.Hit h, long when) {
        try {
            JSONArray recent = read(c, "recent"), keepRecent = new JSONArray();
            boolean dup = false;
            for (int i = 0; i < recent.length(); i++) {
                JSONObject r = recent.getJSONObject(i);
                if (Math.abs(when - r.getLong("t")) > SAME_MS) continue;
                keepRecent.put(r);
                if (r.getLong("a") == h.amount) dup = true;
            }
            if (dup) { sp(c).edit().putString("recent", keepRecent.toString()).apply(); return null; }
            keepRecent.put(new JSONObject().put("a", h.amount).put("t", when));

            JSONArray list = read(c, "list");
            long id = when;
            for (int i = 0; i < list.length(); i++) {
                long other = list.getJSONObject(i).getLong("id");
                if (other >= id) id = other + 1;
            }
            JSONObject it = new JSONObject().put("id", id).put("kind", h.kind).put("amount", h.amount).put("name", h.name).put("t", when);
            list.put(it);
            while (list.length() > MAX) list.remove(0);
            sp(c).edit().putString("list", list.toString()).putString("recent", keepRecent.toString()).apply();
            return it;
        } catch (Exception e) { return null; }
    }

    public static synchronized String listJson(Context c) { return read(c, "list").toString(); }

    public static synchronized int count(Context c) { return read(c, "list").length(); }

    public static synchronized void remove(Context c, long id) {
        try {
            JSONArray list = read(c, "list"), keep = new JSONArray();
            for (int i = 0; i < list.length(); i++) if (list.getJSONObject(i).getLong("id") != id) keep.put(list.getJSONObject(i));
            sp(c).edit().putString("list", keep.toString()).apply();
        } catch (Exception ignored) {}
    }
}
