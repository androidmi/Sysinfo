package sysinfo.app.com.sysinfo;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.IBinder;
import android.util.Log;

import sysinfo.app.com.sysinfo.util.PrintUtils;

public class MonitorService extends Service {

    private static final String TAG = "MonitorService";

    private BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.i(TAG, "screen off");
                AlarmMrg.cancelEvent(getApplication(), AlarmService.DUMP_ACTION);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.i(TAG, "screen on");
                AlarmMrg.scheduleAlarmEvent(getApplication());
            }
        }
    };

    public MonitorService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenReceiver, filter);
        AlarmMrg.scheduleAlarmEvent(getApplication());
        PrintUtils.printEnvInfo();
        PrintUtils.printStateInfo();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }
}
