package app.recordkit.fin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.service.notification.NotificationListenerService;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

// 「기록할까요?」 알림(2026-11-16 룡). 누르거나 「기록」 = 금액·메모가 채워진 빠른 기록 창, 「안 함」 = 그 건을 치운다.
// 알림을 밀어서 지워도 건은 「안 적은 것」에 남는다(빠른 기록 창 위·위젯에 보임).
// 앱이 업데이트되면 알림 읽기를 다시 붙인다(카톡 기록 앱에서 업데이트 뒤 멈춘 적이 있다).
public class FinAsk extends BroadcastReceiver {
    static final String SKIP = "app.recordkit.fin.SKIP";
    static final String CH = "fin_ask";

    static void show(Context c, JSONObject it) {
        try {
            long id = it.getLong("id");
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CH, "기록할까요? (은행·카드 알림)", NotificationManager.IMPORTANCE_HIGH);
            ch.setShowBadge(true);
            nm.createNotificationChannel(ch);

            Class<?> quick = quickClass(c);
            if (quick == null) return;
            Intent open = new Intent(c, quick).putExtra(FinQuick.EXTRA_PENDING, id)
                    .setData(Uri.parse("finpending:" + id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent tap = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Intent skip = new Intent(c, FinAsk.class).setAction(SKIP).putExtra(FinQuick.EXTRA_PENDING, id).setData(Uri.parse("finpending:" + id));
            PendingIntent no = PendingIntent.getBroadcast(c, 0, skip, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String name = it.optString("name", "");
            String title = NumberFormat.getInstance(Locale.KOREA).format(it.getLong("amount")) + "원 " + it.getString("kind")
                    + (name.isEmpty() ? "" : " · " + name);
            Notification n = new Notification.Builder(c, CH)
                    .setSmallIcon(R.drawable.fin_ic_ask)
                    .setContentTitle(title)
                    .setContentText("기록할까요?")
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setWhen(it.optLong("t", System.currentTimeMillis()))
                    .setShowWhen(true)
                    .addAction(new Notification.Action.Builder(null, "기록", tap).build())
                    .addAction(new Notification.Action.Builder(null, "안 함", no).build())
                    .build();
            nm.notify(CH, noteId(id), n);
        } catch (Exception ignored) {}
    }

    static void cancel(Context c, long id) {
        try { c.getSystemService(NotificationManager.class).cancel(CH, noteId(id)); } catch (Exception ignored) {}
    }

    /** 건을 치운다(기록했거나 「안 함」): 목록·알림·위젯 */
    static void done(Context c, long id) {
        FinPending.remove(c, id);
        cancel(c, id);
        FinWidget.refresh(c);
    }

    private static int noteId(long id) { return (int) (id % Integer.MAX_VALUE); }

    // 이 앱의 빠른 기록 창(앱마다 클래스 이름이 다르다: 재정 기록 앱 QuickActivity · 라이프 RPG WalletQuickActivity)
    static Class<?> quickClass(Context c) {
        for (String n : new String[]{".QuickActivity", ".WalletQuickActivity"}) {
            try {
                Class<?> k = Class.forName(c.getPackageName() + n);
                if (FinQuick.class.isAssignableFrom(k)) return k;
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public void onReceive(Context c, Intent in) {
        String a = in.getAction();
        if (SKIP.equals(a)) {
            long id = in.getLongExtra(FinQuick.EXTRA_PENDING, 0);
            if (id != 0) done(c, id);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            if (FinNotice.enabledIn(c, c.getPackageName())) {
                try { NotificationListenerService.requestRebind(new ComponentName(c, FinNotice.class)); } catch (Exception ignored) {}
            }
            FinWidget.refresh(c);
        }
    }
}
