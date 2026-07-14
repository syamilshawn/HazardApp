package com.example.hazardapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReportHazardActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String BASE_URL = "http://10.0.2.2/hazardapp/api/";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 200;

    private EditText editName, editDescription;
    private Spinner spinnerCategory;
    private TextView textLocationStatus;
    private Button btnSubmit;

    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap miniMap;
    private Marker adjustableMarker;

    private double currentLat = 0;
    private double currentLng = 0;
    private boolean locationReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_hazard);

        editName = findViewById(R.id.editName);
        editDescription = findViewById(R.id.editDescription);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        textLocationStatus = findViewById(R.id.textLocationStatus);
        btnSubmit = findViewById(R.id.btnSubmit);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.hazard_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.miniMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnSubmit.setOnClickListener(v -> submitReport());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        miniMap = googleMap;
        getCurrentLocationAndPlacePin();

        miniMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {}

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {}

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                currentLat = marker.getPosition().latitude;
                currentLng = marker.getPosition().longitude;
                textLocationStatus.setText("Adjusted location: " + currentLat + ", " + currentLng);
            }
        });
    }

    private void getCurrentLocationAndPlacePin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        miniMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
                locationReady = true;
                placeDraggablePin(currentLat, currentLng);
                textLocationStatus.setText("Location captured: " + currentLat + ", " + currentLng);
            } else {
                textLocationStatus.setText("Could not get GPS location. Drag the pin manually.");
                placeDraggablePin(6.4214, 100.1986);
                locationReady = true;
            }
        });
    }

    private void placeDraggablePin(double lat, double lng) {
        LatLng position = new LatLng(lat, lng);
        adjustableMarker = miniMap.addMarker(new MarkerOptions()
                .position(position)
                .draggable(true)
                .title("Drag to adjust"));
        miniMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f));
    }

    // ---------- error handling: permission denied ----------
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationAndPlacePin();
            } else {
                Toast.makeText(this, "Location permission is required to report a hazard.", Toast.LENGTH_LONG).show();
                textLocationStatus.setText("Location permission denied. Drag pin manually if map loads.");
            }
        }
    }

    // ---------- error handling: no internet ----------
    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void submitReport() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_LONG).show();
            return;
        }

        String name = editName.getText().toString().trim();
        String description = editDescription.getText().toString().trim();
        String category = spinnerCategory.getSelectedItem().toString();

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (description.isEmpty()) {
            Toast.makeText(this, "Please describe the hazard", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!locationReady) {
            Toast.makeText(this, "Still getting your location, please wait", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("user_name", name);
            json.put("user_agent", System.getProperty("http.agent"));
            json.put("device_info", Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");
            json.put("latitude", currentLat);
            json.put("longitude", currentLng);
            json.put("hazard_category", category);
            json.put("hazard_description", description);

            sendReport(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendReport(String jsonString) {
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(jsonString, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(BASE_URL + "report_hazard.php")
                .post(body)
                .build();

        btnSubmit.setEnabled(false);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    Toast.makeText(ReportHazardActivity.this, "Hazard reported successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(ReportHazardActivity.this, "Failed to submit: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}