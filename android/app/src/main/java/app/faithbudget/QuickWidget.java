package app.faithbudget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

// 홈 화면 위젯: 「− 지출 | + 수입」 버튼 둘뿐. 누르면 그 종류로 빠른 기록 창이 뜬다 (2026-11-03 룡: 위젯은 필수).
public class QuickWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx));
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        v.setOnClickPendingIntent(R.id.out, open(ctx, "지출", 1));
        v.setOnClickPendingIntent(R.id.in, open(ctx, "수입", 2));
        return v;
    }

    private static PendingIntent open(Context ctx, String kind, int code) {
        Intent i = new Intent(ctx, QuickActivity.class).putExtra(QuickActivity.EXTRA_KIND, kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
