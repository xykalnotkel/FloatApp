package io.xystudio.floatspace

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*
import rikka.shizuku.Shizuku
import kotlin.math.abs

class OverlayService:Service(){
    companion object{
        const val ACTION_OPEN="io.xystudio.floatspace.OPEN"
        const val EXTRA_COMPONENT="component"
        const val EXTRA_MODE="mode"
        const val EXTRA_PRESET="preset"
    }
    private lateinit var wm:WindowManager
    private lateinit var displays:DisplayManager
    private var handle:View?=null
    private var panel:View?=null
    private var remote:IRemoteService?=null
    private var mode=5
    private var pending:Triple<String,Int,Int>?=null
    private val windows=mutableListOf<VirtualWindow>()

    private inner class VirtualWindow(val component:String,val packageName:String,val layoutMode:Int,val root:LinearLayout,val params:WindowManager.LayoutParams,val texture:TextureView,val message:TextView){
        var display:VirtualDisplay?=null;var minimized=false;var maximized=false;var oldWidth=params.width;var oldHeight=params.height;var oldX=params.x;var oldY=params.y
    }

    private val connection=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName?,binder:IBinder?){remote=IRemoteService.Stub.asInterface(binder);LocalLogger.info("Overlay terhubung ke Shizuku");pending?.let{pending=null;openVirtualWindow(it.first,it.second,it.third)}}
        override fun onServiceDisconnected(name:ComponentName?){remote=null}
    }

    override fun onCreate(){super.onCreate();wm=getSystemService(WINDOW_SERVICE) as WindowManager;displays=getSystemService(DISPLAY_SERVICE) as DisplayManager;createNotification();if(Settings.canDrawOverlays(this))showHandle()else stopSelf();bindShizuku()}
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==ACTION_OPEN){val c=intent.getStringExtra(EXTRA_COMPONENT);if(c!=null){val m=intent.getIntExtra(EXTRA_MODE,5);val p=intent.getIntExtra(EXTRA_PRESET,0);if(remote==null){pending=Triple(c,m,p);bindShizuku()}else openVirtualWindow(c,m,p)}}
        return START_NOT_STICKY
    }
    override fun onDestroy(){windows.toList().forEach{closeWindow(it,false)};handle?.let{runCatching{wm.removeView(it)}};panel?.let{runCatching{wm.removeView(it)}};LocalLogger.info("Layanan overlay dihentikan");super.onDestroy()}

    private fun bindShizuku(){if(!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)return;val args=Shizuku.UserServiceArgs(ComponentName(this,RemoteService::class.java)).daemon(false).processNameSuffix("float").debuggable(BuildConfig.DEBUG).version(4);runCatching{Shizuku.bindUserService(args,connection)}.onFailure{LocalLogger.error("Overlay gagal bind Shizuku",it)}}
    private fun createNotification(){val id="floatspace_overlay";if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,"Floating windows",NotificationManager.IMPORTANCE_LOW));val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE);val n=Notification.Builder(this,id).setSmallIcon(R.drawable.ic_launcher).setContentTitle("FloatSpace aktif").setContentText("Mesin jendela virtual dan menu layar aktif").setContentIntent(open).setOngoing(true).build();if(Build.VERSION.SDK_INT>=34)startForeground(31,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)else startForeground(31,n)}
    private fun params(width:Int,height:Int,x:Int,y:Int)=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;this.x=x;this.y=y}
    private fun rounded(color:Int,radius:Int,stroke:Int=Color.DKGRAY,width:Int=1)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(width),stroke)}

    private fun showHandle(){if(handle!=null)return;val pill=TextView(this).apply{text="≡";textSize=16f;gravity=Gravity.CENTER;setTextColor(Color.BLACK);background=rounded(Color.WHITE,8,Color.BLACK,2);elevation=dp(8).toFloat()};val p=params(dp(52),dp(30),resources.displayMetrics.widthPixels-dp(64),resources.displayMetrics.heightPixels/2);drag(pill,p){showPanel()};wm.addView(pill,p);handle=pill}

    private fun showPanel(){if(panel!=null)return;val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(9),dp(10),dp(9));background=rounded(Color.rgb(8,8,8),12,Color.rgb(92,92,92));elevation=dp(14).toFloat()};val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};val title=TextView(this).apply{text="FLOATSPACE";textSize=11f;letterSpacing=.14f;setTextColor(Color.WHITE);setPadding(dp(5),0,0,0);layoutParams=LinearLayout.LayoutParams(0,dp(38),1f)};val close=smallButton("×");top.addView(title);top.addView(close);root.addView(top)
        val modes=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(6),0,dp(7))};listOf("Jendela" to 5,"Atas" to 3,"Bawah" to 4).forEachIndexed{index,pair->val b=smallButton(pair.first).apply{setTextColor(if(pair.second==mode)Color.BLACK else Color.WHITE);background=rounded(if(pair.second==mode)Color.WHITE else Color.rgb(25,25,25),8);layoutParams=LinearLayout.LayoutParams(0,dp(36),1f).apply{if(index>0)marginStart=dp(5)}};b.setOnClickListener{mode=pair.second;for(i in 0 until modes.childCount){val c=modes.getChildAt(i) as Button;val active=i==index;c.setTextColor(if(active)Color.BLACK else Color.WHITE);c.background=rounded(if(active)Color.WHITE else Color.rgb(25,25,25),8)}};modes.addView(b)};root.addView(modes)
        val scroll=ScrollView(this);val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val favorites=Favorites.get(this);if(favorites.isEmpty())list.addView(TextView(this).apply{text="Tekan ☆ pada aplikasi untuk menambahkannya ke panel.";textSize=10f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER;setPadding(dp(8),dp(22),dp(8),dp(22))})else favorites.forEach{component->runCatching{val cn=ComponentName.unflattenFromString(component)?:return@runCatching;val info=packageManager.getActivityInfo(cn,0);val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(9),dp(6),dp(9),dp(6));background=rounded(Color.rgb(22,22,22),9,Color.rgb(55,55,55));layoutParams=LinearLayout.LayoutParams(-1,dp(51)).apply{bottomMargin=dp(5)}};row.addView(ImageView(this).apply{setImageDrawable(info.loadIcon(packageManager));layoutParams=LinearLayout.LayoutParams(dp(34),dp(34))});row.addView(TextView(this).apply{text=info.loadLabel(packageManager);textSize=11f;setTextColor(Color.WHITE);setPadding(dp(9),0,0,0);layoutParams=LinearLayout.LayoutParams(0,-2,1f)});row.setOnClickListener{openVirtualWindow(component,mode,0)};list.addView(row)}};scroll.addView(list);scroll.layoutParams=LinearLayout.LayoutParams(-1,0,1f);root.addView(scroll)
        val footer=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};val open=smallButton("Aplikasi").apply{setTextColor(Color.BLACK);background=rounded(Color.WHITE,8,Color.BLACK);layoutParams=LinearLayout.LayoutParams(0,dp(37),1f)};val stop=smallButton("Stop");val resize=TextView(this).apply{text="↘";textSize=18f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);layoutParams=LinearLayout.LayoutParams(dp(40),dp(37))};footer.addView(open);footer.addView(stop);footer.addView(resize);root.addView(footer)
        val p=params(dp(282),dp(420),dp(18),dp(130));drag(top,p){};resizeOverlay(resize,p,dp(230),dp(270),dp(390),dp(650));close.setOnClickListener{hidePanel()};stop.setOnClickListener{stopSelf()};open.setOnClickListener{startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));hidePanel()};wm.addView(root,p);panel=root}
    private fun smallButton(label:String)=Button(this).apply{text=label;textSize=9f;isAllCaps=false;setTextColor(Color.WHITE);background=rounded(Color.rgb(25,25,25),8);layoutParams=LinearLayout.LayoutParams(dp(48),dp(37))}
    private fun hidePanel(){panel?.let{runCatching{wm.removeView(it)}};panel=null}

    private fun openVirtualWindow(component:String,requestedMode:Int,preset:Int){
        val service=remote?:run{pending=Triple(component,requestedMode,preset);bindShizuku();Toast.makeText(this,"Menghubungkan Shizuku…",Toast.LENGTH_SHORT).show();return}
        val cn=ComponentName.unflattenFromString(component)?:return;val info=runCatching{packageManager.getActivityInfo(cn,0)}.getOrNull()?:return
        val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels
        val isSplit=requestedMode==3||requestedMode==4
        if(isSplit)windows.filter{it.layoutMode==requestedMode}.toList().forEach{closeWindow(it,true)}
        val compactW=when(preset){1->(sw*.90).toInt();2->(sw*.94).toInt();else->(sw*.78).toInt()};val compactH=when(preset){1->(sh*.75).toInt();2->(sh*.45).toInt();else->(sh*.58).toInt()}
        val width=if(isSplit)sw else compactW
        val height=if(isSplit)(sh/2) else compactH
        val x=if(isSplit)0 else (sw-width)/2
        val y=when(requestedMode){3->0;4->sh/2;else->dp(95)+(windows.size*dp(18))}
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            background=rounded(Color.rgb(5,5,5),if(isSplit)2 else 11,Color.rgb(105,105,105),1)
            elevation=if(isSplit)0f else dp(16).toFloat()
            clipToOutline=true
        }
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(4),dp(5),dp(4));background=rounded(Color.rgb(16,16,16),9,Color.rgb(55,55,55))}
        header.addView(ImageView(this).apply{setImageDrawable(info.loadIcon(packageManager));layoutParams=LinearLayout.LayoutParams(dp(28),dp(28))});header.addView(TextView(this).apply{text=info.loadLabel(packageManager);textSize=10f;setTextColor(Color.WHITE);setPadding(dp(8),0,0,0);layoutParams=LinearLayout.LayoutParams(0,-2,1f)})
        val min=smallButton("—").apply{layoutParams=LinearLayout.LayoutParams(dp(38),dp(31))};val max=smallButton("□").apply{layoutParams=LinearLayout.LayoutParams(dp(38),dp(31)).apply{marginStart=dp(4)}};val close=smallButton("×").apply{layoutParams=LinearLayout.LayoutParams(dp(38),dp(31)).apply{marginStart=dp(4)}};header.addView(min);header.addView(max);header.addView(close);root.addView(header,LinearLayout.LayoutParams(-1,dp(41)))
        val content=FrameLayout(this).apply{setBackgroundColor(Color.BLACK);layoutParams=LinearLayout.LayoutParams(-1,0,1f)}
        val texture=TextureView(this).apply{isOpaque=true;layoutParams=FrameLayout.LayoutParams(-1,-1)}
        val message=TextView(this).apply{
            text="Membuka ${info.loadLabel(packageManager)}…\nMenyiapkan virtual display"
            textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.LTGRAY)
            setPadding(dp(18),dp(18),dp(18),dp(18));setBackgroundColor(Color.BLACK)
            layoutParams=FrameLayout.LayoutParams(-1,-1)
        }
        content.addView(texture);content.addView(message);root.addView(content)
        val bottom=LinearLayout(this).apply{gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;setBackgroundColor(Color.rgb(12,12,12))};val grip=TextView(this).apply{text="↘";textSize=16f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);layoutParams=LinearLayout.LayoutParams(dp(45),dp(23))};bottom.addView(grip);root.addView(bottom,LinearLayout.LayoutParams(-1,dp(24)))
        val p=params(width,height,x,y);val holder=VirtualWindow(component,cn.packageName,requestedMode,root,p,texture,message);windows.add(holder);drag(header,p){};resizeVirtual(grip,holder)
        min.setOnClickListener{toggleMinimize(holder)};max.setOnClickListener{toggleMaximize(holder,sw,sh)};close.setOnClickListener{closeWindow(holder,true)}
        texture.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(st:SurfaceTexture,w:Int,h:Int){createDisplay(holder,st,w,h,service)}
            override fun onSurfaceTextureSizeChanged(st:SurfaceTexture,w:Int,h:Int){holder.display?.resize(w.coerceAtLeast(1),h.coerceAtLeast(1),resources.displayMetrics.densityDpi)}
            override fun onSurfaceTextureDestroyed(st:SurfaceTexture):Boolean{holder.display?.surface=null;return true}
            override fun onSurfaceTextureUpdated(st:SurfaceTexture){if(holder.message.visibility==View.VISIBLE)holder.message.visibility=View.GONE}
        }
        texture.setOnTouchListener{_,e->val vd=holder.display?:return@setOnTouchListener true;runCatching{service.injectTouch(vd.display.displayId,e.actionMasked,e.x,e.y,e.downTime,e.eventTime)};true}
        wm.addView(root,p);hidePanel();LocalLogger.info("Membuat jendela virtual ${cn.packageName}; mode=$requestedMode ukuran=${width}x$height")
    }

    private fun createDisplay(holder:VirtualWindow,st:SurfaceTexture,w:Int,h:Int,service:IRemoteService){
        runCatching{
            val surface=Surface(st)
            val flags=DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            val vd=displays.createVirtualDisplay(
                "FloatSpace-${System.nanoTime()}",
                w.coerceAtLeast(1),h.coerceAtLeast(1),
                resources.displayMetrics.densityDpi,
                surface,flags
            )
            holder.display=vd
            val id=vd.display.displayId
            LocalLogger.info("Virtual display publik dibuat id=$id flags=$flags size=${w}x$h")
            Thread{
                val result=service.launchOnDisplay(holder.component,id)
                LocalLogger.info("HASIL LAUNCH VIRTUAL\ndisplayId=$id\ncomponent=${holder.component}\n$result")
                if(result.contains("start=0").not()){
                    LocalLogger.error("Launch virtual display bermasalah:\n$result")
                    Handler(Looper.getMainLooper()).post{
                        holder.message.text="Gagal membuka aplikasi\nBuka Cek sistem atau kirim error.txt"
                        holder.message.setTextColor(Color.rgb(255,150,160));holder.message.visibility=View.VISIBLE
                    }
                }else{
                    Handler(Looper.getMainLooper()).postDelayed({
                        if(holder.message.visibility==View.VISIBLE){
                            holder.message.text="Aplikasi berhasil dipanggil, tetapi belum mengirim tampilan.\nPeriksa info.txt jika tetap berhenti di sini."
                        }
                    },8000)
                }
            }.start()
        }.onFailure{
            LocalLogger.error("Gagal membuat virtual display publik",it)
            Toast.makeText(this,"Virtual display gagal: ${it.message}",Toast.LENGTH_LONG).show()
        }
    }
    private fun toggleMinimize(h:VirtualWindow){if(!h.minimized){h.oldWidth=h.params.width;h.oldHeight=h.params.height;h.texture.visibility=View.GONE;h.params.width=dp(170);h.params.height=dp(45);h.minimized=true}else{h.params.width=h.oldWidth;h.params.height=h.oldHeight;h.texture.visibility=View.VISIBLE;h.minimized=false};wm.updateViewLayout(h.root,h.params)}
    private fun toggleMaximize(h:VirtualWindow,screenWidth:Int,screenHeight:Int){
        if(!h.maximized){
            h.oldWidth=h.params.width;h.oldHeight=h.params.height;h.oldX=h.params.x;h.oldY=h.params.y
            h.params.x=0;h.params.y=0;h.params.width=screenWidth;h.params.height=screenHeight
        }else{
            h.params.x=h.oldX;h.params.y=h.oldY;h.params.width=h.oldWidth;h.params.height=h.oldHeight
        }
        h.maximized=!h.maximized
        wm.updateViewLayout(h.root,h.params)
    }
    private fun closeWindow(h:VirtualWindow,stopApp:Boolean){if(stopApp)Thread{remote?.forceStop(h.packageName)}.start();h.display?.release();h.display=null;runCatching{wm.removeView(h.root)};windows.remove(h);LocalLogger.info("Jendela virtual ditutup: ${h.packageName}")}

    private fun drag(view:View,p:WindowManager.LayoutParams,click:()->Unit){view.setOnTouchListener(object:View.OnTouchListener{var sx=0f;var sy=0f;var ox=0;var oy=0;var moved=false;override fun onTouch(v:View,e:android.view.MotionEvent):Boolean{when(e.action){0->{sx=e.rawX;sy=e.rawY;ox=p.x;oy=p.y;moved=false;return true};2->{if(abs(e.rawX-sx)>5||abs(e.rawY-sy)>5)moved=true;p.x=ox+(e.rawX-sx).toInt();p.y=oy+(e.rawY-sy).toInt();val target=when{v===handle->handle;panel!=null&&v.parent===panel->panel;else->(v.parent as? View)};target?.let{runCatching{wm.updateViewLayout(it,p)}};return true};1->{if(!moved)click();return true}};return false}})}
    private fun resizeOverlay(view:View,p:WindowManager.LayoutParams,minW:Int,minH:Int,maxW:Int,maxH:Int){view.setOnTouchListener(object:View.OnTouchListener{var sx=0f;var sy=0f;var ow=0;var oh=0;override fun onTouch(v:View,e:android.view.MotionEvent):Boolean{when(e.action){0->{sx=e.rawX;sy=e.rawY;ow=p.width;oh=p.height;return true};2->{p.width=(ow+e.rawX-sx).toInt().coerceIn(minW,maxW);p.height=(oh+e.rawY-sy).toInt().coerceIn(minH,maxH);panel?.let{wm.updateViewLayout(it,p)};return true}};return false}})}
    private fun resizeVirtual(view:View,h:VirtualWindow){view.setOnTouchListener(object:View.OnTouchListener{var sx=0f;var sy=0f;var ow=0;var oh=0;override fun onTouch(v:View,e:android.view.MotionEvent):Boolean{when(e.action){0->{sx=e.rawX;sy=e.rawY;ow=h.params.width;oh=h.params.height;return true};2->{h.params.width=(ow+e.rawX-sx).toInt().coerceIn(dp(210),resources.displayMetrics.widthPixels);h.params.height=(oh+e.rawY-sy).toInt().coerceIn(dp(260),resources.displayMetrics.heightPixels);wm.updateViewLayout(h.root,h.params);return true}};return false}})}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
