package com.ghosted907.clusterdiag;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.ghosted907.clusterdiag.logic.ClusterTransactions;
import com.ghosted907.clusterdiag.logic.ThemeLabels;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Binds to Autolink {@code CiService} and sends only the five verified transactions:
 * theme get/set, font get/set, and day/night set.
 */
final class ClusterClient {
    static final String PACKAGE = "com.autolink.clusterinteraction";
    static final String ACTION = "com.autolink.clusterinteraction.CiService";
    static final String CLASS_NAME = "com.autolink.clusterinteraction.CiService";
    static final String TAG = "ClusterDiag";

    private static final long BIND_TIMEOUT_MS = 12000L;
    private static final long CALL_TIMEOUT_MS = 5000L;

    enum Tone {
        IDLE,
        OK,
        WARN,
        BAD
    }

    enum ReadKind {
        OK,
        MISSING,
        FAILED
    }

    static final class Status {
        final Tone tone;
        final String pillAr;
        final String pillEn;
        final String detail;
        final String banner;
        final boolean ready;
        final boolean retryEnabled;

        Status(
                Tone tone,
                String pillAr,
                String pillEn,
                String detail,
                String banner,
                boolean ready,
                boolean retryEnabled) {
            this.tone = tone;
            this.pillAr = pillAr;
            this.pillEn = pillEn;
            this.detail = detail;
            this.banner = banner;
            this.ready = ready;
            this.retryEnabled = retryEnabled;
        }
    }

    interface Listener {
        void onStatus(Status status);

        void onLog(String line);

        void onTheme(ReadKind kind, Integer id, String raw);

        void onFont(ReadKind kind, Integer id, String raw);

        void onDayNight(ReadKind kind, Integer id, String raw);

        void onCallResult(Tone tone, String message);

        void onBusy(boolean busy);

        void onCapabilities(boolean theme, boolean font, boolean day);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            post(generation, new Runnable() {
                @Override
                public void run() {
                    onDied();
                }
            });
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            onConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onLinkLost("انقطع الربط، بانتظار إعادة الاتصال.\nLink lost, waiting to reconnect.");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            onLinkLost("مات رابط الخدمة.\nThe service binding died.");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            handleNullBinding();
        }
    };

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            onTimeout();
        }
    };

    private volatile int generation;
    private volatile boolean closed;
    private volatile boolean bindRequested;
    private volatile boolean sawConnected;
    private int busyDepth;
    private volatile IBinder binder;
    private volatile boolean commandsAllowed;
    private Integer lastDaySent;

    ClusterClient(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void start() {
        if (bindRequested && !closed) {
            return;
        }
        closed = false;
        generation++;
        busyDepth = 0;
        bind();
    }

    void stop() {
        closed = true;
        generation++;
        main.removeCallbacks(timeoutRunnable);
        unbind();
        commandsAllowed = false;
        binder = null;
        busyDepth = 0;
    }

    void retryOrReread() {
        if (commandsAllowed && binder != null) {
            reread();
            return;
        }
        unbind();
        commandsAllowed = false;
        binder = null;
        closed = false;
        generation++;
        bind();
    }

    void setTheme(final int id) {
        sendSet(
                "setThemeToCluster",
                ClusterTransactions.SET_THEME,
                id,
                "تم إرسال الثيم " + id + " (" + ThemeLabels.arabic(id) + ") إلى الخدمة",
                "Sent theme " + id + " (" + ThemeLabels.english(id) + ") to the service",
                new Setter() {
                    @Override
                    public void run(IBinder target) throws RemoteException, DescriptorMismatch {
                        ClusterCalls.setThemeToCluster(target, id);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        readTheme();
                    }
                });
    }

    void setFont(final int id) {
        sendSet(
                "setClusterFont",
                ClusterTransactions.SET_FONT,
                id,
                "تم إرسال الخط " + id + " إلى الخدمة",
                "Sent font " + id + " to the service",
                new Setter() {
                    @Override
                    public void run(IBinder target) throws RemoteException, DescriptorMismatch {
                        ClusterCalls.setClusterFont(target, id);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        readFont();
                    }
                });
    }

    void setDayNight(final int id) {
        sendSet(
                "setDayNightMode",
                ClusterTransactions.SET_DAY_NIGHT,
                id,
                "تم إرسال الوضع " + id + " (" + ThemeLabels.dayArabic(id) + ") إلى الخدمة",
                "Sent day/night " + id + " (" + ThemeLabels.dayEnglish(id) + ") to the service",
                new Setter() {
                    @Override
                    public void run(IBinder target) throws RemoteException, DescriptorMismatch {
                        ClusterCalls.setDayNightMode(target, id);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        lastDaySent = Integer.valueOf(id);
                        publishDaySent(id);
                        log("لا توجد معاملة قراءة لليل/النهار. لم تُعاد القراءة. / no verified day/night getter; not read back");
                    }
                });
    }

    void reread() {
        if (!commandsAllowed || binder == null) {
            showResult(Tone.BAD, ErrorText.notConnected());
            return;
        }
        showResult(Tone.WARN, "جارٍ إعادة القراءة…\nRe-reading…");
        log("إعادة القراءة / re-read");
        enqueue("re-read", new Runnable() {
            @Override
            public void run() {
                readTheme();
                readFont();
                publishDayNote();
            }
        });
    }

    private void bind() {
        sawConnected = false;
        commandsAllowed = false;
        publish(new Status(
                Tone.WARN,
                "جارٍ الاتصال…",
                "Connecting…",
                ACTION,
                null,
                false,
                false));
        listener.onCapabilities(false, false, false);
        log("ربط CiService / binding " + CLASS_NAME);
        Intent explicit = new Intent(ACTION);
        explicit.setComponent(new ComponentName(PACKAGE, CLASS_NAME));
        boolean bound = false;
        try {
            bound = context.bindService(explicit, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            fail(ErrorText.security(e), false);
            log("SecurityException: " + e.getMessage());
            return;
        }
        if (!bound) {
            Intent byAction = new Intent(ACTION);
            byAction.setPackage(PACKAGE);
            log("المكوّن الصريح غير موجود، محاولة بالفعل فقط / explicit component missing, trying action");
            try {
                bound = context.bindService(byAction, connection, Context.BIND_AUTO_CREATE);
            } catch (SecurityException e) {
                fail(ErrorText.security(e), false);
                log("SecurityException: " + e.getMessage());
                return;
            }
        }
        bindRequested = bound;
        if (!bound) {
            fail(ErrorText.notInstalled(), true);
            log("bindService أعاد false / bindService returned false");
            return;
        }
        main.removeCallbacks(timeoutRunnable);
        main.postDelayed(timeoutRunnable, BIND_TIMEOUT_MS);
    }

    private void unbind() {
        if (!bindRequested) {
            return;
        }
        unlinkDeath();
        try {
            context.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
            // Already unbound.
        }
        bindRequested = false;
    }

    private void onConnected(IBinder service) {
        if (closed || service == null) {
            return;
        }
        sawConnected = true;
        main.removeCallbacks(timeoutRunnable);
        unlinkDeath();
        binder = service;
        commandsAllowed = false;
        publish(new Status(
                Tone.WARN,
                "متصل، جارٍ التحقق…",
                "Connected, checking descriptor…",
                "binder: " + service.getClass().getName(),
                null,
                false,
                false));
        log("onServiceConnected " + service.getClass().getName());
        try {
            service.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            onDied();
            return;
        }
        final IBinder captured = service;
        final int gen = generation;
        startWorker("startup", new Runnable() {
            @Override
            public void run() {
                checkAndRead(captured, gen);
            }
        });
    }

    private void checkAndRead(IBinder service, int gen) {
        if (!stillCurrent(service, gen)) {
            return;
        }
        final String remote;
        try {
            remote = service.getInterfaceDescriptor();
        } catch (DeadObjectException e) {
            post(gen, new Runnable() {
                @Override
                public void run() {
                    onDied();
                }
            });
            return;
        } catch (RemoteException e) {
            final String message = ErrorText.remote(e);
            post(gen, new Runnable() {
                @Override
                public void run() {
                    fail(message, true);
                    log(message);
                }
            });
            return;
        }
        log("descriptor=" + shown(remote));
        log("expected=" + ClusterTransactions.DESCRIPTOR);
        if (!ClusterTransactions.descriptorMatches(remote)) {
            post(gen, new Runnable() {
                @Override
                public void run() {
                    commandsAllowed = false;
                    publish(new Status(
                            Tone.BAD,
                            "متصل بلا أوامر",
                            "Connected, commands blocked",
                            "descriptor: " + shown(remote)
                                    + "\nexpected: " + ClusterTransactions.DESCRIPTOR,
                            ErrorText.descriptorMismatch(remote),
                            false,
                            true));
                    listener.onCapabilities(false, false, false);
                    log("معرّف الواجهة غير مطابق. لم يُرسل أمر. / descriptor mismatch, nothing sent");
                }
            });
            return;
        }
        commandsAllowed = true;
        post(gen, new Runnable() {
            @Override
            public void run() {
                publishReady(remote);
            }
        });
        log("ping=" + service.pingBinder());
        log("transactions: setTheme=" + ClusterTransactions.SET_THEME
                + " getTheme=" + ClusterTransactions.GET_THEME
                + " setFont=" + ClusterTransactions.SET_FONT
                + " getFont=" + ClusterTransactions.GET_FONT
                + " setDayNight=" + ClusterTransactions.SET_DAY_NIGHT);
        readTheme();
        readFont();
        publishDayNote();
    }

    private void publishReady(String remote) {
        publish(new Status(
                Tone.OK,
                "متصل",
                "Connected",
                "descriptor: " + remote
                        + "\nsetThemeToCluster=" + ClusterTransactions.SET_THEME
                        + "  getClusterTheme=" + ClusterTransactions.GET_THEME
                        + "\nsetClusterFont=" + ClusterTransactions.SET_FONT
                        + "  getClusterFont=" + ClusterTransactions.GET_FONT
                        + "\nsetDayNightMode=" + ClusterTransactions.SET_DAY_NIGHT,
                null,
                true,
                false));
        listener.onCapabilities(true, true, true);
    }

    private void readTheme() {
        IBinder target = binder;
        if (!commandsAllowed || target == null) {
            return;
        }
        try {
            ClusterCalls.IntReply reply = ClusterCalls.getClusterTheme(target);
            log("descriptor=" + reply.descriptor
                    + " tx=" + reply.code
                    + " getClusterTheme → " + reply.value);
            final int value = reply.value;
            post(generation, new Runnable() {
                @Override
                public void run() {
                    listener.onTheme(ReadKind.OK, Integer.valueOf(value), "tx 206");
                }
            });
        } catch (Throwable error) {
            reportReadFailure("getClusterTheme", ClusterTransactions.GET_THEME, error, new ReadSink() {
                @Override
                public void publish(ReadKind kind, String raw) {
                    listener.onTheme(kind, null, raw);
                }
            });
        }
    }

    private void readFont() {
        IBinder target = binder;
        if (!commandsAllowed || target == null) {
            return;
        }
        try {
            ClusterCalls.IntReply reply = ClusterCalls.getClusterFont(target);
            log("descriptor=" + reply.descriptor
                    + " tx=" + reply.code
                    + " getClusterFont → " + reply.value);
            final int value = reply.value;
            post(generation, new Runnable() {
                @Override
                public void run() {
                    listener.onFont(ReadKind.OK, Integer.valueOf(value), "tx 216");
                }
            });
        } catch (Throwable error) {
            reportReadFailure("getClusterFont", ClusterTransactions.GET_FONT, error, new ReadSink() {
                @Override
                public void publish(ReadKind kind, String raw) {
                    listener.onFont(kind, null, raw);
                }
            });
        }
    }

    private void publishDayNote() {
        final Integer sent = lastDaySent;
        post(generation, new Runnable() {
            @Override
            public void run() {
                if (sent == null) {
                    listener.onDayNight(ReadKind.MISSING, null, "");
                } else {
                    listener.onDayNight(
                            ReadKind.OK,
                            null,
                            "أُرسل " + sent + " / sent " + sent);
                }
            }
        });
        log("day/night read skipped (no verified getter). setDayNightMode tx="
                + ClusterTransactions.SET_DAY_NIGHT);
    }

    private void publishDaySent(final int id) {
        post(generation, new Runnable() {
            @Override
            public void run() {
                listener.onDayNight(ReadKind.OK, null, "أُرسل " + id + " / sent " + id);
            }
        });
    }

    private void sendSet(
            final String name,
            final int code,
            final int id,
            final String arabic,
            final String english,
            final Setter setter,
            final Runnable afterSuccess) {
        if (!commandsAllowed || binder == null) {
            showResult(Tone.BAD, ErrorText.notConnected());
            log("refused " + name + " — not ready");
            return;
        }
        showResult(Tone.WARN, "جارٍ الإرسال…\nSending…");
        log(arabic + " / " + english + " tx=" + code);
        enqueue(name, new Runnable() {
            @Override
            public void run() {
                IBinder target = binder;
                if (!commandsAllowed || target == null) {
                    showResult(Tone.BAD, ErrorText.notConnected());
                    return;
                }
                try {
                    String remote = target.getInterfaceDescriptor();
                    log("descriptor=" + shown(remote) + " before " + name);
                    setter.run(target);
                    log("tx=" + code + " " + name + "(" + id + ") result=ok flags=0");
                    showResult(Tone.OK, arabic + "\n" + english + "\ntx " + code + " ok");
                    if (afterSuccess != null) {
                        afterSuccess.run();
                    }
                } catch (Throwable error) {
                    reportFailure(name, code, error);
                }
            }
        });
    }

    private void reportReadFailure(String name, int code, Throwable error, final ReadSink sink) {
        log("tx=" + code + " " + name + " failed: " + error.getClass().getSimpleName()
                + " " + safe(error.getMessage()));
        if (error instanceof DescriptorMismatch) {
            commandsAllowed = false;
            final String remote = ((DescriptorMismatch) error).remote;
            log("descriptor=" + shown(remote) + " — nothing sent");
            post(generation, new Runnable() {
                @Override
                public void run() {
                    commandsAllowed = false;
                    listener.onCapabilities(false, false, false);
                    publish(new Status(
                            Tone.BAD,
                            "متصل بلا أوامر",
                            "Connected, commands blocked",
                            "descriptor: " + shown(remote),
                            ErrorText.descriptorMismatch(remote),
                            false,
                            true));
                }
            });
        }
        if (error instanceof DeadObjectException) {
            post(generation, new Runnable() {
                @Override
                public void run() {
                    onDied();
                }
            });
        }
        final String raw = error.getClass().getSimpleName();
        post(generation, new Runnable() {
            @Override
            public void run() {
                sink.publish(ReadKind.FAILED, raw);
            }
        });
    }

    private void reportFailure(String name, int code, Throwable error) {
        log("tx=" + code + " " + name + " failed: " + error.getClass().getSimpleName()
                + " " + safe(error.getMessage()));
        if (error instanceof DescriptorMismatch) {
            log("descriptor=" + shown(((DescriptorMismatch) error).remote) + " — nothing sent");
            commandsAllowed = false;
        }
        final String message = ErrorText.explain(error);
        showResult(Tone.BAD, message);
        if (error instanceof DeadObjectException) {
            post(generation, new Runnable() {
                @Override
                public void run() {
                    onDied();
                }
            });
        } else if (error instanceof DescriptorMismatch) {
            final String remote = ((DescriptorMismatch) error).remote;
            post(generation, new Runnable() {
                @Override
                public void run() {
                    commandsAllowed = false;
                    listener.onCapabilities(false, false, false);
                    publish(new Status(
                            Tone.BAD,
                            "متصل بلا أوامر",
                            "Connected, commands blocked",
                            "descriptor: " + shown(remote),
                            ErrorText.descriptorMismatch(remote),
                            false,
                            true));
                }
            });
        }
    }

    private void onDied() {
        commandsAllowed = false;
        unlinkDeath();
        fail(ErrorText.dead(), true);
        listener.onCapabilities(false, false, false);
        log(ErrorText.dead());
    }

    private void onLinkLost(String detail) {
        commandsAllowed = false;
        unlinkDeath();
        unbind();
        binder = null;
        publish(new Status(
                Tone.WARN,
                "انقطع الاتصال",
                "Disconnected",
                detail,
                detail,
                false,
                true));
        listener.onCapabilities(false, false, false);
        log(detail);
    }

    private void handleNullBinding() {
        log("onNullBinding");
        unbind();
        fail(ErrorText.nullBinding(), true);
    }

    private void onTimeout() {
        if (closed || sawConnected) {
            return;
        }
        log("bind timeout");
        unbind();
        fail(ErrorText.timeout(), true);
    }

    private void fail(String banner, boolean retry) {
        commandsAllowed = false;
        publish(new Status(
                Tone.BAD,
                "غير متصل",
                "Not connected",
                ACTION,
                banner,
                false,
                retry));
        listener.onCapabilities(false, false, false);
    }

    private void publish(Status status) {
        listener.onStatus(status);
    }

    private void showResult(final Tone tone, final String message) {
        Log.i(TAG, "result " + tone + " " + message.replace('\n', ' '));
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onCallResult(tone, message);
            }
        });
    }

    private void enqueue(final String name, final Runnable work) {
        startWorker(name, work);
    }

    /**
     * Each binder call gets its own thread. A stuck {@code transact} cannot block the next press,
     * and a timeout is logged if that thread is still inside the call after 5 seconds.
     */
    private void startWorker(final String name, final Runnable work) {
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    work.run();
                } catch (Throwable error) {
                    Log.i(TAG, name + " worker " + error);
                    log(name + " worker " + error.getClass().getSimpleName() + " " + safe(error.getMessage()));
                } finally {
                    finished.set(true);
                }
            }
        }, "cluster-diag-" + name);
        thread.setDaemon(true);
        thread.start();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!finished.get() && thread.isAlive()) {
                    Log.i(TAG, "timeout " + name);
                    log("timeout " + name);
                    thread.interrupt();
                }
            }
        }, CALL_TIMEOUT_MS);
    }

    private void log(final String message) {
        Log.i(TAG, message);
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onLog(clock.format(new Date()) + "  " + message);
            }
        });
    }

    private void post(final int gen, final Runnable runnable) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (gen == generation && !closed) {
                    runnable.run();
                }
            }
        });
    }

    private boolean stillCurrent(IBinder service, int gen) {
        return gen == generation && !closed && service == binder;
    }

    private void unlinkDeath() {
        IBinder current = binder;
        if (current == null) {
            return;
        }
        try {
            current.unlinkToDeath(deathRecipient, 0);
        } catch (NoSuchElementException ignored) {
            // Not linked.
        }
    }

    private static String shown(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "(empty)";
        }
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private interface Setter {
        void run(IBinder binder) throws RemoteException, DescriptorMismatch;
    }

    private interface ReadSink {
        void publish(ReadKind kind, String raw);
    }
}
