package com.example.hazardapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    // Emulator special IP that points back to your PC's localhost
    private static final String BASE_URL = "http://10.0.2.2/hazardapp/api/";

    // ---------- auto-refresh setup ----------
    private static final long REFRESH_INTERVAL_MS = 15000; // refresh every 15 seconds
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Hazard Reporter");
        }

        findViewById(R.id.fabReportHazard).setOnClickListener(v -> {
            startActivity(new android.content.Intent(MainActivity.this, ReportHazardActivity.class));
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("Refresh");
        menu.add("About");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if ("Refresh".equals(item.getTitle())) {
            if (mMap != null) {
                mMap.clear();
                fetchHazards();
            }
            Toast.makeText(this, "Hazards refreshed", Toast.LENGTH_SHORT).show();
            return true;
        }
        if ("About".equals(item.getTitle())) {
            startActivity(new android.content.Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Custom styled info window (title + description + reporter name)
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null; // let Google Maps draw the rounded card frame + pointer + close X
            }

            @Override
            public View getInfoContents(Marker marker) {
                View view = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.info_window_hazard, null);

                TextView title = view.findViewById(R.id.infoTitle);
                TextView description = view.findViewById(R.id.infoDescription);
                TextView reporter = view.findViewById(R.id.infoReporter);

                title.setText(marker.getTitle());

                String snippet = marker.getSnippet();
                if (snippet != null && snippet.contains("\n")) {
                    String[] parts = snippet.split("\n", 2);
                    description.setText(parts[0]);
                    reporter.setText(parts[1]);
                } else {
                    description.setText(snippet);
                    reporter.setText("");
                }

                return view;
            }
        });

        checkLocationPermissionAndShow();
        fetchHazards();
        startAutoRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMap != null) {
            mMap.clear();
            fetchHazards();
            startAutoRefresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    // ---------- auto-refresh logic ----------
    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (mMap != null) {
                    mMap.clear();
                    fetchHazards();
                }
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    private void stopAutoRefresh() {
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
    // ------------------------------------------------------------------

    private void checkLocationPermissionAndShow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        showCurrentLocation();
    }

    private void showCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
            }
        });
    }

    // ---------- fetch hazard markers from the PHP API ----------
    private void fetchHazards() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(BASE_URL + "get_hazards.php")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null) return;
                String body = response.body().string();
                try {
                    JSONArray hazards = new JSONArray(body);
                    runOnUiThread(() -> {
                        for (int i = 0; i < hazards.length(); i++) {
                            try {
                                JSONObject h = hazards.getJSONObject(i);
                                double lat = h.getDouble("latitude");
                                double lng = h.getDouble("longitude");
                                String category = h.getString("hazard_category");
                                String desc = h.getString("hazard_description");
                                String reporterName = h.getString("user_name");

                                LatLng position = new LatLng(lat, lng);
                                mMap.addMarker(new MarkerOptions()
                                        .position(position)
                                        .title(category)
                                        .snippet(desc + "\nReported by: " + reporterName)
                                        .icon(getIconForCategory(category)));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
        });
    }

    private BitmapDescriptor getIconForCategory(String category) {
        switch (category) {
            case "Road Hazard":
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
            case "Environmental Hazard":
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
            case "Building Hazard":
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
            default:
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);
        }
    }
    // ------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCurrentLocation();
            } else {
                Toast.makeText(this,
                        "Location permission denied. You can still view hazards but won't see your position.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}