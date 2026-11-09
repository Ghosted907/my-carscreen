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

import com.ghosted907.clusterdiag.logic.SignalIds;
import com.ghosted907.clusterdiag.logic.SignalLights;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only turn and hazard indicators. The only transactions issued are
 * IALCar get/register/unregister and IVehicleControl getInt.
 */
final class CarSignalClient {
    private static final String TAG = "ClusterDiag";
    private static final long POLL_MS = 300L;

    private static final String CAR_PACKAGE = "com.autolink.carservice";
    private static final String CAR_ACTION = "android.autolink.IAutoLinkCar";
    private static final String VEHICLE_PACKAGE = "com.autolink.app.vehicleservice";
    private static final String VEHICLE_ACTION = "com.autolink.vehicleservice";

    interface Listener {
        void onSignalLog(String line);

        void onLamps(boolean left, boolean right, boolean hazard);

        void onSignalTable(String table);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final Map<Integer, Integer> values = new HashMap<Integer, Integer>();
    private final Map<Integer, Long> changedAt = new HashMap<Integer, Long>();
    private final Map<Integer, String> rawText = new HashMap<Integer, String>();

    private final PropertyListener propertyListener = new PropertyListener();

    private volatile boolean stopped;
    private volatile IBinder carBinder;
    private volatile IBinder vehicleBinder;
    private volatile boolean carDescribed;
    private volatile boolean vehicleDescribed;
    private boolean carBound;
    private boolean vehicleBound;
    private volatile boolean registered;
    private volatile boolean registerAttempted;
    private Thread pollThread;
    private long leftOnAt;
    private long rightOnAt;
    private long hazardOnAt;
    private String lastCarError = "";
    private String lastVehicleError = "";

    private final ServiceConnection carConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            carBinder = service;
            carDescribed = false;
            registerAttempted = false;
            log("IALCar onServiceConnected");
            ensurePoll();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            carBinder = null;
            carDescribed = false;
            registered = false;
            registerAttempted = false;
            log("IALCar disconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            carBinder = null;
            carDescribed = false;
            registered = false;
            registerAttempted = false;
            log("IALCar binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            log("IALCar null binding");
            unbindCar();
        }
    };

    private final ServiceConnection vehicleConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            vehicleBinder = service;
            vehicleDescribed = false;
            log("IVehicleControl onServiceConnected");
            ensurePoll();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            vehicleBinder = null;
            vehicleDescribed = false;
            log("IVehicleControl disconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            vehicleBinder = null;
            vehicleDescribed = false;
            log("IVehicleControl binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            log("IVehicleControl null binding");
            unbindVehicle();
        }
    };

    CarSignalClient(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void start() {
        if (carBound || vehicleBound) {
            return;
        }
        stopped = false;
        bindCar();
        bindVehicle();
        ensurePoll();
    }

    void stop() {
        stopped = true;
        Thread poll = pollThread;
        if (poll != null) {
            poll.interrupt();
        }
        unregisterListener();
        unbindCar();
        unbindVehicle();
        carBinder = null;
        vehicleBinder = null;
        carDescribed = false;
        vehicleDescribed = false;
        registered = false;
    }

    private void bindCar() {
        Intent intent = new Intent(CAR_ACTION);
        intent.setPackage(CAR_PACKAGE);
        log("bind IALCar " + CAR_ACTION);
        try {
            carBound = context.bindService(intent, carConnection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            carBound = false;
            log("IALCar SecurityException " + e.getMessage());
        }
        if (!carBound) {
            log("IALCar bindService returned false");
        }
    }

    private void bindVehicle() {
        Intent intent = new Intent(VEHICLE_ACTION);
        intent.setPackage(VEHICLE_PACKAGE);
        log("bind IVehicleControl " + VEHICLE_ACTION);
        try {
            vehicleBound = context.bindService(intent, vehicleConnection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            vehicleBound = false;
            log("IVehicleControl SecurityException " + e.getMessage());
        }
        if (!vehicleBound) {
            log("IVehicleControl bindService returned false");
        }
    }

    private void unbindCar() {
        if (!carBound) {
            return;
        }
        try {
            context.unbindService(carConnection);
        } catch (IllegalArgumentException ignored) {
            // Already unbound.
        }
        carBound = false;
    }

    private void unbindVehicle() {
        if (!vehicleBound) {
            return;
        }
        try {
            context.unbindService(vehicleConnection);
        } catch (IllegalArgumentException ignored) {
            // Already unbound.
        }
        vehicleBound = false;
    }

    private void ensurePoll() {
        if (pollThread != null && pollThread.isAlive()) {
            return;
        }
        pollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopped) {
                    pollOnce();
                    try {
                        Thread.sleep(POLL_MS);
                    } catch (InterruptedException e) {
                        if (stopped) {
                            return;
                        }
                    }
                }
            }
        }, "cluster-diag-signals");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void pollOnce() {
        IBinder car = carBinder;
        if (car != null) {
            if (!carDescribed) {
                carDescribed = confirmDescriptor(car, SignalIds.CAR_DESCRIPTOR, "IALCar");
                if (carDescribed) {
                    registerListener(car);
                }
            }
            if (carDescribed) {
                for (int id : SignalIds.CAR_WATCH) {
                    try {
                        int value = getIntProperty(car, id);
                        publishNumber(id, value, "poll");
                        lastCarError = "";
                    } catch (DeadObjectException e) {
                        carBinder = null;
                        carDescribed = false;
                        registered = false;
                        registerAttempted = false;
                        logOnceCar("IALCar dead " + e.getMessage());
                        break;
                    } catch (RemoteException e) {
                        logOnceCar("IALCar getIntProperty " + id + " " + e.getClass().getSimpleName()
                                + " " + e.getMessage());
                    }
                }
            }
        }
        IBinder vehicle = vehicleBinder;
        if (vehicle != null) {
            if (!vehicleDescribed) {
                vehicleDescribed = confirmDescriptor(vehicle, SignalIds.VEHICLE_DESCRIPTOR, "IVehicleControl");
            }
            if (vehicleDescribed) {
                readVehicle(vehicle, SignalIds.VEHICLE_LEFT);
                readVehicle(vehicle, SignalIds.VEHICLE_RIGHT);
            }
        }
    }

    private void readVehicle(IBinder vehicle, int key) {
        try {
            int value = getVehicleInt(vehicle, key);
            publishNumber(key, value, "vehicle");
            lastVehicleError = "";
        } catch (DeadObjectException e) {
            vehicleBinder = null;
            vehicleDescribed = false;
            logOnceVehicle("IVehicleControl dead " + e.getMessage());
        } catch (RemoteException e) {
            logOnceVehicle("IVehicleControl getInt " + key + " " + e.getClass().getSimpleName()
                    + " " + e.getMessage());
        }
    }

    private boolean confirmDescriptor(IBinder binder, String expected, String name) {
        try {
            String remote = binder.getInterfaceDescriptor();
            log(name + " descriptor=" + remote);
            if (!expected.equals(remote)) {
                log(name + " descriptor mismatch, reads skipped");
                return false;
            }
            return true;
        } catch (RemoteException e) {
            log(name + " getInterfaceDescriptor " + e.getClass().getSimpleName() + " " + e.getMessage());
            return false;
        }
    }

    /** IALCar transaction 2. Area on the wire is 0, as verified. No setter exists here. */
    private static int getIntProperty(IBinder binder, int prop) throws RemoteException {
        if (!SignalIds.carTransactionAllowed(SignalIds.CAR_GET_INT)) {
            throw new RemoteException("blocked");
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SignalIds.CAR_DESCRIPTOR);
            data.writeInt(prop);
            data.writeInt(0);
            boolean delivered = binder.transact(SignalIds.CAR_GET_INT, data, reply, 0);
            reply.readException();
            if (!delivered) {
                throw new RemoteException("transact returned false for getIntProperty");
            }
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /** IVehicleControl transaction 5. Read only. */
    private static int getVehicleInt(IBinder binder, int key) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SignalIds.VEHICLE_DESCRIPTOR);
            data.writeInt(key);
            boolean delivered = binder.transact(SignalIds.VEHICLE_GET_INT, data, reply, 0);
            reply.readException();
            if (!delivered) {
                throw new RemoteException("transact returned false for getInt");
            }
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void registerListener(IBinder binder) {
        if (registered || stopped || registerAttempted) {
            return;
        }
        registerAttempted = true;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SignalIds.CAR_DESCRIPTOR);
            data.writeStrongBinder(propertyListener);
            boolean delivered = binder.transact(SignalIds.CAR_REGISTER, data, reply, 0);
            reply.readException();
            if (!delivered) {
                log("IALCar register transact returned false");
                return;
            }
            registered = true;
            log("IALCar registerVehicleControlPropertyListener ok");
        } catch (RemoteException e) {
            log("IALCar register " + e.getClass().getSimpleName() + " " + e.getMessage());
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void unregisterListener() {
        IBinder binder = carBinder;
        if (!registered || binder == null) {
            registered = false;
            return;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SignalIds.CAR_DESCRIPTOR);
            data.writeStrongBinder(propertyListener);
            boolean delivered = binder.transact(SignalIds.CAR_UNREGISTER, data, reply, 0);
            reply.readException();
            log("IALCar unregister delivered=" + delivered);
        } catch (RemoteException e) {
            log("IALCar unregister " + e.getClass().getSimpleName() + " " + e.getMessage());
        } finally {
            reply.recycle();
            data.recycle();
            registered = false;
        }
    }

    private void publishNumber(final int id, final int value, final String source) {
        main.post(new Runnable() {
            @Override
            public void run() {
                applyNumber(id, value, source);
            }
        });
    }

    private void applyNumber(int id, int value, String source) {
        Integer previous = values.get(Integer.valueOf(id));
        boolean changed = previous == null || previous.intValue() != value;
        values.put(Integer.valueOf(id), Integer.valueOf(value));
        rawText.put(Integer.valueOf(id), Integer.toString(value));
        long now = android.os.SystemClock.uptimeMillis();
        if (changed) {
            changedAt.put(Integer.valueOf(id), Long.valueOf(System.currentTimeMillis()));
            log(source + " " + label(id) + " " + id + " -> " + value + meaning(id, value));
        }
        refreshLamps(now);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshLamps(android.os.SystemClock.uptimeMillis());
            }
        }, SignalLights.BLINK_HOLD_MS);
        publishTable();
    }

    private void applyListener(int id, String cls, Object value) {
        String shown = value == null ? "null" : value.toString();
        rawText.put(Integer.valueOf(id), shown + (cls == null ? "" : " " + cls));
        Integer number = asInt(value);
        if (number != null) {
            applyNumber(id, number.intValue(), "listener");
            return;
        }
        changedAt.put(Integer.valueOf(id), Long.valueOf(System.currentTimeMillis()));
        log("listener " + id + " class=" + cls + " value=" + shown);
        publishTable();
    }

    private void refreshLamps(long now) {
        int left = SignalLights.drivingValue(values.get(Integer.valueOf(SignalIds.SCENE_LEFT)),
                values.get(Integer.valueOf(SignalIds.VEHICLE_LEFT)));
        int right = SignalLights.drivingValue(values.get(Integer.valueOf(SignalIds.SCENE_RIGHT)),
                values.get(Integer.valueOf(SignalIds.VEHICLE_RIGHT)));
        Integer hazardRaw = values.get(Integer.valueOf(SignalIds.CLUSTER_HAZARD));
        int hazard = hazardRaw == null ? Integer.MIN_VALUE : hazardRaw.intValue();
        if (left == 1) {
            leftOnAt = now;
        }
        if (right == 1) {
            rightOnAt = now;
        }
        if (hazard == 1) {
            hazardOnAt = now;
        }
        boolean leftOn = SignalLights.lampOn(left, leftOnAt, now);
        boolean rightOn = SignalLights.lampOn(right, rightOnAt, now);
        boolean hazardOn = SignalLights.hazardTriangle(
                leftOn,
                rightOn,
                SignalLights.lampOn(hazard, hazardOnAt, now) ? 1 : 0);
        listener.onLamps(leftOn, rightOn, hazardOn);
    }

    private void publishTable() {
        StringBuilder builder = new StringBuilder();
        appendRow(builder, "يسار مشهد", "scene L", SignalIds.SCENE_LEFT);
        appendRow(builder, "يمين مشهد", "scene R", SignalIds.SCENE_RIGHT);
        appendRow(builder, "عداد يسار", "clus L", SignalIds.CLUSTER_LEFT);
        appendRow(builder, "عداد يمين", "clus R", SignalIds.CLUSTER_RIGHT);
        appendRow(builder, "خطر عداد", "clus haz", SignalIds.CLUSTER_HAZARD);
        appendRow(builder, "إعداد فلاش", "setting", SignalIds.HAZARD_SETTING);
        appendRow(builder, "مركبة يسار", "veh L", SignalIds.VEHICLE_LEFT);
        appendRow(builder, "مركبة يمين", "veh R", SignalIds.VEHICLE_RIGHT);
        listener.onSignalTable(builder.toString().trim());
    }

    private void appendRow(StringBuilder builder, String arabic, String english, int id) {
        String raw = rawText.containsKey(Integer.valueOf(id)) ? rawText.get(Integer.valueOf(id)) : "—";
        Long when = changedAt.get(Integer.valueOf(id));
        String time = when == null ? "—" : clock.format(new Date(when.longValue()));
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(arabic).append(" / ").append(english)
                .append("  ").append(id)
                .append("  ").append(raw)
                .append("  ").append(time);
    }

    private static String label(int id) {
        if (id == SignalIds.SCENE_LEFT) {
            return "scene-left";
        }
        if (id == SignalIds.SCENE_RIGHT) {
            return "scene-right";
        }
        if (id == SignalIds.CLUSTER_LEFT) {
            return "cluster-left";
        }
        if (id == SignalIds.CLUSTER_RIGHT) {
            return "cluster-right";
        }
        if (id == SignalIds.CLUSTER_HAZARD) {
            return "cluster-hazard";
        }
        if (id == SignalIds.HAZARD_SETTING) {
            return "hazard-setting";
        }
        if (id == SignalIds.VEHICLE_LEFT) {
            return "vehicle-left";
        }
        if (id == SignalIds.VEHICLE_RIGHT) {
            return "vehicle-right";
        }
        return "id";
    }

    private static String meaning(int id, int value) {
        if (id == SignalIds.HAZARD_SETTING) {
            return " (setting, not the lamp)";
        }
        if (value == SignalIds.UNSUPPORTED) {
            return " unsupported";
        }
        if (value == SignalIds.UNAVAILABLE) {
            return " unavailable";
        }
        if ((id == SignalIds.SCENE_LEFT || id == SignalIds.SCENE_RIGHT
                || id == SignalIds.VEHICLE_LEFT || id == SignalIds.VEHICLE_RIGHT) && (value == 0 || value == 1)) {
            return value == 1 ? " on" : " off";
        }
        return "";
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(Integer.parseInt(((String) value).trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void logOnceCar(String message) {
        if (message.equals(lastCarError)) {
            return;
        }
        lastCarError = message;
        log(message);
    }

    private void logOnceVehicle(String message) {
        if (message.equals(lastVehicleError)) {
            return;
        }
        lastVehicleError = message;
        log(message);
    }

    private void log(final String message) {
        Log.i(TAG, message);
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onSignalLog(clock.format(new Date()) + "  " + message);
            }
        });
    }

    private final class PropertyListener extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(SignalIds.LISTENER_DESCRIPTOR);
                return true;
            }
            if (code != SignalIds.LISTENER_EVENT) {
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(SignalIds.LISTENER_DESCRIPTOR);
            try {
                if (data.readInt() != 0) {
                    final int id = data.readInt();
                    final String cls = data.readString();
                    Object value = null;
                    try {
                        value = data.readValue(PropertyListener.class.getClassLoader());
                    } catch (RuntimeException e) {
                        log("listener value " + id + " " + e.getClass().getSimpleName() + " " + e.getMessage());
                    }
                    if (SignalIds.isWatchedCarId(id)) {
                        final Object watched = value;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                applyListener(id, cls, watched);
                            }
                        });
                    }
                }
            } catch (RuntimeException e) {
                log("listener parse " + e.getClass().getSimpleName() + " " + e.getMessage());
            }
            return true;
        }
    }
}
