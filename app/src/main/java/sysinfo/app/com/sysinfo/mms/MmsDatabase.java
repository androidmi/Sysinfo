package sysinfo.app.com.sysinfo.mms;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.util.ArrayList;

import sysinfo.app.com.sysinfo.Message;
import sysinfo.app.com.sysinfo.MyCrashHandler;
import sysinfo.app.com.sysinfo.util.LogTag;

public class MmsDatabase {


    public static class MessageStateColumns implements BaseColumns {
        public static final String STATE_TABLE = "message";
        public static final String MESSAAGE_ID = "msg_id";
        public static final String MESSAAGE_BODY = "msg_body";
        public static final String MESSAAGE_ADDRESS = "msg_address";
        public static final String MESSAAGE_STATE = "state";

        public static final int STATE_NONE = 0;
        public static final int STATE_DELIVERY = 1;
    }

    private static class MmsDatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "sms.db";
        private static final int DATABASE_VERSION = 1;

        private Context mContext;

        private MmsDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createMessageStateTable(db);
        }

        public void createMessageStateTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + MessageStateColumns.STATE_TABLE + " (" +
                    BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    MessageStateColumns.MESSAAGE_ID + " LONG," +
                    MessageStateColumns.MESSAAGE_BODY + " TEXT," +
                    MessageStateColumns.MESSAAGE_ADDRESS + " TEXT" +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }

     private static final boolean DEBUG = LogTag.DEBUG_LOG;
    private static final String TAG = "MmsDatabase";
    private static MmsDatabase sInstance;

    private MmsDatabaseHelper mDatabaseHelper = null;
    private Context mContext;

    private MmsDatabase(Context context) {
        this.mContext = context;
        mDatabaseHelper = new MmsDatabaseHelper(context);
    }

    private SQLiteDatabase getDatabase() throws Exception {
        return mDatabaseHelper.getWritableDatabase();
    }

    public synchronized static MmsDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MmsDatabase(context.getApplicationContext());
        }
        return sInstance;
    }

    public boolean addDeliveredId(Message msg) {
        ContentValues values = new ContentValues();
        values.put(MessageStateColumns.MESSAAGE_ID, msg.msgId);
        values.put(MessageStateColumns.MESSAAGE_BODY, msg.body);
        values.put(MessageStateColumns.MESSAAGE_ADDRESS, msg.number);
        long rowId = insert(MessageStateColumns.STATE_TABLE, values);
        return rowId > -1;
    }

    public ArrayList<Long> getArrayList() {
        Cursor cursor = query(MessageStateColumns.STATE_TABLE, new String[]{BaseColumns._ID,
                        MessageStateColumns.MESSAAGE_ID},
                null, null, null, null, null);
        if (cursor == null || !cursor.moveToFirst()) {
            return null;
        }
        ArrayList<Long> msgList = new ArrayList<>();
        while (cursor.moveToNext()) {
            long msgId = cursor.getLong(1);
            msgList.add(msgId);
        }
        return msgList;
    }

    private long insert(String table, ContentValues values) {
        try {
            return getDatabase().insert(table, null, values);
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
            return -1;
        }
    }

    private Cursor query(String table, String[] columns, String selection,
                         String[] selectionArgs, String groupBy, String having,
                         String orderBy) {
        try {
            return getDatabase().query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
            MyCrashHandler.dumpCrashToSD(e);
            return null;
        }
    }

    private int delete(String table, String whereClause, String[] whereArgs) {
        try {
            return getDatabase().delete(table, whereClause, whereArgs);
        } catch (Exception e) {
            if (DEBUG)e.printStackTrace();
            MyCrashHandler.dumpCrashToSD(e);
            return -1;
        }
    }

    private int update(String table,ContentValues values, String whereClause, String[] whereArgs) {
        try {
            return getDatabase().update(table, values, whereClause, whereArgs);
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
            MyCrashHandler.dumpCrashToSD(e);
            return -1;
        }
    }

    public static void closeCursor(Cursor cur) {
        if (cur != null) {
            try {
                cur.close();
                cur = null;
            } catch (Exception e) {
                MyCrashHandler.dumpCrashToSD(e);
                // ignore
            }
        }
    }
}
