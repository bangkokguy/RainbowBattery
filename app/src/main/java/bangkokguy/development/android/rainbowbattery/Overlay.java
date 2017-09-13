package bangkokguy.development.android.rainbowbattery;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import static android.content.Intent.ACTION_BATTERY_CHANGED;
import static android.content.Intent.ACTION_POWER_CONNECTED;
import static android.content.Intent.ACTION_POWER_DISCONNECTED;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.os.BatteryManager.BATTERY_PLUGGED_AC;
import static android.os.BatteryManager.BATTERY_PLUGGED_USB;
import static android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS;
import static android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER;
import static android.os.BatteryManager.BATTERY_STATUS_CHARGING;
import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static android.support.v4.app.NotificationCompat.FLAG_FOREGROUND_SERVICE;

/*
 * DONE:Multipane setup
 * DONE:Battery bar process animation color depends on bar color
 * DONE:Sound if charged
 * DONE:Sound if discharged
 * DONE:Sound optional via settings
 * DONE:Bar length updated when screen orientation changes
 * DONE:Refined Battery bar process marker animation
 * DONE:New Notification summary text
 * DONE:New Notification Title
 * DONE:Notification text gives text based info instead of status codes
 * DONE:Debug text switchable in settings
 * DONE:Debug text only selectable if development mode enabled
 * DONE:Launch Full/Empty sound notification after plug/unplug
 * DONE:Adjusted battery bar process marker length (depending on battery percent)
 * DONE:Density independent font size in settings activity
 * DONE:Redesigned Sound Settings
 * DONE:Redesigned Settings Activity screen flow
 * ---------------------------------------------
 * V6.0 Hank
 * DONE:Notification priority set to lowest
 * DONE:Sound picker picks now Notification sound instead of Ringtone
 * ---------------------------------------------
 * TODO:Make Full/Empty percent limit adjustable in settings
 * TODO:Make LED notification for low battery switchable in settings
 * DONE:Make it possible to position the bar on any side of the screen
 * DONE:Display "About" data in activity
 * TODO:With new install the initial battery bar parameters are different as defined in the settings -> bug
 *
 * Releases: Jesse, hank, marie, skyler, walter, gustavo
 */

/**---------------------------------------------------------------------------
 * Main Service to draw the battery line and set the led color
 */
public class Overlay extends Service {

    final static String TAG="Overlay";
    final static boolean DEBUG=BuildConfig.BUILD_TYPE.equals("debug"); //true;

    final static int ONMS=4095/*255*/;
    final static int OFFMS=0;
    final static int OPAQUE=0xff;
    final static int MAX_STROKE_WIDTH = 0x10;

    final static int TOP = 0;
    final static int BOTTOM = 1;
    final static int LEFT_TOP_DOWN = 2;
    final static int LEFT_DOWN_TOP = 3;
    final static int RIGHT_TOP_DOWN = 4;
    final static int RIGHT_DOWN_TOP = 5;

    NotificationManagerCompat nm;
    BatteryManager bm;
    WindowManager wm;

    public DrawView barView;
    ReceiveBroadcast receiveBroadcast;

    Display display;
    int screenWidth;
    int screenHeight;
    int barPosition;
    int barHeight;

    String  versionName = "";
    String  stopCode = "";

    boolean screenOn = true;
    boolean showOverlay;
    boolean stopService = false;
    boolean isBatteryCharging = false;
    boolean isFastCharging = false;

    int 	eHealth = 0;        //battery health
    int 	eIconSmall = -1;    //resource ID of the small battery icon
    int 	eLevel = -1;        //battery percentage
    int 	ePlugged = 5;       //0=battery... 5-no value present
    boolean ePresent = true;    //true if battery present
    int     eScale = -1;        //the maximum battery level
    int 	eStatus = 0;        //the current status constant
    String 	eTechnology = "";   //the technology of the current battery
    int     eTemperature = -1;  //the current battery temperature
    int 	eVoltage = -1;      //the current battery voltage level
    boolean	eLEDon = false;     //whether use led or not
    boolean eExtraText = false; //extra debug text in the notification

    SharedPreferences preferences;
    SharedPreferences sharedPref;

    Handler mHandler;
    MyRunnable myRunnable;

    public Overlay() {
    }

    /**---------------------------------------------------------------------------
     * Register the broadcast receiver for
     * - battery events
     * - screen on
     * - screen off
     * and then show the initial notification.
     * Possible improvement for API level 23 and above:
     *      this.registerReceiver(
     *           receiveBroadcast,
     *           new IntentFilter(ACTION_CHARGING));
     *
     *      this.registerReceiver(
     *           receiveBroadcast,
     *           new IntentFilter(ACTION_DISCHARGING));
     */
    @Override
    public void onCreate() {
        if(DEBUG)Log.d(TAG, "OnCreate()");

        mHandler = new Handler();
        myRunnable = new MyRunnable();
        myRunnable.setContext(this);

        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0)
                    .versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        nm = NotificationManagerCompat.from(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bm = (BatteryManager) this.getSystemService(Context.BATTERY_SERVICE);
        }

        receiveBroadcast = new ReceiveBroadcast();

        this.registerReceiver(
                receiveBroadcast,
                new IntentFilter(ACTION_BATTERY_CHANGED));

        this.registerReceiver(
                receiveBroadcast,
                new IntentFilter(ACTION_SCREEN_OFF));

        this.registerReceiver(
                receiveBroadcast,
                new IntentFilter(ACTION_SCREEN_ON));

        this.registerReceiver(
                receiveBroadcast,
                new IntentFilter(ACTION_POWER_DISCONNECTED));

        this.registerReceiver(
                receiveBroadcast,
                new IntentFilter(ACTION_POWER_CONNECTED));

        barView = initBarView(this);
        myRunnable.setBarView(barView);

        MyRunnable.OnFinishListener onFinishListener = new MyRunnable.OnFinishListener() {
            @Override
            public void onFinish() {
                if(myRunnable.isCanceled()) {
                    barView.setColor(argbLedColor(getBatteryPercent()));
                    barView.setLength(
                            ((barPosition==TOP||barPosition==BOTTOM) ? screenWidth : screenHeight)
                            //screenWidth
                                    *getBatteryPercent()/100);
                    barView.invalidate();
                }
            }
        };
        myRunnable.setOnFinishListener(onFinishListener);
    }

    /**---------------------------------------------------------------------------
     * Depending on the Battery percentage delivers the calculated LED color.
     * @param percent the battery percent (int)
     * @return the argb LED color (int)
     */
    public int argbLedColor (int percent) {
        int j = (percent/(100/6));
        int r, g, b;
        int grade=((percent%(100/6))*255/(100/6));

        switch (j) { // @formatter:off
            case 0: r = 255;        g = 0;              b = 255-grade;  break;//0-16 pink_to_red         255,0:255,0
            case 1: r = 255;        g = (grade/2);      b = 0;          break;//17-33 red_to_orange      255:0,255,0
            case 2: r = 255;        g = 128+(grade/2);  b = 0;          break;//34-50 orange_to_yellow   0,255,0:255
            case 3: r = 255-grade;  g = 255;            b = 0;          break;//51-66 yellow_to_green    0,255:0,255
            case 4: r = 0;          g = 255;            b = grade;      break;//67-83 green_to_cyan      0:255,0,255
            case 5: r = 0;          g = 255-grade;      b = 255;        break;//84-100 cyan_to_blue
            default:r = 200;        g = 200;            b = 200;        break;//gray if full
        } //@formatter:on

        return Color.argb(OPAQUE, r, g, b);
    }

    /**---------------------------------------------------------------------------
     * Retrieves the battery % from the os.
     * @return battery percentage (int)
     */
    int getBatteryPercent () {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else
            return eLevel;
    }

    static int BATTERY_EMPTY=15;
    static int BATTERY_FULL=96;

    boolean playSoundIfBatteryFull=false;
    int batteryFullSoundPlayedCount=0;
    int maxNumberOfBatteryFullSoundPlayed=1;

    boolean playSoundIfBatteryEmpty=false;
    int batteryEmptySoundPlayedCount=0;
    int maxNumberOfBatteryEmptySoundPlayed=1;

    /**---------------------------------------------------------------------------
     * Shows a sticky notification. This one will be changed every time the os invokes
     * the battery changed event listener. The LED color will be
     * set according to the battery percentage.
     */
    void showNotification() {
        showNotification("");
    }
    void showNotification(String extraMessage) {

        myRunnable.cancel();
        barView.setColor(argbLedColor(getBatteryPercent()));
        barView.setLength(
                ((barPosition==TOP||barPosition==BOTTOM) ? screenWidth : screenHeight)
                //screenWidth
                        *getBatteryPercent()/100);

        if(isBatteryCharging&&screenOn) {
            mHandler.removeCallbacks(myRunnable);
            myRunnable.start();
            mHandler.post(myRunnable);
        }

        barView.invalidate();

        String actionText = showOverlay ? "STOP" : "START";
        int icon =
                showOverlay ?
                        R.drawable.ic_stop_circle_outline_grey600_24dp :
                        R.drawable.ic_play_box_outline_grey600_24dp;

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

        if(!extraMessage.isEmpty())style.addLine("Extra:"+extraMessage);

        if(preferences.getBoolean("battery_percent", false))
            style.addLine(getString(R.string.battery_percent_n)+" "+Integer.toString(getBatteryPercent()));
        if(preferences.getBoolean("battery_health", false))
            style.addLine(getString(R.string.battery_health_n)+" "+
                    getResources().getStringArray(R.array.battery_health)[eHealth]);
        if(preferences.getBoolean("battery_temperature", false))
            style.addLine(getString(R.string.battery_temperature_n)+" " + Integer.toString(eTemperature/10) + " C°");
        if(preferences.getBoolean("battery_status", false))
            style.addLine(getString(R.string.battery_status_n)+" "+
                    getResources().getStringArray(R.array.battery_statuses)[eStatus]);
        if(preferences.getBoolean("battery_technology", false))
            style.addLine(getString(R.string.battery_technology_n)+" "+eTechnology);
        if(preferences.getBoolean("battery_power_source", false)) {
            style.addLine(getString(R.string.battery_power_source_n)+" " +
                    getResources().getStringArray(R.array.power_sources)[ePlugged]);
            }
        if(preferences.getBoolean("battery_voltage", false)) {
            style.addLine(getString(R.string.battery_voltage_n)+" "+
                    Integer.toString(eVoltage)+ " " +
                    getString(R.string.battery_voltage_n_uom));
        }

        eLEDon = preferences.getBoolean("led_on", false);

        int i;
        try {i = Integer.parseInt(
                preferences.getString("bar_thickness", getString(R.string.pref_bar_thickness_default)));}
            catch(NumberFormatException nfe) {i=8;}
        barView.setStrokeWidth(i);

        try {i = Integer.parseInt(
                preferences.getString("bar_position", getString(R.string.pref_bar_position_default)));}
        catch(NumberFormatException nfe) {i=0;}
        //barPosition=i;
        barView.setPosition(i);

        eExtraText = preferences.getBoolean("extra_text", false);
        String extra =
                (eExtraText ? (":"+stopCode+":") : "") +
                (isBatteryCharging ? "Battery is currently charging." : "Battery is discharging.") +
                " Version: "+
                versionName;
        style.setSummaryText(extra);

        NotificationCompat.Builder ncb = new NotificationCompat.Builder(this);
        ncb
                //.setDefaults(DEFAULT_LIGHTS)
                //.setPriority(Notification.PRIORITY_MIN)
                .setCategory(CATEGORY_SERVICE)
                .setContentIntent(PendingIntent.getActivity(
                        this,
                        1,
                        new Intent(this, SettingsActivity.class),
                        0
                    )
                )
                .setStyle(style)
                .setColor(argbLedColor(getBatteryPercent()))
                .setSmallIcon(R.drawable.ic_car_battery_white_48dp)
                .setContentTitle(getString(R.string.app_title))
                .setOngoing(true)
                .addAction(icon, actionText,
                        PendingIntent.getService(
                                this,
                                1,
                                new Intent(this, Overlay.class)
                                        .putExtra("showOverlay", !showOverlay)
                                        .putExtra("batteryEmptySoundPlayedCount", batteryEmptySoundPlayedCount)
                                        .putExtra("batteryFullSoundPlayedCount", batteryFullSoundPlayedCount),
                                PendingIntent.FLAG_CANCEL_CURRENT))

                .addAction(R.drawable.ic_power_grey600_24dp, "EXIT",
                        PendingIntent.getService(
                                this,
                                2,
                                new Intent(this, Overlay.class)
                                        .putExtra("STOP", true),
                                PendingIntent.FLAG_CANCEL_CURRENT));

        //The following part could be in "OnCreate()".
            //In that case the values should be changed whenever preferences had been changed.
            //But this is not a frequently called function, therefore easier implemented here.

        playSoundIfBatteryFull = preferences.getBoolean("play_battery_full_sound", false);
        playSoundIfBatteryEmpty = preferences.getBoolean("play_battery_empty_sound", false);
        maxNumberOfBatteryFullSoundPlayed = Integer.parseInt(preferences.getString("repeat_battery_full_sound", "1"));
        maxNumberOfBatteryEmptySoundPlayed = Integer.parseInt(preferences.getString("repeat_battery_empty_sound", "1"));
        //---

        if(DEBUG)Log.d(TAG, "Full Sound Counter ="+Integer.toString(batteryFullSoundPlayedCount));
        if(isBatteryCharging) {
            if (playSoundIfBatteryFull) {
                if (getBatteryPercent() >= BATTERY_FULL) {
                    if (batteryFullSoundPlayedCount++ < maxNumberOfBatteryFullSoundPlayed) {
                        Log.d(TAG, "Play battery full sound");
                        ncb.setSound(Uri.parse(preferences.getString(
                                "battery_full_sound",
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())));
                    }
                } else batteryFullSoundPlayedCount = 0;
            }
        } else {
            if(playSoundIfBatteryEmpty) {
                if(getBatteryPercent()<=BATTERY_EMPTY){
                    if(batteryEmptySoundPlayedCount++ < maxNumberOfBatteryEmptySoundPlayed) {
                        ncb.setSound(Uri.parse(preferences.getString(
                                "battery_empty_sound",
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())));
                    }
                } else batteryEmptySoundPlayedCount = 0;
            }
        }

        if (eLEDon)
            if((isBatteryCharging) || (getBatteryPercent()<=15)){
                if(DEBUG)Log.d(TAG,"Battery Charging or low");
                ncb.setLights(argbLedColor(getBatteryPercent()), ONMS, OFFMS+1);
            }
            /*else {
                if(DEBUG)Log.d(TAG,"Battery NOT Charging and not low");
                //turn on "black" LED (this is the only possibility to turn off led in LG G5
                //ncb.setLights(Color.argb(255,0,0,0), 255, 0);
                ncb.setDefaults(DEFAULT_LIGHTS);
            }*/

        if(DEBUG)Log.d(TAG, "Battery Percent="+Integer.toString(getBatteryPercent())
                +" Battery Color="+Integer.toString(argbLedColor(getBatteryPercent())));
        if(DEBUG)Log.d(TAG, "Flags=" + Integer.toString(setLights(argbLedColor(getBatteryPercent()), ONMS, OFFMS+1)));

        Notification noti = ncb.build();
        int n_id = 42;
        if(preferences.getBoolean("suppress_notification", false))
            n_id=0; else n_id=42;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            /*Notification noti = ncb.build();*/
            startForeground(n_id, noti);
        }
        else {
            //noti.flags = noti.flags | FLAG_FOREGROUND_SERVICE | FLAG_SHOW_LIGHTS;
            //noti.ledARGB = argbLedColor(getBatteryPercent());
            //noti.ledOffMS = 0;
            //noti.ledOnMS = 0;
//            nm.notify(1, noti/*ncb.build()*/);
            startForeground(n_id, noti);
        }
    }

    public int setLights(/*@ColorInt*/ int argb, int onMs, int offMs) {
        Notification mNotification = new Notification();
        mNotification.ledARGB = argb;
        mNotification.ledOnMS = onMs;
        mNotification.ledOffMS = offMs;
        mNotification.flags=FLAG_FOREGROUND_SERVICE;
        boolean showLights = mNotification.ledOnMS != 0 && mNotification.ledOffMS != 0;
        mNotification.flags = (mNotification.flags & ~Notification.FLAG_SHOW_LIGHTS) |
            (showLights ? Notification.FLAG_SHOW_LIGHTS : 0);
                   return mNotification.flags;
    }

    /**
     * ---------------------------------------------------------------------------
     * A {@link View} which is extended with the overlay parameters and overlay procedures
     */
    public class DrawView extends View {
        Paint paint, p;
        int barLength;
        int barPosition;

        public DrawView(Context context, int argb, int length, int barPosition) {
            this(context, argb, length, barPosition, barHeight);
        }

        public DrawView(Context context, int argb, int barLength, int barPosition, int strokeWidth) {
            super(context);
            paint = new Paint();
            p = new Paint();

            paint.setStyle(Paint.Style.FILL);
            paint.setStyle(Paint.Style.STROKE);
            p.setStyle(Paint.Style.FILL);
            p.setStyle(Paint.Style.STROKE);

            setColor(argb);
            setStrokeWidth(strokeWidth);
            setLength(barLength);
            setPosition(barPosition);
            setBackgroundColor(Color.TRANSPARENT);
        }

        public void setColor(int argb) {
            paint.setColor(argb);
            p.setColor((0xFFFFFF - argb) | 0xFF000000);
        }

        public void setLength(int barLength) {
            Log.d(TAG, "barlength="+ Integer.toString(barLength));
                    this.barLength=barLength; }

        public void setPosition(int barPosition) { this.barPosition=barPosition; }

        public void setStrokeWidth(int strokeWidth){
            paint.setStrokeWidth(strokeWidth);
            p.setStrokeWidth(strokeWidth);
        }

        static final int LEN = 64;
        static final int STEP = 32;
        int from = STEP * -1;
        int to, len, step;

        @Override
        public void onDraw(Canvas canvas) {

            boolean horizontal=(barPosition==TOP||barPosition==BOTTOM);
            boolean downTop=(barPosition==LEFT_DOWN_TOP||barPosition==RIGHT_DOWN_TOP);
            //Log.d(TAG, "screenHeight="+Integer.toString(screenHeight));

            if (horizontal) canvas.drawLine(0, 0, barLength, 0, paint);
            else {
                canvas.rotate(90); //rotate from portrait to landscape...
                canvas.drawLine(0, 0, barLength, 0, paint);
                if (downTop) { //...and flip when necessary
                    setPivotX(0);
                    setPivotY(screenHeight / 2);
                    setRotation(180);
                }
            }

            if (isBatteryCharging) {
                len = LEN;
                step = STEP;
                if(LEN >= barLength / 2) {
                    len = barLength / 2;
                    step = len / 2;
                }
                if(len == 0) {
                    len = 1;
                    step = 1;
                }
                from = from + step/*STEP*/;
                to = from + len/*LEN*/;
                if(from>barLength){
                    from = (STEP * -1) + (barLength % STEP) + STEP;
                    to = from + len/*LEN*/;
                }
                if(to>barLength){
                    canvas.drawLine(0, 0, to-barLength, 0, p);
                    to=barLength;
                }
                canvas.drawLine(from, 0, to, 0, p);
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------
     * Broadcast receiver for all broadcasts
     */
    public class ReceiveBroadcast extends BroadcastReceiver {

        final static String TAG="ReceiveBroadcast";

        /**
         * above api level 23 - case ACTION_CHARGING: Log.d(TAG,"charger plugged"); isBatteryCharging=true; break;
         * above api level 23 - case ACTION_DISCHARGING: Log.d(TAG,"charger unplugged"); isBatteryCharging=false; break;
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if(DEBUG)Log.d(TAG,"intent: "+intent.toString()+"intent extraInteger:"+Integer.toString(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)));

            switch (intent.getAction()) { // @formatter:off
                case ACTION_SCREEN_OFF: screenOn = false; if(DEBUG)Log.d(TAG,"case screen off"); showNotification(); break;
                case ACTION_SCREEN_ON: screenOn = true; if(DEBUG)Log.d(TAG,"case screen on"); showNotification(); break;
                case ACTION_BATTERY_CHANGED: if(DEBUG)Log.d(TAG,"case battery changed");
                    if(DEBUG)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Log.d(TAG, "BATTERY_PROPERTY_CURRENT_NOW="+Integer.toString(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)));
                            Log.d(TAG, "BATTERY_PROPERTY_CURRENT_AVERAGE="+Integer.toString(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)));
                            Log.d(TAG, "BATTERY_PROPERTY_CHARGE_COUNTER="+Integer.toString(BATTERY_PROPERTY_CHARGE_COUNTER));
                        }
                //get extra info from intent
                    eHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
                    eIconSmall = intent.getIntExtra(BatteryManager.EXTRA_ICON_SMALL, -1);
                    eLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    ePlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 5);
                    ePresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
                    eScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    eStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                    eTechnology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                    eTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                    eVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                //prepare global variables
                    isBatteryCharging = isBatteryCharging();
                    isFastCharging=false; //(ePlugged==BATTERY_STATUS_UNKNOWN);
                //at last show the notification with the actual data
                    showNotification();
                    break;
                case ACTION_POWER_CONNECTED:
                    batteryFullSoundPlayedCount = 0; //To force notification sound after charger plugged
                    showNotification();
                    break;
                case ACTION_POWER_DISCONNECTED:
                    batteryEmptySoundPlayedCount = 0; //To force notification sound after charger unplugged
                    showNotification();
                    break;
                default: if(DEBUG)Log.d(TAG,"case default"); break;
            } // @formatter:on
        }
    }

    boolean isBatteryCharging() {
        //isBatteryCharging=(ePlugged==BATTERY_STATUS_CHARGING||ePlugged==BATTERY_STATUS_UNKNOWN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return ePlugged==BATTERY_PLUGGED_AC
                    ||ePlugged==BATTERY_PLUGGED_USB
                    ||ePlugged==BATTERY_PLUGGED_WIRELESS;}
        else return eStatus == BATTERY_STATUS_CHARGING;
    }

     /**---------------------------------------------------------------------------
     * Prepare the overlay view
     * @param context the application context (Context)
     * @return the overlay view (DrawView)
     */
    private DrawView initBarView(Context context) {
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        int i;
        try {i = Integer.parseInt(
                preferences.getString("bar_thickness", getString(R.string.pref_bar_thickness_default)));}
        catch(NumberFormatException nfe) {i=8;}
        barHeight=i;
        if(DEBUG)Log.d(TAG, "barHeight=" + Integer.toString(barHeight));

        try {i = Integer.parseInt(
                preferences.getString("bar_position", getString(R.string.pref_bar_position_default)));}
        catch(NumberFormatException nfe) {i=0;}
        barPosition=i;
        if(DEBUG)Log.d(TAG, "barPosition="+barPosition);

        boolean horizontal=(barPosition==TOP||barPosition==BOTTOM);

        WindowManager.LayoutParams params = new
                WindowManager.LayoutParams (
                        (horizontal ? screenWidth    : (barHeight / 2)), //width
                        (horizontal ? (barHeight / 2)  : screenHeight),  //height
                        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, //TYPE_SYSTEM_ALERT
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, //FLAG_WATCH_OUTSIDE_TOUCH,
                        PixelFormat./*OPAQUE*/TRANSPARENT
                );

        if(barPosition==TOP)
            params.gravity = Gravity.TOP;
        else
            if(barPosition==BOTTOM)
                params.gravity = Gravity.BOTTOM;
            else
                if(barPosition==LEFT_DOWN_TOP||barPosition==LEFT_TOP_DOWN)
                    params.gravity=Gravity.LEFT;
                else
                    params.gravity=Gravity.RIGHT;

        if(barView!=null)wm.removeViewImmediate(barView);
        DrawView barView =
                new DrawView(
                        this,
                        argbLedColor(getBatteryPercent()),
                        ((barPosition==TOP||barPosition==BOTTOM) ? screenWidth : screenHeight),
                        barPosition);
        try {
            wm.addView(barView, params);
        } catch (java.lang.SecurityException e)  {
            //no overlay permission->forcefully end the service
            stopSelf();
        }

        return barView;

    }

    /**---------------------------------------------------------------------------
     * Callback if the already started service is called from outside.
     * @param intent the invoking intent (Intent)
     * @param flags as defined in the android documentation (int)
     * @param startId as defined in the android documentation (int)
     * @return the Service type as described in the android documentation (int)
     */
    @SuppressLint("CommitPrefEdits")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(DEBUG)Log.d(TAG,"OnStartCommand");

        if (intent==null) {
            if(DEBUG)Log.d(TAG, "null intent in StartCommand");
            showOverlay = sharedPref.getBoolean("savedInstanceOverlay", false);
            batteryEmptySoundPlayedCount = sharedPref.getInt("savedInstanceEmptySoundPlayedCount",0);
            batteryFullSoundPlayedCount = sharedPref.getInt("savedInstanceFullSoundPlayedCount",0);
            if(DEBUG)Log.d(TAG, "restore savedInstanceOverlay:"+Boolean.toString(showOverlay));
        }
        else {
            showOverlay=intent.getBooleanExtra("showOverlay", false);
            batteryEmptySoundPlayedCount = intent.getIntExtra("savedInstanceEmptySoundPlayedCount",0);
            batteryFullSoundPlayedCount = intent.getIntExtra("savedInstanceFullSoundPlayedCount",0);
            stopService=intent.getBooleanExtra("STOP", false);
            if(DEBUG)Log.d(TAG, "save savedInstanceOverlay:"+Boolean.toString(showOverlay));
            sharedPref.edit()
                    .putBoolean("savedInstanceOverlay", showOverlay)
                    .apply();
        }

        stopCode = sharedPref.getString("stop_code", "");
        //to prevent misleading information, clear the stop code
        sharedPref.edit().putString("stop_code","") .commit();

        if(stopService)stopSelf();

        if (showOverlay)barView.setVisibility(View.VISIBLE);
        else barView.setVisibility(View.INVISIBLE);

        showNotification();

        return START_STICKY;
    }

    /**---------------------------------------------------------------------------
     * not used here
     * @param intent the invoking intent (Intent)
     * @return the binder object
     */
    @Override
    public IBinder onBind(Intent intent) {
        // DONE: Return the communication channel to the service. --- not used here
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(DEBUG)Log.d(TAG,"destroyed");
        unregisterReceiver(receiveBroadcast);
        nm.cancelAll();
        sharedPref.edit()
            .putString("stop_code", "destroyed")
            .putInt("savedInstanceEmptySoundPlayedCount", batteryEmptySoundPlayedCount)
            .putInt("savedInstanceFullSoundPlayedCount", batteryFullSoundPlayedCount)
            .apply();
        mHandler = null;
        myRunnable = null;

    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        sharedPref.edit()
                .putString("stop_code", "low memory")
                .putInt("savedInstanceEmptySoundPlayedCount", batteryEmptySoundPlayedCount)
                .putInt("savedInstanceFullSoundPlayedCount", batteryFullSoundPlayedCount)
                .apply();

    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        sharedPref.edit()
                .putString("stop_code", "task removed")
                .putInt("savedInstanceEmptySoundPlayedCount", batteryEmptySoundPlayedCount)
                .putInt("savedInstanceFullSoundPlayedCount", batteryFullSoundPlayedCount)
                .apply();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(DEBUG)Log.d(TAG, Integer.toString(newConfig.orientation) + ":::" +newConfig.toString());

        //DrawView svbarView = barView;
        barView = initBarView(this);
        //wm.removeView(svbarView);
        myRunnable.setBarView(barView);

        showNotification();

        sharedPref.edit()
            .putString("stop_code", "config changed")
            .putInt("savedInstanceEmptySoundPlayedCount", batteryEmptySoundPlayedCount)
            .putInt("savedInstanceFullSoundPlayedCount", batteryFullSoundPlayedCount)
            .apply();
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

    private SharedPreferences.OnSharedPreferenceChangeListener sBindPreferenceSummaryToValueListener =
    new SharedPreferences.OnSharedPreferenceChangeListener() {
        final static String TAG = "OnSharedPrefChgListener";
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            if(DEBUG)Log.d(TAG, "Preference:"+sharedPreferences.toString()+" Value:"+s);
            onConfigurationChanged(new Configuration());
            if(isMyServiceRunning(Overlay.class))showNotification();
        }
    };
}
