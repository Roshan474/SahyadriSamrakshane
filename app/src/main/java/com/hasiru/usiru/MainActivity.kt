package com.hasiru.usiru

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.hasiru.usiru.data.AlertStatus
import com.hasiru.usiru.data.AlertType
import com.hasiru.usiru.data.AppDatabase
import com.hasiru.usiru.data.EcologicalAlert
import com.hasiru.usiru.sync.AlertFirebaseSync
import com.hasiru.usiru.sync.AlertSyncWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private val database by lazy { AppDatabase.get(this) }
    private val dao by lazy { database.alertDao() }
    private val sync by lazy { AlertFirebaseSync(dao) }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var bottomNav: BottomNavigationView
    private var previewView: PreviewView? = null
    private var imageCapture: ImageCapture? = null
    private var latestLocation: Location? = null
    private var latestPhotoPath: String? = null
    private var latestAlerts: List<EcologicalAlert> = emptyList()
    private var googleMap: GoogleMap? = null
    private var gpsSubtitleText: TextView? = null
    private var gpsDetailText: TextView? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startLocationUpdates()
        startCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth.signInAnonymously()
        ensurePermissions()
        showApp()
        observeAlerts()
    }

    private fun showApp() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.mist))
        }
        content = FrameLayout(this).apply { id = View.generateViewId() }
        bottomNav = BottomNavigationView(this).apply {
            setBackgroundColor(color(R.color.mist))
            menu.add(0, NAV_REPORT, 0, "Report").setIcon(android.R.drawable.ic_menu_camera)
            menu.add(0, NAV_ALERTS, 1, "Alerts").setIcon(android.R.drawable.ic_dialog_alert)
            menu.add(0, NAV_MAP, 2, "Map").setIcon(android.R.drawable.ic_dialog_map)
            menu.add(0, NAV_LEARN, 3, "Tips").setIcon(android.R.drawable.ic_menu_info_details)
            setOnItemSelectedListener {
                when (it.itemId) {
                    NAV_REPORT -> showReport()
                    NAV_ALERTS -> showAlerts()
                    NAV_MAP -> showMap()
                    NAV_LEARN -> showEducation()
                }
                true
            }
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        bottomNav.selectedItemId = NAV_REPORT
    }

    private fun observeAlerts() {
        lifecycleScope.launch {
            dao.observeAll().collectLatest { alerts ->
                latestAlerts = alerts
                if (::bottomNav.isInitialized) {
                    when (bottomNav.selectedItemId) {
                        NAV_ALERTS -> showAlerts()
                        NAV_MAP -> refreshMap()
                    }
                }
            }
        }
    }

    private fun showReport() {
        content.removeAllViews()
        content.addView(scroll {
            addView(title("Sahyadri Samrakshane"))
            addView(text("Forest Sentinel for Western Ghats ecological alerts. Reports are cached first, so low-signal areas are still covered."))

            previewView = PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            addView(previewView, LinearLayout.LayoutParams(-1, dp(260)).apply { setMargins(0, 14, 0, 12) })
            addView(Button(context).apply {
                text = "Capture Photo"
                setOnClickListener { capturePhoto() }
            }, matchWrap())

            val typeSpinner = Spinner(context).apply {
                adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    AlertType.entries.map { it.label }
                )
            }
            val notes = TextInputEditText(context).apply {
                hint = "Notes: trail name, visible risk, nearby landmark"
                minLines = 3
            }
            addView(typeSpinner, matchWrap())
            addView(gpsCard())
            addView(notes, matchWrap())
            addView(Button(context).apply {
                text = "Use Best GPS Lock"
                setOnClickListener { startLocationUpdates() }
            }, matchWrap())
            addView(Button(context).apply {
                text = "Submit Ecological Alert"
                setOnClickListener { submitAlert(typeSpinner, notes) }
            }, matchWrap())
            addView(Button(context).apply {
                text = "Sync Pending Reports"
                setOnClickListener { manualSync() }
            }, matchWrap())
        })
        startCamera()
        startLocationUpdates()
    }

    private fun gpsCard(): View {
        return MaterialCardView(this).apply {
            radius = 8f
            cardElevation = 2f
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 18, 20, 18)
                addView(TextView(context).apply {
                    text = "Live GPS Coordinates"
                    textSize = 21f
                    setTextColor(color(R.color.canopy_dark))
                })
                gpsSubtitleText = TextView(context).apply {
                    textSize = 16f
                    setTextColor(color(R.color.canopy))
                    setPadding(0, 4, 0, 4)
                }
                gpsDetailText = text("")
                addView(gpsSubtitleText)
                addView(gpsDetailText)
                updateGpsCard()
            })
        }
    }

    private fun submitAlert(typeSpinner: Spinner, notes: TextInputEditText) {
        val location = latestLocation ?: return toast("Wait for GPS coordinates first")
        val alert = EcologicalAlert(
            type = AlertType.entries[typeSpinner.selectedItemPosition],
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            photoPath = latestPhotoPath,
            notes = notes.text?.toString().orEmpty()
        )
        lifecycleScope.launch {
            dao.insert(alert)
            latestPhotoPath = null
            enqueueBackgroundSync()
            toast("Report cached. It will sync when signal is available.")
            bottomNav.selectedItemId = NAV_ALERTS
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        fusedLocation.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->
            if (location != null) {
                latestLocation = location
                updateGpsCard()
            } else {
                fusedLocation.lastLocation.addOnSuccessListener { last ->
                    latestLocation = last
                    updateGpsCard()
                }
            }
        }
    }

    private fun updateGpsCard() {
        val location = latestLocation
        gpsSubtitleText?.text = if (location == null) {
            "Waiting for GPS lock"
        } else {
            "Lat ${"%.6f".format(location.latitude)}  Lng ${"%.6f".format(location.longitude)}"
        }
        gpsDetailText?.text = if (location == null) {
            "Coordinates are displayed here before submission."
        } else {
            "Accuracy ${location.accuracy.roundToInt()}m. High precision is best below 10m."
        }
    }

    private fun startCamera() {
        val view = previewView ?: return
        if (!hasPermission(Manifest.permission.CAMERA)) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return toast("Camera is starting. Try again in a moment.")
        val photoFile = File(getExternalFilesDir("alerts"), "alert_${timestamp()}.jpg").apply {
            parentFile?.mkdirs()
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    latestPhotoPath = photoFile.absolutePath
                    toast("Photo captured with current GPS")
                }

                override fun onError(exception: ImageCaptureException) {
                    toast("Photo failed: ${exception.message}")
                }
            }
        )
    }

    private fun showAlerts() {
        content.removeAllViews()
        content.addView(scroll {
            addView(title("Alert Dashboard"))
            addView(card(
                "Cached Reports",
                "${latestAlerts.count { !it.synced }} waiting to sync",
                "Offline-first storage keeps reports safe until network returns."
            ))
            latestAlerts.forEach { alert ->
                addView(alertCard(alert), matchWrap())
            }
            if (latestAlerts.isEmpty()) {
                addView(card("No alerts yet", "Create your first report", "Capture a photo and GPS location from the Report tab."))
            }
        })
    }

    private fun alertCard(alert: EcologicalAlert): View =
        MaterialCardView(this).apply {
            radius = 8f
            cardElevation = 2f
            setCardBackgroundColor(color(R.color.mist))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 18, 20, 18)
                addView(TextView(context).apply {
                    text = alert.type.label
                    textSize = 22f
                    setTextColor(color(R.color.canopy_dark))
                })
                addView(text("${alert.status.label}  |  ${if (alert.synced) "Synced" else "Pending sync"}"))
                addView(text("Lat ${"%.6f".format(alert.latitude)}  Lng ${"%.6f".format(alert.longitude)}"))
                addView(text("Accuracy ${alert.accuracyMeters.roundToInt()}m  |  ${date(alert.createdAt)}"))
                if (alert.photoPath != null) {
                    addView(ImageView(context).apply {
                        setImageURI(android.net.Uri.fromFile(File(alert.photoPath)))
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }, LinearLayout.LayoutParams(-1, dp(160)).apply { setMargins(0, 10, 0, 10) })
                }
                addView(statusChips(alert))
            })
        }

    private fun statusChips(alert: EcologicalAlert): View =
        ChipGroup(this).apply {
            isSingleSelection = true
            AlertStatus.entries.forEach { status ->
                addView(Chip(context).apply {
                    id = View.generateViewId()
                    text = status.label
                    isCheckable = true
                    isChecked = alert.status == status
                    setOnClickListener {
                        lifecycleScope.launch {
                            dao.updateStatus(alert.id, status)
                            enqueueBackgroundSync()
                        }
                    }
                })
            }
        }

    private fun showMap() {
        content.removeAllViews()
        val id = View.generateViewId()
        content.addView(FrameLayout(this).apply { this.id = id }, FrameLayout.LayoutParams(-1, -1))
        val fragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction().replace(id, fragment).commitNow()
        fragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        refreshMap()
    }

    private fun refreshMap() {
        val map = googleMap ?: return
        map.clear()
        latestAlerts.forEach { alert ->
            val hue = when (alert.type) {
                AlertType.FOREST_FIRE -> BitmapDescriptorFactory.HUE_RED
                AlertType.LANDSLIDE -> BitmapDescriptorFactory.HUE_ORANGE
                AlertType.ILLEGAL_TREE_CUTTING -> BitmapDescriptorFactory.HUE_GREEN
                AlertType.WILDLIFE_SIGHTING -> BitmapDescriptorFactory.HUE_AZURE
            }
            map.addMarker(
                com.google.android.gms.maps.model.MarkerOptions()
                    .position(LatLng(alert.latitude, alert.longitude))
                    .title(alert.type.label)
                    .snippet("${alert.status.label}, accuracy ${alert.accuracyMeters.roundToInt()}m")
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
            )
        }
        val focus = latestAlerts.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
            ?: latestLocation?.let { LatLng(it.latitude, it.longitude) }
            ?: LatLng(13.3409, 75.8050)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, if (latestAlerts.isEmpty()) 8f else 14f))
    }

    private fun showEducation() {
        content.removeAllViews()
        content.addView(scroll {
            addView(title("Eco-Sensitive Zone Tips"))
            AlertType.entries.forEach { type ->
                addView(card(type.label, "What to do safely", type.guidance), matchWrap())
            }
            addView(card(
                "Western Ghats protocol",
                "Protect India's water tower",
                "Stay on marked trails, carry back all waste, avoid loud music, do not enter restricted forest paths, and share verified alerts with authorities."
            ), matchWrap())
        })
    }

    private fun manualSync() {
        lifecycleScope.launch {
            runCatching { sync.pushUnsynced() }
                .onSuccess { count -> toast("Synced $count pending reports") }
                .onFailure {
                    enqueueBackgroundSync()
                    toast("No signal or Firebase setup issue. Report remains cached.")
                }
        }
    }

    private fun enqueueBackgroundSync() {
        val request = OneTimeWorkRequestBuilder<AlertSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "alert-sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun ensurePermissions() {
        val missing = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        ).filterNot(::hasPermission)
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun scroll(build: LinearLayout.() -> Unit): ScrollView =
        ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                build()
            })
        }

    private fun title(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 28f
        setTextColor(color(R.color.canopy_dark))
        setPadding(0, 0, 0, 10)
    }

    private fun text(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(color(R.color.earth))
        setPadding(0, 4, 0, 4)
    }

    private fun card(title: String, subtitle: String, detail: String): MaterialCardView =
        MaterialCardView(this).apply {
            radius = 8f
            cardElevation = 2f
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 18, 20, 18)
                addView(TextView(context).apply {
                    text = title
                    textSize = 21f
                    setTextColor(color(R.color.canopy_dark))
                })
                addView(TextView(context).apply {
                    text = subtitle
                    textSize = 16f
                    setTextColor(color(R.color.canopy))
                })
                addView(text(detail))
            })
        }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8, 0, 8) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private fun date(value: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(value))

    companion object {
        private const val NAV_REPORT = 1
        private const val NAV_ALERTS = 2
        private const val NAV_MAP = 3
        private const val NAV_LEARN = 4
    }
}
