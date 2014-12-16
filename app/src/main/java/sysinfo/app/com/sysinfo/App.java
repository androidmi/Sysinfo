package sysinfo.app.com.sysinfo;

import android.app.Application;

/**
 * Created by dufan on 2014/12/1.
 */
public class App extends Application{

    public static Application sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        MyCrashHandler.setupDefaultHandler(this);
        sInstance = this;
    }
}
