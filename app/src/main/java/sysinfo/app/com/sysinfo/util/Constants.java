package sysinfo.app.com.sysinfo.util;

import android.os.Environment;

/**
 * Created by dufan on 2014/12/1.
 */
public class Constants {

    public static final long SECOND = 1000;
    public static final long DUMP_INTERVEL = SECOND * 5;

    public static final String APP_PATH = Environment.getExternalStorageDirectory().getPath() + "/Mms/";
}
