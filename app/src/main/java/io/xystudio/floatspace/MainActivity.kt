package io.xystudio.floatspace

import android.Manifest
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
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
    private val requestCodeShizuku = 6042
    private var remote: IRemoteService? = null
    private var virtualDisplayWorks: Boolean? = null
    private var selectedMode = 5
    private var selectedPreset = 0

    private lateinit var shizukuValue: TextView
    private lateinit var overlayValue: TextView
    private lateinit var displayValue: TextView
    private lateinit var engineValue: TextView
    private lateinit var primaryButton: Button
    private lateinit var sizeSection: LinearLayout
    private lateinit var adapter: AppAdapter

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshShizuku() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        remote = null
        LocalLogger.error("Binder Shizuku terputus")
        runOnUiThread { updateDashboard() }
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == requestCodeShizuku && result == PackageManager.PERMISSION_GRANTED) bindRemote()
        else {
            toast("Izin Shizuku ditolak")
            updateDashboard()
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IRemoteService.Stub.asInterface(service)
            LocalLogger.info("Shizuku UserService terhubung")
            runOnUiThread { updateDashboard() }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            updateDashboard()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 9, stroke: Int = Color.rgb(62, 62, 62)) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadApps()
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        primaryButton.post { runVirtualDisplayCheck() }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) adapter.refreshFavorites(Favorites.get(this))
        if (::primaryButton.isInitialized) updateDashboard()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        runCatching { Shizuku.unbindUserService(userServiceArgs(), connection, false) }
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), 0)
            setBackgroundColor(Color.BLACK)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBlock.addView(TextView(this).apply {
            text = "FloatSpace"
            textSize = 27f
            setTextColor(Color.WHITE)
        })
        titleBlock.addView(TextView(this).apply {
            text = "Kontrol jendela virtual  •  ${BuildConfig.VERSION_NAME}"
            textSize = 10f
            setTextColor(Color.rgb(145, 145, 145))
        })
        val helpButton = flatButton("Bantuan") { showGuide() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(38))
        }
        header.addView(titleBlock)
        header.addView(helpButton)
        root.addView(header)

        root.addView(sectionLabel("STATUS PERANGKAT", 15))
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(8), dp(13), dp(8))
            background = shape(Color.rgb(13, 13, 13), 11, Color.rgb(54, 54, 54))
        }
        shizukuValue = addStatusLine(statusCard, "Shizuku")
        addDivider(statusCard)
        overlayValue = addStatusLine(statusCard, "Tampil di atas aplikasi")
        addDivider(statusCard)
        displayValue = addStatusLine(statusCard, "Virtual display")
        addDivider(statusCard)
        engineValue = addStatusLine(statusCard, "Engine FloatSpace")
        root.addView(statusCard)

        primaryButton = Button(this).apply {
            textSize = 12f
            isAllCaps = false
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(9) }
            setOnClickListener { performNextSetupStep() }
        }
        root.addView(primaryButton)

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, 0)
        }
        utilityRow.addView(flatButton("Bilah samping") { startOverlay() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
        })
        utilityRow.addView(flatButton("Cek sistem") { diagnose() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) }
        })
        utilityRow.addView(flatButton("Folder log") { showLogLocation() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) }
        })
        root.addView(utilityRow)

        root.addView(sectionLabel("TATA LETAK JENDELA", 13))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val modeItems = listOf(
            Triple("Bebas", 5, "Bisa digeser dan diubah ukurannya"),
            Triple("Atas", 3, "Menempati setengah layar atas"),
            Triple("Bawah", 4, "Menempati setengah layar bawah")
        )
        modeItems.forEachIndexed { index, item ->
            val button = choiceButton(item.first, item.second == selectedMode).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(41), 1f).apply {
                    if (index > 0) marginStart = dp(6)
                }
            }
            button.setOnClickListener {
                selectedMode = item.second
                for (i in 0 until modeRow.childCount) {
                    styleChoice(modeRow.getChildAt(i) as Button, i == index)
                }
                sizeSection.visibility = if (selectedMode == 5) View.VISIBLE else View.GONE
                toast(item.third)
            }
            modeRow.addView(button)
        }
        root.addView(modeRow)

        sizeSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }
        sizeSection.addView(TextView(this).apply {
            text = "Ukuran awal"
            textSize = 10f
            setTextColor(Color.rgb(145, 145, 145))
            setPadding(0, dp(8), 0, dp(5))
        })
        val sizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Kecil", "Tinggi", "Lebar").forEachIndexed { index, label ->
            val button = choiceButton(label, index == selectedPreset).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(37), 1f).apply {
                    if (index > 0) marginStart = dp(6)
                }
            }
            button.setOnClickListener {
                selectedPreset = index
                for (i in 0 until sizeRow.childCount) {
                    styleChoice(sizeRow.getChildAt(i) as Button, i == index)
                }
            }
            sizeRow.addView(button)
        }
        sizeSection.addView(sizeRow)
        root.addView(sizeSection)

        val appHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(6))
        }
        appHeader.addView(TextView(this).apply {
            text = "Pilih aplikasi"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        appHeader.addView(TextView(this).apply {
            text = "☆ simpan ke bilah"
            textSize = 9f
            setTextColor(Color.GRAY)
        })
        root.addView(appHeader)

        val search = EditText(this).apply {
            hint = "Cari aplikasi"
            setHintTextColor(Color.rgb(120, 120, 120))
            setTextColor(Color.WHITE)
            textSize = 12f
            setSingleLine(true)
            background = getDrawable(R.drawable.bg_search)
            setPadding(dp(13), 0, dp(13), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(43)).apply { bottomMargin = dp(8) }
        }
        adapter = AppAdapter(::launchApp) { entry ->
            val added = Favorites.toggle(this, entry.component)
            adapter.refreshFavorites(Favorites.get(this))
            toast(if (added) "Ditambahkan ke bilah samping" else "Dihapus dari bilah samping")
            added
        }
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                adapter.filter(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        root.addView(search)
        root.addView(list)
        setContentView(root)
        updateDashboard()
    }

    private fun sectionLabel(text: String, top: Int) = TextView(this).apply {
        this.text = text
        textSize = 9f
        letterSpacing = .12f
        setTextColor(Color.rgb(165, 165, 165))
        setPadding(0, dp(top), 0, dp(6))
    }

    private fun addStatusLine(parent: LinearLayout, label: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, dp(27), 1f)
            gravity = Gravity.CENTER_VERTICAL
        })
        val value = TextView(this).apply {
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(9), 0, dp(9), 0)
        }
        row.addView(value, LinearLayout.LayoutParams(-2, dp(27)))
        parent.addView(row)
        return value
    }

    private fun addDivider(parent: LinearLayout) = parent.addView(View(this).apply {
        setBackgroundColor(Color.rgb(40, 40, 40))
    }, LinearLayout.LayoutParams(-1, dp(1)))

    private fun styleStatus(view: TextView, state: Boolean?, active: String, inactive: String) {
        when (state) {
            true -> {
                view.text = "●  $active"
                view.setTextColor(Color.rgb(133, 231, 167))
                view.background = shape(Color.rgb(19, 53, 33), 7, Color.rgb(45, 104, 65))
            }
            false -> {
                view.text = "●  $inactive"
                view.setTextColor(Color.rgb(255, 158, 169))
                view.background = shape(Color.rgb(58, 27, 33), 7, Color.rgb(111, 52, 62))
            }
            null -> {
                view.text = "○  Memeriksa"
                view.setTextColor(Color.LTGRAY)
                view.background = shape(Color.rgb(33, 33, 33), 7, Color.rgb(67, 67, 67))
            }
        }
    }

    private fun updateDashboard() {
        if (!::primaryButton.isInitialized) return
        val shizukuRunning = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED && remote != null
        }.getOrDefault(false)
        val overlayAllowed = Settings.canDrawOverlays(this)
        val ready = shizukuRunning && overlayAllowed && virtualDisplayWorks == true

        styleStatus(shizukuValue, shizukuRunning, "Aktif", "Belum aktif")
        styleStatus(overlayValue, overlayAllowed, "Diizinkan", "Belum diizinkan")
        styleStatus(displayValue, virtualDisplayWorks, "Didukung", "Gagal")
        styleStatus(engineValue, if (virtualDisplayWorks == null) null else ready, "SIAP", "Belum siap")

        when {
            !shizukuRunning -> stylePrimary("1  Hubungkan Shizuku", false)
            !overlayAllowed -> stylePrimary("2  Izinkan tampil di atas aplikasi", false)
            virtualDisplayWorks == null -> stylePrimary("3  Uji virtual display", false)
            virtualDisplayWorks == false -> stylePrimary("Virtual display gagal — buka Cek sistem", false)
            else -> stylePrimary("✓  SIAP — pilih aplikasi di bawah", true)
        }
    }

    private fun stylePrimary(text: String, ready: Boolean) {
        primaryButton.text = text
        primaryButton.setTextColor(if (ready) Color.BLACK else Color.WHITE)
        primaryButton.background = shape(
            if (ready) Color.WHITE else Color.rgb(29, 29, 29),
            9,
            if (ready) Color.WHITE else Color.rgb(78, 78, 78)
        )
    }

    private fun performNextSetupStep() {
        val shizukuReady = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED && remote != null
        }.getOrDefault(false)
        when {
            !shizukuReady -> requestOrBind()
            !Settings.canDrawOverlays(this) -> startOverlay()
            virtualDisplayWorks != true -> runVirtualDisplayCheck()
            else -> toast("Engine sudah siap. Pilih aplikasi dari daftar.")
        }
    }

    private fun flatButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 9.5f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = shape(Color.rgb(22, 22, 22), 8, Color.rgb(58, 58, 58))
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun choiceButton(label: String, selected: Boolean) = Button(this).apply {
        text = label
        textSize = 10f
        isAllCaps = false
        stateListAnimator = null
        styleChoice(this, selected)
    }

    private fun styleChoice(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) Color.BLACK else Color.rgb(195, 195, 195))
        button.background = shape(
            if (selected) Color.WHITE else Color.rgb(20, 20, 20),
            8,
            if (selected) Color.WHITE else Color.rgb(57, 57, 57)
        )
    }

    private fun loadApps() {
        Thread {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val items = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { resolved ->
                    val activity = resolved.activityInfo ?: return@mapNotNull null
                    if (activity.packageName == packageName) return@mapNotNull null
                    AppEntry(
                        resolved.loadLabel(packageManager).toString(),
                        ComponentName(activity.packageName, activity.name).flattenToString(),
                        activity.packageName,
                        resolved.loadIcon(packageManager)
                    )
                }.distinctBy { it.component }.sortedBy { it.label.lowercase() }
            runOnUiThread { adapter.submit(items, Favorites.get(this)) }
        }.start()
    }

    private fun refreshShizuku() {
        if (Shizuku.pingBinder()) requestOrBind() else updateDashboard()
    }

    private fun requestOrBind() {
        try {
            if (!Shizuku.pingBinder()) {
                toast("Jalankan Shizuku melalui Wireless debugging dahulu")
                packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(::startActivity)
                return
            }
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bindRemote()
                Shizuku.shouldShowRequestPermissionRationale() -> toast("Izinkan FloatSpace dari halaman aplikasi Shizuku")
                else -> Shizuku.requestPermission(requestCodeShizuku)
            }
        } catch (t: Throwable) {
            LocalLogger.error("Gagal meminta Shizuku", t)
            toast("Shizuku tidak tersedia")
        }
        updateDashboard()
    }

    private fun userServiceArgs() =
        Shizuku.UserServiceArgs(ComponentName(this, RemoteService::class.java))
            .daemon(false).processNameSuffix("float").debuggable(BuildConfig.DEBUG).version(4)

    private fun bindRemote() {
        styleStatus(shizukuValue, null, "Aktif", "Belum aktif")
        runCatching { Shizuku.bindUserService(userServiceArgs(), connection) }
            .onFailure {
                LocalLogger.error("Bind Shizuku gagal", it)
                toast("Gagal menghubungkan service Shizuku")
                updateDashboard()
            }
    }

    private fun launchApp(app: AppEntry) {
        val ready = remote != null && Settings.canDrawOverlays(this) && virtualDisplayWorks == true
        if (!ready) {
            toast("Selesaikan bagian Status Perangkat dahulu")
            performNextSetupStep()
            return
        }
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_OPEN
            putExtra(OverlayService.EXTRA_COMPONENT, app.component)
            putExtra(OverlayService.EXTRA_MODE, selectedMode)
            putExtra(OverlayService.EXTRA_PRESET, selectedPreset)
        }
        ContextCompat.startForegroundService(this, intent)
        LocalLogger.info("Permintaan jendela ${app.component}; mode=$selectedMode preset=$selectedPreset")
    }

    private fun startOverlay() {
        val service = remote
        if (!Settings.canDrawOverlays(this) && service != null) {
            Thread {
                val result = runCatching { service.allowOverlay(packageName) }.getOrDefault(-1)
                LocalLogger.info("AppOps overlay hasil=$result")
                runOnUiThread { startOverlayAfterPermission() }
            }.start()
        } else startOverlayAfterPermission()
    }

    private fun startOverlayAfterPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            toast("Aktifkan izin tampil di atas aplikasi")
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        toast("Bilah samping aktif")
        updateDashboard()
    }

    private fun runVirtualDisplayCheck() {
        virtualDisplayWorks = null
        updateDashboard()
        primaryButton.post {
            val result = testVirtualDisplay()
            virtualDisplayWorks = result.startsWith("BERHASIL")
            LocalLogger.info("UJI VIRTUAL DISPLAY OTOMATIS\n$result")
            updateDashboard()
        }
    }

    private fun testVirtualDisplay(): String = try {
        val texture = SurfaceTexture(false)
        texture.setDefaultBufferSize(240, 360)
        val surface = Surface(texture)
        val manager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        val display = manager.createVirtualDisplay(
            "FloatSpace-Diagnostic", 240, 360,
            resources.displayMetrics.densityDpi, surface, flags
        )
        val result = "BERHASIL — displayId=${display.display.displayId}, valid=${display.display.isValid}, flags=$flags"
        display.release()
        surface.release()
        texture.release()
        result
    } catch (t: Throwable) {
        LocalLogger.error("Uji virtual display gagal", t)
        "GAGAL — ${t.javaClass.simpleName}: ${t.message}"
    }

    private fun diagnose() {
        val service = remote ?: run {
            toast("Hubungkan Shizuku dahulu")
            return
        }
        Thread {
            val base = runCatching { service.runDiagnostic(packageName) }
                .getOrElse { "Diagnosis gagal: ${it.message}" }
            runOnUiThread {
                val virtual = testVirtualDisplay()
                val text = "$base\n\nUJI VIRTUAL DISPLAY\n$virtual\n\nSTATUS ENGINE\n" +
                    if (remote != null && Settings.canDrawOverlays(this) && virtual.startsWith("BERHASIL")) "SIAP" else "BELUM SIAP"
                LocalLogger.info("HASIL DIAGNOSIS\n$text")
                showTextModal("Cek sistem", text, true)
                updateDashboard()
            }
        }.start()
    }

    private fun showLogLocation() = showTextModal(
        "Log lokal",
        "Kirim ketiga file ini jika terjadi masalah:\n\n${LocalLogger.path()}/info.txt\n${LocalLogger.path()}/error.txt\n${LocalLogger.path()}/crash.txt",
        true
    )

    private fun showGuide() {
        val text = """URUTAN YANG BENAR

1. Pastikan Shizuku berjalan melalui Wireless debugging.
2. Tekan tombol utama sampai status Shizuku menjadi AKTIF.
3. Izinkan tampil di atas aplikasi sampai status menjadi DIIZINKAN.
4. Pastikan Virtual display bertuliskan DIDUKUNG.
5. Engine harus menampilkan SIAP.
6. Pilih Bebas, Atas, atau Bawah.
7. Tekan aplikasi dari daftar.

ARTI TATA LETAK

Bebas: jendela dapat digeser dan di-resize.
Atas: aplikasi mengisi setengah layar atas.
Bawah: aplikasi mengisi setengah layar bawah.

KONTROL JENDELA

—  kecilkan/pulihkan
□  maksimalkan/pulihkan
×  tutup aplikasi
↘  ubah ukuran

LOG

${LocalLogger.path()}"""
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        actions.addView(flatButton("Buka Opsi Pengembang") {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        actions.addView(flatButton("Buka Shizuku") {
            packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(::startActivity)
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(6) })
        actions.addView(flatButton("Buka izin overlay") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(6) })
        showTextModal("Panduan setup", text, false, actions)
    }

    private fun showTextModal(title: String, text: String, copy: Boolean, extra: View? = null) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(13))
            background = shape(Color.rgb(10, 10, 10), 11, Color.rgb(91, 91, 91))
        }
        box.addView(TextView(this).apply {
            this.text = title
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setTextIsSelectable(true)
            setPadding(0, dp(11), 0, dp(11))
        })
        scroll.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        box.addView(scroll)
        extra?.let(box::addView)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(9), 0, 0)
        }
        if (copy) row.addView(flatButton("Salin") {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("FloatSpace", text))
            toast("Disalin")
        }, LinearLayout.LayoutParams(0, dp(41), 1f).apply { marginEnd = dp(6) })
        row.addView(flatButton("Tutup") { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(41), 1f))
        box.addView(row)
        dialog.setContentView(box)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((resources.displayMetrics.widthPixels * .91).toInt(), (resources.displayMetrics.heightPixels * .83).toInt())
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
