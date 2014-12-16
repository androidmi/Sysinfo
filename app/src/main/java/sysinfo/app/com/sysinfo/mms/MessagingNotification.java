/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sysinfo.app.com.sysinfo.mms;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import sysinfo.app.com.sysinfo.R;


/**
 * This class is used to update the notification indicator. It will check whether
 * there are unread messages. If yes, it would show the notification indicator,
 * otherwise, hide the indicator.
 */
public class MessagingNotification {

    private static final int NOTIFICATION_TYPE_RECEIVE = 1;
    private static final int NOTIFICATION_TYPE_SEND = 2;
    private static final int NOTIFICATION_TYPE_RAILWAYTICKET = 3;

    private static final int NOTIFICATION_ID = 123;
    public static final int MESSAGE_FAILED_NOTIFICATION_ID = 789;
    public static final int DOWNLOAD_FAILED_NOTIFICATION_ID = 531;
    public static final int RAILWAYTICKET_NOTIFICATION_ID = 910;

    public static void notifySendFailed(Context context) {
        notifySendFailed(context, 0, false);
    }

    public static void notifySendFailed(Context context, boolean noisy) {
        notifySendFailed(context, 0, noisy);
    }

    private static void notifySendFailed(Context context, long threadId,
                                         boolean noisy) {

        long[] msgThreadId = { 0, 1 }; // Dummy initial values, just to initialize the memory
        // The getUndeliveredMessageCount method puts a non-zero value in msgThreadId[1] if all
        // failures are from the same thread.
        // If isDownload is true, we're dealing with 1 specific failure; therefore "all failed" are
        // indeed in the same thread since there's only 1.
        boolean allFailedInSameThread = (msgThreadId[1] != 0);

        doNotification(context, NOTIFICATION_TYPE_SEND, "通知", "发送失败！");
    }

    public static void doNotification(final Context context, int notifiType, String title ,String message) {
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Notification.Builder mNotifyBuilder = new Notification.Builder(context);
            notification = mNotifyBuilder.build();
        } else {
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
            notification = mBuilder.build();
        }
        RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.notification_sms_text);
        if (notifiType == NOTIFICATION_TYPE_RECEIVE) {
        } else if (notifiType == NOTIFICATION_TYPE_SEND || notifiType == NOTIFICATION_TYPE_RAILWAYTICKET) {
            contentView.setViewVisibility(R.id.iconbrand, View.GONE);
//            contentView.setViewVisibility(R.id.date, View.GONE);
        }
        contentView.setTextViewText(R.id.title, title);
        contentView.setTextViewText(R.id.message, message);
        notification.contentView = contentView;
        notification.when = 1;
        NotificationManager nm = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notifiType == NOTIFICATION_TYPE_RECEIVE) {
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;
            notification.ledARGB = Color.BLUE;
            notification.ledOnMS = 500;
            notification.ledOffMS = 500;
            nm.notify(NOTIFICATION_ID, notification);
        } else if (notifiType == NOTIFICATION_TYPE_SEND) {
            nm.notify(MESSAGE_FAILED_NOTIFICATION_ID, notification);
        } else {
            nm.notify(RAILWAYTICKET_NOTIFICATION_ID, notification);
        }
    }


}
