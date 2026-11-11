package app.faithbudget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

// 15분마다 새 버전이 있나 보고, 있으면 알림만 한 번 띄운다(설치는 앱을 열었을 때).
// 업무 기록 앱 SyncJob의 업데이트 알림과 같음 (2026-11-11 룡 「업무 기록처럼 업데이트 알림」).
public class UpdateJob extends JobService {
    private static final int JOB = 9100;

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB) != null) return;
        JobInfo.Builder b = new JobInfo.Builder(JOB, new ComponentName(c, UpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60_000L)
                .setPersisted(true);
        try { js.schedule(b.build()); } catch (Exception ignored) {}
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Updater.notifyIfNew(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
