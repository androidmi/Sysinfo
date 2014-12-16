package sysinfo.app.com.sysinfo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import sysinfo.app.com.sysinfo.util.Constants;

/**
 * Created by dufan on 2014/12/1.
 */
public class AlarmMrg {
    private static final String TAG = "AlarmMrg";

    public static void scheduleAlarmEvent(Context context) {
        cancelEvent(context, AlarmService.DUMP_ACTION);
        addAlarmEvent(context, AlarmService.DUMP_ACTION, System.currentTimeMillis() + Constants.DUMP_INTERVEL);
    }

    /**
     * Cancel a scheduled event.
     * @param cxt
     * @param intentAction The intent action, which is used to identify the event.
     */
    public static void cancelEvent(Context cxt, String intentAction) {
        Log.i(TAG, "event canceled: " + intentAction);
        AlarmManager am = (AlarmManager) cxt.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getAlarmEventPendingIntent(cxt, intentAction, 0);
        am.cancel(pi);
    }

    private static void addAlarmEvent(Context cxt, String intentAction, long triggerAtTime) {
        Log.i(TAG, "event scheduled: " + intentAction);
        AlarmManager am = (AlarmManager) cxt.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getAlarmEventPendingIntent(cxt, intentAction, triggerAtTime);
        am.set(AlarmManager.RTC, triggerAtTime, pi);
    }

    private static PendingIntent getAlarmEventPendingIntent(Context cxt, String intentAction, long triggerAtTime) {
        Intent intent = new Intent(cxt, AlarmService.class);
        intent.setAction(intentAction);
        return PendingIntent.getService(cxt, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
