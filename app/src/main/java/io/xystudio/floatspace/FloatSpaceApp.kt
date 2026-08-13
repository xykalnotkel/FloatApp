package io.xystudio.floatspace

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class FloatSpaceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        LocalLogger.initialize(this)
        LocalLogger.info("FloatSpace ${BuildConfig.VERSION_NAME}; perangkat=${Build.MANUFACTURER} ${Build.MODEL}; SDK=${Build.VERSION.SDK_INT}; build=${Build.DISPLAY}")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LocalLogger.crash(throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
