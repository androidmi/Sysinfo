/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import sysinfo.app.com.sysinfo.util.LogTag;

public class MessageStatusReceiver extends BroadcastReceiver {
    private static final String TAG = "MessageStatusReceiver";
    public static final String MESSAGE_STATUS_RECEIVED_ACTION =
            "com.ss.mms.transaction.MessageStatusReceiver.MESSAGE_STATUS_RECEIVED";



    @Override
    public void onReceive(final Context context, Intent intent) {
        if (LogTag.DEBUG_LOG) {
            Log.i("verifySingleRecipient", "receiver");
        }
        if (MESSAGE_STATUS_RECEIVED_ACTION.equals(intent.getAction())) {
            intent.setClass(context, MessageStatusService.class);
            context.startService(intent);
        }
    }
}
