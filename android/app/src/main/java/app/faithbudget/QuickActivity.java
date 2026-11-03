package app.faithbudget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

// 빠른 기록 창(2026-11-03 룡: 위젯은 필수, One hand는 만들어 두고 쓰는 사람 마음).
// 앱 전체를 열지 않고 투명 창에 사이트의 기록 화면(?quick=1)만 띄운다. 업무 기록 앱의 「할 일 적기」와 같은 방식이라
// One hand로 열어도 보던 화면 위에 뜨고, 저장하면 사이트가 Android.close()를 불러 창이 닫힌다.
// 위젯 − 지출 / + 수입 버튼은 EXTRA_KIND로 종류를 정해 연다.
public class QuickActivity extends Activity {
    static final String EXTRA_KIND = "kind";

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        FrameLayout root = new FrameLayout(this);
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);   // 사이트가 둥근 카드를 그리고 바깥은 비친다
        web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.addView(web);
        setContentView(root);

        // 화면 가운데 둥근 카드(2026-11-03 룡: 위젯 밑이 아니라 가운데). 높이는 화면의 7/10, 키패드까지 보이는 크기
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.round(dm.widthPixels * 0.94f), h = Math.round(dm.heightPixels * 0.70f);
        getWindow().setLayout(w, h);
        getWindow().setGravity(Gravity.CENTER);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // 로그인·장부 기억은 본 앱과 같은 저장소를 쓴다
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);   // 사이트를 고치면 바로 새 화면(지출 창만 옛 화면이 남던 일, 2026-11-03)
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (Uri.parse(MainActivity.BASE).getHost().equals(req.getUrl().getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl())); } catch (Exception ignored) {}
                return true;
            }
        });

        String kind = getIntent().getStringExtra(EXTRA_KIND);
        String url = MainActivity.BASE + "?quick=1" + ("수입".equals(kind) ? "&kind=%EC%88%98%EC%9E%85" : "") + "&t=" + System.currentTimeMillis();
        web.loadUrl(url);

        // One hand 제스처에서 손가락을 뗄 때 바깥 누름으로 잡혀 바로 닫히지 않게(업무 기록 앱과 같음)
        setFinishOnTouchOutside(false);
        root.postDelayed(() -> setFinishOnTouchOutside(true), 1000);
    }

    class Bridge {
        @JavascriptInterface
        public void close() { runOnUiThread(QuickActivity.this::finish); }
    }
}
