package com.ghosted907.t1launcher

import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** No generic write API, and deliberately no guessed tailgate transaction. */
class VehicleControl(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private val descriptor = "com.autolink.adapterinterface.car.IALCar"
    private fun binder(): IBinder {
        val sm = Class.forName("android.os.ServiceManager")
        val b = sm.getMethod("getService", String::class.java).invoke(null, "alcar_service") as? IBinder
            ?: throw IllegalStateException("خدمة التحكم غير متصلة")
        check(b.interfaceDescriptor == descriptor) { "واجهة خدمة التحكم غير مطابقة" }
        return b
    }
    private fun call(code: Int, args: List<Int>): Int? {
        val b = binder()
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            args.forEach { data.writeInt(it) }
            check(b.transact(code, data, reply, 0)) { "الأمر غير مدعوم" }
            reply.readException()
            return if (code == 12) null else reply.readInt()
        } finally { data.recycle(); reply.recycle() }
    }
    /** Safe app-UID getter probe; success does not assert write permission. */
    fun probe(done: (String) -> Unit) {
        worker.execute {
            val message = runCatching {
                val value = call(2, listOf(557843526, 0))
                check(value != null && value >= 0) { "قراءة السقف غير متاحة" }
                "وصلت خدمة التحكم: نجحت القراءة من التطبيق؛ أوامر الحركة لم تُختبر"
            }.getOrElse { "تعذر الاتصال من التطبيق: ${it.cause?.message ?: it.message ?: it.javaClass.simpleName}" }
            main.post { done(message) }
        }
    }
    fun send(id: String, done: (String) -> Unit) {
        val command = VehicleCommands.all.firstOrNull { it.id == id } ?: return
        if (!busy.compareAndSet(false, true)) return
        worker.execute {
            val message = runCatching {
                val result = call(command.transaction, command.args)
                check(result == null || result == 1) { "رفضت الخدمة الطلب ($result)" }
                "أُرسل طلب: ${command.label}؛ تأكد من حركة السيارة"
            }.getOrElse { "تعذر ${command.label}: ${it.cause?.message ?: it.message ?: it.javaClass.simpleName}" }
            busy.set(false)
            main.post { done(message) }
        }
    }
    fun close() { worker.shutdownNow() }
}
