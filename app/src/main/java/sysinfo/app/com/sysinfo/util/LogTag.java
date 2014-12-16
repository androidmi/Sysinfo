package sysinfo.app.com.sysinfo.util;

import android.database.sqlite.SQLiteException;
import android.util.Log;

import sysinfo.app.com.sysinfo.MyCrashHandler;

/**
 * Created by dufan on 2014/12/5.
 */
public class LogTag {
    public static final boolean DEBUG_LOG = true;
    private static final String TAG = "LogTag";
    public static void i(String log) {
        Log.i(TAG, log);
        PrintUtils.cachedInfo(log);
    }

    public static void i(String tag, String s) {
        Log.i(tag, s);
        PrintUtils.cachedInfo(tag + ":\n" + s);
    }

    public static void e(String tag, String s, Exception e) {
        Log.i(tag, s);
        MyCrashHandler.dumpCrashToSD(e);
    }

    public static void dump(Exception e) {
        MyCrashHandler.dumpCrashToSD(e);
    }

    public static void d(String tag, String s) {
        Log.i(tag, s);
        PrintUtils.cachedInfo(tag + ":\n" + s);
    }

    public static void v(String tag, String s) {
        Log.i(tag, s);
        PrintUtils.cachedInfo(tag + ":" + s);
    }

    public static void e(String logTag, String s) {
        Log.e(logTag, s);
        PrintUtils.cachedInfo(logTag + ":" + s);
    }
}
