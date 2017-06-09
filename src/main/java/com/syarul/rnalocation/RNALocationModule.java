package com.syarul.rnalocation;

import android.location.LocationListener;
import android.location.Location;
import android.location.LocationManager;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;
import android.os.Bundle;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

// See https://developer.android.com/reference/android/location/LocationManager.html
// See https://stackoverflow.com/questions/28458831/android-getlastknownlocationlocationmanager-gps-provider-returns-null

public class RNALocationModule extends ReactContextBaseJavaModule implements LocationListener {

    public static final String REACT_CLASS = "RNALocation"; // React Class Name as called from JS
    public static final String TAG = RNALocationModule.class.getSimpleName(); // Log tag

    boolean isGPSEnabled = false;
    boolean isNetworkEnabled = false;
    boolean canGetLocation = false;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; // in meters
    private static final long MIN_TIME_BW_UPDATES = 0; // in milliseconds

    protected LocationManager locationManager;
    ReactApplicationContext mReactContext;

    // Constructor Method as called in Package
    public RNALocationModule(ReactApplicationContext reactContext) {
        super(reactContext);
        // Save Context for later use
        mReactContext = reactContext;
        Log.i(TAG, "RNALocationModule created.");
    }


    @ReactMethod
    public void startLocationUpdates() {
        try {
            locationManager = (LocationManager)mReactContext.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                Log.e(TAG, "Location service not enabled (no locationManager available).");
                return;
            }

            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!isGPSEnabled && !isNetworkEnabled) {
                Log.e(TAG, "Location service not enabled (no GPS or Network provider available).");
                return;
            }

            if (isGPSEnabled) {
                this.canGetLocation = true;
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                Log.i(TAG, "GPS provider enabled");
                sendUpdateLocationEvent(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
            }

            if (isNetworkEnabled && !this.canGetLocation) {
                this.canGetLocation = true;
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                Log.i(TAG, "Network provider enabled");
                sendUpdateLocationEvent(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
            }
        } catch (Exception e) {
            // e.printStackTrace();
            Log.e(TAG, "Impossible to connect to LocationManager", e);
        }
        return;
    }

    @ReactMethod
    public void stopLocationUpdates() {
        if (locationManager == null) {
            Log.e(TAG, "no location updates to stop (locationManager not available).");
            return;
        }
        locationManager.removeUpdates(this);
        locationManager = null;
        isGPSEnabled = false;
        isNetworkEnabled = false;
        canGetLocation = false;
    }
    
    @ReactMethod
    public boolean canGetLocation() {
        return canGetLocation;
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    private void sendUpdateLocationEvent(Location location) {
        if (location == null) {
            Log.e(TAG, "location not available.");
            return;
        }
        try {
            double lon = location.getLongitude();
            double lat = location.getLatitude();
            // location.getAltitude() if needed
            Log.i(TAG, "Got new location from " + location.getProvider() + ". Lng: " + lon + " Lat: " + lat);

            WritableMap params = Arguments.createMap();
            params.putDouble("Longitude", lon);
            params.putDouble("Latitude", lat);
            if (location.hasAltitude()) {
                params.putDouble("Altitude", getAltitude());
            }
            params.putLong("Time", location.getTime());
            params.putString("Provider", location.getProvider());
            params.putString("Description", location.toString());
            if (location.hasSpeed()) {
                params.putFloat("Speed", getSpeed());
            }
            if (location.hasAccuracy()) {
                params.putFloat("Accuracy", getAccuracy());
            }
            if (location.hasBearing()) {
                params.putDouble("Bearing", getBearing());
            }

            sendEventToJSListener(mReactContext, "updateLocation", params);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Location services disconnected after exception.", e);
        }
    }

    public void onLocationChanged(Location location) {
        sendUpdateLocationEvent(location);
    }

    public void onProviderDisabled(String provider) {
        Log.i(TAG, "disable provider " + provider);
    }

    public void onProviderEnabled(String provider) {
        Log.i(TAG, "enabled provider " + provider);
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.i(TAG, "provider: " + provider + " status: " + status + " extras: " + extras.toString());
    }

    private void sendEventToJSListener(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
        } else {
            Log.i(TAG, "Waiting for CatalystInstance...");
        }
    }
}
