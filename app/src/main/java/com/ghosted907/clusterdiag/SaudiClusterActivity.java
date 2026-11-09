package com.ghosted907.clusterdiag;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.ghosted907.clusterdiag.logic.GaugeFormat;

import java.lang.ref.WeakReference;

/** Full-screen custom cluster. Launch on display 1, or preview it on the center screen. */
public final class SaudiClusterActivity extends AppCompatActivity implements ClusterGaugeClient.Callback {
    public static final String EXTRA_DEBUG = "debug";
    public static final String EXTRA_GEAR_PROP = "gearProp";
    public static final String EXTRA_GEAR_MAP = "gearMap";
    public static final String EXTRA_PROBE = "probe";
    private static final String PREFS = "saudi_cluster";
    private static final String TAG = "ClusterDiag";
    private static WeakReference<SaudiClusterActivity> running = new WeakReference<SaudiClusterActivity>(null);

    private SaudiClusterView clusterView;
    private ClusterGaugeClient gauges;

    public static void finishIfRunning() {
        SaudiClusterActivity activity = running.get();
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
        running = new WeakReference<SaudiClusterActivity>(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        clusterView = new SaudiClusterView(this);
        clusterView.setDebug(getIntent() != null && getIntent().getBooleanExtra(EXTRA_DEBUG, false));
        clusterView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                clusterView.toggleDebug();
                Log.i(TAG, "cluster debug=" + (getIntent() != null));
                return true;
            }
        });
        setContentView(clusterView);
        gauges = new ClusterGaugeClient(this, this);
        applyConfig(getIntent());
        gauges.start();
        Log.i(TAG, "SaudiClusterActivity created");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("finish", false)) {
            finish();
            return;
        }
        applyConfig(intent);
        if (clusterView != null && intent != null && intent.hasExtra(EXTRA_DEBUG)) {
            clusterView.setDebug(intent.getBooleanExtra(EXTRA_DEBUG, false));
        }
    }

    private void applyConfig(Intent intent) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int gearProp = prefs.getInt(EXTRA_GEAR_PROP, GaugeFormat.GEAR_POSITION);
        String gearMap = prefs.getString(EXTRA_GEAR_MAP, "");
        boolean save = false;
        if (intent != null && intent.hasExtra(EXTRA_GEAR_PROP)) {
            gearProp = intent.getIntExtra(EXTRA_GEAR_PROP, gearProp);
            save = true;
        }
        if (intent != null && intent.hasExtra(EXTRA_GEAR_MAP)) {
            gearMap = intent.getStringExtra(EXTRA_GEAR_MAP);
            if (gearMap == null) {
                gearMap = "";
            }
            save = true;
        }
        if (save) {
            prefs.edit().putInt(EXTRA_GEAR_PROP, gearProp).putString(EXTRA_GEAR_MAP, gearMap).apply();
        }
        int[] probe = parseProbe(intent == null ? null : intent.getStringExtra(EXTRA_PROBE));
        Log.i(TAG, "gear config prop=" + gearProp + " map=" + gearMap + " probe=" + probe.length);
        if (gauges != null) {
            gauges.applyConfig(gearProp, gearMap, probe);
        }
    }

    static int[] parseProbe(String text) {
        if (text == null || text.trim().length() == 0) {
            return new int[0];
        }
        String[] parts = text.split(",");
        int[] ids = new int[parts.length];
        int count = 0;
        for (int i = 0; i < parts.length; i++) {
            try {
                ids[count] = Integer.parseInt(parts[i].trim());
                count++;
            } catch (NumberFormatException ignored) {
                Log.i(TAG, "ignore probe token " + parts[i]);
            }
        }
        if (count == ids.length) {
            return ids;
        }
        int[] trimmed = new int[count];
        System.arraycopy(ids, 0, trimmed, 0, count);
        return trimmed;
    }

    @Override
    protected void onDestroy() {
        if (gauges != null) {
            gauges.stop();
        }
        SaudiClusterActivity current = running.get();
        if (current == this) {
            running = new WeakReference<SaudiClusterActivity>(null);
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
