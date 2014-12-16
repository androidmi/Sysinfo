package sysinfo.app.com.sysinfo;

import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;

import sysinfo.app.com.sysinfo.util.Constants;
import sysinfo.app.com.sysinfo.util.FileHelper;
import sysinfo.app.com.sysinfo.util.LogTag;
import sysinfo.app.com.sysinfo.util.MemoryUtils;
import sysinfo.app.com.sysinfo.util.StorageUtils;

public class MyCrashHandler implements UncaughtExceptionHandler {
    private static final boolean DEBUG = LogTag.DEBUG_LOG;
    private static final String TAG = "MyCrashHandler";

    private static final String CRASH_LOG_FILENAME = "crash.trace";
    private static final String CRASH_HPROF_FILENAME = "crash.hprof";

    private UncaughtExceptionHandler mDefaultHandler;
    private Context mAppContext;

    private MyCrashHandler(Context context) {
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        mAppContext = context.getApplicationContext();
    }

    public static void setupDefaultHandler(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new MyCrashHandler(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable e) {
        Log.i(TAG, "uncaughtException:"+e);
        if (e == null) {
            return;
        }

        byte[] crashData = null;
        ObjectOutputStream objStream = null;
        try {
            ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
            objStream = new ObjectOutputStream(bytesStream);
            objStream.writeObject(e);
            objStream.flush();
            crashData = bytesStream.toByteArray();
        } catch (IOException e1) {
            Log.w(TAG, "failed to serialize crash data", e1);
        } finally {
            FileHelper.close(objStream);
        }

        try {
            dumpCrashToSD(e);
        } catch (Throwable e1) {
            Log.w(TAG, "failed to process crash", e1);
        }

        // rethrow the exception
        if (mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(thread, e);
        }
    }

    public synchronized static void dumpCrashToSD(Throwable throwable) {
//        if (!StorageUtils.externalStorageAvailable()) {
//            return; // no SD card available
//        }
        Log.i(TAG, "dumpCrashToSD");

        File crashLog = FileHelper.getSafeExternalFile(Constants.APP_PATH,
                CRASH_LOG_FILENAME);
        try {
            FileWriter filename = new FileWriter(crashLog, true);
            PrintWriter pw = new PrintWriter(filename);
            printEnvironmentInfo(pw);
            pw.println();
            throwable.printStackTrace(pw);
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "cannot dump crash logs: " + e);
        }

        if (DEBUG) {
            Throwable rootCause = getRootCause(throwable);
            if (rootCause instanceof OutOfMemoryError) {
                File crashHprof = FileHelper.getSafeExternalFile(Constants.APP_PATH,
                        CRASH_HPROF_FILENAME);
                try {
                    Debug.dumpHprofData(crashHprof.getAbsolutePath());
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.w(TAG, "Failed to dump HPROF: " + e);
                }
            }
        }
    }

    private static Throwable getRootCause(Throwable throwable) {
        Throwable parent = throwable.getCause();
        while (parent != null) {
            throwable = parent;
            parent = throwable.getCause();
        }
        return throwable;
    }

    private static void printEnvironmentInfo(PrintWriter pw) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        pw.println(format.format(new Date(System.currentTimeMillis())));
        pw.print("App Version: ");

        pw.print("OS Version: ");
        pw.print(Build.VERSION.RELEASE);
        pw.print("_");
        pw.println(Build.VERSION.SDK_INT);

        pw.print("Vendor: " );
        pw.println(Build.MANUFACTURER);

        pw.print("Model: ");
        pw.println(Build.MODEL);

        pw.print("CPU ABI: ");
        pw.println(Build.CPU_ABI);

        pw.print("Sys mem: ");
        int[] sysMemInfo = MemoryUtils.getSystemMemory();
        pw.print(sysMemInfo[0]);
        pw.print(", ");
        pw.println(sysMemInfo[1]);

        pw.print("Data space: ");
        pw.print(StorageUtils.getInternalStorageAvailableSize() / (1024 * 1024));
        pw.print(", ");
        pw.println(StorageUtils.getInternalStorageTotalSize() / (1024 * 1024));

        // Bytes
        pw.print("Debug.NativeHeapAllocated: ");
        pw.println(Debug.getNativeHeapAllocatedSize());
        pw.print("Debug.NativeHeapFree: ");
        pw.println(Debug.getNativeHeapFreeSize());
        pw.print("Debug.NativeHeapSize: ");
        pw.println(Debug.getNativeHeapSize());

        // KB
        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);
        pw.print("Debug.MemInfo.dalvikPss: ");
        pw.println(memInfo.dalvikPss);
        pw.print("Debug.MemInfo.dalvikPrivateDirty: ");
        pw.println(memInfo.dalvikPrivateDirty);
        pw.print("Debug.MemInfo.dalvikSharedDirty: ");
        pw.println(memInfo.dalvikSharedDirty);

        pw.print("Debug.MemInfo.nativePss: ");
        pw.println(memInfo.nativePss);
        pw.print("Debug.MemInfo.nativePrivateDirty: ");
        pw.println(memInfo.nativePrivateDirty);
        pw.print("Debug.MemInfo.nativeSharedDirty: ");
        pw.println(memInfo.nativeSharedDirty);

        pw.print("Debug.MemInfo.otherPss: ");
        pw.println(memInfo.otherPss);
        pw.print("Debug.MemInfo.otherPrivateDirty: ");
        pw.println(memInfo.otherPrivateDirty);
        pw.print("Debug.MemInfo.otherSharedDirty: ");
        pw.println(memInfo.otherSharedDirty);

        pw.print("Debug.MemInfo.totalPss: ");
        pw.println(memInfo.getTotalPss());
        pw.print("Debug.MemInfo.totalPrivateDirty: ");
        pw.println(memInfo.getTotalPrivateDirty());
        pw.print("Debug.MemInfo.totalSharedDirty: ");
        pw.println(memInfo.getTotalSharedDirty());
    }

    /**
     * Make the exception "OutOfMemoryError" for test purpose.
     */
    public static void makeOutOfMemoryError() {
        if (!DEBUG) return;
        final int BLOCK_SIZE = 1024 * 1024; // 1M
        final int BLOCK_COUNT = 1000;
        class TestBuffer {
            Byte[] innerBuffer = new Byte[BLOCK_SIZE];
        }
        TestBuffer[] buffers = new TestBuffer[BLOCK_COUNT];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new TestBuffer();
        }
    }

    /**
     * Make the normal exception for test purpose
     */
    public static void makeCrash() {
        try {
            throwCrashNested();
        } catch (Exception e) {
            throw new RuntimeException("exception found", e);
        }
    }

    /**
     * for test only
     */
    private static void throwCrashNested() {
        throw new RuntimeException("this is a test exception");
    }
}
