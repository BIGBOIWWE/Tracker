package com.example.friendlink

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var map: MapView
    private lateinit var fused: FusedLocationProviderClient
    private var usersListener: ListenerRegistration? = null
    private var myName = "Friend"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        fused = LocationServices.getFusedLocationProviderClient(this)
        askPermissions()
        signInThenShowApp()
    }

    private fun askPermissions() {
        val needed = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
    }

    private fun signInThenShowApp() {
        if (auth.currentUser != null) {
            showNamePrompt()
        } else {
            auth.signInAnonymously().addOnSuccessListener { showNamePrompt() }
                .addOnFailureListener { Toast.makeText(this, "Firebase sign-in failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun showNamePrompt() {
        val input = EditText(this)
        input.hint = "Your display name"
        AlertDialog.Builder(this)
            .setTitle("FriendLink")
            .setMessage("Enter a name your friends will see.")
            .setView(input)
            .setPositiveButton("Start") { _, _ ->
                myName = input.text.toString().ifBlank { "Friend" }
                saveMyProfile()
                showMainScreen()
                startLocationUpdates()
            }
            .show()
    }

    private fun saveMyProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(mapOf(
            "name" to myName,
            "lastSeen" to Timestamp.now()
        ), com.google.firebase.firestore.SetOptions.merge())
    }

    private fun showMainScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(30.2672, -97.7431))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val everyone = Button(this).apply { text = "Everyone Chat"; setOnClickListener { openChat("global", "Everyone") } }
        val talk = Button(this).apply { text = "Hold to Talk"; setOnClickListener { toastVoiceMvp() } }
        bar.addView(everyone, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(talk, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(map)
        root.addView(bar)
        setContentView(root)
        listenForUsers()
    }

    private fun listenForUsers() {
        usersListener?.remove()
        usersListener = db.collection("users").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            map.overlays.clear()
            val myUid = auth.currentUser?.uid
            for (doc in snap.documents) {
                val lat = doc.getDouble("lat") ?: continue
                val lng = doc.getDouble("lng") ?: continue
                val name = doc.getString("name") ?: "Friend"
                val uid = doc.id
                val marker = Marker(map).apply {
                    position = GeoPoint(lat, lng)
                    title = name
                    snippet = if (uid == myUid) "This is you" else "Tap to DM"
                    setOnMarkerClickListener { m, _ ->
                        m.showInfoWindow()
                        if (uid != myUid) openChat(privateThreadId(myUid ?: "me", uid), name)
                        true
                    }
                }
                map.overlays.add(marker)
            }
            map.invalidate()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10000L).build()
        fused.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val uid = auth.currentUser?.uid ?: return
                map.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                db.collection("users").document(uid).set(mapOf(
                    "name" to myName,
                    "lat" to loc.latitude,
                    "lng" to loc.longitude,
                    "lastSeen" to Timestamp.now()
                ), com.google.firebase.firestore.SetOptions.merge())
            }
        }, mainLooper)
    }

    private fun openChat(threadId: String, title: String) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = TextView(this).apply { text = title; textSize = 22f; gravity = Gravity.CENTER; setPadding(8,16,8,16) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val input = EditText(this).apply { hint = "Message" }
        val send = Button(this).apply { text = "Send" }
        val back = Button(this).apply { text = "Map"; setOnClickListener { showMainScreen() } }
        val ptt = Button(this).apply { text = "Private Talk"; setOnClickListener { toastVoiceMvp() } }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        inputRow.addView(send)
        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(inputRow)
        root.addView(ptt)
        root.addView(back)
        setContentView(root)

        db.collection("chats").document(threadId).collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snap, _ ->
                list.removeAllViews()
                snap?.documents?.forEach { d ->
                    val name = d.getString("senderName") ?: "Friend"
                    val text = d.getString("text") ?: ""
                    list.addView(TextView(this).apply { this.text = "$name: $text"; textSize = 18f; setPadding(12,8,12,8) })
                }
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }

        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                db.collection("chats").document(threadId).collection("messages").add(mapOf(
                    "senderId" to (auth.currentUser?.uid ?: "unknown"),
                    "senderName" to myName,
                    "text" to text,
                    "timestamp" to Timestamp.now()
                ))
                input.setText("")
            }
        }
    }

    private fun privateThreadId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    private fun toastVoiceMvp() {
        Toast.makeText(this, "Voice button placeholder. Text/location MVP first; add WebRTC foreground service next.", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        usersListener?.remove()
        super.onDestroy()
    }
}
