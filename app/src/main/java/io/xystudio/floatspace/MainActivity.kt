package io.xystudio.floatspace

import android.Manifest
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private val requestCodeShizuku=6042
    private var remote:IRemoteService?=null
    private lateinit var status:TextView
    private lateinit var adapter:AppAdapter
    private var selectedMode=5
    private var selectedPreset=0

    private val binderReceived=Shizuku.OnBinderReceivedListener{refreshShizuku()}
    private val binderDead=Shizuku.OnBinderDeadListener{remote=null;updateStatus(false,"Shizuku terputus")}
    private val permissionResult=Shizuku.OnRequestPermissionResultListener{code,result->if(code==requestCodeShizuku&&result==PackageManager.PERMISSION_GRANTED)bindRemote()else updateStatus(false,"Izin Shizuku ditolak")}
    private val connection=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName?,service:IBinder?){remote=IRemoteService.Stub.asInterface(service);updateStatus(true,"Siap membuka jendela") ;LocalLogger.info("Shizuku UserService terhubung")}
        override fun onServiceDisconnected(name:ComponentName?){remote=null;updateStatus(false,"Layanan Shizuku terputus")}
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun shape(color:Int,radius:Int=10,stroke:Int=Color.DKGRAY)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(1),stroke)}

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);buildUi();loadApps();Shizuku.addBinderReceivedListenerSticky(binderReceived);Shizuku.addBinderDeadListener(binderDead);Shizuku.addRequestPermissionResultListener(permissionResult);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),44)}
    override fun onResume(){super.onResume();if(::adapter.isInitialized)adapter.refreshFavorites(Favorites.get(this))}
    override fun onDestroy(){Shizuku.removeBinderReceivedListener(binderReceived);Shizuku.removeBinderDeadListener(binderDead);Shizuku.removeRequestPermissionResultListener(permissionResult);runCatching{Shizuku.unbindUserService(args(),connection,false)};super.onDestroy()}

    private fun button(label:String,onClick:()->Unit)=Button(this).apply{text=label;textSize=10f;isAllCaps=false;setTextColor(Color.BLACK);background=getDrawable(R.drawable.bg_button);setOnClickListener{onClick()};stateListAnimator=null;elevation=0f}
    private fun darkButton(label:String,onClick:()->Unit)=Button(this).apply{text=label;textSize=10f;isAllCaps=false;setTextColor(Color.WHITE);background=getDrawable(R.drawable.bg_search);setOnClickListener{onClick()}}

    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),0);setBackgroundColor(Color.BLACK)}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val names=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
        names.addView(TextView(this).apply{text="FloatSpace";textSize=28f;setTextColor(Color.WHITE)})
        names.addView(TextView(this).apply{text="Jendela mini  •  Shizuku";textSize=10f;setTextColor(Color.GRAY)})
        val guide=darkButton("Bantuan"){showGuide()}.apply{layoutParams=LinearLayout.LayoutParams(dp(82),dp(40))}
        top.addView(names);top.addView(guide);root.addView(top)

        val statusRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(12),0,dp(8))}
        status=TextView(this).apply{text="Memeriksa Shizuku…";textSize=10f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER_VERTICAL;background=getDrawable(R.drawable.bg_status_bad);setPadding(dp(11),0,dp(11),0);layoutParams=LinearLayout.LayoutParams(0,dp(40),1f)}
        val connect=button("Hubungkan"){requestOrBind()}.apply{layoutParams=LinearLayout.LayoutParams(dp(97),dp(42)).apply{marginStart=dp(7)}}
        statusRow.addView(status);statusRow.addView(connect);root.addView(statusRow)

        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("Siapkan mesin" to {enableModes()},"Bilah samping" to {startOverlay()},"Cek sistem" to {diagnose()}).forEachIndexed{index,pair->actions.addView(darkButton(pair.first,pair.second).apply{layoutParams=LinearLayout.LayoutParams(0,dp(43),1f).apply{if(index>0)marginStart=dp(6)}})}
        root.addView(actions)

        val modeLabel=TextView(this).apply{text="TATA LETAK";textSize=9f;letterSpacing=.14f;setTextColor(Color.LTGRAY);setPadding(0,dp(13),0,dp(6))};root.addView(modeLabel)
        val modes=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("Bebas" to 5,"Atas" to 3,"Bawah" to 4).forEachIndexed{index,pair->
            val b=Button(this).apply{text=pair.first;textSize=9f;isAllCaps=false;setTextColor(if(pair.second==selectedMode)Color.BLACK else Color.WHITE);background=if(pair.second==selectedMode)getDrawable(R.drawable.bg_button)else getDrawable(R.drawable.bg_search);layoutParams=LinearLayout.LayoutParams(0,dp(41),1f).apply{if(index>0)marginStart=dp(6)}}
            b.setOnClickListener{selectedMode=pair.second;for(i in 0 until modes.childCount){val c=modes.getChildAt(i) as Button;val active=i==index;c.setTextColor(if(active)Color.BLACK else Color.WHITE);c.background=if(active)getDrawable(R.drawable.bg_button)else getDrawable(R.drawable.bg_search)}};modes.addView(b)
        };root.addView(modes)

        val sizeRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(7),0,0)}
        listOf("Kecil","Tinggi","Lebar").forEachIndexed{index,label->
            val b=Button(this).apply{text=label;textSize=9f;isAllCaps=false;setTextColor(if(index==selectedPreset)Color.BLACK else Color.WHITE);background=if(index==selectedPreset)getDrawable(R.drawable.bg_button)else getDrawable(R.drawable.bg_search);layoutParams=LinearLayout.LayoutParams(0,dp(37),1f).apply{if(index>0)marginStart=dp(6)}}
            b.setOnClickListener{selectedPreset=index;for(i in 0 until sizeRow.childCount){val c=sizeRow.getChildAt(i) as Button;val active=i==index;c.setTextColor(if(active)Color.BLACK else Color.WHITE);c.background=if(active)getDrawable(R.drawable.bg_button)else getDrawable(R.drawable.bg_search)}}
            sizeRow.addView(b)
        };root.addView(sizeRow)

        val appHead=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(13),0,dp(6))}
        appHead.addView(TextView(this).apply{text="Aplikasi";textSize=15f;setTextColor(Color.WHITE);layoutParams=LinearLayout.LayoutParams(0,-2,1f)})
        appHead.addView(TextView(this).apply{text="☆ tambah ke bilah samping";textSize=9f;setTextColor(Color.GRAY)})
        root.addView(appHead)
        val search=EditText(this).apply{hint="Cari aplikasi untuk dibuka";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);textSize=12f;setSingleLine(true);background=getDrawable(R.drawable.bg_search);setPadding(dp(13),0,dp(13),0);layoutParams=LinearLayout.LayoutParams(-1,dp(44)).apply{bottomMargin=dp(8)}}
        adapter=AppAdapter(::launchApp){entry->val added=Favorites.toggle(this,entry.component);adapter.refreshFavorites(Favorites.get(this));toast(if(added)"Ditambah ke menu layar" else "Dihapus dari menu layar");added}
        val list=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@MainActivity);adapter=this@MainActivity.adapter;overScrollMode=View.OVER_SCROLL_NEVER;layoutParams=LinearLayout.LayoutParams(-1,0,1f)}
        search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){adapter.filter(s?.toString().orEmpty())};override fun afterTextChanged(s:Editable?){}})
        root.addView(search);root.addView(list);setContentView(root)
    }

    private fun loadApps(){Thread{val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);val items=packageManager.queryIntentActivities(intent,PackageManager.MATCH_ALL).mapNotNull{r->val a=r.activityInfo?:return@mapNotNull null;if(a.packageName==packageName)return@mapNotNull null;AppEntry(r.loadLabel(packageManager).toString(),ComponentName(a.packageName,a.name).flattenToString(),a.packageName,r.loadIcon(packageManager))}.distinctBy{it.component}.sortedBy{it.label.lowercase()};runOnUiThread{adapter.submit(items,Favorites.get(this))}}.start()}
    private fun refreshShizuku(){if(Shizuku.pingBinder())requestOrBind()else updateStatus(false,"Aktifkan Shizuku dahulu")}
    private fun requestOrBind(){try{if(!Shizuku.pingBinder()){updateStatus(false,"Shizuku belum berjalan");packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(::startActivity);return};when{Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED->bindRemote();Shizuku.shouldShowRequestPermissionRationale()->toast("Izinkan FloatSpace lewat pengaturan Shizuku");else->Shizuku.requestPermission(requestCodeShizuku)}}catch(t:Throwable){LocalLogger.error("Gagal meminta Shizuku",t);updateStatus(false,"Shizuku tidak tersedia")}}
    private fun args()=Shizuku.UserServiceArgs(ComponentName(this,RemoteService::class.java)).daemon(false).processNameSuffix("float").debuggable(BuildConfig.DEBUG).version(3)
    private fun bindRemote(){updateStatus(false,"Menghubungkan layanan…");runCatching{Shizuku.bindUserService(args(),connection)}.onFailure{LocalLogger.error("Bind Shizuku gagal",it);updateStatus(false,"Gagal: ${it.message}")}}

    private fun bounds():Rect{val w=resources.displayMetrics.widthPixels;val h=resources.displayMetrics.heightPixels;return when(selectedPreset){1->Rect((w*.06).toInt(),(h*.06).toInt(),(w*.94).toInt(),(h*.91).toInt());2->Rect((w*.03).toInt(),(h*.28).toInt(),(w*.97).toInt(),(h*.68).toInt());else->Rect((w*.08).toInt(),(h*.12).toInt(),(w*.92).toInt(),(h*.72).toInt())}}
    private fun launchApp(app:AppEntry){
        if(remote==null){toast("Hubungkan Shizuku dahulu");requestOrBind();return}
        if(!Settings.canDrawOverlays(this)){startOverlay();toast("Izinkan menu di atas aplikasi, lalu tekan aplikasinya lagi");return}
        val intent=Intent(this,OverlayService::class.java).apply{action=OverlayService.ACTION_OPEN;putExtra(OverlayService.EXTRA_COMPONENT,app.component);putExtra(OverlayService.EXTRA_MODE,selectedMode);putExtra(OverlayService.EXTRA_PRESET,selectedPreset)}
        ContextCompat.startForegroundService(this,intent)
        LocalLogger.info("Permintaan jendela virtual ${app.component}; posisi=$selectedMode preset=$selectedPreset")
    }
    private fun enableModes(){val s=remote?:run{toast("Hubungkan Shizuku dahulu");return};Thread{val r=runCatching{s.enableWindowModes()}.getOrDefault(-1);LocalLogger.info("Aktivasi mode jendela hasil=$r");runOnUiThread{toast(if(r==0)"Mode aktif. Reboot satu kali bila perlu." else "Aktivasi gagal; lihat log")}}.start()}
    private fun startOverlay(){val s=remote;if(!Settings.canDrawOverlays(this)&&s!=null){Thread{val r=runCatching{s.allowOverlay(packageName)}.getOrDefault(-1);LocalLogger.info("AppOps overlay hasil=$r");runOnUiThread{startOverlayAfterPermission()}}.start()}else startOverlayAfterPermission()}
    private fun startOverlayAfterPermission(){if(!Settings.canDrawOverlays(this)){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));toast("Aktifkan 'Tampil di atas aplikasi lain'");return};ContextCompat.startForegroundService(this,Intent(this,OverlayService::class.java));toast("Floating handle diaktifkan")}

    private fun diagnose(){val s=remote?:run{toast("Hubungkan Shizuku dahulu");return};Thread{val base=runCatching{s.runDiagnostic(packageName)}.getOrElse{"Diagnosis gagal: ${it.message}"};runOnUiThread{val virtual=testVirtualDisplay();val text="$base\n\nUJI VIRTUAL DISPLAY\n$virtual";LocalLogger.info("HASIL DIAGNOSIS\n$text");showTextModal("Diagnosis perangkat",text,true)}}.start()}
    private fun testVirtualDisplay():String{return try{val st=SurfaceTexture(false);st.setDefaultBufferSize(240,360);val surface=Surface(st);val manager=getSystemService(DISPLAY_SERVICE) as DisplayManager;val flags=DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;val vd=manager.createVirtualDisplay("FloatSpace-Diagnostic",240,360,resources.displayMetrics.densityDpi,surface,flags);val result="BERHASIL — displayId=${vd.display.displayId}, valid=${vd.display.isValid}";vd.release();surface.release();st.release();result}catch(t:Throwable){LocalLogger.error("Uji virtual display gagal",t);"GAGAL — ${t.javaClass.simpleName}: ${t.message}"}}
    private fun showGuide(){
        val text="""PERSIAPAN WAJIB
1. Aktifkan Opsi pengembang.
2. Aktifkan Wireless debugging.
3. Jalankan Shizuku dan izinkan FloatSpace.
4. Tekan 'Aktifkan mode', lalu reboot satu kali.
5. Aktifkan Shizuku kembali setelah reboot.
6. Tekan 'Menu layar' untuk floating handle.

PENGGUNAAN SPLIT VIRTUAL
Pilih Split A lalu aplikasi pertama. Kembali ke FloatSpace, pilih Split B lalu aplikasi kedua. FloatSpace membuat dua virtual display karena firmware ini tidak memiliki split native.

KONTROL JENDELA
Geser header untuk memindahkan. Gunakan ↘ untuk resize. Tombol — mengecilkan, □ memaksimalkan, dan × menutup aplikasi. Mesin virtual tidak bergantung pada fitur freeform firmware.

LOG LOKAL
${LocalLogger.path()}"""
        val extra=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        extra.addView(darkButton("Buka Opsi Pengembang"){runCatching{startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}}.apply{layoutParams=LinearLayout.LayoutParams(-1,dp(42))})
        extra.addView(darkButton("Buka izin tampil di atas aplikasi"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}.apply{layoutParams=LinearLayout.LayoutParams(-1,dp(42)).apply{topMargin=dp(7)}})
        extra.addView(darkButton("Buka Shizuku"){packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(::startActivity)}.apply{layoutParams=LinearLayout.LayoutParams(-1,dp(42)).apply{topMargin=dp(7)}})
        showTextModal("Panduan FloatSpace",text,false,extra)
    }
    private fun showTextModal(title:String,text:String,copy:Boolean,extra:View?=null){
        val dialog=Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(17),dp(16),dp(17),dp(14));background=shape(Color.rgb(10,10,10),12,Color.rgb(92,92,92))}
        box.addView(TextView(this).apply{this.text=title;textSize=18f;setTextColor(Color.WHITE)})
        val scroll=ScrollView(this);val body=TextView(this).apply{this.text=text;textSize=11f;setTextColor(Color.LTGRAY);setTextIsSelectable(true);setPadding(0,dp(11),0,dp(11))};scroll.addView(body);scroll.layoutParams=LinearLayout.LayoutParams(-1,0,1f);box.addView(scroll);extra?.let{box.addView(it)}
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(10),0,0)}
        if(copy)row.addView(darkButton("Salin"){(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Diagnosis FloatSpace",text));toast("Diagnosis disalin")}.apply{layoutParams=LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(6)}})
        row.addView(button("Tutup"){dialog.dismiss()}.apply{layoutParams=LinearLayout.LayoutParams(0,dp(44),1f)});box.addView(row)
        dialog.setContentView(box);dialog.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setLayout((resources.displayMetrics.widthPixels*.91).toInt(),(resources.displayMetrics.heightPixels*.83).toInt());attributes=attributes.apply{gravity=Gravity.CENTER}};dialog.show();dialog.window?.setLayout((resources.displayMetrics.widthPixels*.91).toInt(),(resources.displayMetrics.heightPixels*.83).toInt())
    }
    private fun updateStatus(ok:Boolean,msg:String)=runOnUiThread{status.text=(if(ok)"●  " else "○  ")+msg;status.background=getDrawable(if(ok)R.drawable.bg_status_ok else R.drawable.bg_status_bad);status.setTextColor(if(ok)Color.WHITE else Color.LTGRAY)}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
