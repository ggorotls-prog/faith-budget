package app.faithbudget;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

// 재정 기록 앱(2026-11-02 룡: 기록의 마무리는 앱). 앱 화면 = 사이트를 그대로 띄운다.
// 사이트를 고치면 앱도 바로 바뀐다. 나중에 기록 앱 하나로 합칠 때 이 주소만 옮기면 된다.
public class MainActivity extends Activity {
    static final String BASE = "https://ggorotls-prog.github.io/faith-budget/";

    static volatile boolean visible; // 업데이트 확인 화면을 바로 띄울지
    private boolean backFromSecurity; // 「업데이트」를 눌러 보안 화면에 갔다 왔는지
    private WebView web;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#f4f5f2"));
        web = new WebView(this);
        // 폰의 자동 완성(삼성 패스 등)을 끈다. 다른 앱에서 로그인 칸의 키보드가 안 뜬 적이 있다(2026-10-30)
        web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.addView(web);
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return WindowInsets.CONSUMED;
            });
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (Uri.parse(BASE).getHost().equals(req.getUrl().getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl())); } catch (Exception ignored) {}
                return true;
            }

            // 폰이 메모리가 모자라 화면을 끊어도 앱이 같이 꺼지지 않게 다시 연다
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                root.removeView(view);
                view.destroy();
                recreate();
                return true;
            }
        });

        web.loadUrl(BASE);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }

    // 뒤로 가기로 앱이 바로 꺼져도 기록은 서버에 있어 그대로다
    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (web != null) web.evaluateJavascript("window.refresh&&window.refresh()", null);
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

    // 뒤로 가기: 사이트의 appBack(열린 창 → 직전 화면)을 먼저. 더 갈 데가 없으면 앱 닫기.
    // (2026-11-11 룡 「만든 앱들 뒤로 가기 하면 꺼지는 게 아니라 전 단계로」, 업무·영성 기록과 같은 방식)
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(function(){try{return window.appBack?appBack():false}catch(e){return false}})()", r -> {
            if ("true".equals(r)) return;
            super.onBackPressed();
        });
    }
}
