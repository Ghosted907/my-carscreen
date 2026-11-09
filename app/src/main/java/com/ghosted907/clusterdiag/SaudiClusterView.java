package com.ghosted907.clusterdiag;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.ghosted907.clusterdiag.logic.GaugeFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * 1920×720 Saudi dusk cluster. All drawing is in that design space and scaled to the view.
 */
public final class SaudiClusterView extends View {
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 720f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final SimpleDateFormat clock = new SimpleDateFormat("h:mm a", Locale.US);

    private float speedTarget = Float.NaN;
    private float speedShown;
    private float rpmTarget = Float.NaN;
    private float rpmShown;
    private boolean speedKnown;
    private boolean rpmKnown;
    private float outsideC = Float.NaN;
    private int fuel = Integer.MIN_VALUE;
    private int rangeKm = Integer.MIN_VALUE;
    private int driveMode = 1;
    private String gear = "";
    private String gearRaw = "";
    private boolean leftTurn;
    private boolean rightTurn;
    private boolean lowBeam;
    private boolean highBeam;
    private boolean hazard;
    private String loadText = "--";
    private String clockOverride;
    private boolean debug;
    private boolean previewStill;
    private Map<String, String> debugRows;

    public SaudiClusterView(Context context) {
        this(context, null);
    }

    public SaudiClusterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
        invalidate();
    }

    public void toggleDebug() {
        debug = !debug;
        invalidate();
    }

    public void apply(ClusterGaugeClient.Reading reading) {
        if (reading == null) {
            return;
        }
        if (GaugeFormat.validSpeed(reading.speed)) {
            speedKnown = true;
            speedTarget = Math.min(220f, reading.speed);
        }
        if (GaugeFormat.validRpm(reading.rpm)) {
            rpmKnown = true;
            rpmTarget = Math.min(8000f, reading.rpm);
        }
        outsideC = reading.outsideC;
        fuel = reading.fuel;
        rangeKm = reading.rangeKm;
        driveMode = reading.driveMode;
        gear = reading.gearLetter == null ? "" : reading.gearLetter;
        gearRaw = reading.gearValue == Integer.MIN_VALUE
                ? reading.gearProp + "=--"
                : reading.gearProp + "=" + reading.gearValue;
        leftTurn = reading.leftTurn;
        rightTurn = reading.rightTurn;
        lowBeam = reading.lowBeam;
        highBeam = reading.highBeam;
        hazard = leftTurn && rightTurn;
        debugRows = reading.debug;
        invalidate();
    }

    /** Stable frame used for the design preview image. */
    public void showDesignSample() {
        speedKnown = rpmKnown = true;
        speedTarget = speedShown = 86f;
        rpmTarget = rpmShown = 1760f;
        outsideC = 22f;
        fuel = 75;
        rangeKm = 430;
        driveMode = 1;
        gear = "D";
        leftTurn = true;
        rightTurn = false;
        lowBeam = true;
        highBeam = false;
        hazard = false;
        loadText = "0.7";
        clockOverride = "10:30 AM";
        previewStill = true;
        debug = false;
        invalidate();
    }

    public void snapNeedles() {
        speedShown = speedTarget;
        rpmShown = rpmTarget;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!Float.isNaN(speedTarget)) {
                speedShown += (speedTarget - speedShown) * 0.14f;
            }
            if (!Float.isNaN(rpmTarget)) {
                rpmShown += (rpmTarget - rpmShown) * 0.14f;
            }
            if (Math.abs(speedTarget - speedShown) < 0.05f) {
                speedShown = speedTarget;
            }
            if (Math.abs(rpmTarget - rpmShown) < 2f) {
                rpmShown = rpmTarget;
            }
            invalidate();
            postDelayed(this, 16L);
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H);
        float dx = (getWidth() - DESIGN_W * scale) / 2f;
        float dy = (getHeight() - DESIGN_H * scale) / 2f;
        canvas.drawColor(0xFF07110C);
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        drawScene(canvas);
        drawDial(canvas, 360f, 400f, 230f, speedShown, 220f, false);
        drawDial(canvas, 1560f, 400f, 230f, rpmShown / 1000f, 8f, true);
        drawCar(canvas, 960f, 430f);
        drawTopBar(canvas);
        drawBottomBar(canvas);
        drawBlinkers(canvas);
        if (debug) {
            drawDebug(canvas);
        }
        canvas.restore();
    }

    private void drawScene(Canvas canvas) {
        paint.setShader(new LinearGradient(0f, 0f, 0f, 430f,
                new int[] {0xFF10182A, 0xFF3A2C4A, 0xFFC4784A, 0xFFE7B56A},
                new float[] {0f, 0.45f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, DESIGN_W, 460f, paint);
        paint.setShader(null);

        paint.setColor(0xFF2C3A34);
        path.reset();
        path.moveTo(0f, 300f);
        path.lineTo(180f, 210f);
        path.lineTo(340f, 280f);
        path.lineTo(520f, 170f);
        path.lineTo(700f, 260f);
        path.lineTo(860f, 190f);
        path.lineTo(1040f, 270f);
        path.lineTo(1240f, 160f);
        path.lineTo(1460f, 250f);
        path.lineTo(1680f, 150f);
        path.lineTo(1920f, 280f);
        path.lineTo(1920f, 420f);
        path.lineTo(0f, 420f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0xFFC6A15A);
        dune(canvas, 0f, 390f, 700f, 300f, 1400f, 400f, 1920f, 360f);
        paint.setColor(0xFFA67C3D);
        dune(canvas, 0f, 430f, 500f, 360f, 1100f, 450f, 1920f, 400f);
        paint.setColor(0xFF6E4A28);
        canvas.drawRect(0f, 470f, DESIGN_W, DESIGN_H, paint);

        paint.setColor(0xFF2A241C);
        path.reset();
        path.moveTo(760f, 720f);
        path.lineTo(900f, 470f);
        path.lineTo(1020f, 470f);
        path.lineTo(1160f, 720f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0xFFD4A84B);
        paint.setStrokeWidth(4f);
        paint.setStyle(Paint.Style.STROKE);
        for (float y = 520f; y < 700f; y += 36f) {
            float t = (y - 470f) / 250f;
            float half = 40f + t * 90f;
            canvas.drawLine(960f - half, y, 960f + half, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        drawPalm(canvas, 1240f, 250f);
        paint.setShader(new RadialGradient(200f, 80f, 160f, 0x55FFE0A0, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(200f, 90f, 150f, paint);
        paint.setShader(null);
    }

    private void dune(Canvas canvas, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        path.reset();
        path.moveTo(0f, 520f);
        path.lineTo(x0, y0);
        path.quadTo(x1, y1, x2, y2);
        path.quadTo((x2 + x3) / 2f, y3 - 30f, x3, y3);
        path.lineTo(1920f, 560f);
        path.lineTo(0f, 560f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawPalm(Canvas canvas, float x, float y) {
        paint.setColor(0xFF5C3A1E);
        paint.setStrokeWidth(14f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x, y + 40f, x + 10f, y + 170f, paint);
        paint.setColor(0xFF1E6B3A);
        paint.setStrokeWidth(8f);
        canvas.drawLine(x, y + 40f, x - 70f, y - 10f, paint);
        canvas.drawLine(x, y + 40f, x - 40f, y - 50f, paint);
        canvas.drawLine(x, y + 40f, x + 10f, y - 60f, paint);
        canvas.drawLine(x, y + 40f, x + 60f, y - 40f, paint);
        canvas.drawLine(x, y + 40f, x + 80f, y + 10f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawCar(Canvas canvas, float cx, float cy) {
        paint.setColor(0xFF1A1E1C);
        oval.set(cx - 46f, cy - 78f, cx + 46f, cy + 78f);
        canvas.drawRoundRect(oval, 20f, 20f, paint);
        paint.setColor(0xFFD5DCE6);
        oval.set(cx - 36f, cy - 70f, cx + 36f, cy + 70f);
        canvas.drawRoundRect(oval, 16f, 16f, paint);
        paint.setColor(0xFF8AA0B8);
        oval.set(cx - 24f, cy - 48f, cx + 24f, cy - 8f);
        canvas.drawRoundRect(oval, 8f, 8f, paint);
        paint.setColor(0xFF22262B);
        canvas.drawRoundRect(cx - 48f, cy - 48f, cx - 36f, cy - 16f, 4f, 4f, paint);
        canvas.drawRoundRect(cx + 36f, cy - 48f, cx + 48f, cy - 16f, 4f, 4f, paint);
        canvas.drawRoundRect(cx - 48f, cy + 16f, cx - 36f, cy + 48f, 4f, 4f, paint);
        canvas.drawRoundRect(cx + 36f, cy + 16f, cx + 48f, cy + 48f, 4f, 4f, paint);
        paint.setColor(0xFFF0D78C);
        canvas.drawCircle(cx - 16f, cy - 66f, 4f, paint);
        canvas.drawCircle(cx + 16f, cy - 66f, 4f, paint);
    }

    private void drawDial(Canvas canvas, float cx, float cy, float radius, float value, float max, boolean tach) {
        paint.setShader(new RadialGradient(cx, cy, radius, 0xFF143024, 0xFF07140E, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10f);
        paint.setColor(0xFF1F8A4C);
        canvas.drawCircle(cx, cy, radius - 8f, paint);
        paint.setStrokeWidth(4f);
        paint.setColor(0xFFD4A84B);
        canvas.drawCircle(cx, cy, radius - 18f, paint);
        paint.setStrokeWidth(3f);
        int steps = tach ? 8 : 11;
        for (int i = 0; i <= steps; i++) {
            float fraction = i / (float) steps;
            float angle = (float) Math.toRadians(140f + 260f * fraction);
            float x1 = cx + (float) Math.cos(angle) * (radius - 36f);
            float y1 = cy + (float) Math.sin(angle) * (radius - 36f);
            float x2 = cx + (float) Math.cos(angle) * (radius - 22f);
            float y2 = cy + (float) Math.sin(angle) * (radius - 22f);
            paint.setColor(tach && i >= 7 ? 0xFFE25B4A : 0xFFD4A84B);
            canvas.drawLine(x1, y1, x2, y2, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(tach ? 22f : 18f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            String label = tach ? Integer.toString(i) : Integer.toString(i * 20);
            float lx = cx + (float) Math.cos(angle) * (radius - 54f);
            float ly = cy + (float) Math.sin(angle) * (radius - 54f) + 7f;
            canvas.drawText(label, lx, ly, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
        paint.setStyle(Paint.Style.FILL);
        drawEmblem(canvas, cx, cy - 46f, 28f);
        paint.setColor(0xFFF4F7FB);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        if (tach) {
            paint.setTextSize(64f);
            String rpmText = (rpmKnown || previewStill)
                    ? String.format(Locale.US, "%.2f", rpmShown / 1000f) : "--";
            canvas.drawText(rpmText, cx, cy + 18f, paint);
            paint.setTextSize(18f);
            paint.setColor(0xFFD4A84B);
            canvas.drawText("x1000 rpm", cx, cy + 44f, paint);
            paint.setTextSize(16f);
            paint.setColor(0xFFB7C4B8);
            canvas.drawText("Load " + loadText, cx, cy + 68f, paint);
        } else {
            paint.setTextSize(78f);
            String number = (speedKnown || previewStill)
                    ? Integer.toString(Math.round(speedShown)) : "--";
            canvas.drawText(number, cx, cy + 24f, paint);
            paint.setTextSize(20f);
            paint.setColor(0xFFD4A84B);
            canvas.drawText("km/h", cx, cy + 52f, paint);
        }
        float shown = tach ? rpmShown / 1000f : speedShown;
        float fraction = max <= 0f ? 0f : Math.max(0f, Math.min(1f, shown / max));
        float angle = (float) Math.toRadians(140f + 260f * fraction);
        float nx = cx + (float) Math.cos(angle) * (radius - 70f);
        float ny = cy + (float) Math.sin(angle) * (radius - 70f);
        paint.setColor(0xFFF0D78C);
        paint.setStrokeWidth(6f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx, cy, nx, ny, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0C1F14);
        canvas.drawCircle(cx, cy, 8f, paint);
        paint.setColor(0xFFD4A84B);
        canvas.drawCircle(cx, cy, 4f, paint);
    }

    private void drawEmblem(Canvas canvas, float cx, float cy, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xFFD4A84B);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(-32f);
        canvas.drawLine(-size, 0f, size, 0f, paint);
        canvas.rotate(64f);
        canvas.drawLine(-size, 0f, size, 0f, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF2E8B57);
        canvas.drawCircle(cx, cy - size * 0.15f, 3f, paint);
        paint.setStrokeWidth(2.5f);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(cx, cy - 2f, cx - size * 0.45f, cy - size * 0.7f, paint);
        canvas.drawLine(cx, cy - 2f, cx - size * 0.15f, cy - size * 0.85f, paint);
        canvas.drawLine(cx, cy - 2f, cx + size * 0.15f, cy - size * 0.85f, paint);
        canvas.drawLine(cx, cy - 2f, cx + size * 0.45f, cy - size * 0.7f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawTopBar(Canvas canvas) {
        paint.setColor(0xCC0C1F14);
        canvas.drawRoundRect(280f, 18f, 1640f, 92f, 18f, 18f, paint);
        String[] gears = {"P", "R", "N", "D"};
        float x = 360f;
        for (String item : gears) {
            boolean active = item.equals(gear);
            if (active) {
                paint.setColor(0xFF1F8A4C);
                canvas.drawRoundRect(x - 28f, 32f, x + 28f, 78f, 10f, 10f, paint);
            }
            paint.setColor(active ? 0xFFF4F7FB : 0xFF8FA392);
            paint.setTextSize(32f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            canvas.drawText(item, x, 68f, paint);
            x += 78f;
        }
        if (debug && gearRaw.length() > 0) {
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(16f);
            paint.setColor(0xFFD4A84B);
            canvas.drawText(gearRaw, x + 8f, 66f, paint);
            paint.setTextAlign(Paint.Align.CENTER);
        }
        paint.setColor(0xFFD4A84B);
        paint.setTextSize(26f);
        canvas.drawText(GaugeFormat.driveMode(driveMode), 960f, 66f, paint);
        paint.setColor(0xFFF4F7FB);
        paint.setTextSize(28f);
        String time = clockOverride != null ? clockOverride : clock.format(new Date());
        canvas.drawText(time, 1420f, 66f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xFF7DDEA0);
        canvas.drawRoundRect(1505f, 36f, 1548f, 72f, 4f, 4f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(1512f, 44f, 1536f, 64f, paint);
        canvas.drawRect(1548f, 48f, 1554f, 60f, paint);
    }

    private void drawBottomBar(Canvas canvas) {
        paint.setColor(0xCC0C1F14);
        canvas.drawRoundRect(280f, 630f, 1640f, 700f, 16f, 16f, paint);
        paint.setColor(0xFFF4F7FB);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(26f);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        String temp = GaugeFormat.validTemp(outsideC)
                ? Math.round(outsideC) + "°C" : "--°C";
        canvas.drawText(temp, 430f, 676f, paint);
        paint.setColor(0xFFD4A84B);
        drawPump(canvas, 620f, 666f);
        paint.setColor(0xFFF4F7FB);
        String fuelText = fuel >= 0 && fuel <= 100 ? fuel + "%" : "--%";
        canvas.drawText(fuelText, 700f, 676f, paint);
        paint.setColor(lowBeam || highBeam ? 0xFF7DDEA0 : 0xFF5C6B60);
        canvas.drawText(highBeam ? "HIGH" : "LOW", 960f, 676f, paint);
        paint.setColor(0xFF8FA392);
        canvas.drawText("LANE", 1160f, 676f, paint);
        paint.setColor(0xFFF4F7FB);
        String range = rangeKm >= 0 && rangeKm < 5000 ? rangeKm + " km" : "-- km";
        canvas.drawText(range, 1400f, 676f, paint);
    }

    private void drawPump(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawRoundRect(x - 10f, y - 16f, x + 6f, y + 10f, 2f, 2f, paint);
        canvas.drawLine(x + 6f, y - 8f, x + 16f, y - 8f, paint);
        canvas.drawLine(x + 16f, y - 8f, x + 16f, y + 2f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBlinkers(Canvas canvas) {
        boolean phase = previewStill || (System.currentTimeMillis() / 420L) % 2L == 0L;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(leftTurn && phase ? 0xFF3DDC7A : 0xFF243028);
        path.reset();
        path.moveTo(70f, 150f);
        path.lineTo(150f, 100f);
        path.lineTo(150f, 130f);
        path.lineTo(230f, 130f);
        path.lineTo(230f, 170f);
        path.lineTo(150f, 170f);
        path.lineTo(150f, 200f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(rightTurn && phase ? 0xFF3DDC7A : 0xFF243028);
        path.reset();
        path.moveTo(1850f, 150f);
        path.lineTo(1770f, 100f);
        path.lineTo(1770f, 130f);
        path.lineTo(1690f, 130f);
        path.lineTo(1690f, 170f);
        path.lineTo(1770f, 170f);
        path.lineTo(1770f, 200f);
        path.close();
        canvas.drawPath(path, paint);
        if (hazard && phase) {
            paint.setColor(0xFFF0B429);
            path.reset();
            path.moveTo(960f, 110f);
            path.lineTo(990f, 160f);
            path.lineTo(930f, 160f);
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    private void drawDebug(Canvas canvas) {
        paint.setColor(0xE0101612);
        canvas.drawRoundRect(40f, 210f, 560f, 620f, 12f, 12f, paint);
        paint.setColor(0xFFD4A84B);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(18f);
        paint.setTypeface(Typeface.MONOSPACE);
        canvas.drawText("READ ONLY  " + gearRaw, 56f, 240f, paint);
        paint.setColor(0xFFF4F7FB);
        paint.setTextSize(16f);
        float y = 268f;
        if (debugRows == null || debugRows.isEmpty()) {
            canvas.drawText("no samples yet", 56f, y, paint);
            return;
        }
        for (Map.Entry<String, String> entry : debugRows.entrySet()) {
            if (y > 600f) {
                break;
            }
            canvas.drawText(entry.getKey() + " = " + entry.getValue(), 56f, y, paint);
            y += 22f;
        }
    }
}
