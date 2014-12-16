/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.app.IntentService;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony.Sms;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import sysinfo.app.com.sysinfo.util.LogTag;

/**
 * Service that gets started by the MessageStatusReceiver when a message status report is
 * received.
 */
public class MessageStatusService extends IntentService {
    private static final String[] ID_PROJECTION = new String[] { Sms._ID };
    private static final String LOG_TAG = "MessageStatusReceiver";
    private static final Uri STATUS_URI = Uri.parse("content://sms/status");

    private static final boolean DEBUG = LogTag.DEBUG_LOG;

    private Handler sHandler = new Handler();

    public MessageStatusService() {
        // Class name will be the thread name.
        super(MessageStatusService.class.getName());

        // Intent should be redelivered if the process gets killed before completing the job.
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // This method is called on a worker thread.

        Uri messageUri = intent.getData();
        byte[] pdu = intent.getByteArrayExtra("pdu");
        String format = intent.getStringExtra("format");

        SmsMessage message = updateMessageStatus(this, messageUri, pdu, format);

        // Called on a background thread, so it's OK to block.
//        if (message != null && message.getStatus() < Sms.STATUS_PENDING) {
//            MessagingNotification.showNewMessageNotification(this,
//                    MessagingNotification.THREAD_NONE, message.isStatusReportMessage());
//        }
    }

    @SuppressLint("InlinedApi")
    private SmsMessage updateMessageStatus(final Context context, Uri messageUri, byte[] pdu,
            String format) {
        SmsMessage message = SmsMessageCompat.createFromPdu(pdu, format);
        if (message == null) {
            return null;
        }
        // Create a "status/#" URL and use it to update the
        // message's status in the database.
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                            messageUri, ID_PROJECTION, null, null, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                int messageId = cursor.getInt(0);
                Uri updateUri = ContentUris.withAppendedId(STATUS_URI, messageId);
                // 安全短信自身维护短信发送状态
                int status = message.getStatus();
                boolean isStatusReport = message.isStatusReportMessage();
                ContentValues contentValues = new ContentValues(2);

                log("updateMessageStatus: msgUrl=" + messageUri + ", status=" + status +
                        ", isStatusReport=" + isStatusReport);

                contentValues.put(Sms.STATUS, status);
                contentValues.put(Sms.Inbox.DATE_SENT, System.currentTimeMillis());
                SqliteWrapper.update(context, context.getContentResolver(),
                                    updateUri, contentValues, null, null);
                sHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "短信已经接收成功", Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                error("Can't find message for status update: " + messageUri);
            }
        } finally {
            MmsDatabase.closeCursor(cursor);
        }
        return message;
    }

    private void error(String message) {
        if (DEBUG) {
            LogTag.e(LOG_TAG, "[MessageStatusReceiver] " + message);
        }
    }

    private void log(String message) {
        if (DEBUG) {
            LogTag.d(LOG_TAG, "[MessageStatusReceiver] " + message);
        }
    }
}
