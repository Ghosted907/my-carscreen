package com.ghosted907.t1launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.widget.Toast

/**
 * Opens the OEM voice session so the user can speak to the car assistant.
 * This class never sends window, sunroof, or other vehicle commands.
 *
 * The AIDL for IVoiceAssistantApi / IVoiceSettingOperator is not in this tree,
 * so no transaction code is guessed. A wrong code on that binder could be a
 * vehicle setter. The call binds, logs the live descriptor, and stops.
 */
object OemAssistant {
    private const val TAG = "T1Launcher"
    private val main = Handler(Looper.getMainLooper())

    fun request(context: Context) {
        val app = context.applicationContext
        val intent = Intent("com.autolink.voiceassistant.connect").setClassName(
            "com.autolink.voiceassistant",
            "com.autolink.voiceassistant.VoiceAssistantService"
        )
        Log.i(TAG, "bind voice assistant action=${intent.action}")
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Thread({
                    val descriptor = try {
                        service?.interfaceDescriptor
                    } catch (e: RemoteException) {
                        Log.i(TAG, "voice descriptor failed ${e.message}")
                        null
                    }
                    Log.i(TAG, "voice assistant descriptor=$descriptor")
                    Log.i(TAG, "toggleVR not sent: IVoiceAssistantApi transaction codes are not in the local AIDL set")
                    main.post {
                        toast(
                            app,
                            "تم الاتصال بالمساعد، لكن أمر الفتح غير مؤكد من ملف الواجهة. لم يُرسل أي أمر."
                        )
                        unbind(app, this)
                    }
                }, "t1-voice-probe").start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.i(TAG, "voice assistant disconnected")
            }

            override fun onNullBinding(name: ComponentName?) {
                Log.i(TAG, "voice assistant null binding")
                toast(app, "المساعد الصوتي لم يُرجع رابطاً")
                unbind(app, this)
            }

            override fun onBindingDied(name: ComponentName?) {
                Log.i(TAG, "voice assistant binding died")
                toast(app, "انقطع الاتصال بالمساعد الصوتي")
            }
        }
        val bound = try {
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.i(TAG, "voice assistant denied ${e.message}")
            toast(app, "رُفض الاتصال بالمساعد الصوتي")
            false
        }
        if (!bound) {
            Log.i(TAG, "voice assistant bindService returned false")
            toast(app, "المساعد الصوتي غير متوفر على هذا الجهاز")
        }
    }

    private fun unbind(context: Context, connection: ServiceConnection) {
        try {
            context.unbindService(connection)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
