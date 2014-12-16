package sysinfo.app.com.sysinfo.util;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Debug;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import sysinfo.app.com.sysinfo.App;
import sysinfo.app.com.sysinfo.MyCrashHandler;

/**
 * Created by dufan on 2014/12/1.
 */
public class PrintUtils {

    private static final String TAG = "PrintUtils";

    /**
     * log和硬件信息都写入该文件
     */
    private static final String ENV_FILE = "env";
    /**
     * 手机当前状态写入该信息
     */
    private static final String PHONE_STATE_FILE = "state";

    private static void printDDR() {
        Log.i(TAG, "printDDR");
        PrintWriter pw = null;
        try {
            pw = getOutput(Constants.APP_PATH, ENV_FILE);
            ExecTerminal et = new ExecTerminal();
            String cmdLog = et.exec("getprop | grep heap");
            pw.print("heap state: ");
            pw.println(cmdLog);
            pw.println();
        } catch (Exception e) {
            MyCrashHandler.dumpCrashToSD(e);
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }

    public  static void printEnvInfo() {
        PrintWriter pw = null;
        try {
            pw = getOutput(Constants.APP_PATH, ENV_FILE);
            printEnvironmentInfo(pw);
            pw.println();
        } catch (Exception e) {
            MyCrashHandler.dumpCrashToSD(e);
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }

    public static void cachedInfo(String log) {
        PrintWriter pw = null;
        try {
            pw = getOutput(Constants.APP_PATH, ENV_FILE);
            pw.print(log);
            pw.println();
        } catch (Exception e) {
            MyCrashHandler.dumpCrashToSD(e);
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }

    public static void printStateInfo() {
        Log.i(TAG, "printStateInfo");
        PrintWriter pw = null;
        try {
            pw = getOutput(Constants.APP_PATH, PHONE_STATE_FILE);
            printPoneStateInfo(pw);
            pw.println();
        } catch (Exception e) {
            MyCrashHandler.dumpCrashToSD(e);
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }

    private static PrintWriter getOutput(String path, String file) {
        File crashLog = FileHelper.getSafeExternalFile(path, file);
        PrintWriter pw = null;
        FileWriter filename = null;
        try {
            filename = new FileWriter(crashLog, true);
            pw = new PrintWriter(filename);
            printDate(pw);
            return pw;
        } catch (Exception e) {
            e.printStackTrace();
            MyCrashHandler.dumpCrashToSD(e);
        }
        return null;
    }

    private static void printPoneStateInfo(PrintWriter pw) {

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

        pw.println("phone info ---");
        TelephonyManager tm = (TelephonyManager) App.sInstance.getSystemService(Context.TELEPHONY_SERVICE);
        int state = tm.getDataState();

        pw.println("data state: " + getDataState(state) + " " + state);

        int phoneType = tm.getPhoneType();
        pw.println("phone type:" + getPhoneType(phoneType) + " " + phoneType);

        int simState = tm.getSimState();
        pw.println("sim state: " + getSimState(simState) + " " + simState);

//        pw.println("network info ---");
//        ConnectivityManager cm = (ConnectivityManager) App.sInstance.getSystemService(Context.CONNECTIVITY_SERVICE);
//        NetworkInfo info = cm.getActiveNetworkInfo();
//        int cmType = info.getType();
//        pw.print("net type: ");
//        pw.println(cmType);
    }

    private static String getPhoneType(int phoneType) {
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.PHONE_TYPE_GSM:
                return "GSM";
            case TelephonyManager.PHONE_TYPE_SIP:
                return "SIP";
            default:
                //TelephonyManager.PHONE_TYPE_NONE:
                return "NONE";
        }
    }

    private static String getDataState(int phoneType) {
        switch (phoneType) {
            case TelephonyManager.DATA_DISCONNECTED:
                return "DISCONNECTED";
            case TelephonyManager.DATA_CONNECTING:
                return "CONNECTING";
            case TelephonyManager.DATA_CONNECTED:
                return "DATA_CONNECTED";
            case TelephonyManager.DATA_SUSPENDED:
                return "DATA_SUSPENDED";
            default:
                return "NONE";
        }
    }

    private static String getSimState(int phoneType) {
        switch (phoneType) {
            case TelephonyManager.SIM_STATE_READY:
                return "READY";
            case TelephonyManager.SIM_STATE_ABSENT:
                return "ABSENT";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                return "NETWORK_LOCKED";
            case  TelephonyManager.SIM_STATE_PIN_REQUIRED:
                return "PIN_REQUIRED";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                return "PUK_REQUIRED";
            default:
                //TelephonyManager.SIM_STATE_UNKNOWN:
                return "UNKNOWN";
        }
    }

    private static void printEnvironmentInfo(PrintWriter pw) {
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
        pw.println();
    }

    private void getRunningAppProcessInfo() {
        ActivityManager mActivityManager = (ActivityManager) App.sInstance.getSystemService(Context.ACTIVITY_SERVICE);

        //���ϵͳ���������е����н��
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessesList = mActivityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcessesList) {
            // ���ID��
            int pid = runningAppProcessInfo.pid;
            // �û�ID
            int uid = runningAppProcessInfo.uid;
            // �����
            String processName = runningAppProcessInfo.processName;
            // ռ�õ��ڴ�
            int[] pids = new int[] {pid};
            Debug.MemoryInfo[] memoryInfo = mActivityManager.getProcessMemoryInfo(pids);
            int memorySize = memoryInfo[0].dalvikPrivateDirty;

            System.out.println("processName="+processName+",pid="+pid+",uid="+uid+",memorySize="+memorySize+"kb");
        }
    }

    private String getDeviceInfo() {
        String infoText = "";
        infoText += "Build info:\n";
        infoText += getTextLine("Codename", Build.VERSION.CODENAME);
        infoText += getTextLine("Incremental", Build.VERSION.INCREMENTAL);
        infoText += getTextLine("Release", Build.VERSION.RELEASE);
        infoText += getTextLine("SDK", Build.VERSION.SDK);
        infoText += getTextLine("SDK#", String.valueOf(Build.VERSION.SDK_INT));
        infoText += "\n";
        infoText += getTextLine("Board", Build.BOARD);
        infoText += getTextLine("Bootloader", Build.BOOTLOADER);
        infoText += getTextLine("Brand", Build.BRAND);
        infoText += getTextLine("CPU", Build.CPU_ABI);
        //infoText += getTextLine("CPU2", Build.CPU_ABI2);
        infoText += getTextLine("Device", Build.DEVICE);
        infoText += getTextLine("Display", Build.DISPLAY);
        infoText += getTextLine("Fingerprint", Build.FINGERPRINT);
        //infoText += getTextLine("Hardware", Build.HARDWARE);
        infoText += getTextLine("Host", Build.HOST);
        infoText += getTextLine("ID", Build.ID);
        infoText += getTextLine("Manufacturer", Build.MANUFACTURER);
        infoText += getTextLine("Model", Build.MODEL);
        infoText += getTextLine("Product", Build.PRODUCT);
        //infoText += getTextLine("Radio", Build.RADIO);
        //infoText += getTextLine("Serial", Build.SERIAL);
        infoText += getTextLine("Tags", Build.TAGS);
        infoText += getTextLine("Time", String.valueOf(Build.TIME));
        infoText += getTextLine("Type", Build.TYPE);
        //infoText += getTextLine("Unknown", Build.UNKNOWN);
        infoText += getTextLine("User", Build.USER);
        return infoText;
    }

    public static String getTextLine(String label, String value) {
        return label + ":\t" + value + "\n";
    }

    private static void printDate(PrintWriter pw) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        pw.println(format.format(new Date(System.currentTimeMillis())));
    }

}
