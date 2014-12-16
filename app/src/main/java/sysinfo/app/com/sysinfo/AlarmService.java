package sysinfo.app.com.sysinfo;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import sysinfo.app.com.sysinfo.util.PrintUtils;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class AlarmService extends IntentService {

    public static final String DUMP_ACTION = "action.alarm.dump.sys";
    // TODO: Rename actions, choose action names that describe tasks that this

    public AlarmService() {
        super("AlarmService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i("AlarmWeak", "onHandle");
        if (intent != null) {
            final String action = intent.getAction();
            if (DUMP_ACTION.equals(action)) {
                PrintUtils.printStateInfo();
                AlarmMrg.scheduleAlarmEvent(this);
            }
        }
    }

}
