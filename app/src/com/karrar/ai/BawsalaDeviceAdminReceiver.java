package com.karrar.ai;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Device Admin receiver with only the real, limited policies declared in res/xml/device_admin.xml:
 *  force-lock (lock the screen on command), limit-password, disable-camera. Android itself shows the
 *  activation screen and the user must tap "Activate" there — this class cannot self-grant anything.
 *  This API has no ability to read, enter or type into other apps; that is a different, separate
 *  Android permission (Accessibility Service) that this app deliberately never requests. */
public class BawsalaDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context ctx, Intent intent) {
        Toast.makeText(ctx, "بوصلة: تم تفعيل مشرف الجهاز", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context ctx, Intent intent) {
        Toast.makeText(ctx, "بوصلة: أُلغي مشرف الجهاز", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context ctx, Intent intent) {
        return "هل تريد إلغاء صلاحية مشرف الجهاز عن بوصلة؟";
    }
}
