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
        web.setBackgroundColor(Color.parseColor("#f4f5f2"));
        web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.addView(web);
        setContentView(root);

        // 아래에 붙는 창, 화면 높이의 3/4. 키패드·칩이 다 보이는 크기
        int h = Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, h);
        getWindow().setGravity(Gravity.BOTTOM);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // 로그인·장부 기억은 본 앱과 같은 저장소를 쓴다
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
        String url = MainActivity.BASE + "?quick=1" + ("수입".equals(kind) ? "&kind=%EC%88%98%EC%9E%85" : "");
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
