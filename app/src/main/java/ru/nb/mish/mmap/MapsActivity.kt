package ru.nb.mish.mmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.annotation.DrawableRes
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.*
import com.google.maps.model.DirectionsResult
import kotlinx.android.synthetic.main.activity_maps.*
import com.google.maps.model.TravelMode


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var tmpPoint: LatLng? = null
    private var fromMarker: Marker? = null
    private var toMarker: Marker? = null
    private var polyLine: Polyline? = null
    private var travelMode = TravelMode.DRIVING
    private var tmpMarker: Marker? = null
    private var fromCurrentLocation = true
    private val REQUEST_CODE_LOCATION_PERMISSION = 1
    private var currentLocation: Location? = null
    private var myLatLngBounds: LatLngBounds? = null
    private var myLocationMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult?) {
            super.onLocationResult(p0)
            p0?.lastLocation?.let {
                if (mMap != null) {
                    myLocationMarker?.remove()
                    myLocationMarker = mMap.addMarker(MarkerOptions().flat(true).icon(vectorToBitmap(R.drawable.my_location))
                            .anchor(0.5f, 0.5f).position(LatLng(it.latitude, it.longitude)))
                }

                currentLocation = it
                if (fromCurrentLocation && toMarker != null) {
                    foundPoints(com.google.maps.model.LatLng(it.latitude, it.longitude),
                            com.google.maps.model.LatLng(toMarker!!.position.latitude, toMarker!!.position.longitude))
                }
            }
        }
    }

    private fun vectorToBitmap(@DrawableRes id: Int): BitmapDescriptor {
        val vectorDrawable = ResourcesCompat.getDrawable(getResources(), id, null)
        val bitmap = Bitmap.createBitmap(vectorDrawable!!.intrinsicWidth,
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        ibMyLocation.setOnClickListener {
            myLocationMarker?.let {
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(it.position, 18f)))
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        bFrom.setOnClickListener {

            fromCurrentLocation = false
            fromMarker?.remove()
            fromMarker = mMap.addMarker(MarkerOptions().position(tmpPoint!!).title(getString(R.string.from)))
            llButtons.visibility = View.GONE
            if (toMarker != null)
                foundPoints(com.google.maps.model.LatLng(fromMarker!!.position.latitude, fromMarker!!.position.longitude),
                        com.google.maps.model.LatLng(toMarker!!.position.latitude, toMarker!!.position.longitude))
        }

        bTo.setOnClickListener {
            toMarker?.remove()
            toMarker = mMap.addMarker(MarkerOptions().position(tmpPoint!!).title(getString(R.string.to)))
            llButtons.visibility = View.GONE

            if (fromCurrentLocation && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (currentLocation != null) {
                    foundPoints(com.google.maps.model.LatLng(currentLocation!!.latitude, currentLocation!!.longitude),
                            com.google.maps.model.LatLng(toMarker!!.position.latitude, toMarker!!.position.longitude))
                } else Toast.makeText(this, R.string.wait_route, Toast.LENGTH_LONG).show()

            } else {
                if (fromMarker != null)
                    foundPoints(com.google.maps.model.LatLng(fromMarker!!.position.latitude, fromMarker!!.position.longitude),
                            com.google.maps.model.LatLng(toMarker!!.position.latitude, toMarker!!.position.longitude))
            }
        }

        ivCloseRoute.setOnClickListener {
            AlertDialog.Builder(this)
                    .setMessage(R.string.cancel_route)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, { _, _ ->
                        toMarker?.remove()
                        fromMarker?.remove()
                        tmpMarker?.remove()
                        fromMarker = null
                        toMarker = null
                        llButtons.visibility = View.GONE
                        polyLine?.remove()
                        llDistanceAndClose.visibility = View.GONE
                        llRouteSelection.visibility = View.GONE
                        fromCurrentLocation = true
                    })
                    .setNegativeButton(android.R.string.cancel, { _, _ ->
                    }).show()
        }
        updateColorButton()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdate()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdate() {
        val locationRequest = LocationRequest()
        locationRequest.interval = 30 * 1000
        locationRequest.fastestInterval = 30 * 1000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }

    private fun stopLocationUpdate() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ibMyLocation.visibility = View.VISIBLE
            startLocationUpdate()
        } else
            Toast.makeText(this, R.string.no_gps_permission, Toast.LENGTH_LONG).show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setPadding(0, 0, 0, 170)
        mMap.uiSettings.isZoomGesturesEnabled = true

        if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ibMyLocation.visibility = View.VISIBLE

            startLocationUpdate()

        } else
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_CODE_LOCATION_PERMISSION)

        mMap.setOnMapLongClickListener {
            llButtons.visibility = View.VISIBLE

            tmpPoint = it
            tmpMarker?.remove()
            tmpMarker = mMap.addMarker(MarkerOptions().position(tmpPoint!!).title("")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))

            llDistanceAndClose.visibility = View.GONE
        }

        mMap.setOnMapClickListener {
            llButtons.visibility = View.GONE
        }
    }

    private fun foundPoints(fromPoint: com.google.maps.model.LatLng?, toPoint: com.google.maps.model.LatLng?) {

        polyLine?.remove()
        if (fromPoint != null && toPoint != null) {
            DirectionsApi.newRequest(
                    GeoApiContext.Builder()
                            .apiKey(getString(R.string.google_maps_key))
                            .build()
            ).origin(fromPoint)
                    .destination(toPoint)
                    .mode(travelMode)
                    .language("ru")
                    .setCallback(object : PendingResult.Callback<DirectionsResult> {

                        override fun onFailure(e: Throwable?) {
                            e?.printStackTrace()

                            runOnUiThread {
                                Toast.makeText(this@MapsActivity,
                                        R.string.fail, Toast.LENGTH_LONG).show()
                            }
                        }

                        override fun onResult(result: DirectionsResult?) {

                            runOnUiThread {
                                if (result != null && result.routes.isNotEmpty()) {
                                    llDistanceAndClose.visibility = View.VISIBLE
                                    tvDistanceTime.setText(getString(R.string.distance_time, result.routes[0].legs[0].duration.humanReadable,
                                            result.routes[0].legs[0].distance.humanReadable))
                                    llRouteSelection.visibility = View.VISIBLE

                                    val bounds = LatLngBounds.Builder()

                                    val lineOptions = PolylineOptions().width(12f).color(ContextCompat.getColor(this@MapsActivity, R.color.lineColor))
                                    result.routes[0].overviewPolyline.decodePath().forEach {
                                        val mLatLng = LatLng(it.lat, it.lng)
                                        lineOptions.add(mLatLng)
                                        bounds.include(mLatLng)
                                    }

                                    myLatLngBounds = bounds.build()
                                    val cuf = CameraUpdateFactory.newLatLngBounds(myLatLngBounds, 150)
                                    mMap.animateCamera(cuf)

                                    polyLine = mMap.addPolyline(lineOptions)

                                } else {
                                    Toast.makeText(this@MapsActivity,
                                            R.string.no_marshrut, Toast.LENGTH_LONG).show()
                                    llDistanceAndClose.visibility = View.GONE
                                }
                            }

                        }
                    })
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        myLatLngBounds?.let {
            Handler().postDelayed({
                val cuf = CameraUpdateFactory.newLatLngBounds(it, 150)
                mMap.animateCamera(cuf)
            }, 300)
        }
    }

    fun onClickRoute(view: View) {
        when (view.id) {
            R.id.ivDriving -> travelMode = TravelMode.DRIVING
            R.id.ivWalking -> travelMode = TravelMode.WALKING
        }

        if (fromCurrentLocation && ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (currentLocation != null) {
                foundPoints(com.google.maps.model.LatLng(currentLocation!!.latitude, currentLocation!!.longitude),
                        com.google.maps.model.LatLng(toMarker!!.position.latitude, toMarker!!.position.longitude))
            } else Toast.makeText(this, R.string.wait_route,
                    Toast.LENGTH_LONG).show()

        } else {
            if
                    (fromMarker != null)
                foundPoints(com.google.maps.model.LatLng(fromMarker!!.position.latitude, fromMarker!!.position.longitude),
                        com.google.maps.model.LatLng(toMarker!!.position.latitude, toMarker!!.position.longitude))
        }

        updateColorButton()
    }

    fun updateColorButton() {
        DrawableCompat.setTint(ivDriving.getDrawable(), ContextCompat.getColor(this, R.color.buttonOff))
        DrawableCompat.setTint(ivWalking.getDrawable(), ContextCompat.getColor(this, R.color.buttonOff))

        when (travelMode) {
            TravelMode.DRIVING -> DrawableCompat.setTint(ivDriving.getDrawable(), ContextCompat.getColor(this, R.color.buttonOn))
            TravelMode.WALKING -> DrawableCompat.setTint(ivWalking.getDrawable(), ContextCompat.getColor(this, R.color.buttonOn))
        }
    }
}