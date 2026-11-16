package app.recordkit.fin;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

// 은행·카드 알림 읽기(2026-11-16 룡: 「입금·출금 알림이 뜨면 기록할까요?를 넣어야 한다」. 카톡 기록 앱과 같은 「알림 읽기」).
// 읽기만 한다: 알림을 지우거나 누르지 않는다. 돈 알림이면 금액·종류·짧은 이름만 「안 적은 것」에 넣고 「기록할까요?」 알림을 띄운다.
// 재정 기록 앱과 라이프 RPG에 같이 들어간다(finkit manifest). 둘 다 켜져 있으면 라이프 RPG만 일한다(룡은 지갑을 거기서 쓴다).
public class FinNotice extends NotificationListenerService {
    static final String RPG = "app.liferpg";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(getPackageName())) return;
            if (!RPG.equals(getPackageName()) && enabledIn(this, RPG)) return;   // 두 앱이 같은 알림을 두 번 묻지 않게
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || sbn.isOngoing()) return;
            Bundle x = n.extras;
            if (x == null) return;
            String title = str(x.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(x.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.isEmpty()) text = str(x.getCharSequence(Notification.EXTRA_TEXT));
            if (!FinParse.fromMoneyApp(pkg, title)) return;
            FinParse.Hit h = FinParse.parse(title, text);
            if (h == null) return;
            JSONObject it = FinPending.add(this, h, sbn.getPostTime());
            if (it == null) return;
            FinAsk.show(this, it);
            FinWidget.refresh(this);
        } catch (Exception ignored) {}
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString(); }

    /** 그 앱(패키지)의 이 알림 읽기가 켜져 있나 */
    static boolean enabledIn(Context c, String pkg) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(pkg + "/" + FinNotice.class.getName());
    }

    /** 빠른 기록 창이 보는 상태: on(이 앱이 읽는 중) · other(다른 기록 앱이 읽는 중) · off */
    static String state(Context c) {
        if (enabledIn(c, c.getPackageName())) return "on";
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains("/" + FinNotice.class.getName()) ? "other" : "off";
    }
}
