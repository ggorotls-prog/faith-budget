package app.recordkit.fin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

// 재정 기록 화면 원본 한 벌(2026-11-15 룡 「기록 앱은 동일, 라이프 RPG는 비추는 창」·「하나로 모으자」).
// 재정 기록 앱 MainActivity와 라이프 RPG 👛 지갑 장부(WalletActivity)가 이 클래스를 이어받는다.
// 화면 = 재정 기록 사이트를 그대로 띄운다(사이트를 고치면 두 앱이 바로 바뀐다). 앱으로 돌아오면 새로 불러오고, 뒤로 가기는 사이트의 appBack 먼저.
// 새 버전 확인(재정 기록 앱)·자동 로그인(라이프 RPG)처럼 앱마다 다른 것은 이어받는 쪽에서.
public abstract class FinScreen extends Activity {
    public static final String BASE = "https://ggorotls-prog.github.io/faith-budget/";

    protected WebView web;

    /** 사이트가 다 열렸을 때(라이프 RPG는 여기서 자동 로그인) */
    protected void onPageReady() {}

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#f6f5f1"));
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

            @Override
            public void onPageFinished(WebView view, String url) { onPageReady(); }

            // 폰이 메모리가 모자라 화면을 끊어도 앱이 같이 꺼지지 않게 다시 연다
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                root.removeView(view);
                view.destroy();
                web = null;
                recreate();
                return true;
            }
        });

        web.loadUrl(BASE);
    }

    // 앱으로 돌아오면 사이트를 새로 불러온다(다른 데서 적은 것이 바로 보이게)
    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.refresh&&window.refresh()", null);
    }

    // 뒤로 가기: 사이트의 appBack(열린 창 → 직전 화면)을 먼저. 더 갈 데가 없으면 닫기.
    // (2026-11-11 룡 「만든 앱들 뒤로 가기 하면 꺼지는 게 아니라 전 단계로」)
    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(function(){try{return window.appBack?appBack():false}catch(e){return false}})()", r -> {
            if ("true".equals(r)) return;
            FinScreen.super.onBackPressed();
        });
    }
}
