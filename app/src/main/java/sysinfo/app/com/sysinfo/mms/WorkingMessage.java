 /*
 * Copyright (C) 2009 The Android Open Source Project
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

 import android.content.Context;
 import android.text.TextUtils;
 import android.util.Log;

 import sysinfo.app.com.sysinfo.MainActivity;
 import sysinfo.app.com.sysinfo.MyCrashHandler;
 import sysinfo.app.com.sysinfo.util.LogTag;

 /**
  * Contains all state related to a message being edited by the user.
  */
 public class WorkingMessage {

     /**
      * Callback interface for communicating important state changes back to
      * ComposeMessageActivity.
      */
     public interface MessageStatusListener {
         /**
          * Called when the protocol for sending the message changes from SMS
          * to MMS, and vice versa.
          *
          * @param mms If true, it changed to MMS.  If false, to SMS.
          */
         void onProtocolChanged(boolean mms);

         /**
          * Called when an attachment on the message has changed.
          */
         void onAttachmentChanged();

         /**
          * Called just before the process of sending a message.
          */
         void onPreMessageSent();

         /**
          * Called once the process of sending a message, triggered by
          * just that it has been dispatched to the network.
          */
         void onMessageSent();

         /**
          * Called if there are too many unsent messages in the queue and we're not allowing
          * any more Mms's to be sent.
          */
         void onMaxPendingMessagesReached();

         /**
          * Called if there's an attachment error while resizing the images just before sending.
          */
         void onAttachmentError(int error);
     }

     private static final String TAG = "WorkingMessage";
     private static final boolean DEBUG = false;
     private final MessageStatusListener mStatusListener;
     Context mActivity;

     private WorkingMessage(MainActivity activity) {
         mStatusListener = activity;
         mActivity = activity.getApplicationContext();
     }

     public static WorkingMessage create(MainActivity activity) {
         return new WorkingMessage(activity);
     }

     // Message sending stuff
     public void preSendSmsWorker(String msgText, String address, int simSlot) {

         // recipientsInUI can be empty when the user types in a number and hits send

             // just do a regular send. We're already on a non-ui thread so no need to fire
             // off another thread to do this work.
             sendSmsWorker(mActivity, msgText, address, MmsUtils.getOrCreateThreadId(mActivity, address), simSlot);

             // Be paranoid and clean any draft SMS up.
     }

     private void sendSmsWorker(Context mActivity, String msgText, String semiSepRecipients, long threadId, int simSlot) {
         String[] dests = TextUtils.split(semiSepRecipients, ";");
        LogTag.d(TAG, "sendSmsWorker sending message: recipients=" +
                     semiSepRecipients + ", threadId=" + threadId);
         MessageSender sender = new SmsMessageSender(mActivity, dests, msgText, threadId, simSlot);
         try {
             sender.sendMessage(threadId);
         } catch (MmsException e) {
            LogTag.e(TAG, "Failed to send SMS message, threadId=" + threadId, e);
         }
         mStatusListener.onMessageSent();
     }
}
