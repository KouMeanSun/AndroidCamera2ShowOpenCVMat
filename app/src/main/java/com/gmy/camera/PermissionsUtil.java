package com.gmy.camera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * @author 高明阳 on 2021-07-08
 * @Description:
 * @Copyright © 2020 gaomingyang. All rights reserved.
 */
public class PermissionsUtil {
    public static boolean doRequestPermissions(Activity activity) {
        boolean hasAllPermission = true;
        if (Build.VERSION.SDK_INT > 22) {
            String[] permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, permissions, 1000);
                    hasAllPermission = false;
                    break;
                }
            }
        }

        return hasAllPermission;
    }
}
