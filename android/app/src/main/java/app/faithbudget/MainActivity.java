package app.faithbudget;

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

// 재정 기록 앱(2026-11-02 룡: 기록의 마무리는 앱). 앱 화면 = 사이트를 그대로 띄운다.
// 사이트를 고치면 앱도 바로 바뀐다. 나중에 기록 앱 하나로 합칠 때 이 주소만 옮기면 된다.
public class MainActivity extends Activity {
    static final String BASE = "https://ggorotls-prog.github.io/faith-budget/";

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
    }

    // 뒤로 가기로 앱이 바로 꺼져도 기록은 서버에 있어 그대로다
    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.refresh&&window.refresh()", null);
    }
}
