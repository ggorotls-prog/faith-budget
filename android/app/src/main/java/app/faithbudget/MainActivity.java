package app.faithbudget;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

// 재정 기록 앱(2026-11-02 룡: 기록의 마무리는 앱). 앱 화면 = 사이트를 그대로 띄운다.
// 화면·새로 불러오기·뒤로 가기는 원본 한 벌 finkit의 FinScreen(라이프 RPG 👛 지갑 장부와 같이 씀, 2026-11-15 룡 「하나로 모으자」).
// 여기에는 이 앱만의 것: 새 버전 확인(스스로 업데이트)·알림 권한.
public class MainActivity extends app.recordkit.fin.FinScreen {
    static volatile boolean visible; // 업데이트 확인 화면을 바로 띄울지
    private boolean backFromSecurity; // 「업데이트」를 눌러 보안 화면에 갔다 왔는지

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        UpdateJob.schedule(getApplicationContext()); // 앱을 안 열어도 새 버전이면 알림
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        // 앱을 열 때마다 새 버전이 있나 본다(업무·영성 기록 앱과 같은 방식, 2026-11-03 룡: 모든 기록 앱 동일)
        boolean retry = backFromSecurity;
        backFromSecurity = false;
        new Thread(() -> {
            int latest = Updater.latest(getApplicationContext());
            if (latest <= BuildConfig.VERSION_CODE) return;
            if (!Updater.canInstall(this)) { runOnUiThread(this::askInstallPermission); return; }
            if (retry) { Updater.checkAndInstall(getApplicationContext(), true, true); return; }
            if (!Updater.askedRecently(getApplicationContext(), latest)) runOnUiThread(this::askUpdate);
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
    }

    // 새 버전이 있을 때: 「업데이트」를 누르면 보안 화면을 연다. 자동 차단을 끄고 돌아오면 설치가 시작된다.
    private void askUpdate() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("새 버전이 있어요")
                .setMessage("업데이트하려면 삼성 「자동 차단」을 잠깐 꺼야 해요.\n\n「업데이트」를 누르면 보안 화면이 열려요. 「자동 차단」을 끄고 뒤로 오면 업데이트가 시작돼요.\n(자동 차단은 30분 뒤 저절로 다시 켜져요)")
                .setPositiveButton("업데이트", (d, w) -> {
                    backFromSecurity = true;
                    for (String action : new String[]{Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_SETTINGS}) {
                        try { startActivity(new Intent(action)); return; } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("나중에", null)
                .show();
    }

    // 처음 한 번: 이 앱이 스스로 업데이트할 수 있게 「이 출처 허용」을 켜 달라고 한다
    private void askInstallPermission() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("새 버전이 있어요")
                .setMessage("앞으로 저절로 업데이트되게, 다음 화면에서 「이 출처 허용」을 켜 주세요. 한 번만 하면 돼요.")
                .setPositiveButton("켜러 가기", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("나중에", null)
                .show();
    }
}
