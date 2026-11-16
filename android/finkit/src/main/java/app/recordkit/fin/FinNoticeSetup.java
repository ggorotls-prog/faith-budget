package app.recordkit.fin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

// 「은행·카드 알림 켜기」 창(2026-11-16 룡: 「재정 기록 앱에서 내가 사용하는 앱 알림 켤 수 있도록」).
// ① 이 앱의 알림 읽기 켜기 ② 폰에 깔린 내 은행·카드·페이 앱 목록 → 누르면 그 앱의 알림 설정 화면이 바로 열린다.
// 다른 앱의 알림이 켜져 있는지는 읽을 수 없어서(안드로이드가 안 알려 준다) 켜짐/꺼짐 표시는 ①에만 있다.
public final class FinNoticeSetup {
    private FinNoticeSetup() {}

    private static final int THEME = android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;

    public static void show(Activity a) {
        if (a.isFinishing()) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> taps = new ArrayList<>();
        ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_list_item_1, labels);
        Runnable fill = () -> {
            labels.clear();
            taps.clear();
            boolean on = FinNotice.enabledIn(a, a.getPackageName());
            String state = FinNotice.state(a);
            labels.add(on ? "✅ ① 이 앱이 알림 읽기: 켜짐"
                    : "other".equals(state) ? "✅ ① 알림 읽기: 다른 기록 앱에서 켜짐"
                    : "⬜ ① 이 앱이 알림 읽기: 꺼짐 → 눌러서 켜기");
            taps.add(() -> howTo(a));
            labels.add("② 내 은행·카드 앱 (누르면 그 앱 알림 설정)");
            taps.add(null);
            TreeMap<String, String> apps = moneyApps(a);
            for (String name : apps.keySet()) {
                String pkg = apps.get(name);
                labels.add("      " + name + "  ›");
                taps.add(() -> {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg));
                    } catch (Exception ignored) {}
                });
            }
            if (apps.isEmpty()) {
                labels.add("      은행·카드 앱을 못 찾았어요");
                taps.add(null);
            }
            ad.notifyDataSetChanged();
        };
        fill.run();
        ListView lv = new ListView(a);
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, pos, id) -> { Runnable r = pos < taps.size() ? taps.get(pos) : null; if (r != null) r.run(); });
        // 설정에 갔다 돌아오면 ①의 켜짐/꺼짐을 다시 본다
        lv.getViewTreeObserver().addOnWindowFocusChangeListener(has -> { if (has) fill.run(); });
        new AlertDialog.Builder(a, THEME)
                .setTitle("은행·카드 알림 켜기")
                .setMessage("입금·출금·결제 알림이 뜨면 「기록할까요?」를 물어요. ①을 켜고, ②에서 쓰는 앱을 눌러 「알림 허용」을 켜 주세요. 은행 앱 안의 입출금 알림도 켜져 있어야 해요.")
                .setView(lv)
                .setPositiveButton("닫기", null)
                .show();
    }

    // 폰에 깔린(홈 화면에 보이는) 앱 중 은행·카드·페이 앱: 이름 → 패키지
    private static TreeMap<String, String> moneyApps(Activity a) {
        TreeMap<String, String> out = new TreeMap<>();
        try {
            PackageManager pm = a.getPackageManager();
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(a.getPackageName()) || !FinParse.isMoneyPkg(pkg) || out.containsValue(pkg)) continue;
                out.put(String.valueOf(r.loadLabel(pm)), pkg);
            }
        } catch (Exception ignored) {}
        return out;
    }

    // 알림 읽기 켜는 법(카톡 기록 앱과 같은 순서: 받아 깐 앱은 「제한된 설정 허용」이 먼저)
    static void howTo(Activity a) {
        if (a.isFinishing()) return;
        new AlertDialog.Builder(a, THEME)
                .setTitle("① 알림 읽기 켜기")
                .setMessage("1. 「켜러 가기」 → 목록에서 「재정 기록 (은행·카드 알림 → 기록할까요?)」을 켜요.\n"
                        + "2. 회색이라 안 눌리면 「앱 정보」 → 오른쪽 위 ⋮ → 「제한된 설정 허용」을 누르고 다시 1번.\n\n"
                        + "알림에서 금액·가게 이름만 읽어요. 계좌번호·잔액은 저장하지 않아요.")
                .setPositiveButton("켜러 가기", (d, w) -> {
                    try { a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); } catch (Exception ignored) {}
                })
                .setNeutralButton("앱 정보", (d, w) -> {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("닫기", null)
                .show();
    }
}
