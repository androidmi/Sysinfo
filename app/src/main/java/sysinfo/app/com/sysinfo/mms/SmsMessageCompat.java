/*
 * Copyright (C) 2012 Tapas Mobile Ltd.  All Rights Reserved.
 */

package sysinfo.app.com.sysinfo.mms;

import android.app.PendingIntent;
import android.os.Build.VERSION;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import sysinfo.app.com.sysinfo.MyCrashHandler;
import sysinfo.app.com.sysinfo.util.LogTag;

public class SmsMessageCompat {
    private static final String TAG = "SmsMessageCompat";

    private static boolean DEBUG = LogTag.DEBUG_LOG;

    public static final String FORMAT_3GPP = "3gpp";
    public static final String FORMAT_3GPP2 = "3gpp2";

    private static Class<?> sSmsMessageBaseCls;
    private static Constructor<SmsMessage> sSmsMessageCons;
    private static Class<?> sSmsMessageGSMCls;
    private static Method sSmsMessageGSMCreateFromPdu;
    private static Class<?> sSmsMessageCDMACls;
    private static Method sSmsMessageCDMACreateFromPdu;

    private static Method sSendMessageGTI9100ICS;

    static {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        try {
            sSmsMessageBaseCls = cl.loadClass("com.android.internal.telephony.SmsMessageBase");
        } catch (ClassNotFoundException e) {
            if (DEBUG)
                Log.e(TAG, "SmsMessageBase cls missing");
            sSmsMessageBaseCls = null;
            MyCrashHandler.dumpCrashToSD(e);
        }
        try {
            if (sSmsMessageBaseCls == null) {
                sSmsMessageCons = null;
            } else {
                sSmsMessageCons = SmsMessage.class.getDeclaredConstructor(sSmsMessageBaseCls);
                sSmsMessageCons.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            if (DEBUG)
                Log.e(TAG, "SmsMessage cons missing");
            sSmsMessageCons = null;
            MyCrashHandler.dumpCrashToSD(e);
        }
        try {
            sSmsMessageGSMCls =
                    cl.loadClass("com.android.internal.telephony.gsm.SmsMessage");
            sSmsMessageGSMCreateFromPdu =
                    sSmsMessageGSMCls.getDeclaredMethod("createFromPdu", byte[].class);
            sSmsMessageGSMCreateFromPdu.setAccessible(true);
        } catch (Exception e) {
            if (DEBUG)
                Log.e(TAG, "gsm.SmsMessage missing", e);
            sSmsMessageGSMCls = null;
            sSmsMessageGSMCreateFromPdu = null;
            MyCrashHandler.dumpCrashToSD(e);
        }
        try {
            sSmsMessageCDMACls =
                    cl.loadClass("com.android.internal.telephony.cdma.SmsMessage");
            sSmsMessageCDMACreateFromPdu =
                    sSmsMessageCDMACls.getDeclaredMethod("createFromPdu", byte[].class);
            sSmsMessageCDMACreateFromPdu.setAccessible(true);
        } catch (Exception e) {
            if (DEBUG)
                Log.e(TAG, "cdma.SmsMessage missing", e);
            sSmsMessageCDMACls = null;
            sSmsMessageCDMACreateFromPdu = null;
            MyCrashHandler.dumpCrashToSD(e);
        }
        try {
            Class<?>[] arrayOfClass = new Class[] { String.class, String.class, ArrayList.class,
                    ArrayList.class, ArrayList.class, Boolean.TYPE, Integer.TYPE, Integer.TYPE,
                    Integer.TYPE, };
            sSendMessageGTI9100ICS = SmsManager.class.getMethod("sendMultipartTextMessage", arrayOfClass);
        } catch (NoSuchMethodException localNoSuchMethodException) {
            sSendMessageGTI9100ICS = null;
            MyCrashHandler.dumpCrashToSD(localNoSuchMethodException);
        }
    }

    private static SmsMessage constructSmsMessage(Method mtd, byte[] pdu) {
        if ((sSmsMessageCons == null) || (mtd == null) || (pdu == null)) {
            return null;
        }
        try {
            Object smsbase;
            smsbase = mtd.invoke(null, pdu);
            if (smsbase == null) {
                return null;
            }
            return sSmsMessageCons.newInstance(smsbase);
        } catch (InvocationTargetException e) {
            if (DEBUG)
                Log.e(TAG, "constructSmsMessage", e);
            MyCrashHandler.dumpCrashToSD(e);
        } catch (Exception e) {
            if (DEBUG)
                e.printStackTrace();
                Log.e(TAG, "constructSmsMessage reflection", e);
            MyCrashHandler.dumpCrashToSD(e);
        }
        return null;
    }

    public static SmsMessage createFromPdu(byte[] pdu, String format) {
        // 防止恶意程序攻击
        if (pdu == null || pdu.length == 0) {
            return null;
        }
        SmsMessage ret = null;
        if (FORMAT_3GPP2.equals(format)) {
            // cdma
            ret = constructSmsMessage(sSmsMessageCDMACreateFromPdu, pdu);
            if (ret != null)
                return ret;
        } else if (FORMAT_3GPP.equals(format)) {
            // gsm
            ret = constructSmsMessage(sSmsMessageGSMCreateFromPdu, pdu);
            if (ret != null)
                return ret;
        } else {
            // no hint, maybe a dual card device?
            // try GSM
            ret = constructSmsMessage(sSmsMessageGSMCreateFromPdu, pdu);
            if (ret != null)
                return ret;
            // try CDMA
            ret = constructSmsMessage(sSmsMessageCDMACreateFromPdu, pdu);
            if (ret != null)
                return ret;
        }
        // Failed, try standard interface

        // if anything wrong, will be here
        return SmsMessage.createFromPdu(pdu);
    }

    /**
     * For fix a bug in I9100 4.0.3, it will send duplicate sms by 3rd party sms client
     *
     * @param number
     * @param body
     * @param sentIntent
     */
    public static void sendMessage(String number, String body, PendingIntent sentIntent) {
        SmsManager sm = SmsManager.getDefault();
        ArrayList<String> messages = sm.divideMessage(body);
        ArrayList<PendingIntent> sentIntents = null;
        if (sentIntent != null) {
            sentIntents = new ArrayList<PendingIntent>();
            sentIntents.add(sentIntent);
        }
        if (sendMessageForSamsungI9100(sm, number, null, messages, sentIntents)) {
            return;
        }
        sm.sendMultipartTextMessage(number, null, messages, sentIntents, null);
    }

    /**
     * For fix a bug in I9100 4.0.3, it will send duplicate sms by 3rd party sms client
     *
     * @param sm SmsManager
     * @param number
     * @param serviceCenter
     * @param messages
     * @param sentIntents
     * @return if send successfully.
     */
    public static boolean sendMessageForSamsungI9100(SmsManager sm, String number, String serviceCenter,
            ArrayList<String> messages, ArrayList<PendingIntent> sentIntents) {
        if (sSendMessageGTI9100ICS != null && VERSION.SDK_INT < 16) {
            try {
                if (DEBUG) Log.d(TAG, "send for 9100 ics");
                Method localMethod = sSendMessageGTI9100ICS;
                Object[] arrayOfObject = new Object[] {
                        number, serviceCenter, messages, sentIntents, null,
                        Boolean.FALSE, Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0),
                };
                localMethod.invoke(sm, arrayOfObject);
                return true;
            } catch (IllegalAccessException localIllegalAccessException) {
                // ignore this, will to the final
                MyCrashHandler.dumpCrashToSD(localIllegalAccessException);
            } catch (InvocationTargetException localInvocationTargetException) {
                // ignore this, will to the final
                MyCrashHandler.dumpCrashToSD(localInvocationTargetException);
            }
        }
        return false;
    }
}
