package com.ghosted907.clusterdiag;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.ghosted907.clusterdiag.logic.ClusterTransactions;

/**
 * Talks to the already-bound CiService binder with {@link Parcel} and
 * {@link IBinder#transact}. Each method checks the live descriptor and then
 * sends only its own hard-coded transaction.
 */
final class ClusterCalls {
    private ClusterCalls() {}

    static final class IntReply {
        final String descriptor;
        final int code;
        final int value;

        IntReply(String descriptor, int code, int value) {
            this.descriptor = descriptor;
            this.code = code;
            this.value = value;
        }
    }

    static String requireDescriptor(IBinder binder) throws RemoteException, DescriptorMismatch {
        String remote = binder.getInterfaceDescriptor();
        if (!ClusterTransactions.descriptorMatches(remote)) {
            throw new DescriptorMismatch(remote);
        }
        return remote;
    }

    static IntReply getClusterTheme(IBinder binder) throws RemoteException, DescriptorMismatch {
        return transactGet(binder, ClusterTransactions.GET_THEME);
    }

    static void setThemeToCluster(IBinder binder, int theme) throws RemoteException, DescriptorMismatch {
        if (theme != 1 && theme != 2 && theme != 3) {
            throw new IllegalArgumentException("theme preset must be 1, 2, or 3");
        }
        transactSet(binder, ClusterTransactions.SET_THEME, theme);
    }

    static IntReply getClusterFont(IBinder binder) throws RemoteException, DescriptorMismatch {
        return transactGet(binder, ClusterTransactions.GET_FONT);
    }

    static void setClusterFont(IBinder binder, int font) throws RemoteException, DescriptorMismatch {
        if (font != 1 && font != 2) {
            throw new IllegalArgumentException("font preset must be 1 or 2");
        }
        transactSet(binder, ClusterTransactions.SET_FONT, font);
    }

    static void setDayNightMode(IBinder binder, int mode) throws RemoteException, DescriptorMismatch {
        if (mode != 0 && mode != 1) {
            throw new IllegalArgumentException("day/night preset must be 0 or 1");
        }
        transactSet(binder, ClusterTransactions.SET_DAY_NIGHT, mode);
    }

    private static void transactSet(IBinder binder, int code, int value)
            throws RemoteException, DescriptorMismatch {
        if (!ClusterTransactions.isAllowlisted(code)) {
            throw new IllegalArgumentException("refusing transaction " + code);
        }
        String remote = requireDescriptor(binder);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(ClusterTransactions.DESCRIPTOR);
            data.writeInt(value);
            boolean delivered = binder.transact(code, data, reply, 0);
            reply.readException();
            if (!delivered) {
                throw new RemoteException("transact returned false for " + code + " descriptor=" + remote);
            }
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static IntReply transactGet(IBinder binder, int code)
            throws RemoteException, DescriptorMismatch {
        if (!ClusterTransactions.isAllowlisted(code)) {
            throw new IllegalArgumentException("refusing transaction " + code);
        }
        String remote = requireDescriptor(binder);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(ClusterTransactions.DESCRIPTOR);
            boolean delivered = binder.transact(code, data, reply, 0);
            reply.readException();
            if (!delivered) {
                throw new RemoteException("transact returned false for " + code + " descriptor=" + remote);
            }
            int value = reply.readInt();
            return new IntReply(remote, code, value);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
