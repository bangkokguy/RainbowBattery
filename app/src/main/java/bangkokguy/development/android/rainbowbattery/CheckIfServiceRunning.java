package bangkokguy.development.android.rainbowbattery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CheckIfServiceRunning extends BroadcastReceiver {
    static final String TAG = "CheckIfServiceRunning";
    static final boolean DEBUG = BuildConfig.BUILD_TYPE.equals("debug"); //true;

    public CheckIfServiceRunning() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(DEBUG)Log.d(TAG, "onReceive");
    }
}
