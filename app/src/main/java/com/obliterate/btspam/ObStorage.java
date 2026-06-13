package com.obliterate.btspam;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

final class ObStorage {
    private ObStorage() {}

    static boolean hasLegacyWritePermission(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        return activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    static void requestLegacyWritePermission(Activity activity, int requestCode) {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        }
    }
}
