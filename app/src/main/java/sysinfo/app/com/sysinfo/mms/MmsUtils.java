package sysinfo.app.com.sysinfo.mms;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by dufan on 2014/12/5.
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class MmsUtils {

    private static final String TAG = "MmsUtils";

    /**
     * Move a message to the given folder.
     *
     * @param context the context to use
     * @param uri the message to move
     * @param folder the folder to move to
     * @return true if the operation succeeded
     * @hide
     */
    public static boolean moveMessageToFolder(Context context,
                                              Uri uri, int folder, int error) {
        if (uri == null) {
            return false;
        }

        boolean markAsUnread = false;
        boolean markAsRead = false;
        switch(folder) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX:
            case Telephony.Sms.MESSAGE_TYPE_DRAFT:
                break;
            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
            case Telephony.Sms.MESSAGE_TYPE_SENT:
                markAsRead = true;
                break;
            case Telephony.Sms.MESSAGE_TYPE_FAILED:
            case Telephony.Sms.MESSAGE_TYPE_QUEUED:
                markAsUnread = true;
                break;
            default:
                return false;
        }

        ContentValues values = new ContentValues(3);

        values.put(Telephony.Sms.TYPE, folder);
        if (markAsUnread) {
            values.put(Telephony.Sms.READ, 0);
        } else if (markAsRead) {
            values.put(Telephony.Sms.READ, 1);
        }
        values.put(Telephony.Sms.ERROR_CODE, error);

        return 1 == SqliteWrapper.update(context, context.getContentResolver(),
                uri, values, null, null);
    }

    /**
     * This is a single-recipient version of {@code getOrCreateThreadId}.
     * It's convenient for use with SMS messages.
     * @param context the context object to use.
     * @param recipient the recipient to send to.
     * @hide
     */
    public static long getOrCreateThreadId(Context context, String recipient) {
        Set<String> recipients = new HashSet<String>();

        recipients.add(recipient);
        return getOrCreateThreadId(context, recipients);
    }

    private static final String[] ID_PROJECTION = { BaseColumns._ID };

    /**
     * Private {@code content://} style URL for this table. Used by
     * {@link #getOrCreateThreadId(android.content.Context, java.util.Set)}.
     */
    private static final Uri THREAD_ID_CONTENT_URI = Uri.parse(
            "content://mms-sms/threadID");

    /**
     * The {@code content://} style URL for this table, by conversation.
     */
    public static final Uri CONTENT_URI = Uri.withAppendedPath(
            Telephony.MmsSms.CONTENT_URI, "conversations");

    /**
     * Given the recipients list and subject of an unsaved message,
     * return its thread ID.  If the message starts a new thread,
     * allocate a new thread ID.  Otherwise, use the appropriate
     * existing thread ID.
     *
     * <p>Find the thread ID of the same set of recipients (in any order,
     * without any additions). If one is found, return it. Otherwise,
     * return a unique thread ID.</p>
     * @hide
     */
    public static long getOrCreateThreadId(
            Context context, Set<String> recipients) {
        Uri.Builder uriBuilder = THREAD_ID_CONTENT_URI.buildUpon();

        for (String recipient : recipients) {
            uriBuilder.appendQueryParameter("recipient", recipient);
        }

        Uri uri = uriBuilder.build();
        //if (DEBUG) Rlog.v(TAG, "getOrCreateThreadId uri: " + uri);

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                uri, ID_PROJECTION, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                } else {
                    Log.e(TAG, "getOrCreateThreadId returned no rows!");
                }
            } finally {
                cursor.close();
            }
        }

        Log.e(TAG, "getOrCreateThreadId failed with uri " + uri.toString());
        throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
    }

}
