package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class KeyMapService : AccessibilityService() {

    companion object {
        const val ACTION_EDIT_MODE = "com.example.ACTION_EDIT_MODE"
    }

    private lateinit var windowManager: WindowManager
    private var editOverlay: View? = null
    private var bindingsContainer: FrameLayout? = null

    private var isEditMode = false
    private var waitForKey = false
    private var bindings = mutableListOf<KeyBinding>()
    private var currentPackage: String = "Global"

    private var notificationManager: NotificationManager? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_EDIT_MODE) {
                try {
                    @Suppress("DEPRECATION")
                    sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                } catch(e: Exception) {}
                openEditMode()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val filter = IntentFilter(ACTION_EDIT_MODE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        loadCurrentBindings()
        updateNotification()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            unregisterReceiver(receiver)
        } catch(e: Exception) {}
        notificationManager?.cancel(1)
        removeEditOverlay()
        return super.onUnbind(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "octomap_service",
                "OctoMap Status",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val intent = Intent(ACTION_EDIT_MODE).apply { setPackage(packageName) }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "octomap_service")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("OctoMap: $currentPackage")
            .setContentText("Tap to edit keybindings for this app")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
            
        notificationManager?.notify(1, notification)
    }

    private fun loadCurrentBindings() {
        bindings = BindingRepository.loadBindings(this, currentPackage)
    }

    private fun saveCurrentBindings() {
        BindingRepository.saveBindings(this, currentPackage, bindings)
    }

    private fun openEditMode() {
        if (isEditMode) return
        isEditMode = true
        showEditOverlay()
        Toast.makeText(this, "Edit Mode: Drag bindings or Add new ones.", Toast.LENGTH_SHORT).show()
    }

    private fun closeEditMode() {
        isEditMode = false
        saveCurrentBindings()
        removeEditOverlay()
        Toast.makeText(this, "Bindings Saved for $currentPackage", Toast.LENGTH_SHORT).show()
    }

    private fun showEditOverlay() {
        if (editOverlay != null) return

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        bindingsContainer = FrameLayout(this)
        container.addView(bindingsContainer, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val appName = try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(currentPackage, 0)
            pm.getApplicationLabel(ai)
        } catch (e: Exception) { currentPackage }

        val profileLabel = TextView(this).apply {
            text = "Profile: $appName"
            setTextColor(Color.WHITE)
            setPadding(0, 0, dpToPx(16), 0)
            textSize = 14f
        }
        
        val presetsBtn = Button(this).apply {
            text = "Presets"
            setOnClickListener {
                showPresetsMenu()
            }
        }
        
        val addBtn = Button(this).apply {
            text = "+ Add"
            setOnClickListener {
                waitForKey = true
                Toast.makeText(this@KeyMapService, "Press any key", Toast.LENGTH_LONG).show()
            }
        }

        val clearBtn = Button(this).apply {
            text = "Clear All"
            setOnClickListener {
                bindings.clear()
                refreshBindingViews()
            }
        }

        val saveBtn = Button(this).apply {
            text = "Save & Close"
            setOnClickListener {
                closeEditMode()
            }
        }

        topBar.addView(profileLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f))
        topBar.addView(presetsBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(addBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(clearBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(saveBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f))

        container.addView(topBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        editOverlay = container
        windowManager.addView(container, params)

        refreshBindingViews()
    }

    private fun removeEditOverlay() {
        editOverlay?.let {
            windowManager.removeView(it)
            editOverlay = null
            bindingsContainer = null
        }
        waitForKey = false
    }

    private fun showPresetsMenu() {
        val menuContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E6000000"))
        }

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(32), dpToPx(32), dpToPx(32), dpToPx(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        
        val title = TextView(this).apply {
            text = "Presets"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(0, 0, 0, dpToPx(24))
        }
        layout.addView(title)

        val saveBtn = Button(this).apply {
            text = "Save Current as Preset"
            setOnClickListener {
                val presetName = "Preset ${(Math.random() * 10000).toInt()}"
                BindingRepository.savePreset(this@KeyMapService, presetName, bindings)
                Toast.makeText(this@KeyMapService, "Saved as $presetName", Toast.LENGTH_SHORT).show()
                (menuContainer.parent as? ViewGroup)?.removeView(menuContainer)
            }
        }
        layout.addView(saveBtn)

        val presets = BindingRepository.getPresets(this)
        for (preset in presets) {
            val pBtn = Button(this).apply {
                text = "Load $preset"
                setOnClickListener {
                    val loaded = BindingRepository.loadPreset(this@KeyMapService, preset)
                    bindings.clear()
                    bindings.addAll(loaded)
                    refreshBindingViews()
                    Toast.makeText(this@KeyMapService, "Loaded $preset", Toast.LENGTH_SHORT).show()
                    (menuContainer.parent as? ViewGroup)?.removeView(menuContainer)
                }
            }
            layout.addView(pBtn)
        }
        
        val closeBtn = Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                (menuContainer.parent as? ViewGroup)?.removeView(menuContainer)
            }
        }
        layout.addView(closeBtn)

        scroll.addView(layout)
        menuContainer.addView(scroll, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        
        (editOverlay as? FrameLayout)?.addView(menuContainer)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun refreshBindingViews() {
        bindingsContainer?.removeAllViews()
        for (binding in bindings) {
            val view = TextView(this).apply {
                text = binding.keyName
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#E91E63"))
                bg.cornerRadius = dpToPx(12).toFloat()
                background = bg
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                x = binding.x
                y = binding.y
            }

            var dX = 0f
            var dY = 0f

            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = v.x - event.rawX
                        dY = v.y - event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newX = event.rawX + dX
                        val newY = event.rawY + dY
                        v.x = newX
                        v.y = newY
                        binding.x = newX
                        binding.y = newY
                    }
                }
                true
            }

            val layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            view.minimumWidth = dpToPx(60)
            view.minimumHeight = dpToPx(60)
            bindingsContainer?.addView(view, layoutParams)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isEditMode) {
            if (waitForKey && event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    waitForKey = false
                    return true
                }
                
                val centerItemX = resources.displayMetrics.widthPixels / 2f - dpToPx(30)
                val centerItemY = resources.displayMetrics.heightPixels / 2f - dpToPx(30)
                
                var name = KeyEvent.keyCodeToString(event.keyCode)
                if (name.startsWith("KEYCODE_")) name = name.removePrefix("KEYCODE_")

                val newBinding = KeyBinding(
                    keyCode = event.keyCode,
                    keyName = name,
                    x = centerItemX,
                    y = centerItemY
                )
                bindings.add(newBinding)
                waitForKey = false

                Handler(Looper.getMainLooper()).post {
                    refreshBindingViews()
                    Toast.makeText(this, "Mapped: ${newBinding.keyName}", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            return true
        } else {
            val binding = bindings.find { it.keyCode == event.keyCode }
            if (binding != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    dispatchTap(binding.x, binding.y)
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun dispatchTap(x: Float, y: Float) {
        val adjustedX = x + dpToPx(30)
        val adjustedY = y + dpToPx(30)

        val path = Path().apply { moveTo(adjustedX, adjustedY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (isEditMode) return // Don't switch profiles while editing

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            
            // Ignore system UI and self
            if (pkg != currentPackage && pkg != packageName && pkg != "com.android.systemui" && pkg != "com.google.android.permissioncontroller") {
                currentPackage = pkg
                loadCurrentBindings()
                updateNotification()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
