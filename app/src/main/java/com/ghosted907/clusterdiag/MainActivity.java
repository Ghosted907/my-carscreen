package com.ghosted907.clusterdiag;

import android.app.ActivityOptions;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ghosted907.clusterdiag.logic.ThemeLabels;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends AppCompatActivity implements ClusterClient.Listener, CarSignalClient.Listener {
    static final String TAG = "ClusterDiag";

    private ClusterClient client;
    private CarSignalClient signals;

    private View statusDot;
    private TextView statusAr;
    private TextView statusEn;
    private TextView errorBanner;
    private TextView themeValue;
    private TextView themeAr;
    private TextView themeEn;
    private TextView fontLine;
    private TextView dayLine;
    private TextView lastResult;
    private TextView descriptor;
    private TextView logView;
    private ScrollView logScroll;

    private MaterialButton theme1;
    private MaterialButton theme2;
    private MaterialButton theme3;
    private MaterialButton font1;
    private MaterialButton font2;
    private MaterialButton day;
    private MaterialButton night;
    private MaterialButton reread;
    private ImageView turnLeft;
    private ImageView turnRight;
    private ImageView hazardLamp;
    private TextView signalTable;

    private boolean ready;
    private boolean retryMode;
    private boolean capTheme;
    private boolean capFont;
    private boolean capDay;
    private boolean busy;
    private Integer activeTheme;
    private Integer activeFont;
    private Integer activeDay;
    private final List<String> logLines = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View content = findViewById(android.R.id.content);
        content.setFilterTouchesWhenObscured(false);
        bindViews();
        wireClicks();
        client = new ClusterClient(this, this);
        signals = new CarSignalClient(this, this);
        applyButtons();
    }

    @Override
    protected void onStart() {
        super.onStart();
        client.start();
        signals.start();
    }

    @Override
    protected void onDestroy() {
        signals.stop();
        client.stop();
        super.onDestroy();
    }

    private void bindViews() {
        statusDot = findViewById(R.id.viewStatusDot);
        statusAr = findViewById(R.id.textStatusAr);
        statusEn = findViewById(R.id.textStatusEn);
        errorBanner = findViewById(R.id.textError);
        themeValue = findViewById(R.id.textThemeValue);
        themeAr = findViewById(R.id.textThemeAr);
        themeEn = findViewById(R.id.textThemeEn);
        fontLine = findViewById(R.id.textFont);
        dayLine = findViewById(R.id.textDay);
        lastResult = findViewById(R.id.textLastResult);
        descriptor = findViewById(R.id.textDescriptor);
        logView = findViewById(R.id.textLog);
        logScroll = findViewById(R.id.scrollLog);
        disarmLog();
        theme1 = findViewById(R.id.buttonTheme1);
        theme2 = findViewById(R.id.buttonTheme2);
        theme3 = findViewById(R.id.buttonTheme3);
        font1 = findViewById(R.id.buttonFont1);
        font2 = findViewById(R.id.buttonFont2);
        day = findViewById(R.id.buttonDay);
        night = findViewById(R.id.buttonNight);
        reread = findViewById(R.id.buttonReread);
        turnLeft = findViewById(R.id.imageTurnLeft);
        turnRight = findViewById(R.id.imageTurnRight);
        hazardLamp = findViewById(R.id.imageHazard);
        signalTable = findViewById(R.id.textSignalTable);
        disarm(turnLeft);
        disarm(turnRight);
        disarm(hazardLamp);
        disarm(signalTable);
    }

    private void disarm(View view) {
        if (view == null) {
            return;
        }
        view.setClickable(false);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        view.setFilterTouchesWhenObscured(false);
    }

    private void wireClicks() {
        wire(theme1, "theme 1", new Runnable() {
            @Override
            public void run() {
                client.setTheme(1);
            }
        });
        wire(theme2, "theme 2", new Runnable() {
            @Override
            public void run() {
                client.setTheme(2);
            }
        });
        wire(theme3, "theme 3", new Runnable() {
            @Override
            public void run() {
                client.setTheme(3);
            }
        });
        wire(font1, "font 1", new Runnable() {
            @Override
            public void run() {
                client.setFont(1);
            }
        });
        wire(font2, "font 2", new Runnable() {
            @Override
            public void run() {
                client.setFont(2);
            }
        });
        wire(day, "day", new Runnable() {
            @Override
            public void run() {
                client.setDayNight(0);
            }
        });
        wire(night, "night", new Runnable() {
            @Override
            public void run() {
                client.setDayNight(1);
            }
        });
        wire(reread, "re-read", new Runnable() {
            @Override
            public void run() {
                client.retryOrReread();
            }
        });
        MaterialButton showCluster = findViewById(R.id.buttonClusterShow);
        MaterialButton previewCluster = findViewById(R.id.buttonClusterPreview);
        MaterialButton closeCluster = findViewById(R.id.buttonClusterClose);
        wire(showCluster, "cluster show", new Runnable() {
            @Override
            public void run() {
                openCluster(false);
            }
        });
        wire(previewCluster, "cluster preview", new Runnable() {
            @Override
            public void run() {
                openCluster(true);
            }
        });
        wire(closeCluster, "cluster close", new Runnable() {
            @Override
            public void run() {
                SaudiClusterActivity.finishIfRunning();
                LaylNajdActivity.finishIfRunning();
                Log.i(TAG, "cluster close requested");
            }
        });
        MaterialButton showLayl = findViewById(R.id.buttonLaylShow);
        MaterialButton previewLayl = findViewById(R.id.buttonLaylPreview);
        wire(showLayl, "layl show", new Runnable() {
            @Override
            public void run() {
                openLayl(false);
            }
        });
        wire(previewLayl, "layl preview", new Runnable() {
            @Override
            public void run() {
                openLayl(true);
            }
        });
    }

    private void openLayl(boolean preview) {
        Intent intent = new Intent(this, LaylNajdActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            if (preview) {
                startActivity(intent);
                Log.i(TAG, "layl preview on this display");
                return;
            }
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(1);
            startActivity(intent, options.toBundle());
            Log.i(TAG, "layl launch display 1");
        } catch (RuntimeException e) {
            Log.i(TAG, "layl launch failed " + e.getClass().getSimpleName() + " " + e.getMessage());
            appendLog("layl launch failed " + e.getClass().getSimpleName());
        }
    }

    private void openCluster(boolean preview) {
        Intent intent = new Intent(this, SaudiClusterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            if (preview) {
                intent.putExtra(SaudiClusterActivity.EXTRA_DEBUG, true);
                startActivity(intent);
                Log.i(TAG, "cluster preview on this display");
                return;
            }
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(1);
            startActivity(intent, options.toBundle());
            Log.i(TAG, "cluster launch display 1");
        } catch (RuntimeException e) {
            Log.i(TAG, "cluster launch failed " + e.getClass().getSimpleName() + " " + e.getMessage());
            appendLog("cluster launch failed " + e.getClass().getSimpleName());
        }
    }

    private void wire(MaterialButton button, final String name, final Runnable action) {
        button.setFilterTouchesWhenObscured(false);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "pressed " + name);
                appendLog("pressed " + name);
                action.run();
            }
        });
    }

    /** The log must not take focus or swallow taps meant for the buttons. */
    private void disarmLog() {
        logView.setTextIsSelectable(false);
        logView.setLongClickable(false);
        logView.setClickable(false);
        logView.setFocusable(false);
        logView.setFocusableInTouchMode(false);
        if (logScroll != null) {
            logScroll.setFocusable(false);
            logScroll.setFocusableInTouchMode(false);
            logScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }

    @Override
    public void onSignalLog(String line) {
        appendLog(line);
    }

    @Override
    public void onLamps(boolean left, boolean right, boolean hazard) {
        if (isDestroyed()) {
            return;
        }
        tint(turnLeft, left ? R.color.ok : R.color.dot_idle);
        tint(turnRight, right ? R.color.ok : R.color.dot_idle);
        tint(hazardLamp, hazard ? R.color.amber : R.color.dot_idle);
    }

    @Override
    public void onSignalTable(String table) {
        if (isDestroyed() || signalTable == null) {
            return;
        }
        signalTable.setText(table);
    }

    private void tint(ImageView image, int colorId) {
        if (image == null) {
            return;
        }
        image.setImageTintList(ColorStateList.valueOf(color(colorId)));
    }

    @Override
    public void onStatus(ClusterClient.Status status) {
        ready = status.ready;
        retryMode = status.retryEnabled;
        statusAr.setText(status.pillAr);
        statusEn.setText(status.pillEn);
        descriptor.setText(status.detail == null ? "" : status.detail);
        statusDot.setBackgroundTintList(ColorStateList.valueOf(colorFor(status.tone)));
        if (status.banner == null || status.banner.length() == 0) {
            errorBanner.setVisibility(View.GONE);
            errorBanner.setText("");
        } else {
            errorBanner.setVisibility(View.VISIBLE);
            errorBanner.setText(status.banner);
        }
        if (!status.ready) {
            activeTheme = null;
            activeFont = null;
            activeDay = null;
            markActive();
        }
        applyButtons();
    }

    @Override
    public void onLog(String line) {
        appendLog(line);
    }

    private void appendLog(String line) {
        if (isDestroyed()) {
            return;
        }
        logLines.add(line);
        if (logLines.size() > 200) {
            logLines.remove(0);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < logLines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(logLines.get(i));
        }
        logView.setText(builder.toString());
        if (logScroll != null) {
            logScroll.post(new Runnable() {
                @Override
                public void run() {
                    logScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    @Override
    public void onTheme(ClusterClient.ReadKind kind, Integer id, String raw) {
        if (kind == ClusterClient.ReadKind.MISSING) {
            themeValue.setText("—");
            themeValue.setTextColor(color(R.color.text));
            themeAr.setText("لا توجد دالة قراءة");
            themeEn.setText("No getter on this interface");
            activeTheme = null;
        } else if (kind == ClusterClient.ReadKind.FAILED) {
            themeValue.setText("!");
            themeValue.setTextColor(color(R.color.danger));
            themeAr.setText("تعذّرت القراءة");
            themeEn.setText("Read failed");
            activeTheme = null;
        } else if (id == null) {
            themeValue.setText("?");
            themeValue.setTextColor(color(R.color.text));
            themeAr.setText("قيمة غير معروفة");
            themeEn.setText(raw == null ? "Unrecognized return" : raw);
            activeTheme = null;
        } else {
            themeValue.setText(String.valueOf(id));
            themeValue.setTextColor(themeColor(id));
            themeAr.setText(ThemeLabels.arabic(id));
            themeEn.setText(ThemeLabels.english(id));
            activeTheme = id;
        }
        markActive();
    }

    @Override
    public void onFont(ClusterClient.ReadKind kind, Integer id, String raw) {
        fontLine.setText(extraLine("الخط", "Font", kind, id, raw, false));
        activeFont = kind == ClusterClient.ReadKind.OK ? id : null;
        markActive();
    }

    @Override
    public void onDayNight(ClusterClient.ReadKind kind, Integer id, String raw) {
        if (kind == ClusterClient.ReadKind.OK && id != null) {
            dayLine.setText("الوضع · Day/Night: " + id + "  " + ThemeLabels.dayArabic(id)
                    + " / " + ThemeLabels.dayEnglish(id));
            activeDay = id;
        } else if (kind == ClusterClient.ReadKind.OK) {
            dayLine.setText("الوضع · Day/Night: " + (raw == null ? "?" : raw));
            activeDay = null;
        } else {
            dayLine.setText(extraLine("الوضع", "Day/Night", kind, null, raw, true));
            activeDay = null;
        }
        markActive();
    }

    @Override
    public void onCallResult(ClusterClient.Tone tone, String message) {
        lastResult.setText(message);
        lastResult.setTextColor(colorFor(tone));
    }

    @Override
    public void onBusy(boolean busy) {
        this.busy = busy;
        applyButtons();
    }

    @Override
    public void onCapabilities(boolean theme, boolean font, boolean day) {
        capTheme = theme;
        capFont = font;
        capDay = day;
        applyButtons();
    }

    private void applyButtons() {
        boolean live = ready && !busy;
        theme1.setEnabled(live && capTheme);
        theme2.setEnabled(live && capTheme);
        theme3.setEnabled(live && capTheme);
        font1.setEnabled(live && capFont);
        font2.setEnabled(live && capFont);
        day.setEnabled(live && capDay);
        night.setEnabled(live && capDay);
        reread.setEnabled(!busy && (ready || retryMode));
        reread.setText(ready ? R.string.reread : R.string.retry_bind);
    }

    private void markActive() {
        stroke(theme1, activeTheme != null && activeTheme.intValue() == 1);
        stroke(theme2, activeTheme != null && activeTheme.intValue() == 2);
        stroke(theme3, activeTheme != null && activeTheme.intValue() == 3);
        stroke(font1, activeFont != null && activeFont.intValue() == 1);
        stroke(font2, activeFont != null && activeFont.intValue() == 2);
        stroke(day, activeDay != null && activeDay.intValue() == 0);
        stroke(night, activeDay != null && activeDay.intValue() == 1);
    }

    private void stroke(MaterialButton button, boolean active) {
        int width = active ? Math.round(3f * getResources().getDisplayMetrics().density) : 0;
        button.setStrokeWidth(width);
        button.setStrokeColor(ColorStateList.valueOf(active ? 0xFFFFFFFF : 0x00000000));
    }

    private String extraLine(
            String arabic,
            String english,
            ClusterClient.ReadKind kind,
            Integer id,
            String raw,
            boolean dayNight) {
        if (kind == ClusterClient.ReadKind.MISSING) {
            return arabic + " · " + english + ": غير متاح / no getter";
        }
        if (kind == ClusterClient.ReadKind.FAILED) {
            return arabic + " · " + english + ": تعذّرت القراءة / read failed";
        }
        if (id == null) {
            return arabic + " · " + english + ": " + (raw == null ? "?" : raw);
        }
        if (dayNight) {
            return arabic + " · " + english + ": " + id;
        }
        return arabic + " · " + english + ": " + id;
    }

    private int themeColor(int id) {
        if (id == 1) {
            return color(R.color.digital);
        }
        if (id == 2) {
            return color(R.color.classic);
        }
        if (id == 3) {
            return color(R.color.navigation);
        }
        return color(R.color.text);
    }

    private int colorFor(ClusterClient.Tone tone) {
        if (tone == ClusterClient.Tone.OK) {
            return color(R.color.ok);
        }
        if (tone == ClusterClient.Tone.BAD) {
            return color(R.color.danger);
        }
        if (tone == ClusterClient.Tone.WARN) {
            return color(R.color.amber);
        }
        return color(R.color.dot_idle);
    }

    private int color(int id) {
        return getColor(id);
    }
}
