/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.Telephony;
import android.provider.Telephony.Sms;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


import java.util.Calendar;
import java.util.GregorianCalendar;

import sysinfo.app.com.sysinfo.MyCrashHandler;
import sysinfo.app.com.sysinfo.util.LogTag;
import sysinfo.app.com.sysinfo.util.PrintUtils;

import static android.content.Intent.ACTION_BOOT_COMPLETED;

/**
 * This service essentially plays the role of a "worker thread", allowing us to store
 * incoming messages to the database, update notifications, etc. without blocking the
 * main thread that SmsReceiver runs on.
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class SmsReceiverService extends Service {
    private static final String TAG = "SmsReceiverService";

    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    private boolean mSending;
    private int mSimSlot = DualSimConstants.SIM_SLOT_NONE;
    private static final boolean DEBUG = LogTag.DEBUG_LOG;

    public static final String MESSAGE_SENT_ACTION =
        "com.ss.mms.transaction.MESSAGE_SENT";
    public static final String SMS_RECEIVED_ACTION = Constants.ACTION_SMSCENTER_DELIVERY_SMS;

    // Indicates next message can be picked up and sent out.
    public static final String EXTRA_MESSAGE_SENT_SEND_NEXT ="SendNextMsg";

    public static final String ACTION_SEND_MESSAGE =
            "com.ss.mms.transaction.SEND_MESSAGE";
    public static final String ACTION_SEND_INACTIVE_MESSAGE =
            "com.ss.mms.transaction.SEND_INACTIVE_MESSAGE";
    public static final String ACTION_AIRPLANE_MODE_CHANGED =
            "com.ss.mms.transaction.ACTION_AIRPLANE_MODE_CHANGED";
    public static final String ACTION_SIM_STATE_CHANGED =
            "com.ss.mms.transaction.ACTION_SIM_STATE_CHANGED";

    // This must match the column IDs below.
    private static final String[] SEND_PROJECTION = new String[] {
            Telephony.Sms._ID,        //0
        Sms.THREAD_ID,  //1
        Sms.ADDRESS,    //2
        Sms.BODY,       //3
        Sms.STATUS,     //4
    };

    public Handler mToastHandler = new Handler();

    // This must match SEND_PROJECTION.
    private static final int SEND_COLUMN_ID         = 0;
    private static final int SEND_COLUMN_THREAD_ID  = 1;
    private static final int SEND_COLUMN_ADDRESS    = 2;
    private static final int SEND_COLUMN_BODY       = 3;
    private static final int SEND_COLUMN_STATUS     = 4;

    private int mResultCode;

    @Override
    public void onCreate() {
        // Temporarily removed for this duplicate message track down.
        if (DEBUG) {
            Log.v(TAG, "onCreate");
        }

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Temporarily removed for this duplicate message track down.

        mResultCode = intent != null ? intent.getIntExtra("result", 0) : 0;

        if (mResultCode != 0) {
            if (DEBUG) {
                LogTag.i(TAG, "onStart: #" + startId + " mResultCode: " + mResultCode +
                        " = " + translateResultCode(mResultCode));
            }
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        return Service.START_NOT_STICKY;
    }

    private static String translateResultCode(int resultCode) {
        switch (resultCode) {
            case Activity.RESULT_OK:
                return "Activity.RESULT_OK";
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                return "SmsManager.RESULT_ERROR_GENERIC_FAILURE";
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return "SmsManager.RESULT_ERROR_RADIO_OFF";
            case SmsManager.RESULT_ERROR_NULL_PDU:
                return "SmsManager.RESULT_ERROR_NULL_PDU";
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return "SmsManager.RESULT_ERROR_NO_SERVICE";
            case 5/**SmsManager.RESULT_ERROR_LIMIT_EXCEEDED*/:
                return "SmsManager.RESULT_ERROR_LIMIT_EXCEEDED";
            case 6/*SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE*/:
                return "SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE";
            default:
                return "Unknown error code";
        }
    }

    @Override
    public void onDestroy() {
        // Temporarily removed for this duplicate message track down.
        if (DEBUG) {
            LogTag.i(TAG, "onDestroy");
        }
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        /**
         * Handle incoming transaction requests.
         * The incoming requests are initiated by the MMSC Server or by the MMS Client itself.
         */
        @Override
        public void handleMessage(Message msg) {
            int serviceId = msg.arg1;
            Intent intent = (Intent)msg.obj;
            if (DEBUG) {
                LogTag.v(TAG, "handleMessage serviceId: " + serviceId + " intent: " + intent);
            }
            if (intent != null) {
                String action = intent.getAction();

                int error = intent.getIntExtra("errorCode", 0);

                if (DEBUG) {
                    LogTag.v(TAG, "handleMessage action: " + action + " error: " + error);
                }
                if (MESSAGE_SENT_ACTION.equals(intent.getAction())) {
                    handleSmsSent(intent, error);
                } else if (SMS_RECEIVED_ACTION.equals(action) || Constants.ACTION_RECEIVER_SMS.equals(action)) {
                    try {

                        handleSmsReceived(intent, error);
                    } catch (Exception e) {
                        // 当短信读取权限被禁止，接收短信时发生crash
                        if (DEBUG) e.printStackTrace();
                        LogTag.dump(e);
                    }
                } else if (ACTION_BOOT_COMPLETED.equals(action)) {
                    //handleBootCompleted();
                } else if ("android.intent.action.SERVICE_STATE"/*TelephonyIntents.ACTION_SERVICE_STATE_CHANGED*/.equals(action)) {
                    handleServiceStateChanged(intent);
                } else if (ACTION_SEND_MESSAGE.endsWith(action)) {
                        mSimSlot = intent.getIntExtra(DualSimConstants.EXTRA_SIMSLOT,
                                DualSimConstants.SIM_SLOT_NONE);
                    handleSendMessage();
                        if (DEBUG) Log.d(TAG, "ServiceHandler handleMessage mSimSlot: " + mSimSlot);
                } else if (ACTION_SEND_INACTIVE_MESSAGE.equals(action)) {
                    handleSendInactiveMessage();
                } else if (ACTION_AIRPLANE_MODE_CHANGED.equals(action)
                        || ACTION_SIM_STATE_CHANGED.equals(action)) {
                    moveQueuedMessagesToFailedBox();
                }
            }
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            SmsReceiver.finishStartingService(SmsReceiverService.this, serviceId);
        }
    }

    private void handleServiceStateChanged(Intent intent) {
        // If service just returned, start sending out the queued messages
        Bundle data = intent.getExtras();
        if (data != null) {
            // from ServiceState.newFromBundle
            int state = ServiceState.STATE_OUT_OF_SERVICE;
            if (data.containsKey("state")) { // 4.3.1以下系统参数
                state = data.getInt("state");
            } else if (data.containsKey("voiceRegState")) { // 4.3.1及以上系统参数
                state = data.getInt("voiceRegState");
            }
            if (state == ServiceState.STATE_IN_SERVICE) {
                sendFirstQueuedMessage();
            }
        }
    }

    private void handleSendMessage() {
        if (!mSending) {
            sendFirstQueuedMessage();
        }
    }

    private void handleSendInactiveMessage() {
        // Inactive messages includes all messages in outbox and queued box.
        moveOutboxMessagesToQueuedBox();
        sendFirstQueuedMessage();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public synchronized void sendFirstQueuedMessage() {
        boolean success = true;
        // get all the queued messages from the database
        final Uri uri = Uri.parse("content://sms/queued");

        ContentResolver resolver = getContentResolver();
        Cursor c = SqliteWrapper.query(this, resolver, uri,
                        SEND_PROJECTION, null, null, "date ASC");   // date ASC so we send out in
                                                                    // same order the user tried
                                                                    // to send messages.
        if (c != null) {
            try {
                // 如果正处于飞行模式或者Sim卡不可用，所有发送短信置为发送失败。
                // TODO 短信发送失败率太高，5.8版本先去掉主观的失败提示。定位是否这个原因导致的
                if (!isSimValid()) {
                    mToastHandler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(SmsReceiverService.this, "sim错误，请重试",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    LogTag.i("Sim card not ready");
                    while (c.moveToNext()) {
                        int msgId = c.getInt(SEND_COLUMN_ID);
                        Uri msgUri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, msgId);
                        messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                    }
                    mSending = false;
                    return;
                }

                if (c.moveToFirst()) {
                    String msgText = c.getString(SEND_COLUMN_BODY);
                    String address = c.getString(SEND_COLUMN_ADDRESS);
                    int threadId = c.getInt(SEND_COLUMN_THREAD_ID);
                    int status = c.getInt(SEND_COLUMN_STATUS);

                    int msgId = c.getInt(SEND_COLUMN_ID);
                    Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);

                    SmsMessageSender sender = new SmsSingleRecipientSender(this,
                            address, msgText, threadId, status == Sms.STATUS_PENDING,
                            msgUri);

                    if (DEBUG) {
                        LogTag.v(TAG, "sendFirstQueuedMessage " + msgUri +
                                ", address: " + address +
                                ", threadId: " + threadId);
                    }

                    try {
                        sender.sendMessage(SendingProgressTokenManager.NO_TOKEN);
                        mSending = true;
                    } catch (MmsException e) {
                        LogTag.e(TAG, "sendFirstQueuedMessage: failed to send message " + msgUri
                                    + ", caught ", e);
                        mSending = false;
                        messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                        success = false;
                        // Sending current message fails. Try to send more pending messages
                        // if there is any.
                        Intent sendIntent = new Intent(SmsReceiverService.ACTION_SEND_MESSAGE,
                                null,
                                this,
                                SmsReceiver.class);
                        sendBroadcast(sendIntent);
                    }
                }
            } finally {
                MmsDatabase.closeCursor(c);
            }
        }
        if (success) {
            // We successfully sent all the messages in the queue. We don't need to
            // be notified of any service changes any longer.
            unRegisterForServiceStateChanges();
        }
    }

    private void handleSmsSent(Intent intent, int error) {
        Uri uri = intent.getData();
        mSending = false;
        boolean sendNextMsg = intent.getBooleanExtra(EXTRA_MESSAGE_SENT_SEND_NEXT, false);

        if (DEBUG) {
            String log = "handleSmsSent uri: " + uri + " sendNextMsg: " + sendNextMsg +
                    " mResultCode: " + mResultCode +
                    " = " + translateResultCode(mResultCode) + " error: " + error;
            LogTag.v(TAG, log);
        }

        if (mResultCode == Activity.RESULT_OK) {
            if (DEBUG) {
                Log.v(TAG, "handleSmsSent move message to sent folder uri: " + uri);
            }
            if (!MmsUtils.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_SENT, error)) {
                if (DEBUG) {
                    Log.e(TAG, "handleSmsSent: failed to move message " + uri + " to sent folder");
                }
            }
            if (sendNextMsg) {
                sendFirstQueuedMessage();
            }
        } else if ((mResultCode == SmsManager.RESULT_ERROR_RADIO_OFF) ||
                (mResultCode == SmsManager.RESULT_ERROR_NO_SERVICE)) {
            if (DEBUG) {
                LogTag.v(TAG, "handleSmsSent: no service, queuing message w/ uri: " + uri);
            }
            // We got an error with no service or no radio. Register for state changes so
            // when the status of the connection/radio changes, we can try to send the
            // queued up messages.
            registerForServiceStateChanges();
            // We couldn't send the message, put in the queue to retry later.
            MmsUtils.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_QUEUED, error);
            mToastHandler.post(new Runnable() {
                public void run() {
                    LogTag.i("net work error，resend latter");
                    Toast.makeText(SmsReceiverService.this, "网络异常，待网络恢复后会自动发送",
                    Toast.LENGTH_SHORT).show();
                }
            });
        } else if (mResultCode == 6/*SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE*/) {
            messageFailedToSend(uri, mResultCode);
            mToastHandler.post(new Runnable() {
                public void run() {
                    LogTag.i("fix number error");
                    Toast.makeText(SmsReceiverService.this, "号码受限",
                            Toast.LENGTH_SHORT).show();
                }
            });
            // 133404 is htc-specific error, which means temporary failure and device will retry
            // automatically
        } else if (mResultCode == 133404/* add for htc Desire 4.0.3*/){
            Cursor cursor = null;
            try {
                cursor = SqliteWrapper.query(this, this.getContentResolver(), uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    if (cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE)) == Sms.MESSAGE_TYPE_FAILED
                            && cursor.getInt(cursor.getColumnIndex(Telephony.Sms.ERROR_CODE)) == error) {
                        return;
                    }
                }
                messageFailedToSend(uri, error);
                if (sendNextMsg) {
                    sendFirstQueuedMessage();
                }
            } catch (Exception e) {
                    e.printStackTrace();
                LogTag.dump(e);
            } finally {
                MmsDatabase.closeCursor(cursor);
            }
        } else {
            messageFailedToSend(uri, error);
            if (sendNextMsg) {
                sendFirstQueuedMessage();
            }
        }
    }

    private void messageFailedToSend(Uri uri, int error) {
        if (DEBUG) {
            LogTag.v(TAG, "messageFailedToSend msg failed uri: " + uri + " error: " + error);
        }
        MmsUtils.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_FAILED, error);
//        MessagingNotification.notifySendFailed(getApplicationContext(), true);
        mToastHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(SmsReceiverService.this, "发送失败！！！！",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }



    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void handleSmsReceived(Intent intent, int error) {
        SmsMessage[] msgs = Sms.Intents.getMessagesFromIntent(intent);
        String format = intent.getStringExtra("format");

        //TODO replace it
        Uri messageUri = insertMessage(this, msgs, error, format, "sim");

        if (DEBUG) {
            final SmsMessage sms = msgs[0];
            LogTag.v(TAG, "handleSmsReceived" + (sms.isReplace() ? "(replace)" : "") +
                    " messageUri: " + messageUri +
                    ", address: " + sms.getOriginatingAddress() +
                    ", body: " + sms.getMessageBody());
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, sms.getOriginatingAddress()+":"+sms.getMessageBody(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (messageUri != null) {
            // Called off of the UI thread so ok to block.
            if (DEBUG) {
                LogTag.d(TAG, "handleSmsReceived messageUri: " + messageUri + " threadId: ");
            }
        }
    }

      private void handleBootCompleted() {
        // Some messages may get stuck in the outbox. At this point, they're probably irrelevant
        // to the user, so mark them as failed and notify the user, who can then decide whether to
        // resend them manually.
        int numMoved = moveOutboxMessagesToFailedBox();
        if (numMoved > 0) {
            MessagingNotification.notifySendFailed(getApplicationContext(), true);
        }

        // Send any queued messages that were waiting from before the reboot.
        sendFirstQueuedMessage();

        // Called off of the UI thread so ok to block.
//        MessagingNotification.showNewMessageNotification(
//                this, MessagingNotification.THREAD_ALL, false);
    }

    /**
     * Move all messages that are in the outbox to the queued state
     * @return The number of messages that were actually moved
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private int moveOutboxMessagesToQueuedBox() {
        ContentValues values = new ContentValues(1);

        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_QUEUED);

        int messageCount = SqliteWrapper.update(
                getApplicationContext(), getContentResolver(), Telephony.Mms.Outbox.CONTENT_URI,
                values, "type = " + Sms.MESSAGE_TYPE_OUTBOX, null);
        if (DEBUG) {
            LogTag.v(TAG, "moveOutboxMessagesToQueuedBox messageCount: " + messageCount);
        }
        return messageCount;
    }

    /**
     * 切换到飞行模式或Sim卡不可用，将发送队列里的短信状态改为发送失败
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void moveQueuedMessagesToFailedBox() {
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://sms/queued");
            ContentResolver resolver = getContentResolver();
            c = SqliteWrapper.query(this, resolver, uri,
                    SEND_PROJECTION, null, null, "date ASC");
            if (c != null) {
                while (c.moveToNext()) {
                    int msgId = c.getInt(SEND_COLUMN_ID);
                    Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
                    messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                }
            }
        } catch (Exception e) {
            if (DEBUG) {
                LogTag.e(TAG, "move queue messages to failed box exception:",e);
            }
        } finally {
            MmsDatabase.closeCursor(c);
        }
    }

    /**
     * Move all messages that are in the outbox to the failed state and set them to unread.
     * @return The number of messages that were actually moved
     */

    private int moveOutboxMessagesToFailedBox() {
        ContentValues values = new ContentValues(3);

        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_FAILED);
        values.put(Sms.ERROR_CODE, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        values.put(Sms.READ, Integer.valueOf(0));

        int messageCount = SqliteWrapper.update(
                getApplicationContext(), getContentResolver(), Telephony.Mms.Outbox.CONTENT_URI,
                values, "type = " + Sms.MESSAGE_TYPE_OUTBOX, null);
        if (DEBUG) {
            LogTag.v(TAG, "moveOutboxMessagesToFailedBox messageCount: " + messageCount);
        }
        return messageCount;
    }

    public static final String CLASS_ZERO_BODY_KEY = "CLASS_ZERO_BODY";

    // This must match the column IDs below.
    private final static String[] REPLACE_PROJECTION = new String[] {
        Sms._ID,
        Sms.ADDRESS,
        Sms.PROTOCOL
    };

    // This must match REPLACE_PROJECTION.
    private static final int REPLACE_COLUMN_ID = 0;

    /**
     * If the message is a class-zero message, display it immediately
     * and return null.  Otherwise, store it using the
     * <code>ContentResolver</code> and return the
     * <code>Uri</code> of the thread containing this message
     * so that we can use it for notification.
     */
    private Uri insertMessage(Context context, SmsMessage[] msgs, int error, String format, String slot) {
        // Build the helper classes to parse the messages.
        SmsMessage sms = msgs[0];
        LogTag.i(TAG, "nessageClass:" + getSmsMessageClass(sms.getMessageClass()));
        if (sms.getMessageClass() == SmsMessage.MessageClass.CLASS_0) {
            // 5.8版本处理flash sms类型
            //displayClassZeroMessage(context, sms, format);
            return null;
        } else if (sms.isReplace()) {
            return replaceMessage(context, msgs, error, slot);
        } else {
            return storeMessage(context, msgs, error, slot);
        }
    }

    private String getSmsMessageClass(SmsMessage.MessageClass messageClass) {
        switch (messageClass){
            case CLASS_0:
                return "CLASS_0";
            case CLASS_1:
                return "CLASS_1";
            case CLASS_2:
                return "CLASS_2";
            case CLASS_3:
                return "CLASS_3";
            default:
                // UNKNOW
                return "NUKNOW";
        }
    }


    public static String replaceFormFeeds(String s) {
        // Some providers send formfeeds in their messages. Convert those formfeeds to newlines.
        return s == null ? "" : s.replace('\f', '\n');
    }

    /**
     * This method is used if this is a "replace short message" SMS.
     * We find any existing message that matches the incoming
     * message's originating address and protocol identifier.  If
     * there is one, we replace its fields with those of the new
     * message.  Otherwise, we store the new message as usual.
     *
     * See TS 23.040 9.2.3.9.
     */
    private Uri replaceMessage(Context context, SmsMessage[] msgs, int error, String slot) {
        SmsMessage sms = msgs[0];
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Sms.Inbox.BODY, replaceFormFeeds(sms.getDisplayMessageBody()));
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                /*if (sms.mWrappedSmsMessage != null) {*/
                    body.append(sms.getDisplayMessageBody());
                /*}*/
            }
            values.put(Sms.Inbox.BODY, replaceFormFeeds(body.toString()));
        }

        ContentResolver resolver = context.getContentResolver();
        String originatingAddress = sms.getOriginatingAddress();
        int protocolIdentifier = sms.getProtocolIdentifier();
        String selection =
                Sms.ADDRESS + " = ? AND " +
                Sms.PROTOCOL + " = ?";
        String[] selectionArgs = new String[] {
            originatingAddress, Integer.toString(protocolIdentifier)
        };

        Cursor cursor = SqliteWrapper.query(context, resolver, Telephony.Mms.Inbox.CONTENT_URI,
                            REPLACE_PROJECTION, selection, selectionArgs, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long messageId = cursor.getLong(REPLACE_COLUMN_ID);
                    Uri messageUri = ContentUris.withAppendedId(
                            Sms.CONTENT_URI, messageId);

                    SqliteWrapper.update(context, resolver, messageUri,
                                        values, null, null);
                    return messageUri;
                }
            } finally {
                MmsDatabase.closeCursor(cursor);
            }
        }
        return storeMessage(context, msgs, error, slot);
    }

    private Uri storeMessage(Context context, SmsMessage[] msgs, int error, String slot) {
        SmsMessage sms = msgs[0];

        // Store the message in the content provider.
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Sms.Inbox.BODY, replaceFormFeeds(sms.getDisplayMessageBody()));
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                /*if (sms.mWrappedSmsMessage != null) {*/
                    body.append(sms.getDisplayMessageBody());
                /*}*/
            }
            values.put(Sms.Inbox.BODY, replaceFormFeeds(body.toString()));
        }

        // Make sure we've got a thread id so after the insert we'll be able to delete
        // excess messages.
        Long threadId = values.getAsLong(Sms.THREAD_ID);
        String address = values.getAsString(Sms.ADDRESS);

        // Code for debugging and easy injection of short codes, non email addresses, etc.
        // See Contact.isAlphaNumber() for further comments and results.
//        switch (count++ % 8) {
//            case 0: address = "AB12"; break;
//            case 1: address = "12"; break;
//            case 2: address = "Jello123"; break;
//            case 3: address = "T-Mobile"; break;
//            case 4: address = "Mobile1"; break;
//            case 5: address = "Dogs77"; break;
//            case 6: address = "****1"; break;
//            case 7: address = "#4#5#6#"; break;
//        }

        if (!TextUtils.isEmpty(address)) {
//            Contact cacheContact = Contact.get(address,true);
//            if (cacheContact != null) {
//                address = cacheContact.getNumber();
//            }
        } else {
            address = "未知号码";
            values.put(Sms.ADDRESS, address);
        }

        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            threadId = MmsUtils.getOrCreateThreadId(context, address);
            values.put(Sms.THREAD_ID, threadId);
        }
        ContentResolver resolver = context.getContentResolver();
        Uri insertedUri = SqliteWrapper.insert(context, resolver, Telephony.Sms.Inbox.CONTENT_URI, values);
        return insertedUri;
    }



    /**
     * Extract all the content values except the body from an SMS
     * message.
     */
    private ContentValues extractContentValues(SmsMessage sms) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();

        values.put(Sms.Inbox.ADDRESS, sms.getDisplayOriginatingAddress());

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        // Check to make sure the system is giving us a non-bogus time.
        Calendar buildDate = new GregorianCalendar(2011, 8, 18);    // 18 Sep 2011
        Calendar nowDate = new GregorianCalendar();
        long now = System.currentTimeMillis();
        nowDate.setTimeInMillis(now);

        if (nowDate.before(buildDate)) {
            // It looks like our system clock isn't set yet because the current time right now
            // is before an arbitrary time we made this build. Instead of inserting a bogus
            // receive time in this case, use the timestamp of when the message was sent.
            now = sms.getTimestampMillis();
        }

        values.put(Telephony.Mms.Inbox.DATE, Long.valueOf(now));
        values.put(Telephony.Mms.Inbox.DATE_SENT, Long.valueOf(sms.getTimestampMillis()));
        values.put(Sms.Inbox.PROTOCOL, sms.getProtocolIdentifier());
        values.put(Telephony.Mms.Inbox.READ, 1);
        values.put(Telephony.Mms.Inbox.SEEN, 1);
        if (sms.getPseudoSubject().length() > 0) {
            values.put(Telephony.Mms.Inbox.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Sms.Inbox.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);
        values.put(Sms.Inbox.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    private void registerForServiceStateChanges() {
        Context context = getApplicationContext();
        unRegisterForServiceStateChanges();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SERVICE_STATE"/*TelephonyIntents.ACTION_SERVICE_STATE_CHANGED*/);
        if (DEBUG) {
            LogTag.v(TAG, "registerForServiceStateChanges");
        }

        context.registerReceiver(SmsReceiver.getInstance(), intentFilter);
    }

    private void unRegisterForServiceStateChanges() {
        if (DEBUG) {
            LogTag.v(TAG, "unRegisterForServiceStateChanges");
        }
        try {
            Context context = getApplicationContext();
            context.unregisterReceiver(SmsReceiver.getInstance());
        } catch (IllegalArgumentException e) {
            // Allow un-matched register-unregister calls
            e.printStackTrace();
//            throw new IllegalArgumentException("unRegisterForServiceStateChanges 网络注册失败");
            MyCrashHandler.dumpCrashToSD(e);
        }
    }

    private boolean isSimValid() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getSimState() == TelephonyManager.SIM_STATE_READY;
    }
}

