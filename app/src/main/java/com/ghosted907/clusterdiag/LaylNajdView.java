package com.ghosted907.clusterdiag;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.ghosted907.clusterdiag.logic.GaugeFormat;
import com.ghosted907.clusterdiag.logic.LaylNajdLogic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 1920×720 Layl Najd cluster. Drawing is in that space and letterboxed into the view. */
public final class LaylNajdView extends View {
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 720f;
    private static final int NAVY = 0xFF0A1426;
    private static final int NIGHT = 0xFF05070C;
    private static final int SAND = 0xFFE8D9B5;
    private static final int GOLD = 0xFFC9A24A;
    private static final int RED = 0xFFD23B3B;
    private static final int GREEN = 0xFF3FBF6F;
    private static final int AMBER = 0xFFF0A030;
    private static final int DIM = 0xFF5C6570;
    private static final int WHITE = 0xFFF7F4EC;
    private static final int BLUE = 0xFF4C8DFF;
    private static final int BEAM = 0xFF7EC8FF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("h:mm a", Locale.US);

    private float speed = Float.NaN;
    private float rpm = Float.NaN;
    private float outside = Float.NaN;
    private float engine = Float.NaN;
    private float odometer = Float.NaN;
    private float cruise = Float.NaN;
    private float heading = Float.NaN;
    private int fuel = Integer.MIN_VALUE;
    private int rangeKm = Integer.MIN_VALUE;
    private int driveMode = Integer.MIN_VALUE;
    private int lka = Integer.MIN_VALUE;
    private int ldw = Integer.MIN_VALUE;
    private int fcw = Integer.MIN_VALUE;
    private String gear = "";
    private boolean leftTurn;
    private boolean rightTurn;
    private boolean lowBeam;
    private boolean highBeam;

    public LaylNajdView(Context context) {
        this(context, null);
    }

    public LaylNajdView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setTypeface(Typeface.DEFAULT);
    }

    public void apply(ClusterGaugeClient.Reading reading) {
        if (reading == null) {
            return;
        }
        speed = reading.speed;
        rpm = reading.rpm;
        outside = LaylNajdLogic.outsideTemp(reading.outsideC, reading.outsideFallback);
        engine = reading.engineC;
        odometer = reading.odometerKm;
        cruise = reading.cruiseKmh;
        fuel = reading.fuel;
        rangeKm = reading.rangeKm;
        driveMode = reading.driveMode;
        lka = reading.lka;
        ldw = reading.ldw;
        fcw = reading.fcw;
        gear = LaylNajdLogic.gearLetter(reading.gearLetter, reading.gearProp, reading.gearValue);
        leftTurn = reading.leftTurn;
        rightTurn = reading.rightTurn;
        lowBeam = reading.lowBeam;
        highBeam = reading.highBeam;
        invalidate();
    }

    public void setHeading(float degrees) {
        heading = degrees;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(NIGHT);
        float scale = Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H);
        if (scale <= 0f) {
            return;
        }
        canvas.save();
        canvas.translate((getWidth() - DESIGN_W * scale) / 2f, (getHeight() - DESIGN_H * scale) / 2f);
        canvas.scale(scale, scale);
        drawScene(canvas);
        canvas.restore();
    }

    private void drawScene(Canvas canvas) {
        paint.setShader(new LinearGradient(0f, 0f, 0f, DESIGN_H, NAVY, NIGHT, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0f, 0f, DESIGN_W, DESIGN_H, paint);
        paint.setShader(null);
        dunes(canvas);
        speedDial(canvas, 500f, 330f, 210f);
        fuelGauge(canvas, 118f, 250f);
        coolantGauge(canvas, 118f, 500f);
        clockBlock(canvas);
        road(canvas);
        gearRow(canvas);
        odometer(canvas);
        rpmDial(canvas, 1560f, 330f, 210f);
        assistColumn(canvas, 1240f);
        bottomBar(canvas);
    }

    private void dunes(Canvas canvas) {
        path.rewind();
        path.moveTo(0f, 640f);
        path.quadTo(280f, 590f, 560f, 640f);
        path.quadTo(900f, 700f, 1240f, 620f);
        path.quadTo(1560f, 560f, 1920f, 640f);
        path.lineTo(1920f, 720f);
        path.lineTo(0f, 720f);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33241810);
        canvas.drawPath(path, paint);
    }

    private void speedDial(Canvas canvas, float cx, float cy, float radius) {
        boolean known = GaugeFormat.validSpeed(speed);
        float fraction = LaylNajdLogic.speedFraction(speed);
        dial(canvas, cx, cy, radius, fraction, 200f, 220f, LaylNajdLogic.SPEED_MAX, 20f, known);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(SAND);
        paint.setTextSize(86f);
        canvas.drawText(LaylNajdLogic.speedLabel(speed), cx, cy + 24f, paint);
        paint.setTextSize(22f);
        paint.setColor(GOLD);
        canvas.drawText("km/h", cx, cy + 56f, paint);
    }

    private void rpmDial(Canvas canvas, float cx, float cy, float radius) {
        boolean known = GaugeFormat.validRpm(rpm);
        float fraction = LaylNajdLogic.rpmFraction(rpm);
        dial(canvas, cx, cy, radius, fraction, 7000f, 8000f, LaylNajdLogic.RPM_MAX, 1000f, known);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(known && !Float.isNaN(heading) ? SAND : DIM);
        String letter = LaylNajdLogic.headingLetter(heading);
        paint.setTextSize(54f);
        canvas.drawText(letter.length() == 0 ? "—" : letter, cx, cy - 6f, paint);
        paint.setTextSize(20f);
        paint.setColor(SAND);
        canvas.drawText(Float.isNaN(heading) ? "—" : Math.round(heading) + "°", cx, cy + 24f, paint);
        paint.setTextSize(36f);
        canvas.drawText(LaylNajdLogic.rpmLabel(rpm), cx, cy + 78f, paint);
        paint.setTextSize(16f);
        paint.setColor(GOLD);
        canvas.drawText("x1000rpm", cx, cy + 102f, paint);
    }

    private void dial(Canvas canvas, float cx, float cy, float radius, float fraction,
            float redFrom, float redTo, float max, float step, boolean known) {
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(22f);
        paint.setColor(0x33C9A24A);
        canvas.drawArc(oval, 150f, 240f, false, paint);
        paint.setStrokeWidth(8f);
        paint.setColor(GOLD);
        canvas.drawArc(oval, 150f, known ? 240f * fraction : 0f, false, paint);
        float redStart = 150f + 240f * (redFrom / max);
        float redSweep = 240f * ((redTo - redFrom) / max);
        paint.setColor(RED);
        paint.setStrokeWidth(8f);
        canvas.drawArc(oval, redStart, redSweep, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(step >= 1000f ? 16f : 15f);
        paint.setColor(SAND);
        int count = Math.round(max / step);
        for (int i = 0; i <= count; i++) {
            float value = step * i;
            float angle = (float) Math.toRadians(150.0 + 240.0 * (value / max));
            float lx = cx + (float) Math.cos(angle) * (radius - 40f);
            float ly = cy + (float) Math.sin(angle) * (radius - 40f);
            int shown = step >= 1000f ? Math.round(value / 1000f) : Math.round(value);
            canvas.drawText(Integer.toString(shown), lx, ly + 6f, paint);
        }
        float needle = (float) Math.toRadians(150.0 + 240.0 * (known ? fraction : 0.0));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(WHITE);
        canvas.drawLine(cx, cy,
                cx + (float) Math.cos(needle) * (radius - 28f),
                cy + (float) Math.sin(needle) * (radius - 28f),
                paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, 7f, paint);
    }

    private void fuelGauge(Canvas canvas, float x, float y) {
        boolean low = LaylNajdLogic.fuelLow(fuel);
        int color = low ? AMBER : GOLD;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10f);
        paint.setColor(0x335C6570);
        canvas.drawLine(x, y, x, y + 140f, paint);
        paint.setColor(color);
        float fill = 140f * LaylNajdLogic.fuelFraction(fuel);
        canvas.drawLine(x, y + 140f, x, y + 140f - fill, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(16f);
        paint.setColor(SAND);
        canvas.drawText("F", x, y - 8f, paint);
        canvas.drawText("E", x, y + 160f, paint);
        droplet(canvas, x + 28f, y + 40f, low ? AMBER : DIM);
        paint.setTextSize(18f);
        paint.setColor(SAND);
        canvas.drawText(LaylNajdLogic.rangeLabel(rangeKm), x + 8f, y + 190f, paint);
    }

    private void droplet(Canvas canvas, float x, float y, int color) {
        path.rewind();
        path.moveTo(x, y - 16f);
        path.quadTo(x + 14f, y + 4f, x, y + 16f);
        path.quadTo(x - 14f, y + 4f, x, y - 16f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private void coolantGauge(Canvas canvas, float cx, float cy) {
        float fraction = LaylNajdLogic.coolantFraction(engine);
        boolean known = !Float.isNaN(fraction);
        oval.set(cx - 46f, cy - 46f, cx + 46f, cy + 46f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setColor(0x335C6570);
        canvas.drawArc(oval, 140f, 220f, false, paint);
        paint.setColor(known && fraction > 0.85f ? RED : GOLD);
        canvas.drawArc(oval, 140f, known ? 220f * fraction : 0f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(16f);
        paint.setColor(SAND);
        canvas.drawText("C", cx - 36f, cy + 36f, paint);
        canvas.drawText("H", cx + 36f, cy + 36f, paint);
    }

    private void clockBlock(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(SAND);
        paint.setTextSize(42f);
        canvas.drawText(clockFormat.format(new Date()), 960f, 78f, paint);
        lamp(canvas, 860f, 130f, lowBeam ? GREEN : DIM, "LOW");
        lamp(canvas, 960f, 130f, highBeam ? BEAM : DIM, "HIGH");
        arrow(canvas, 1040f, 118f, true, leftTurn);
        arrow(canvas, 1100f, 118f, false, rightTurn);
        wrench(canvas, 880f, 190f);
        triangle(canvas, 980f, 178f);
    }

    private void lamp(Canvas canvas, float x, float y, int color, String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(x, y, 10f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(14f);
        paint.setColor(SAND);
        canvas.drawText(label, x, y + 28f, paint);
    }

    private void arrow(Canvas canvas, float x, float y, boolean left, boolean on) {
        path.rewind();
        if (left) {
            path.moveTo(x + 16f, y);
            path.lineTo(x, y + 14f);
            path.lineTo(x + 16f, y + 28f);
            path.lineTo(x + 16f, y + 18f);
            path.lineTo(x + 34f, y + 18f);
            path.lineTo(x + 34f, y + 10f);
            path.lineTo(x + 16f, y + 10f);
        } else {
            path.moveTo(x, y);
            path.lineTo(x + 16f, y + 14f);
            path.lineTo(x, y + 28f);
            path.lineTo(x, y + 18f);
            path.lineTo(x - 18f, y + 18f);
            path.lineTo(x - 18f, y + 10f);
            path.lineTo(x, y + 10f);
        }
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(on ? GREEN : DIM);
        canvas.drawPath(path, paint);
    }

    private void wrench(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(DIM);
        canvas.drawCircle(x, y, 8f, paint);
        canvas.drawLine(x + 6f, y + 6f, x + 22f, y + 22f, paint);
        canvas.drawLine(x + 16f, y + 14f, x + 26f, y + 8f, paint);
    }

    private void triangle(Canvas canvas, float x, float y) {
        path.rewind();
        path.moveTo(x, y);
        path.lineTo(x + 28f, y + 24f);
        path.lineTo(x - 28f, y + 24f);
        path.close();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(DIM);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y + 16f, 2f, paint);
    }

    private void road(Canvas canvas) {
        int tint = LaylNajdLogic.laneTint(lka, ldw);
        int line = tint == LaylNajdLogic.LANE_LKA ? GREEN
                : tint == LaylNajdLogic.LANE_LDW ? AMBER : 0x66E8D9B5;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(line);
        canvas.drawLine(820f, 560f, 940f, 390f, paint);
        canvas.drawLine(1100f, 560f, 980f, 390f, paint);
        paint.setStrokeWidth(3f);
        canvas.drawLine(960f, 540f, 960f, 500f, paint);
        canvas.drawLine(960f, 470f, 960f, 430f, paint);
        carSilhouette(canvas, 960f, 590f);
    }

    private void carSilhouette(Canvas canvas, float cx, float y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1C2838);
        canvas.drawRoundRect(cx - 36f, y - 28f, cx + 36f, y + 18f, 8f, 8f, paint);
        canvas.drawRoundRect(cx - 22f, y - 52f, cx + 22f, y - 22f, 6f, 6f, paint);
        paint.setColor(0xFF0A1018);
        canvas.drawCircle(cx - 28f, y + 16f, 8f, paint);
        canvas.drawCircle(cx + 28f, y + 16f, 8f, paint);
        paint.setColor(GOLD);
        canvas.drawRect(cx - 8f, y - 46f, cx + 8f, y - 40f, paint);
    }

    private void gearRow(Canvas canvas) {
        if (LaylNajdLogic.driveModeVisible(driveMode)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(22f);
            paint.setColor(LaylNajdLogic.ecoMode(driveMode) ? GREEN : SAND);
            canvas.drawText(LaylNajdLogic.driveModeLabel(driveMode), 960f, 250f, paint);
        }
        String[] gears = {"P", "R", "N", "D"};
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < gears.length; i++) {
            boolean active = gears[i].equals(gear);
            paint.setTextSize(active ? 54f : 28f);
            paint.setColor(active ? WHITE : DIM);
            canvas.drawText(gears[i], 860f + i * 66f, 320f, paint);
        }
    }

    private void odometer(Canvas canvas) {
        String line = LaylNajdLogic.odometerLabel(odometer);
        if (line.length() == 0) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(20f);
        paint.setColor(SAND);
        canvas.drawText(line, 960f, 560f, paint);
    }

    private void assistColumn(Canvas canvas, float x) {
        float y = 180f;
        if (LaylNajdLogic.showCruise(cruise)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(28f);
            paint.setColor(SAND);
            canvas.drawText(Integer.toString(Math.round(cruise)), x, y, paint);
            paint.setTextSize(12f);
            paint.setColor(GOLD);
            canvas.drawText("CRUISE", x, y + 16f, paint);
        }
        badge(canvas, x, y + 70f, "LKA", LaylNajdLogic.assistState(lka) == LaylNajdLogic.ASSIST_ON ? GREEN : DIM);
        badge(canvas, x, y + 140f, "LDW", LaylNajdLogic.assistState(ldw) == LaylNajdLogic.ASSIST_ON ? AMBER : DIM);
        badge(canvas, x, y + 210f, "FCW", LaylNajdLogic.assistState(fcw) == LaylNajdLogic.ASSIST_ON ? RED : DIM);
        wheel(canvas, x, y + 290f);
        placeholderCar(canvas, x, y + 360f);
    }

    private void badge(Canvas canvas, float x, float y, String label, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(color);
        canvas.drawRoundRect(x - 28f, y - 16f, x + 28f, y + 16f, 8f, 8f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(14f);
        canvas.drawText(label, x, y + 5f, paint);
    }

    private void wheel(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(DIM);
        canvas.drawCircle(x, y, 16f, paint);
        canvas.drawLine(x - 10f, y, x + 10f, y, paint);
        canvas.drawLine(x, y, x, y + 12f, paint);
    }

    private void placeholderCar(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0x66D23B3B);
        canvas.drawRoundRect(x - 16f, y - 10f, x + 16f, y + 12f, 4f, 4f, paint);
        canvas.drawCircle(x - 8f, y + 12f, 3f, paint);
        canvas.drawCircle(x + 8f, y + 12f, 3f, paint);
    }

    private void bottomBar(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(BLUE);
        canvas.drawRoundRect(48f, 652f, 72f, 688f, 4f, 4f, paint);
        canvas.drawLine(60f, 646f, 60f, 652f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(22f);
        paint.setColor(SAND);
        canvas.drawText("—  غير متاح", 88f, 678f, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(28f);
        canvas.drawText(LaylNajdLogic.tempLabel(outside), 1860f, 678f, paint);
    }
}
