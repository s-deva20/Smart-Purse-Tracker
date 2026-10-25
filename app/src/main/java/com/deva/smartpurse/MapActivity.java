package com.deva.smartpurse;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapActivity extends AppCompatActivity {


    private static final int LOCATION_PERMISSION_REQUEST = 200;

    private MapView mapView;
    private TextView tvMapStatus;
    private FusedLocationProviderClient locationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);
        tvMapStatus = findViewById(R.id.tvMapStatus);

        Button btnShowLocation = findViewById(R.id.btnShowLocation);

        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(5.0);

        locationClient =
                LocationServices.getFusedLocationProviderClient(this);

        btnShowLocation.setOnClickListener(v -> requestLocation());
    }

    private void requestLocation() {

        boolean fineGranted =
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        boolean coarseGranted =
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST
            );
            return;
        }

        tvMapStatus.setText("Getting phone location...");

        try {
            locationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {

                        if (location == null) {
                            tvMapStatus.setText(
                                    "Location unavailable. Enable location and try again."
                            );
                            return;
                        }

                        showLocation(location);
                    })
                    .addOnFailureListener(this, error ->
                            tvMapStatus.setText(
                                    "Unable to obtain location."
                            ));

        } catch (SecurityException e) {
            tvMapStatus.setText("Location permission is required.");
        }
    }

    private void showLocation(Location location) {

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        GeoPoint point = new GeoPoint(latitude, longitude);

        mapView.getController().setZoom(17.0);
        mapView.getController().animateTo(point);

        mapView.getOverlays().removeIf(
                overlay -> overlay instanceof Marker
        );

        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        marker.setTitle("Phone's last known location");
        marker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
        );

        mapView.getOverlays().add(marker);
        marker.showInfoWindow();

        mapView.invalidate();

        tvMapStatus.setText(
                String.format(
                        java.util.Locale.getDefault(),
                        "Phone location: %.5f, %.5f",
                        latitude,
                        longitude
                )
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == LOCATION_PERMISSION_REQUEST) {

            boolean granted = false;

            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                requestLocation();
            } else {
                tvMapStatus.setText("Location permission denied.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) {
            mapView.onDetach();
        }

        super.onDestroy();
    }


}
