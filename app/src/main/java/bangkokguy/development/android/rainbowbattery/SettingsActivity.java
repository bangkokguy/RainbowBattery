package bangkokguy.development.android.rainbowbattery;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.List;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity
implements AboutPreferenceFragment.OnFragmentInteractionListener {


    final static String TAG="SettingsActivity";
    final static boolean DEBUG = BuildConfig.BUILD_TYPE.equals("debug"); //true;

    static boolean firstRun = true;
    static int adb = 0;

    final static int OVERLAY_PERMISSION_REQ_CODE = 1234;

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if(DEBUG)Log.d(TAG,"instanceof RingtonePreference");
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    if(DEBUG)Log.e(TAG, stringValue);

                    Ringtone ringtone = RingtoneManager.getRingtone(
                             preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                if(DEBUG)Log.d(TAG, "my service is running");
                return true;
            }
        }
        if(DEBUG)Log.d(TAG, "my service is NOT running");
        return false;
    }

    void startMyService(Class<?> serviceClass) {
        if(!isMyServiceRunning(serviceClass)){
            startService(new Intent(this, Overlay.class)
                    .putExtra("showOverlay", true)
                    .putExtra("batteryEmptySoundPlayedCount", 0)
                    .putExtra("batteryFullSoundPlayedCount", 0));
        }
    }

    static Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        context = getApplicationContext();

        Log.d(TAG, "Build_Type:"+BuildConfig.BUILD_TYPE);
        if(DEBUG)Log.d(TAG, "OnCreate, First run "+Boolean.toString(firstRun));
        if(!firstRun)return; else firstRun=false;

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_notification, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_layout, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_sound, false);

//        sendBroadcast(new Intent("bangkokguy.development.android.intent.action.SERVICE_PING"));
//        getFragmentManager().beginTransaction()
//                .replace(android.R.id.content, new GeneralPreferenceFragment())
//                .commit();
        /*
         * it should be checked here, whether the service is already started or not
         * it should be started only when if it's not running, otherwise the sound counters will be
         * set to zero which is wrong;
         */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(DEBUG)Log.d(TAG, Boolean.toString(Settings.canDrawOverlays(this)));
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            } else startMyService(Overlay.class);
        } else startMyService(Overlay.class);

        PermissionUtil.checkPermission(this, READ_EXTERNAL_STORAGE, new PermissionUtil.PermissionAskListener() {
            @Override
            public void onPermissionAsk() {
                Toast.makeText(SettingsActivity.this, "Permission Ask.", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(
                        SettingsActivity.this,
                        new String[]{READ_EXTERNAL_STORAGE},
                        REQUEST_READ_EXTERNAL_STORAGE
                );
            }

            @Override
            public void onPermissionPreviouslyDenied() {
                if(DEBUG)Toast.makeText(SettingsActivity.this, "Permission Previously Disabled.", Toast.LENGTH_LONG).show();
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(getString(R.string.permission_needed))
                        .setMessage(getString(R.string.dialog_fire_missiles))
                        .setPositiveButton(R.string.fire, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // request permission again
                                ActivityCompat.requestPermissions(
                                        SettingsActivity.this,
                                        new String[]{READ_EXTERNAL_STORAGE},
                                        REQUEST_READ_EXTERNAL_STORAGE
                                );
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .show();
            }

            @Override
            public void onPermissionDisabled() {
                Toast.makeText(SettingsActivity.this, "Permission Disabled.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPermissionGranted() {
                if(DEBUG)Toast.makeText(SettingsActivity.this, "Permission Granted.", Toast.LENGTH_LONG).show();
            }
        });

    }

    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1234;

    @Override
    protected void onStart() {
        super.onStart();
        if(DEBUG)Log.d(TAG, "OnStart, Version:"+Integer.toString(Build.VERSION.SDK_INT));

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN)
            adb = Settings.Secure.getInt(this.getContentResolver(),
                    Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED , 0);
        else
            adb = Settings.Secure.getInt(this.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED , 0);

     }

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if(requestCode==OVERLAY_PERMISSION_REQ_CODE) {
            if(DEBUG)Log.d(TAG, "result "+Integer.toString(resultCode));
            if(resultCode==0)
                startMyService(Overlay.class);
        }
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        Log.d(TAG, "fragmentName="+fragmentName);
        Log.d(TAG, "fragmentClassName="+AboutPreferenceFragment.class.getName());

        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || LayoutPreferenceFragment.class.getName().equals(fragmentName)
                || SoundPreferenceFragment.class.getName().equals(fragmentName)
                || NotificationPreferenceFragment.class.getName().equals(fragmentName)
                || AboutPreferenceFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onPause() {
        super.onPause();
        if(DEBUG)Log.d(TAG, "onPause()");
    }

    @Override
    public void onResume() {
        super.onResume();
        if(DEBUG)Log.d(TAG, "onResume()");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(Uri uri) {
        Log.d(TAG, "onFragmentInteraction");
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);

            if(adb==1)
                findPreference("extra_text").setEnabled(true);

        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getFragmentManager().popBackStack();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NotificationPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_notification);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getFragmentManager().popBackStack();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SoundPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_sound);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines. No boolean values are bound.
            bindPreferenceSummaryToValue(findPreference("battery_full_sound"));
            bindPreferenceSummaryToValue(findPreference("battery_empty_sound"));
            bindPreferenceSummaryToValue(findPreference("repeat_battery_full_sound"));
            bindPreferenceSummaryToValue(findPreference("repeat_battery_empty_sound"));
            if (DEBUG) Log.d(TAG, "after bindPreferenceSummaryToValue");
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getFragmentManager().popBackStack();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class LayoutPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_layout);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines. No boolean values are bound.
            bindPreferenceSummaryToValue(findPreference("bar_thickness"));
            bindPreferenceSummaryToValue(findPreference("bar_position"));
            if(DEBUG)Log.d(TAG, "after bindPreferenceSummaryToValue");
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getFragmentManager().popBackStack();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
/*    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class AboutPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_about);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines. No boolean values are bound.
            //bindPreferenceSummaryToValue(findPreference("krrr"));
            //bindPreferenceSummaryToValue(findPreference("bar_position"));
            //if(DEBUG)Log.d(TAG, "after bindPreferenceSummaryToValue");
            Preference p = this.findPreference("krrr");
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getFragmentManager().popBackStack();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }*/
}
