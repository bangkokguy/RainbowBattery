package bangkokguy.development.android.rainbowbattery;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;

/**
 * Created by bangkokguy on 1/2/17.
 *
 */
class PermissionUtil {
    /*
    * Check if version is marshmallow and above.
    * Used in deciding to ask runtime permission
    * */
    private static boolean shouldAskPermission() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
    }

    private static boolean shouldAskPermission(Context context, String permission){
        if (shouldAskPermission()) {
            int permissionResult = ActivityCompat.checkSelfPermission(context, permission);
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    static void checkPermission(Activity context, String permission, PermissionAskListener listener){

        //If permission is not granted
        if (shouldAskPermission(context, permission)){

            // If permission denied previously
            if (ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
                listener.onPermissionPreviouslyDenied(permission);
            } else {
                //Permission denied or first time requested
                if (PreferencesUtil.isFirstTimeAskingPermission(context, permission)) {
                    PreferencesUtil.firstTimeAskingPermission(context, permission, false);
                    listener.onPermissionAsk(permission);
                } else {
                    //Handle the feature without permission or ask user to manually allow permission
                    listener.onPermissionDisabled(permission);
                }
            }
        } else {
            listener.onPermissionGranted(permission);
        }
    }
    /**
     * Callback on various cases on checking permission
     *
     * 1.  Below M, runtime permission not needed. In that case onPermissionGranted() would be called.
     *     If permission is already granted, onPermissionGranted() would be called.
     *
     * 2.  Above M, if the permission is being asked first time onPermissionAsk() would be called.
     *
     * 3.  Above M, if the permission is previously asked but not granted, onPermissionPreviouslyDenied()
     *     would be called.
     *
     * 4.  Above M, if the permission is disabled by device policy or the user checked "Never ask again"
     *     check box on previous request permission, onPermissionDisabled() would be called.
     */
    interface PermissionAskListener {
        /*
                * Callback to ask permission
                * */
        void onPermissionAsk(String permission);
        /*
                * Callback on permission denied
                * */
        void onPermissionPreviouslyDenied(String permission);
        /*
                * Callback on permission "Never show again" checked and denied
                * */
        void onPermissionDisabled(String permission);
        /*
                * Callback on permission granted
                * */
        void onPermissionGranted(String permission);
    }
}