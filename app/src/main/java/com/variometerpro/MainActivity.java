package com.variometerpro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;

public class MainActivity extends Activity {

    private final static String DEGREE = "\u00b0";

    protected static final int REQUEST_PRIORITY_LOW_POWER = 1;
    protected static final int REQUEST_PRIORITY_BALANCED_POWER_ACCURACY = 2;
    protected static final int REQUEST_PRIORITY_HIGH_ACCURACY = 3;

    Criteria criteria = null;

    private int gpsPower = REQUEST_PRIORITY_HIGH_ACCURACY;

    private static final double KF_VAR_ACCEL = 0.0075;  // Variance of pressure acceleration noise input.
    private static final double KF_VAR_MEASUREMENT = 0.05;  // Variance of pressure measurement noise.

    private final static int REQUEST_LOCATION = 1;
    private final static int MY_PERMISSIONS_REQUEST_READ_STORAGE = 2;

    private boolean isGPSEnabled = false, isNetworkEnabled = false;
    private boolean loginLW = false, livetrackenabled = false, error = false, getvalues = false;
    private boolean getTakeoff = false, logging = false, gps = false, hasWind = false;
    private boolean barometer = false, logfooter = false, logheader = false, gpsfix = false;

    private Location mobileLocation = null;
    private Location lastLocation = null;

    private String gpsProvider = "";
    private String username, password, serverUrl, errorinfo, wingmodel = "No Model";
    private String pilotname, glidermodel, glidercertf, civlid, logFileName = null;
    private String formattedDate;

    private int vechiletype = 1, LWcount = 0, type = 0, gpsBearing = 0;
    private int GPS_TIMEUPDATE = 1000; // update gps period
    private int GPS_DISTANCEUPDATE = 0; // update gps every 1m
    private int logTime = 1000, graphspeed = 1, trckCount = 0, soundtype = 2;

    private double[] wind;
    private double[] windError;
    private double[][] headingArray;
    private double gpsAccuracy = 0, lastAltitude = 0, baroAltitude = 0, damping = 25, avgvario = 0, currentLatitude = 0, currentLongitude = 0;
    private double lastLatitude = 0, lastLongitude = 0, dblTakeoffLatitude = 0, dblTakeoffLongitude = 0, gpsAltitude = 0, gpsSpeed = 0, sinkalarm = 1;
    private double slp_inHg_ = 29.92, pressure_hPa_ = 1013.0912, d_temperature = 0;
    private double last_measurement_time, last_log_time;

    private long gpsTime;

    private TextView Live, Utm, Temp, AltitudeBaro, GpsSpeed, VertSpeed, Gpsfix;
    private TextView Latitude, Longitude, Wind, Distancetotakeoff, AltitudeGps;

    private ProgressBar climbProgress, sinkProgress;
    private Button exit, altinc, altdec, mute, volumedec, volumeinc, btn_settings;

    private static Context basecontext;
    private static WindCalculator windCalculator;
    private static PositionWriter liveWriter;
    private Compass compass, needle;
    private ImageView compassImage;
    private ImageView needleImage;
    private AudioManager amanager = null;
    private SensorEventListener mPSensorListener, mTSensorListener;
    private SensorManager mSensorManager;
    private Sensor mPSensor, mTSensor;
    private LocationManager locManager = null;
    private LocationListener locListener = null;
    private GraphView mGraphView;
    private KalmanFilter pressureFilter;
    private KalmanFilter altitudeFilter;
    private BeepThread beeps = null;
    private PowerManager.WakeLock wl;

    Conversion Converter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        wl.acquire();

        PackageManager PM = this.getPackageManager();
        gps = PM.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        barometer = PM.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER);
        AppLog.logString("AppLog barometer: " + barometer + " gps " + gps);

        Latitude = (TextView) findViewById(R.id.Latitudetxt);
        Longitude = (TextView) findViewById(R.id.Longitudetxt);
        AltitudeBaro = (TextView) findViewById(R.id.AltitudeBaro);
        AltitudeGps = (TextView) findViewById(R.id.AltitudeGps);
        GpsSpeed = (TextView) findViewById(R.id.Speed);
        VertSpeed = (TextView) findViewById(R.id.Vert_Speed);
        Gpsfix = (TextView) findViewById(R.id.Gpsfix);
        Distancetotakeoff = (TextView) findViewById(R.id.Distancetakeoff);
        Wind = (TextView) findViewById(R.id.Wind_Speed);
        Temp = (TextView) findViewById(R.id.Temperature);
        Live = (TextView) findViewById(R.id.TextLive);
        Utm = (TextView) findViewById(R.id.TextUtm);
        altinc = (Button) findViewById(R.id.altinc);
        altdec = (Button) findViewById(R.id.altdec);
        volumeinc = (Button) findViewById(R.id.volumeinc);
        volumedec = (Button) findViewById(R.id.volumedec);
        exit = (Button) findViewById(R.id.exit);
        mute = (Button) findViewById(R.id.mute);
        btn_settings = (Button) findViewById(R.id.btn_settings);
        compassImage = (ImageView) findViewById(R.id.compass_rose);
        needleImage = (ImageView) findViewById(R.id.compass_arrow);
        mGraphView = (GraphView) findViewById(R.id.graph);

        climbProgress = (ProgressBar) findViewById(R.id.climb_progressbar);
        sinkProgress = (ProgressBar) findViewById(R.id.sink_progressbar);
        climbProgress.setMax(100);
        sinkProgress.setMax(100);

        compass = new Compass(compassImage);
        needle = new Compass(needleImage);
        amanager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        Converter = new Conversion();
        windCalculator = new WindCalculator(16, 0.3, 300);
        wind = new double[3];

        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss");
        formattedDate = df.format(c.getTime());
        logFileName = "FlightLog_" + formattedDate.replace(" ", "_");

        SimpleDateFormat dfdetail = new SimpleDateFormat("ddMMyy");
        formattedDate = dfdetail.format(c.getTime());

        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                exit();
            }
        });
        Utm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
               setClipboard(basecontext,Utm.getText().toString());
               flashMessage("Utm coordinate (" + Utm.getText().toString() + ") copied to clipboard.");
            }
        });

        if (beeps == null)
            beeps = new BeepThread(MainActivity.this);
        if (beeps != null)
            beeps.start(getApplicationContext(), soundtype, sinkalarm);

        beeps.beepON(true);

        mute.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                try {
                    if (beeps != null) {
                        if (beeps.getBeepStatus()) {
                            beeps.beepON(false);
                            mute.setTextColor(Color.YELLOW);

                        } else {
                            beeps.beepON(true);
                            mute.setTextColor(Color.WHITE);
                        }
                    }
                } catch (Exception e) {
                }
            }
        });

        volumeinc.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                amanager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);

            }
        });
        volumedec.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                amanager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);

            }
        });

        btn_settings.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Intent i = new Intent(getApplicationContext(), Prefs.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            }
        });


        altinc.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                long slp_inHg_long = Math.round(100.0 * slp_inHg_);
                if (slp_inHg_long < 3100) ++slp_inHg_long;
                slp_inHg_ = slp_inHg_long / 100.0;
            }
        });
        altdec.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                long slp_inHg_long = Math.round(100.0 * slp_inHg_);
                if (slp_inHg_long > 2810) --slp_inHg_long;
                slp_inHg_ = slp_inHg_long / 100.0;
            }
        });

        if (!gps) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder
                    .setMessage("GPS is not supported on this device!")
                    .setCancelable(true)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {
                                    exit();
                                }
                            });
            AlertDialog alert = alertDialogBuilder.create();
            alert.show();
        }
        else
        {
            getPreferences();

            if(locManager != null && locListener != null)
            {
                locManager.removeUpdates((LocationListener) locListener);
                locManager = null;
            }

            criteria = new Criteria();

            if(gpsPower == REQUEST_PRIORITY_HIGH_ACCURACY)
                criteria.setPowerRequirement(Criteria.POWER_HIGH);
            else if(gpsPower == REQUEST_PRIORITY_BALANCED_POWER_ACCURACY)
                criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
            else if(gpsPower == REQUEST_PRIORITY_LOW_POWER)
                criteria.setPowerRequirement(Criteria.POWER_LOW);

            criteria.setAltitudeRequired(false);
            criteria.setBearingRequired(false);
            criteria.setCostAllowed(false);

            locManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            isGPSEnabled = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnabled = locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (barometer) {
                startvario();
            }

            StoragePermissionCheck();

            if(GPSPremissionCheck() && isGPSEnabled)
            {
                GetCurrentLocation();
            }
            else
            {
                checkLocationProviders();
            }
        }

    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wl.release();
    }

    @Override
    public void onPause() {
        super.onPause();  // Always call the superclass method first

    }

    @Override
    protected void onResume() {
        super.onResume();
        wl.acquire();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getPreferences();

        if (barometer) {
            startvario();
        }

        if(locManager != null && locListener != null)
        {
            locManager.removeUpdates((LocationListener) locListener);
            locManager = null;
        }

        criteria = new Criteria();

        if(gpsPower == REQUEST_PRIORITY_HIGH_ACCURACY) {
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            flashMessage("Gps Power : POWER_HIGH");
        }
        else if(gpsPower == REQUEST_PRIORITY_BALANCED_POWER_ACCURACY) {
            criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
            flashMessage("Gps Power : POWER_MEDIUM");
        }
        else if(gpsPower == REQUEST_PRIORITY_LOW_POWER) {
            criteria.setPowerRequirement(Criteria.POWER_LOW);
            flashMessage("Gps Power : POWER_LOW");
        }

        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(false);

        locManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        isGPSEnabled = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        isNetworkEnabled = locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        GetCurrentLocation();
    }

    private void getPreferences()
    {
        if (!getvalues) {
            getvalues();
            getvalues = true;
        }

        basecontext = getBaseContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        pilotname = preferences.getString("pilotname", "n/a");
        glidermodel = preferences.getString("glidermodel", "n/a");
        wingmodel = glidermodel;
        glidercertf = preferences.getString("glidercertf", "n/a");
        civlid = preferences.getString("civlid", "n/a");

        livetrackenabled = preferences.getBoolean("livetrackenabled", false);
        String soundfreqstr = preferences.getString("soundfreq", "2");
        soundtype = Integer.parseInt(soundfreqstr);
        String logtimestr = preferences.getString("log_updates_interval", "3000");
        logTime = Integer.parseInt(logtimestr);
        String graphspeedstr = preferences.getString("graphspeed_interval", "1");
        graphspeed = Integer.parseInt(graphspeedstr);
        String sinkalarmstr = preferences.getString("sink_alarm", "1.5");
        sinkalarm = Double.parseDouble(sinkalarmstr);
        String dumpLevelStr = preferences.getString("dumpLevel", "25");
        damping = Integer.parseInt(dumpLevelStr);

        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
        String formattedTime = df.format(c.getTime());
        Gpsfix.setText(formattedTime + "  Log: " + trckCount);

        username = preferences.getString("liveusername", "").trim();
        password = preferences.getString("livepassword", "").trim();
        // serverUrl="http://test.livetrack24.com";
        serverUrl = "http://www.livetrack24.com/";
        if (loginLW && !livetrackenabled) {
            setLivePos emitPos = new setLivePos();
            emitPos.execute(3);
        }

        String gpspowerstr = preferences.getString("gps_power", "3");
        gpsPower = (int) Integer.parseInt(gpspowerstr);
    }

    private void setClipboard(Context context, String text) {
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
        } else {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", text);
            clipboard.setPrimaryClip(clip);
        }
    }

    private void checkLocationProviders()
    {
        if (!isGPSEnabled) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Gps providers is not available. Enable GPS ?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(intent, REQUEST_LOCATION);
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //finish();
                        }
                    }).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != -1) {
            switch (requestCode) {
                case REQUEST_LOCATION:

                    isGPSEnabled = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    AppLog.logString("AppLog isGPSEnabled: " + isGPSEnabled);

                    if(isGPSEnabled)
                    {
                        GetCurrentLocation();
                    }

                    super.onResume();
                    //super.finish();
                    break;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Handle the back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            exit();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {

        return false;
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void exit() {

        if (logging) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
            alertDialogBuilder
                    .setMessage("Are you sure you want exit?")
                    .setCancelable(true)
                    .setPositiveButton("YES",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {
                                    preparelogfooter();
                                    stopdevices();
                                    savevalues();

                                    checkLog();

                                    if (livetrackenabled && loginLW) {
                                        setLivePos emitPos = new setLivePos();
                                        emitPos.execute(3);
                                    } else {
                                        finish();
                                        System.exit(0);
                                        Process.killProcess(Process.myPid());
                                    }

                                }
                            })
                    .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //  Action for 'NO' Button
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = alertDialogBuilder.create();
            alert.show();
        } else {
            stopdevices();
            savevalues();

            checkLog();

            finish();
            System.exit(0);
            Process.killProcess(Process.myPid());
        }
    }

    private void setGps(Location loc)
    {
        mobileLocation = loc;

        if (mobileLocation != null) {

            final double curr_log_time = SystemClock.elapsedRealtime();
            final double dt = curr_log_time - last_log_time;

            gpsAccuracy = mobileLocation.getAccuracy();
            currentLatitude = mobileLocation.getLatitude();
            currentLongitude = mobileLocation.getLongitude();
            gpsAltitude = mobileLocation.getAltitude();
            gpsSpeed = mobileLocation.getSpeed() * 3600 / 1000;
            gpsTime = mobileLocation.getTime();
            gpsBearing = (int) mobileLocation.getBearing();

            if (!barometer)
            {
                setvario(gpsAltitude);
            }

            if(logging && dt >= logTime)
            {
                setigcfile();
                last_log_time = curr_log_time;
            }

            if (!getTakeoff) {

                dblTakeoffLatitude = currentLatitude;
                dblTakeoffLongitude = currentLongitude;
                getTakeoff = true;

                distancetotakeoff(dblTakeoffLatitude, dblTakeoffLongitude);

                if (!logging)
                {
                    preparelogheader();
                    logging = true;
                    File root = new File(Environment.getExternalStorageDirectory(), "VarioLog");
                    flashMessage("IGC Log File path\n" + root.toString());
                }
            }

            compass.rotate(gpsBearing);
            AltitudeGps.setText(String.format("%.0f m", gpsAltitude));
            Latitude.setText(Converter.ConvertDecimalToDegMinSec(currentLatitude) + " " + Converter.getHemisphereLat(currentLatitude));
            Longitude.setText(Converter.ConvertDecimalToDegMinSec(currentLongitude) + " " + Converter.getHemisphereLon(currentLongitude));
            Utm.setText(Converter.latLon2UTM(currentLatitude,currentLongitude));

            distancetotakeoff(dblTakeoffLatitude, dblTakeoffLongitude);

            GpsSpeed.setText(String.format("%.0f km", gpsSpeed));

            Date date = new Date(gpsTime);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String igcgpstime = sdf.format(date);
            if (gpsfix)
                Gpsfix.setText(igcgpstime + "  " + gpsProvider +" fixed" + "  Log: " + trckCount);

            //new wind calculation
            if(windCalculator != null)
            {
                windCalculator.addSpeedVector(mobileLocation.getBearing(), mobileLocation.getSpeed() * 3600 / 1000, mobileLocation.getTime() / 1000.0);
                headingArray = windCalculator.getPoints();
                if (headingArray.length > 2) {
                    wind = FitCircle.taubinNewton(headingArray);
                    windError = FitCircle.getErrors(headingArray, wind);
                    hasWind = true;
                } else {
                    hasWind = false;
                }
                if (hasWind) {
                    double windspeed = getWindSpeed();
                    if (!Double.isNaN(windspeed) && !Double.isInfinite(windspeed)) {

                        float windBearing = (float) getWindDirection() - gpsBearing;
                        float H = windBearing;
                        float M = gpsBearing;

                        if (M > H) {
                            needle.rotate((int) (-1 * (H + (360 - M))));
                        } else {
                            needle.rotate((int) (-1 * (H - M)));
                        }

                        if (windspeed < 100) {
                            Wind.setText(String.format("%.0f km", windspeed));
                        }
                    }
                }
            }

            lastLatitude = currentLatitude;
            lastLongitude = currentLongitude;
        }
    }

    private void setLastGps(Location loc)
    {
        lastLocation = loc;

        if (lastLocation != null) {

            currentLatitude = lastLocation.getLatitude();
            currentLongitude = lastLocation.getLongitude();
            gpsAltitude = lastLocation.getAltitude();
            gpsSpeed = lastLocation.getSpeed() * 3600 / 1000;

            AltitudeGps.setText(String.format("%.0f m", gpsAltitude));
            Latitude.setText(Converter.ConvertDecimalToDegMinSec(currentLatitude) + " " + Converter.getHemisphereLat(currentLatitude));
            Longitude.setText(Converter.ConvertDecimalToDegMinSec(currentLongitude) + " " + Converter.getHemisphereLon(currentLongitude));
            GpsSpeed.setText(String.format("%.0f km", gpsSpeed));
            Utm.setText(Converter.latLon2UTM(currentLatitude,currentLongitude));

            /*try
            {
                Thread.sleep(2000);
            }
            catch (InterruptedException e) { e.printStackTrace();
            }*/
        }
    }

    private void GetCurrentLocation() {

            locListener = new LocationListener() {
                @Override
                public void onStatusChanged(String provider, int status,
                                            Bundle extras) {
                    // TODO Auto-generated method stub
                }

                @Override
                public void onProviderEnabled(String provider) {
                    // TODO Auto-generated method stub
                }

                @Override
                public void onProviderDisabled(String provider) {
                    // TODO Auto-generated method stub
                }

                @Override
                public void onLocationChanged(Location location) {
                    // TODO Auto-generated method stub
                    setGps(location);
                }
            };

            last_log_time = SystemClock.elapsedRealtime();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                {
                    AppLog.logString("AppLog checkSelfPermission error");
                    return;
                }
            }

            gpsProvider = locManager.getBestProvider(criteria, false);
            lastLocation = locManager.getLastKnownLocation(gpsProvider);
            locManager.requestLocationUpdates(gpsProvider, GPS_TIMEUPDATE, GPS_DISTANCEUPDATE, locListener);

            if(lastLocation != null)
            {
                setLastGps(lastLocation);
            }
            else
            {
                AppLog.logString("AppLog GPS_PROVIDER location is null, will try NETWORK_PROVIDER");

                if(lastLocation != null) {

                    if(isNetworkEnabled){

                        lastLocation = locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        setLastGps(lastLocation);
                        AppLog.logString("AppLog NETWORK_PROVIDER location ok");
                    }
                }
                else
                AppLog.logString("AppLog NETWORK_PROVIDER location is null");
            }

            GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
                @Override
                public void onGpsStatusChanged(int event) {

                    if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS || event == GpsStatus.GPS_EVENT_FIRST_FIX) {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }
                        }

                        GpsStatus status = locManager.getGpsStatus(null);

                        if (status != null) {
                            Iterable<GpsSatellite> satellites = status.getSatellites();
                            Iterator<GpsSatellite> sat = satellites.iterator();
                            int i = 0;
                            while (sat.hasNext()) {
                                GpsSatellite satellite = sat.next();
                                i++;
                                if (satellite.usedInFix()) {
                                    gpsfix = true;

                                    Date date = new Date(gpsTime);
                                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                                    String igcgpstime = sdf.format(date);
                                    Gpsfix.setText(igcgpstime + "  " + gpsProvider +" fixed" + "  Log: " + trckCount);
                                }
                                else
                                {
                                    gpsfix = false;

                                    Calendar c = Calendar.getInstance();
                                    SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
                                    String formattedTime = df.format(c.getTime());
                                    Gpsfix.setText(formattedTime + "  " + "In view: " + i + "  Log: " + trckCount);
                                }
                            }
                        }
                    }
                }
            };

            locManager.addGpsStatusListener(gpsStatusListener);
    }
    public static int getMyPermissionsRequestReadStorage() {
        return MY_PERMISSIONS_REQUEST_READ_STORAGE;
    }

    private boolean StoragePermissionCheck()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && this.checkSelfPermission(
                        Manifest.permission. READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED
                && this.checkSelfPermission(
                        Manifest.permission. WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED)
        {
            requestPermissions(new String[]{Manifest.permission. READ_EXTERNAL_STORAGE, Manifest.permission. WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_STORAGE);
            return false;
        }
        else
        {
            AppLog.logString("AppLog Storage PERMISSION_GRANTED");
            return true;
        }
    }

    private boolean GPSPremissionCheck()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && this.checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && this.checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION);
            return false;
        }
        else
        {
            AppLog.logString("AppLog Gps PERMISSION_GRANTED");
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (requestCode == REQUEST_LOCATION)
        {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {            //  gps functionality
                AppLog.logString("AppLog Gps PERMISSION_GRANTED");
                GetCurrentLocation();
            }
            else
            {
                Toast.makeText(this, "We Need permission for running gps", Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == MY_PERMISSIONS_REQUEST_READ_STORAGE)
        {
            //premission to read storage
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                AppLog.logString("AppLog Storage PERMISSION_GRANTED");

            } else
            {
                Toast.makeText(this, "We Need permission for logging to Storage", Toast.LENGTH_SHORT).show();
            }

            if(GPSPremissionCheck() && isGPSEnabled)
            {
                GetCurrentLocation();
            }
            else
            {
                checkLocationProviders();
            }
        }
    }


    public void startvario() {
        try {
            pressureFilter = new KalmanFilter(KF_VAR_ACCEL);
            pressureFilter.reset(pressure_hPa_);
            altitudeFilter = new KalmanFilter(KF_VAR_ACCEL);
            altitudeFilter.reset(0);

            last_measurement_time = SystemClock.elapsedRealtime() / 1000.0f;

            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mPSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            mTSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

            mPSensorListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event)
                {
                    pressure_hPa_ = event.values[0];
                    setvario(pressure_hPa_);
                }

                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };
            mTSensorListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    d_temperature = event.values[0];
                }

                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };
            if (mPSensor != null) {
                mSensorManager.registerListener(mPSensorListener, mPSensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (mTSensor != null) {
                mSensorManager.registerListener(mTSensorListener, mTSensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
        } catch (Exception e) {
        }
    }

    public void setvario(double value)
    {
        if (lastAltitude != 0) {

                final double curr_measurement_time = SystemClock.elapsedRealtime() / 1000.0f;
                final double dt = curr_measurement_time - last_measurement_time;

                if (!barometer)
                {
                    baroAltitude = value;
                    baroAltitude = ((50 * baroAltitude / 100) + ((100 - 50) * lastAltitude / 100));
                    avgvario = baroAltitude - lastAltitude;
                }
                else
                {
                    pressureFilter.update(pressure_hPa_, KF_VAR_MEASUREMENT, dt);

                    baroAltitude = (long) Converter.hPaToMeter(slp_inHg_, pressureFilter.getXAbs());
                    baroAltitude = ((damping * baroAltitude / 100) + ((100 - (damping)) * lastAltitude / 100));
                    altitudeFilter.update(baroAltitude, KF_VAR_MEASUREMENT, dt);
                    avgvario = altitudeFilter.getXVel();
                }

                if (avgvario >= 0) {
                    climbProgress.setProgress((int) (avgvario * 100 / 8));
                    sinkProgress.setProgress(0);
                    VertSpeed.setTextColor(Color.parseColor("#2E9AFE"));
                } else if (avgvario < 0) {
                    sinkProgress.setProgress((int) ((-1 * avgvario) * 100 / 8));
                    climbProgress.setProgress(0);
                    VertSpeed.setTextColor(Color.parseColor("#B40404"));
                }

                VertSpeed.setText(String.format("%.1f m/s", avgvario));

                if (barometer)
                AltitudeBaro.setText(String.format("%.0f m", baroAltitude));

                playsound();

                SetGraph(avgvario + 6, 12, graphspeed);

                if (mTSensor != null)
                    Temp.setText(String.format("%.1f ", d_temperature) + DEGREE + "C");

                lastAltitude = baroAltitude;
                last_measurement_time = curr_measurement_time;
        } else
        {
            if (!barometer)
                lastAltitude = value;
            else
                lastAltitude = Converter.hPaToMeter(slp_inHg_, value);
        }
    }

    private void stopdevices() {
        try {

            if (beeps != null) {
                beeps.onDestroy();
            }
            if (mPSensor != null) {
                mSensorManager.unregisterListener(mPSensorListener);
            }
            if (mTSensor != null) {
                mSensorManager.unregisterListener(mTSensorListener);
            }
            if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if(isGPSEnabled && locManager != null)
            {
                locManager.removeUpdates((LocationListener) locListener);
                locManager = null;
            }

        } catch (Exception e) {
        }
    }

    private void preparelogfooter() {
        if (!logfooter) {
            try {
                Calendar c = Calendar.getInstance();
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss");
                String formattedDate = df.format(c.getTime());
                String value = "LXGD Turkay Biliyor Android Igc Version 1.00" + "\n";
                value = value + ("LXGD Downloaded " + formattedDate);
                generateIGC_onSD(logFileName + ".igc", value);
                File root = new File(Environment.getExternalStorageDirectory(), "VarioLog");
                File myFile = new File(root, logFileName + ".igc");
                refreshGallery(myFile);
                logfooter = true;
            } catch (Exception e) {
            }
        }
    }

    public void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);
    }
    private void checkLog() {

        File root = new File(Environment.getExternalStorageDirectory(), "VarioLog");
        File file = new File(root, logFileName + ".igc");

        boolean find = false;

        if (file.exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("B")) {
                        find = true;
                    }
                }
            } catch (IOException e) {
            }
        }

        if (!find) {
            try {
                delete(file);
            } catch (IOException e) {
            }
        }
    }

    void delete(File f) throws IOException {
        if (!f.delete()) {
            new FileNotFoundException("Failed to delete file: " + f);
        }
    }

    private void preparelogheader() {
        if (!logheader) {
            try {
                String value = "AXGD000 Variometer v1.0\n";
                value = value + "HFDTE" + formattedDate + "\n";
                value = value + "HFPLTPILOTINCHARGE: " + pilotname + "\n";
                value = value + "HFGTYGLIDERTYPE: " + glidermodel + "\n";
                value = value + "HFCCLCOMPETITIONCLASS: " + glidercertf + "\n";
                value = value + "HFCIDCOMPETITIONID: " + civlid + "\n";
                value = value + "HODTM100GPSDATUM: WGS-84\n";
                value = value + "HFFTYFRTYPE: Turkay Biliyor Variometer";

                generateIGC_onSD(logFileName + ".igc", value);

                logheader = true;

            } catch (Exception e) {
            }
        }
    }

    public void savevalues() {
        String sFileName = "variometer_settings.txt";
        try {
            File root = new File(Environment.getExternalStorageDirectory(),
                    "VarioLog");
            if (!root.exists()) {
                root.mkdirs();
            }
            File settingsfile = new File(root, sFileName);
            FileWriter writer = new FileWriter(settingsfile);
            writer.write(String.valueOf(slp_inHg_));
            writer.flush();
            writer.close();
        } catch (Exception e) {
        }
    }

    public void getvalues() {
        String sFileName = "variometer_settings.txt";
        try {
            File root = new File(Environment.getExternalStorageDirectory(), "VarioLog");
            File settingsfile = new File(root, sFileName);
            if (settingsfile.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(settingsfile));
                String line;
                line = br.readLine();
                this.slp_inHg_ = Double.parseDouble(line);
                if (slp_inHg_ < 28.1 || slp_inHg_ > 31.0) slp_inHg_ = 29.92;
                br.close();
            } else {
                this.slp_inHg_ = 29.92;
            }
        } catch (Exception e) {
        }
    }

    public void generateIGC_onSD(String filename, String val) {
        File myFile = null;
        try {
            if (!logfooter) {
                //mediaDir = QAndroidJniObject::callStaticObjectMethod("android/os/Environment", "getExternalStorageDirectory", "()Ljava/io/File;");
                File root = new File(Environment.getExternalStorageDirectory(), "VarioLog");
                AppLog.logString("AppLog: path= " + root);
                if (!root.exists()) {
                    root.mkdirs();
                }
                myFile = new File(root, filename);
                if (!myFile.exists())
                    myFile.createNewFile();
                BufferedWriter buf = new BufferedWriter(new FileWriter(myFile, true));
                buf.append(val);
                buf.newLine();
                buf.close();
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void flashMessage(String customText) {
        try {

            Toast.makeText(getBaseContext(), customText, Toast.LENGTH_LONG).show();

        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void SetGraph(final double alt, final double maxvalue, final float graphspeed) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGraphView.addDataPoint(alt, maxvalue, graphspeed);
            }
        });
    }

    private void distancetotakeoff(double targetlt, double targetlon) {
        Location currentLocation = new Location("currentLocation");
        currentLocation.setLatitude(currentLatitude);
        currentLocation.setLongitude(currentLongitude);

        Location targetLocation = new Location("targetLocation");
        targetLocation.setLatitude(targetlt);
        targetLocation.setLongitude(targetlon);

        String str = null;
        double distance = (int) currentLocation.distanceTo(targetLocation);
        if (distance <= 999)
            str = String.valueOf(String.format("%.0f", distance)) + " m";
        else
            str = String.valueOf(String.format("%.1f", distance / 1000)) + " km";
        Distancetotakeoff.setText(str);
    }

    void playsound() {
        if (beeps != null)
            beeps.setAvgVario(avgvario);
    }

    private void setigcfile() {
        //B
        Date date = new Date(gpsTime);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String igcgpstime = sdf.format(date);
        String igclat = Converter.decimalToDMSLat(currentLatitude);
        String igclon = Converter.decimalToDMSLon(currentLongitude);
        //A
        String igcaltpressure = String.format("%05.0f", baroAltitude);
        String igcaltgps = String.format("%05.0f", gpsAltitude);
        String igcval = "B" + igcgpstime.replace(":", "") + igclat + igclon + "A" + igcaltpressure + igcaltgps;

        generateIGC_onSD(logFileName + ".igc", igcval);

        if (livetrackenabled) {
            if (!loginLW) {
                Live.setText("LiveTrack24: trying");
                setLivePos emitPos = new setLivePos();
                emitPos.execute(1);
            } else {
                setLivePos emitPos = new setLivePos();
                emitPos.execute(2);
            }
        }

        trckCount++;
    }

    public synchronized double getWindSpeed() {
        return Math.sqrt(wind[0] * wind[0] + wind[1] * wind[1]);
    }

    public synchronized double getWindSpeedError() {
        return Math.sqrt(windError[0] * windError[0] + windError[1] * windError[1]);
    }

    public synchronized double getAirSpeed() {
        return wind[2];
    }

    public synchronized double getAirSpeedError() {
        return windError[2];
    }

    public synchronized double[] getWindError() {
        return ArrayUtil.copy(windError);
    }

    public synchronized double[] getWind() {
        return ArrayUtil.copy(wind);
    }

    public synchronized double getWindDirection() {
        return resolveDegrees(Math.toDegrees(Math.atan2(wind[1], wind[0])));
    }

    public double resolveDegrees(double degrees) {
        if (degrees < 0.0) {
            return resolveDegrees(degrees + 360.0);

        }
        if (degrees > 360.0) {
            return resolveDegrees(degrees - 360.0);
        }
        return degrees;
    }


    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private class setLivePos extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            errorinfo = "";
            error = false;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                type = (Integer) params[0];
                if (!loginLW && type == 1) {
                    liveWriter = new LeonardoLiveWriter(
                            basecontext,
                            serverUrl,
                            username,
                            password,
                            wingmodel,
                            vechiletype,
                            logTime / 1000);
                    liveWriter.emitProlog();
                } else if (loginLW && type == 2) {
                    liveWriter.emitPosition(gpsTime, currentLatitude, currentLongitude, (float) gpsAltitude, (int) gpsBearing, (float) gpsSpeed);

                } else if (loginLW && type == 3) {
                    liveWriter.emitEpilog();
                }
                return true;
            } catch (Exception e) {
                errorinfo = "Live Connection Error";
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                error = false;
                if (type == 1) {
                    loginLW = true;
                } else if (type == 3) {
                    loginLW = false;
                    type = 0;
                    LWcount = 0;
                    Live.setText("LiveTrack24: not live");
                    if (livetrackenabled)
                        Process.killProcess(Process.myPid());

                } else {

                    LWcount = liveWriter.getLWCount();

                    if (livetrackenabled)
                        Live.setText("Live Sending: " + String.valueOf(LWcount));
                }

            } else {
                error = true;
                Live.setText("LiveTrack24: trying");
            }
        }
    }
}
