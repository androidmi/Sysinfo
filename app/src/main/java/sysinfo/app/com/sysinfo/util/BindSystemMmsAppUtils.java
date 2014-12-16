package sysinfo.app.com.sysinfo.util;

import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BindSystemMmsAppUtils {
    private static final String TAG = "BindSystemMmsAppUtils";
    private static final boolean DEBUG = true;

    private static final String DEFAULT_TOGGLE_RECENTS_COMP = "com.android.systemui";
    private static final String DEFAULT_TOGGLE_RECENTS_ACTIVITY = "com.android.systemui.recent.RecentsActivity";// 系统切换最近任务界面

    private static final String OUR_MMS_CONVERSATION_ACTIVITY = "ConversationList";
    private static final int TASKID_NONE = -1;
    private static final int LAUNCH_TYPE_MMS = 1;// 启动短信应用
    private static final int LAUNCH_TYPE_NONE = -1;// 不启动任何应用

    private static String DEFAULT_PKG_MMS = "cn.opda.a.phonoalbumshoushou";
    private static String DEFAULT_LAUNCH_MMS_ACTIVITY = "com.dianxinos.optimizer.module.mms.ui.ConversationList";
    private static BindSystemMmsAppUtils sInstance;

    private volatile boolean mIsRunning = false;
    private Context mContext;
    private ActivityManager mActivityManager;
    private ArrayList<ComponentName> mMmsCompList = new ArrayList<ComponentName>();
    private ArrayList<ComponentName> mLauncherCompList = new ArrayList<ComponentName>();

    private boolean mInteractive;
    private Object mInteractiveLock = new Object();

    private BindSystemMmsAppUtils(Context context) {
        mContext = context;
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        initSystemMmsPackage();
        initLauncherPackage();
    }

    public static BindSystemMmsAppUtils getInstance(Context context) {
        if (sInstance == null) {
            synchronized (BindSystemMmsAppUtils.class) {
                sInstance = new BindSystemMmsAppUtils(context);
            }
        }
        return sInstance;
    }

    public synchronized void unBindMmsAppIcon() {
        mIsRunning = false;
    }

    public synchronized void bindMmsAppIcon() {
        if (!mIsRunning) {
            mIsRunning = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    doMonitorLauchSmsApp();
                }
            }, "SmsAppMonitor").start();
        }
    }

    public void initLauncherPackage() {
        mLauncherCompList.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {// 4.2以上系统切换最近任务时会由lancher进程切换为com.android.systemui进程
            mLauncherCompList.add(new ComponentName(DEFAULT_TOGGLE_RECENTS_COMP,
                    DEFAULT_TOGGLE_RECENTS_ACTIVITY));
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfo = mContext.getPackageManager().queryIntentActivities(intent, 0);
        if (resolveInfo == null || resolveInfo.isEmpty()) {
            return;
        }
        for (ResolveInfo ri : resolveInfo) {
            ActivityInfo actInfo = ri.activityInfo;
            String pkgName = actInfo.packageName;
            String clsName = actInfo.name;
            if (pkgName == null || clsName == null) {
                continue;
            }
            mLauncherCompList.add(new ComponentName(pkgName, clsName));
            if (DEBUG) Log.d(TAG, "pkg: " + actInfo.packageName + ", cls: " + actInfo.name);
        }
    }

    private void doMonitorLauchSmsApp() {
        Log.i(TAG,"checkSmsAppStartup");
        initMoveTaskToBackStatus();
        while (mIsRunning) {
            // put all the work into another method for method profiling
            checkSmsAppStartup();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                if (DEBUG) e.printStackTrace();
            }
        }
    }

    private void checkSmsAppStartup() {
        Log.i(TAG,"checkSmsAppStartup----");
            int taskId = getCurrenProcessTaskId();
            if (taskId != TASKID_NONE) {
                PrintUtils.printEnvInfo();
            } else {
            }
    }

    /**
     * 云端引导开关拉取成功之后会执行绑定、弹出引导窗的操作，如果云端引导开关拉取成功之前，用户从桌面进入到系统短信应用，
     * 拉取成功之后我们不应该直接弹出引导窗，而应该在用户退回到桌面后，下次点击系统短信icon时再弹出引导窗
     */
    private void initMoveTaskToBackStatus() {
        List<RunningTaskInfo> list = mActivityManager.getRunningTasks(1);
        if (list != null && list.size() == 1) {
            RunningTaskInfo runTask = list.get(0);
            ComponentName component = runTask.topActivity;
            for (ComponentName comp : mMmsCompList) {
                if (comp == null) continue;
                String mmsPkg = comp.getPackageName();
                String curPkg = component.getPackageName();
                if (mmsPkg.equals(curPkg)) {
                    break;
                }
            }
        }
    }

    /**
     * 用户回到桌面后，重置状态为：任务可以移动到后台
     * @param runInfo
     */
    private void resetMoveTaskToBackStatus(RecentTaskInfo runInfo) {
        Intent intent = runInfo.baseIntent;
        if (intent == null || mLauncherCompList.isEmpty()) {
            return;
        }
        for (ComponentName comp : mLauncherCompList) {
            if (comp == null) continue;
            String mmsPkg = comp.getPackageName();
            String curPkg = intent.getPackage();
            if (mmsPkg.equals(curPkg)) {
                break;
            }
        }
    }

    /**
     * 获取当前运行的任务id
     * @return
     */
    private int getCurrenProcessTaskId() {
        List<RecentTaskInfo> recentTasks = mActivityManager.getRecentTasks(2,
                ActivityManager.RECENT_WITH_EXCLUDED);
        List<RunningTaskInfo> runningTasks = mActivityManager.getRunningTasks(2);
        int launchType = getNeedLaunchType(recentTasks, runningTasks);
        Log.d(TAG,"launchType: " + launchType);
        if (launchType == LAUNCH_TYPE_MMS) {
            return getCurrentMmsTaskId(recentTasks, runningTasks);
        }
        return TASKID_NONE;
    }

    /**
     * 获取需要启动应用的类型
     * @return
     */
    private int getNeedLaunchType(List<RecentTaskInfo> recentTasks, List<RunningTaskInfo> runningTasks) {
        if (recentTasks != null && recentTasks.size() >= 1) {
            RecentTaskInfo recentTask = recentTasks.get(0);
            Intent intent = recentTask.baseIntent;
            if (intent != null) {
                if (isMmsPkg(intent)) {
                    return LAUNCH_TYPE_MMS;
                }
            }
        }

        // 处理短信应用ActivityRecord和桌面应用ActivityRecord在同一个TaskStack中的情况
        if (runningTasks != null && runningTasks.size() >= 1) {
            RunningTaskInfo runningTask = runningTasks.get(0);
            if (isLauncherComponent(runningTask.baseActivity)
                    && isMmsComponent(runningTask.topActivity)) {
                return LAUNCH_TYPE_MMS;
            }
        }
        Log.i(TAG, "getNeedLaunchType fail");
        return LAUNCH_TYPE_NONE;
    }

    /**
     * 是否为短信应用
     *
     * @param intent
     * @return
     */
    private boolean isMmsPkg(Intent intent) {
        if (intent == null || intent.getComponent() == null) {
            return false;
        }
        String pkg = intent.getComponent().getPackageName();
        for (ComponentName comp : mMmsCompList) {
            if (comp == null) continue;
            Log.i(TAG, comp.getPackageName()+":pkg:"+pkg);
            if (comp.getPackageName().equals(pkg)) {
                Log.i(TAG, "isMmsPkg");
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前系统短信应用的任务id
     * @return
     */
    private int getCurrentMmsTaskId(List<RecentTaskInfo> recentTasks, List<RunningTaskInfo> runningTasks) {
        if (recentTasks != null && recentTasks.size() == 2) {
            RecentTaskInfo runTask = recentTasks.get(1);
            Intent intent = runTask.baseIntent;
            ComponentName compName = intent != null ? intent.getComponent() : null;
            ComponentName origComp = runTask.origActivity;
            boolean needLaunchMms = false;
            if (isLauncherComponent(compName) || isLauncherComponent(origComp)) {
                needLaunchMms = true;
            } else if (runningTasks != null && runningTasks.size() == 2) {
                // 处理类似魅族情况，在RecentTask中倒数第二个ActivityRecord为ResolveActivity
                RunningTaskInfo runningTaskInfo = runningTasks.get(1);
                if (isLauncherComponent(runningTaskInfo.topActivity)) {
                    needLaunchMms = true;
                }
            }
            RecentTaskInfo recentTask = recentTasks.get(0);
            resetMoveTaskToBackStatus(recentTask);
            if (needLaunchMms) {
                ComponentName component = recentTask.origActivity;
                if (component == null) {
                    intent = recentTask.baseIntent;
                    if (intent != null) {
                        component = intent.getComponent();
                        if (DEBUG) Log.d(TAG, "baseIntent component: " + component);
                    }
                }
                if (DEBUG) Log.d(TAG, "component: " + component);
                if (isMmsComponent(component)) {
                    return recentTask.id;
                } else if (runningTasks != null && runningTasks.size() >= 1) {
                    // 处理类似联想S720绑定问题，Recent Task中找不到匹配的Component
                    RunningTaskInfo runningTaskInfo = runningTasks.get(0);
                    if (isMmsComponent(runningTaskInfo.topActivity)) {
                        return runningTaskInfo.id;
                    }
                }
            }

            // 处理短信应用ActivityRecord和桌面应用ActivityRecord在同一个TaskStack中的情况
            if (runningTasks != null && runningTasks.size() >= 1) {
                RunningTaskInfo runningTask = runningTasks.get(0);
                ComponentName baseComp = runningTask.baseActivity;
                if (isLauncherComponent(baseComp) && isMmsComponent(runningTask.topActivity)) {
                    return runningTask.id;
                }
            }
        }
        return TASKID_NONE;
    }

    private boolean isMmsComponent(ComponentName component) {
        if (component == null) return false;
        for (ComponentName comp : mMmsCompList) {
            if (comp == null) continue;
            String mmsPkg = comp.getPackageName();
            String mmsCls = comp.getClassName();
            String curPkg = component.getPackageName();
            String curCls = component.getClassName();
            if (mmsPkg.equals(curPkg)
                    && mmsCls.equals(curCls)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLauncherComponent(ComponentName component) {
        if (component == null || mLauncherCompList.isEmpty()) return false;
        for (ComponentName comp : mLauncherCompList) {
            // NullPointException YHDS-12797 YHDS - 5.9.0安全短信pasta crash上报。
            if (comp == null) continue;
            String launcherPkg = comp.getPackageName();
            String launcherCls = comp.getClassName();
            String curPkg = component.getPackageName();
            String curCls = component.getClassName();
            if (launcherPkg.equals(curPkg)
                    && launcherCls.equals(curCls)) {
                return true;
            }
        }
        return false;
    }

    private void initSystemMmsPackage() {
        mMmsCompList.clear();
        ArrayList<ComponentName> compList = querySystemMmsAppPackage(mContext);
        if (compList.isEmpty()) {
            ComponentName comp = new ComponentName(DEFAULT_PKG_MMS, DEFAULT_LAUNCH_MMS_ACTIVITY);
            mMmsCompList.add(comp);
        } else {
            mMmsCompList.addAll(compList);
        }
        if (DEBUG) Log.d(TAG, "initSystemMmsPackage mMmsCompList: " + mMmsCompList);
    }

    /**
     * 查询系统自带短信应用包名
     * @param context
     * @return
     */
    private ArrayList<ComponentName> querySystemMmsAppPackage(Context context) {
        ArrayList<ComponentName> compList = new ArrayList<ComponentName>();
        compList.add(new ComponentName(DEFAULT_PKG_MMS, DEFAULT_LAUNCH_MMS_ACTIVITY));
        return compList;
    }
}
