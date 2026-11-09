package com.ghosted907.clusterdiag;

import android.os.DeadObjectException;
import android.os.RemoteException;

/** Arabic-first messages for the on-screen banner. English follows in the same string. */
final class ErrorText {
    private ErrorText() {}

    static String notInstalled() {
        return "الخدمة غير موجودة على هذا الجهاز.\n"
                + "لم يُعثر على com.autolink.clusterinteraction.CiService.\n"
                + "هذا متوقّع على الهاتف، أو على أي جهاز ليس وحدة Jetour Autolink.\n"
                + "لم يُرسل أي أمر.\n\n"
                + "CiService is not installed on this device.\n"
                + "That is expected on a phone, or on anything other than the Jetour Autolink head unit.\n"
                + "No command was sent.";
    }

    static String security(SecurityException error) {
        return "رُفض الاتصال بالخدمة.\n"
                + "النظام منع الربط بـ CiService.\n"
                + safeMessage(error) + "\n\n"
                + "The system denied binding to CiService.\n"
                + safeMessage(error);
    }

    static String timeout() {
        return "انتهت مهلة الاتصال.\n"
                + "الخدمة لم ترد. تأكد أن الحزمة com.autolink.clusterinteraction موجودة.\n"
                + "لم يُرسل أي أمر.\n\n"
                + "Bind timed out. CiService did not connect.\n"
                + "No command was sent.";
    }

    static String nullBinding() {
        return "الخدمة رُبطت لكنها أعادت رابطاً فارغاً.\n"
                + "لم يُرسل أي أمر.\n\n"
                + "The service returned a null binder. No command was sent.";
    }

    static String dead() {
        return "انقطع الاتصال بالخدمة.\n"
                + "الكائن البعيد لم يعد حيّاً (DeadObjectException). أعد الاتصال ثم أعد القراءة.\n\n"
                + "The cluster service died (DeadObjectException). Reconnect, then re-read.";
    }

    static String remote(RemoteException error) {
        return "خطأ في الاتصال البعيد.\n"
                + safeMessage(error) + "\n\n"
                + "RemoteException.\n"
                + safeMessage(error);
    }

    static String descriptorMismatch(String remote) {
        String shown = remote == null || remote.trim().isEmpty() ? "(فارغ / empty)" : remote;
        return "تم الربط بالخدمة، لكن معرّف الواجهة لا يطابق CiService.\n"
                + "لم يُرسل أي أمر.\n"
                + "descriptor: " + shown + "\n\n"
                + "Bound to CiService, but the interface descriptor does not match.\n"
                + "No command was sent.\n"
                + "descriptor: " + shown;
    }

    static String notConnected() {
        return "غير متصل بالخدمة.\nلم يُرسل أمر.\n\nNot connected. Nothing was sent.";
    }

    static String generic(Throwable error) {
        String name = error == null ? "Error" : error.getClass().getSimpleName();
        return "فشل الاستدعاء: " + name + "\n"
                + safeMessage(error) + "\n\n"
                + "Call failed: " + name + "\n"
                + safeMessage(error);
    }

    static String explain(Throwable error) {
        if (error instanceof DeadObjectException) {
            return dead();
        }
        if (error instanceof DescriptorMismatch) {
            return descriptorMismatch(((DescriptorMismatch) error).remote);
        }
        if (error instanceof RemoteException) {
            return remote((RemoteException) error);
        }
        if (error instanceof SecurityException) {
            return security((SecurityException) error);
        }
        return generic(error);
    }

    private static String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return "";
        }
        return error.getMessage();
    }
}
