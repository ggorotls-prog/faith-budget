package app.recordkit.fin;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

// 홈 화면 위젯 원본 한 벌: 「+ 수입 | − 지출」 버튼 둘뿐. 누르면 그 종류로 빠른 기록 창이 뜬다 (2026-11-03 룡: 위젯은 필수).
// 재정 기록 앱 QuickWidget과 라이프 RPG(WalletWidget)가 이어받는다. 앱마다 다른 것: 열 빠른 기록 창(quick).
public abstract class FinWidget extends AppWidgetProvider {
    protected abstract Class<? extends FinQuick> quick();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx));
    }

    private RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.fin_widget);
        v.setOnClickPendingIntent(R.id.fin_out, open(ctx, quick(), "지출", 1));
        v.setOnClickPendingIntent(R.id.fin_in, open(ctx, quick(), "수입", 2));
        return v;
    }

    /** 빠른 기록 창을 여는 인텐트(위젯 버튼·라이프 RPG 지갑 버튼이 같이 쓴다) */
    public static Intent quickIntent(Context ctx, Class<? extends FinQuick> quick, String kind) {
        return new Intent(ctx, quick).putExtra(FinQuick.EXTRA_KIND, kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private static PendingIntent open(Context ctx, Class<? extends FinQuick> quick, String kind, int code) {
        return PendingIntent.getActivity(ctx, code, quickIntent(ctx, quick, kind), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
