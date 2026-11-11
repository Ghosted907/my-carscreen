package com.ghosted907.clusterdiag;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.ghosted907.clusterdiag.logic.GaugeFormat;
import com.ghosted907.clusterdiag.logic.SignalIds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only gauges for the custom cluster. Never calls setIntProperty (IALCar 1 or 11)
 * or any CiService setter.
 */
final class ClusterGaugeClient {
    static final String TAG = "ClusterDiag";

    static final int SPEED_PROP = 559941888;
    static final int RPM_PROP = 559944245;
    static final int TEMP_PROP = 559940264;
    static final int TEMP_FALLBACK = 559953700;
    static final int ENGINE_TEMP = 559940105;
    static final int ODOMETER = 291504644;
    static final int CRUISE_SET = 559941688;
    static final int LKA_STATUS = 557843733;
    static final int LDW_STATUS = 557843283;
    static final int FCW_STATUS = 557843461;
    static final int LOW_BEAM = 557843647;
    static final int HIGH_BEAM = 557843648;

    private static final int GET_INT = 2;
    private static final int GET_FLOAT = 9;
    private static final int REG_PROP = 5;
    private static final int UNREG_PROP = 6;
    private static final int REG_SPEED = 17;
    private static final int UNREG_SPEED = 18;
    private static final int REG_RPM = 25;
    private static final int UNREG_RPM = 26;

    private static final int CI_FUEL = 56;
    private static final int CI_DTE = 57;
    private static final int CI_MODE = 58;
    private static final int CI_SPEED = 102;

    private static final String SPEED_LISTENER = "com.autolink.adapterinterface.car.IALCarSpeedListener";
    private static final String RPM_LISTENER = "com.autolink.adapterinterface.car.IALCarEngineSpeedListener";
    private static final String CAR_PKG = "com.autolink.carservice";
    private static final String CAR_ACTION = "android.autolink.IAutoLinkCar";

    static final class Reading {
        float speed = Float.NaN;
        float rpm = Float.NaN;
        float outsideC = Float.NaN;
        int fuel = Integer.MIN_VALUE;
        int rangeKm = Integer.MIN_VALUE;
        int driveMode = Integer.MIN_VALUE;
        int gearProp = GaugeFormat.GEAR_POSITION;
        int gearValue = Integer.MIN_VALUE;
        String gearLetter = "";
        boolean leftTurn;
        boolean rightTurn;
        boolean lowBeam;
        boolean highBeam;
        float outsideFallback = Float.NaN;
        float engineC = Float.NaN;
        float odometerKm = Float.NaN;
        float cruiseKmh = Float.NaN;
        int lka = Integer.MIN_VALUE;
        int ldw = Integer.MIN_VALUE;
        int fcw = Integer.MIN_VALUE;
        final Map<String, String> debug = new LinkedHashMap<String, String>();
    }

    interface Callback {
        void onReading(Reading reading);

        void onGaugeLog(String line);
    }

    private final Context context;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Reading reading = new Reading();
    private final SpeedBinder speedBinder = new SpeedBinder();
    private final RpmBinder rpmBinder = new RpmBinder();
    private final BeamBinder beamBinder = new BeamBinder();

    private volatile boolean stopped;
    private volatile IBinder car;
    private volatile IBinder cluster;
    private volatile boolean carOk;
    private volatile boolean carSpeedValid;
    private volatile boolean clusterOk;
    private volatile boolean carChecked;
    private volatile boolean clusterChecked;
    private boolean carBound;
    private boolean clusterBound;
    private boolean listenersOn;
    private Thread poll;
    private int tick;
    private long lastSnapAt;
    private volatile int gearProp = GaugeFormat.GEAR_POSITION;
    private volatile String gearMap = "";
    private volatile int[] probeIds = new int[0];
    private volatile boolean laylReads;
    private final Map<Integer, Integer> lastGear = new LinkedHashMap<Integer, Integer>();
    private final Map<Integer, Integer> lastProbe = new LinkedHashMap<Integer, Integer>();

    private final ServiceConnection carConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            car = service;
            carOk = false;
            carChecked = false;
            listenersOn = false;
            log("IALCar gauge connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            car = null;
            carOk = false;
            carSpeedValid = false;
            carChecked = false;
            listenersOn = false;
            log("IALCar gauge disconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            onServiceDisconnected(name);
            log("IALCar gauge binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            log("IALCar gauge null binding");
            unbind(carConn, true);
        }
    };

    private final ServiceConnection clusterConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            cluster = service;
            clusterOk = false;
            clusterChecked = false;
            log("CiService gauge connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            cluster = null;
            clusterOk = false;
            clusterChecked = false;
            log("CiService gauge disconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            onServiceDisconnected(name);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            log("CiService gauge null binding");
            unbind(clusterConn, false);
        }
    };

    ClusterGaugeClient(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    void applyConfig(int prop, String map, int[] probe) {
        gearProp = prop == 0 ? GaugeFormat.GEAR_POSITION : prop;
        gearMap = map == null ? "" : map;
        probeIds = probe == null ? new int[0] : probe;
        log("gear config prop=" + gearProp + " map=" + gearMap + " probe=" + probeIds.length);
    }

    /** Extra read-only polls used only by Layl Najd. Getters only, never a setter. */
    void enableLaylReads() {
        laylReads = true;
    }

    void start() {
        if (poll != null) {
            return;
        }
        stopped = false;
        bindCar();
        bindCluster();
        poll = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopped) {
                    pollOnce();
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException e) {
                        if (stopped) {
                            return;
                        }
                    }
                }
            }
        }, "cluster-diag-gauges");
        poll.setDaemon(true);
        poll.start();
    }

    void stop() {
        stopped = true;
        Thread thread = poll;
        poll = null;
        if (thread != null) {
            thread.interrupt();
        }
        dropListeners();
        unbind(carConn, true);
        unbind(clusterConn, false);
        car = null;
        cluster = null;
    }

    private void bindCar() {
        Intent intent = new Intent(CAR_ACTION);
        intent.setPackage(CAR_PKG);
        log("bind IALCar gauges");
        try {
            carBound = context.bindService(intent, carConn, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            carBound = false;
            log("IALCar gauge SecurityException " + e.getMessage());
        }
        if (!carBound) {
            log("IALCar gauge bindService returned false");
        }
    }

    private void bindCluster() {
        Intent intent = new Intent(ClusterClient.ACTION);
        intent.setComponent(new ComponentName(ClusterClient.PACKAGE, ClusterClient.CLASS_NAME));
        log("bind CiService gauges");
        try {
            clusterBound = context.bindService(intent, clusterConn, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            clusterBound = false;
            log("CiService gauge SecurityException " + e.getMessage());
        }
        if (!clusterBound) {
            log("CiService gauge bindService returned false");
        }
    }

    private void unbind(ServiceConnection connection, boolean isCar) {
        boolean bound = isCar ? carBound : clusterBound;
        if (!bound) {
            return;
        }
        try {
            context.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
            // Already unbound.
        }
        if (isCar) {
            carBound = false;
        } else {
            clusterBound = false;
        }
    }

    private void pollOnce() {
        tick++;
        IBinder carBinder = car;
        if (carBinder != null && !carChecked) {
            carChecked = true;
            carOk = match(carBinder, SignalIds.CAR_DESCRIPTOR, "IALCar");
        }
        if (carBinder != null && carOk) {
            if (!listenersOn) {
                listenersOn = registerAll(carBinder);
            }
            readFloat(carBinder, SPEED_PROP, "speed");
            readFloat(carBinder, RPM_PROP, "rpm");
            if (tick % 5 == 0) {
                readFloat(carBinder, TEMP_PROP, "temp");
                readInt(carBinder, SignalIds.SCENE_LEFT, "turnL");
                readInt(carBinder, SignalIds.SCENE_RIGHT, "turnR");
                readInt(carBinder, LOW_BEAM, "lowBeam");
                readInt(carBinder, HIGH_BEAM, "highBeam");
                for (int id : GaugeFormat.GEAR_CANDIDATES) {
                    readGear(carBinder, id);
                }
                int[] extra = probeIds;
                for (int i = 0; i < extra.length; i++) {
                    readProbe(carBinder, extra[i]);
                }
            }
            if (laylReads && (tick == 1 || tick % 10 == 0)) {
                readLayl(carBinder);
            }
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastSnapAt >= 2000L) {
                lastSnapAt = now;
                log(GaugeFormat.gearSnap(lastGear));
            }
        } else {
            carSpeedValid = false;
        }
        IBinder ci = cluster;
        if (ci != null && !clusterChecked) {
            clusterChecked = true;
            clusterOk = match(ci, com.ghosted907.clusterdiag.logic.ClusterTransactions.DESCRIPTOR, "CiService");
        }
        if (ci != null && clusterOk && tick % 5 == 0) {
            readCi(ci);
        }
        if (tick % 2 == 0) {
            publish();
        }
    }

    private boolean match(IBinder binder, String expected, String name) {
        try {
            String remote = binder.getInterfaceDescriptor();
            log(name + " descriptor=" + remote);
            if (!expected.equals(remote)) {
                log(name + " descriptor mismatch, gauge reads skipped");
                return false;
            }
            return true;
        } catch (RemoteException e) {
            log(name + " descriptor " + e.getClass().getSimpleName() + " " + e.getMessage());
            return false;
        }
    }

    private void readFloat(IBinder binder, int prop, String key) {
        try {
            float value = getFloatProperty(binder, prop);
            synchronized (reading) {
                if ("speed".equals(key)) {
                    carSpeedValid = GaugeFormat.validSpeed(value);
                    if (carSpeedValid) {
                        reading.speed = value;
                    }
                } else if ("rpm".equals(key) && GaugeFormat.validRpm(value)) {
                    reading.rpm = value;
                } else if ("temp".equals(key)) {
                    reading.outsideC = value;
                } else if ("temp2".equals(key)) {
                    reading.outsideFallback = value;
                } else if ("engine".equals(key)) {
                    reading.engineC = value;
                } else if ("odo".equals(key)) {
                    reading.odometerKm = value;
                } else if ("cruise".equals(key)) {
                    reading.cruiseKmh = value;
                }
                reading.debug.put(key + " " + prop, Float.toString(value));
            }
        } catch (DeadObjectException e) {
            car = null;
            carOk = false;
            carSpeedValid = false;
            listenersOn = false;
            log("IALCar dead during " + key);
        } catch (RemoteException e) {
            log("getFloatProperty " + prop + " " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void readInt(IBinder binder, int prop, String key) {
        try {
            int value = getIntProperty(binder, prop);
            synchronized (reading) {
                if ("turnL".equals(key)) {
                    reading.leftTurn = value == 1;
                } else if ("turnR".equals(key)) {
                    reading.rightTurn = value == 1;
                } else if ("lowBeam".equals(key)) {
                    reading.lowBeam = value == 1;
                } else if ("highBeam".equals(key)) {
                    reading.highBeam = value == 1;
                } else if ("lka".equals(key)) {
                    reading.lka = value;
                } else if ("ldw".equals(key)) {
                    reading.ldw = value;
                } else if ("fcw".equals(key)) {
                    reading.fcw = value;
                }
                reading.debug.put(key + " " + prop, Integer.toString(value));
            }
        } catch (DeadObjectException e) {
            car = null;
            carOk = false;
            carSpeedValid = false;
            listenersOn = false;
            log("IALCar dead during " + key);
        } catch (RemoteException e) {
            log("getIntProperty " + prop + " " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void readGear(IBinder binder, int prop) {
        try {
            int value = getIntProperty(binder, prop);
            Integer previous = lastGear.get(Integer.valueOf(prop));
            if (previous == null || previous.intValue() != value) {
                lastGear.put(Integer.valueOf(prop), Integer.valueOf(value));
                Log.i(TAG, "GEAR id=" + prop + " val=" + value);
                log("GEAR id=" + prop + " val=" + value);
            }
            synchronized (reading) {
                reading.debug.put("GEAR " + prop, Integer.toString(value));
            }
        } catch (DeadObjectException e) {
            car = null;
            carOk = false;
            carSpeedValid = false;
            carChecked = false;
            listenersOn = false;
            log("IALCar dead during gear " + prop);
        } catch (RemoteException e) {
            log("gear " + prop + " " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void readProbe(IBinder binder, int prop) {
        try {
            int value = getIntProperty(binder, prop);
            Integer previous = lastProbe.get(Integer.valueOf(prop));
            if (previous == null || previous.intValue() != value) {
                lastProbe.put(Integer.valueOf(prop), Integer.valueOf(value));
                log("PROBE id=" + prop + " val=" + value);
            }
            synchronized (reading) {
                reading.debug.put("PROBE " + prop, Integer.toString(value));
            }
        } catch (DeadObjectException e) {
            car = null;
            carOk = false;
            carSpeedValid = false;
            carChecked = false;
            listenersOn = false;
            log("IALCar dead during probe " + prop);
        } catch (RemoteException e) {
            log("PROBE id=" + prop + " err=" + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void readLayl(IBinder binder) {
        readFloat(binder, TEMP_FALLBACK, "temp2");
        readFloat(binder, ENGINE_TEMP, "engine");
        readFloat(binder, ODOMETER, "odo");
        readFloat(binder, CRUISE_SET, "cruise");
        readInt(binder, LKA_STATUS, "lka");
        readInt(binder, LDW_STATUS, "ldw");
        readInt(binder, FCW_STATUS, "fcw");
        Integer gear = lastGear.get(Integer.valueOf(GaugeFormat.GEAR_POSITION));
        synchronized (reading) {
            Log.i("LaylNajd", "raw temp2=" + reading.outsideFallback
                    + " engine=" + reading.engineC
                    + " odo=" + reading.odometerKm
                    + " cruise=" + reading.cruiseKmh
                    + " lka=" + reading.lka
                    + " ldw=" + reading.ldw
                    + " fcw=" + reading.fcw
                    + " rpm=" + reading.rpm
                    + " speed=" + reading.speed
                    + " gear557843105=" + (gear == null ? "none" : gear.toString()));
        }
    }

    private void readCi(IBinder binder) {
        try {
            int fuel = ciInt(binder, CI_FUEL);
            int range = ciInt(binder, CI_DTE);
            int mode = ciInt(binder, CI_MODE);
            float speed = ciFloat(binder, CI_SPEED);
            synchronized (reading) {
                reading.fuel = fuel;
                reading.rangeKm = range;
                reading.driveMode = mode;
                reading.speed = com.ghosted907.clusterdiag.logic.SpeedSource.choose(
                        carSpeedValid, reading.speed, speed);
                reading.debug.put("fuel 56", Integer.toString(fuel));
                reading.debug.put("dte 57", Integer.toString(range));
                reading.debug.put("mode 58", Integer.toString(mode));
                reading.debug.put("ciSpeed 102", Float.toString(speed));
            }
        } catch (DeadObjectException e) {
            cluster = null;
            clusterOk = false;
            log("CiService gauge dead");
        } catch (RemoteException e) {
            log("CiService gauge " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private boolean registerAll(IBinder binder) {
        boolean speed = register(binder, speedBinder, REG_SPEED, "speed");
        boolean rpm = register(binder, rpmBinder, REG_RPM, "rpm");
        boolean beams = register(binder, beamBinder, REG_PROP, "property");
        return speed && rpm && beams;
    }

    private void dropListeners() {
        IBinder binder = car;
        if (binder == null || !listenersOn) {
            listenersOn = false;
            return;
        }
        unregister(binder, speedBinder, UNREG_SPEED, "speed");
        unregister(binder, rpmBinder, UNREG_RPM, "rpm");
        unregister(binder, beamBinder, UNREG_PROP, "property");
        listenersOn = false;
    }

    private boolean register(IBinder binder, Binder callbackBinder, int code, String name) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SignalIds.CAR_DESCRIPTOR);
            data.writeStrongBinder(callbackBinder);
            boolean delivered = binder.transact(code, data, reply, 0);
            reply.readException();
            log("IALCar register " + name + " code " + code + " delivered=" + delivered);
            return delivered;
        } catch (RemoteException e) {
            log("IALCar register " + name + " " + e.getClass().getSimpleName() + " " + e.getMessage());
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void unregister(IBinder binder, Binder callbackBinder, int code, String name) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SignalIds.CAR_DESCRIPTOR);
            data.writeStrongBinder(callbackBinder);
            binder.transact(code, data, reply, 0);
            reply.readException();
            log("IALCar unregister " + name);
        } catch (RemoteException e) {
            log("IALCar unregister " + name + " " + e.getMessage());
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static int getIntProperty(IBinder binder, int prop) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SignalIds.CAR_DESCRIPTOR);
            data.writeInt(prop);
            data.writeInt(0);
            boolean delivered = binder.transact(GET_INT, data, reply, 0);
            reply.readException();
            if (!delivered) {
                throw new RemoteException("getIntProperty transact false");
            }
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static float getFloatProperty(IBinder binder, int prop) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SignalIds.CAR_DESCRIPTOR);
            data.writeInt(prop);
            data.writeInt(0);
            boolean delivered = binder.transact(GET_FLOAT, data, reply, 0);
            reply.readException();
            if (!delivered) {
                throw new RemoteException("getFloatProperty transact false");
            }
            return reply.readFloat();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static int ciInt(IBinder binder, int code) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(com.ghosted907.clusterdiag.logic.ClusterTransactions.DESCRIPTOR);
            boolean delivered = binder.transact(code, data, reply, 0);
            reply.readException();
            if (!delivered) {
                throw new RemoteException("CiService transact false " + code);
            }
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static float ciFloat(IBinder binder, int code) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(com.ghosted907.clusterdiag.logic.ClusterTransactions.DESCRIPTOR);
            boolean delivered = binder.transact(code, data, reply, 0);
            reply.readException();
            if (!delivered) {
                throw new RemoteException("CiService transact false " + code);
            }
            return reply.readFloat();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void publish() {
        final Reading copy = copyReading();
        main.post(new Runnable() {
            @Override
            public void run() {
                callback.onReading(copy);
            }
        });
    }

    private Reading copyReading() {
        Reading copy = new Reading();
        synchronized (reading) {
            copy.speed = reading.speed;
            copy.rpm = reading.rpm;
            copy.outsideC = reading.outsideC;
            copy.fuel = reading.fuel;
            copy.rangeKm = reading.rangeKm;
            copy.driveMode = reading.driveMode;
            copy.gearProp = gearProp;
            Integer selected = lastGear.get(Integer.valueOf(gearProp));
            copy.gearValue = selected == null ? Integer.MIN_VALUE : selected.intValue();
            copy.gearLetter = copy.gearValue == Integer.MIN_VALUE
                    ? ""
                    : GaugeFormat.letterFor(copy.gearValue, gearMap, gearProp);
            copy.leftTurn = reading.leftTurn;
            copy.rightTurn = reading.rightTurn;
            copy.lowBeam = reading.lowBeam;
            copy.highBeam = reading.highBeam;
            copy.outsideFallback = reading.outsideFallback;
            copy.engineC = reading.engineC;
            copy.odometerKm = reading.odometerKm;
            copy.cruiseKmh = reading.cruiseKmh;
            copy.lka = reading.lka;
            copy.ldw = reading.ldw;
            copy.fcw = reading.fcw;
            copy.debug.putAll(reading.debug);
        }
        return copy;
    }

    private void acceptSpeed(float value, int valid) {
        if (valid == 0 || !GaugeFormat.validSpeed(value)) {
            return;
        }
        synchronized (reading) {
            carSpeedValid = true;
            reading.speed = value;
            reading.debug.put("speedListener", value + " valid=" + valid);
        }
    }

    private void acceptRpm(float value, int valid) {
        if (valid == 0 || !GaugeFormat.validRpm(value)) {
            return;
        }
        synchronized (reading) {
            reading.rpm = value;
            reading.debug.put("rpmListener", value + " valid=" + valid);
        }
    }

    private void log(final String message) {
        Log.i(TAG, message);
        main.post(new Runnable() {
            @Override
            public void run() {
                callback.onGaugeLog(message);
            }
        });
    }

    private final class SpeedBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(SPEED_LISTENER);
                return true;
            }
            if (code == 1) {
                data.enforceInterface(SPEED_LISTENER);
                acceptSpeed(data.readFloat(), data.readInt());
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    private final class RpmBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(RPM_LISTENER);
                return true;
            }
            if (code == 1) {
                data.enforceInterface(RPM_LISTENER);
                acceptRpm(data.readFloat(), data.readInt());
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    private final class BeamBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(SignalIds.LISTENER_DESCRIPTOR);
                return true;
            }
            if (code != 1) {
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(SignalIds.LISTENER_DESCRIPTOR);
            try {
                if (data.readInt() != 0) {
                    int id = data.readInt();
                    data.readString();
                    Object value = data.readValue(BeamBinder.class.getClassLoader());
                    int number = value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
                    synchronized (reading) {
                        if (id == SignalIds.SCENE_LEFT) {
                            reading.leftTurn = number == 1;
                        } else if (id == SignalIds.SCENE_RIGHT) {
                            reading.rightTurn = number == 1;
                        } else if (id == LOW_BEAM) {
                            reading.lowBeam = number == 1;
                        } else if (id == HIGH_BEAM) {
                            reading.highBeam = number == 1;
                        }
                    }
                }
            } catch (RuntimeException e) {
                log("beam listener " + e.getClass().getSimpleName());
            }
            return true;
        }
    }
}
