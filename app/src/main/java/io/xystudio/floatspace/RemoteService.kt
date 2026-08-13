package io.xystudio.floatspace

import android.annotation.SuppressLint
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass

class RemoteService : IRemoteService.Stub() {
    init { if (Build.VERSION.SDK_INT >= 28) runCatching { HiddenApiBypass.addHiddenApiExemptions("") } }

    private fun execute(vararg command: String): Pair<Int, String> = try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() to output.trim()
    } catch (t: Throwable) { -1 to (t.message ?: t.javaClass.simpleName) }

    override fun launchWindow(component:String,windowMode:Int,left:Int,top:Int,right:Int,bottom:Int):Int {
        val command=mutableListOf("am","start","--user","0","--windowingMode",windowMode.toString())
        if(windowMode==5)command+=listOf("--bounds","$left,$top,$right,$bottom")
        command+=listOf("-n",component)
        return execute(*command.toTypedArray()).first
    }

    override fun launchOnDisplay(component:String,displayId:Int):Int = execute(
        "am","start","--user","0","--display",displayId.toString(),"--windowingMode","1","-n",component
    ).first

    @SuppressLint("BlockedPrivateApi", "PrivateApi")
    override fun injectTouch(displayId:Int,action:Int,x:Float,y:Float,downTime:Long,eventTime:Long):Boolean = try {
        val event=MotionEvent.obtain(downTime,eventTime,action,x,y,0).apply{source=InputDevice.SOURCE_TOUCHSCREEN}
        MotionEvent::class.java.getDeclaredMethod("setDisplayId",Int::class.javaPrimitiveType).apply{isAccessible=true}.invoke(event,displayId)
        val clazz=Class.forName("android.hardware.input.InputManager")
        val manager=clazz.getDeclaredMethod("getInstance").apply{isAccessible=true}.invoke(null)
        val inject=clazz.declaredMethods.first{it.name=="injectInputEvent"&&it.parameterTypes.size==2}.apply{isAccessible=true}
        val result=inject.invoke(manager,event,0) as? Boolean ?: true
        event.recycle();result
    } catch(t:Throwable){false}

    override fun forceStop(packageName:String):Int=execute("am","force-stop","--user","0",packageName).first

    override fun enableWindowModes():Int { val results=listOf(
        execute("settings","put","global","enable_freeform_support","1").first,
        execute("settings","put","global","force_resizable_activities","1").first,
        execute("settings","put","global","enable_non_resizable_multi_window","1").first)
        return if(results.all{it==0})0 else -1 }

    override fun allowOverlay(packageName:String):Int=execute("appops","set",packageName,"SYSTEM_ALERT_WINDOW","allow").first

    override fun runDiagnostic(packageName:String):String {
        val identity=execute("id").second
        val feature=execute("pm","has-feature","android.software.freeform_window_management").second
        val freeform=execute("settings","get","global","enable_freeform_support").second
        val resize=execute("settings","get","global","force_resizable_activities").second
        val multi=execute("settings","get","global","enable_non_resizable_multi_window").second
        val overlay=execute("appops","get",packageName,"SYSTEM_ALERT_WINDOW").second
        return """IDENTITAS SHIZUKU
$identity

FREEFORM NATIVE FIRMWARE
$feature

FREEFORM DIAKTIFKAN
$freeform

PAKSA APLIKASI RESIZABLE
$resize

MULTI-WINDOW NON-RESIZABLE
$multi

IZIN TAMPIL DI ATAS APLIKASI
$overlay

MESIN KOMPATIBILITAS
Virtual Display tersedia pada Android 8+. FloatSpace akan memakai mesin ini jika freeform native tidak tersedia."""
    }
    override fun destroy(){System.exit(0)}
}
