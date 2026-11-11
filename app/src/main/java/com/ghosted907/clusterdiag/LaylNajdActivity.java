package com.ghosted907.clusterdiag;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;

/** Full-screen Layl Najd cluster. Launch on display 1, or preview it on this screen. */
public final class LaylNajdActivity extends AppCompatActivity implements ClusterGaugeClient.Callback {
    private static final String TAG = "LaylNajd";
    private static WeakReference<LaylNajdActivity> running = new WeakReference<LaylNajdActivity>(null);

    private LaylNajdView clusterView;
    private ClusterGaugeClient gauges;
    private LocationManager locations;
    private boolean locationListening;

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location != null && location.hasBearing() && clusterView != null) {
                clusterView.setHeading(location.getBearing());
            }
        }
    };

    public static void finishIfRunning() {
        LaylNajdActivity activity = running.get();
        if (activity != null) {
            activity.finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null && getIntent().getBooleanExtra("finish", false)) {
            finish();
            return;
        }
        running = new WeakReference<LaylNajdActivity>(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        clusterView = new LaylNajdView(this);
        setContentView(clusterView);
        gauges = new ClusterGaugeClient(this, this);
        gauges.enableLaylReads();
        gauges.start();
        listenForBearing();
        Log.i(TAG, "LaylNajdActivity created");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("finish", false)) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        stopBearing();
        if (gauges != null) {
            gauges.stop();
        }
        LaylNajdActivity current = running.get();
        if (current == this) {
            running = new WeakReference<LaylNajdActivity>(null);
        }
        super.onDestroy();
    }

    @Override
    public void onReading(ClusterGaugeClient.Reading reading) {
        if (!isDestroyed() && clusterView != null) {
            clusterView.apply(reading);
        }
    }

    @Override
    public void onGaugeLog(String line) {
        Log.i(TAG, line);
    }

    private void listenForBearing() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "location not granted; compass stays —");
            return;
        }
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locations == null) {
            return;
        }
        try {
            Location last = locations.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null && last.hasBearing() && clusterView != null) {
                clusterView.setHeading(last.getBearing());
            }
            locations.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper());
            locationListening = true;
            Log.i(TAG, "compass listening");
        } catch (RuntimeException e) {
            Log.i(TAG, "compass " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void stopBearing() {
        if (locations != null && locationListening) {
            try {
                locations.removeUpdates(locationListener);
            } catch (RuntimeException ignored) {
                // The provider may already be gone.
            }
        }
        locationListening = false;
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            View decor = getWindow().getDecorView();
            android.view.WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
