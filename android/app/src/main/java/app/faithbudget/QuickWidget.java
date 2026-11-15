package app.faithbudget;

// 홈 화면 위젯 「+ 수입 | − 지출」. 내용은 원본 한 벌 finkit의 FinWidget(라이프 RPG와 같이 씀, 2026-11-15).
// 이 클래스 이름은 바꾸지 않는다: 홈 화면에 놓인 위젯이 이 이름으로 붙어 있다.
public class QuickWidget extends app.recordkit.fin.FinWidget {
    @Override
    protected Class<? extends app.recordkit.fin.FinQuick> quick() { return QuickActivity.class; }
}
