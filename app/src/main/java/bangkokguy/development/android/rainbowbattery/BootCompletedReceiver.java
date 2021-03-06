package bangkokguy.development.android.rainbowbattery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import static android.content.Intent.ACTION_BOOT_COMPLETED;

public class BootCompletedReceiver extends BroadcastReceiver {

    final static String TAG = "BootCompletedReceiver";
    final static boolean DEBUG = BuildConfig.BUILD_TYPE.equals("debug"); //true;

    public BootCompletedReceiver() {
        super();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        if (intent != null) {
            final String action = intent.getAction();
            if (action.equals(ACTION_BOOT_COMPLETED)) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(context);
                boolean autoStart = prefs.getBoolean("start_on_boot", false);
                if (autoStart) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // startForegroundService added to avoid IllegalStatementException seen in google play console;
                        context.startForegroundService(new Intent(context, Overlay.class)
                                .putExtra("showOverlay", true));
                    } else {
                        context.startService(new Intent(context, Overlay.class)
                                .putExtra("showOverlay", true));
                    }
                    Log.i(TAG, "Service started");
                } else {
                    Log.i(TAG, "Auto start disabled");
                }
            } else {
                Log.e(TAG, "Unknown action:"+action);
            }
        }
    }
}
