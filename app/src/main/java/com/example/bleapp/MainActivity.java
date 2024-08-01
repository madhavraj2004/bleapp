package com.example.bleapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.polidea.rxandroidble3.RxBleClient;
import com.polidea.rxandroidble3.RxBleDevice;
import com.polidea.rxandroidble3.RxBleConnection;
import com.polidea.rxandroidble3.scan.ScanResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    private static final String SERVICE_UUID = "00001800-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_UUID = "0000fef4-0000-1000-8000-00805f9b34fb";
    private static final String TARGET_DEVICE_MAC = "7C:DF:A1:EE:D4:96";

    private TextView statusTextView, label1;
    private TextView tempValue, humValue, no2Value, c2h5ohValue, vocValue, coValue, pm1Value, pm2Value, pm10Value;

    private RxBleClient rxBleClient;
    private RxBleDevice selectedDevice;
    private RxBleConnection connection;
    private Disposable connectionDisposable;
    private Disposable scanDisposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        label1 = findViewById(R.id.label1);
        tempValue = findViewById(R.id.temp_value);
        humValue = findViewById(R.id.hum_value);
        no2Value = findViewById(R.id.no2_value);
        c2h5ohValue = findViewById(R.id.c2h5oh_value);
        vocValue = findViewById(R.id.voc_value);
        coValue = findViewById(R.id.co_value);
        pm1Value = findViewById(R.id.pm1_value);
        pm2Value = findViewById(R.id.pm2_value);
        pm10Value = findViewById(R.id.pm10_value);

        Button scanButton = findViewById(R.id.scanButton);
        Button connectButton = findViewById(R.id.connectButton);
        Button readButton = findViewById(R.id.readButton);

        scanButton.setOnClickListener(v -> startScan());
        connectButton.setOnClickListener(v -> connectToDevice());
        readButton.setOnClickListener(v -> readCharacteristic());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }

        rxBleClient = RxBleClient.create(this);
    }

    private void startScan() {
        statusTextView.setText("Scanning...");
        scanDisposable = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(scanResult -> {
                    if (scanResult.getBleDevice().getMacAddress().equals(TARGET_DEVICE_MAC)) {
                        selectedDevice = scanResult.getBleDevice();
                        statusTextView.setText("Target device found: " + selectedDevice.getName() + " - " + selectedDevice.getMacAddress());
                        Log.d("BLE", "Target device found: " + selectedDevice.getName() + " - " + selectedDevice.getMacAddress());
                        if (scanDisposable != null && !scanDisposable.isDisposed()) {
                            scanDisposable.dispose(); // Stop scanning when the target device is found
                        }
                    }
                }, throwable -> {
                    statusTextView.setText("Scan failed.");
                    Log.e("BLE", "Scan failed: " + throwable.toString());
                });
    }

    private void connectToDevice() {
        if (selectedDevice == null) {
            statusTextView.setText("No device selected.");
            Log.d("BLE", "No device selected.");
            return;
        }

        connectionDisposable = selectedDevice.establishConnection(false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rxBleConnection -> {
                    connection = rxBleConnection;
                    statusTextView.setText("Connected.");
                    Log.d("BLE", "Connected to device.");
                }, throwable -> {
                    statusTextView.setText("Connection failed.");
                    Log.e("BLE", "Connection failed: " + throwable.toString());
                });
    }

    private void readCharacteristic() {
        if (connection == null) {
            statusTextView.setText("Connection is null.");
            Log.e("BLE", "Connection is null.");
            return;
        }

        connection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bytes -> {
                    String jsonString = new String(bytes);
                    Log.d("BLE", "Data received: " + jsonString);
                    updateUIWithData(jsonString);
                }, throwable -> {
                    statusTextView.setText("Failed to read characteristic.");
                    Log.e("BLE", "Failed to read characteristic: " + throwable.toString());
                });
    }

    private void updateUIWithData(String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            double temperature = jsonObject.getDouble("temperature");
            double humidity = jsonObject.getDouble("humidity");
            int no2 = jsonObject.getInt("no2");
            int c2h5oh = jsonObject.getInt("c2h5oh");
            int voc = jsonObject.getInt("voc");
            int co = jsonObject.getInt("co");
            int pm1 = jsonObject.getInt("pm1");
            int pm2_5 = jsonObject.getInt("pm2_5");
            int pm10 = jsonObject.getInt("pm10");

            label1.setText(jsonString);

            tempValue.setText(String.valueOf(temperature));
            humValue.setText(String.valueOf(humidity));
            no2Value.setText(String.valueOf(no2));
            c2h5ohValue.setText(String.valueOf(c2h5oh));
            vocValue.setText(String.valueOf(voc));
            coValue.setText(String.valueOf(co));
            pm1Value.setText(String.valueOf(pm1));
            pm2Value.setText(String.valueOf(pm2_5));
            pm10Value.setText(String.valueOf(pm10));
        } catch (JSONException e) {
            e.printStackTrace();
            statusTextView.setText("JSON parsing error.");
            Log.e("BLE", "JSON parsing error: " + e.toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectionDisposable != null && !connectionDisposable.isDisposed()) {
            connectionDisposable.dispose();
        }
        if (scanDisposable != null && !scanDisposable.isDisposed()) {
            scanDisposable.dispose();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusTextView.setText("Permissions granted.");
                Log.d("BLE", "Permissions granted.");
            } else {
                statusTextView.setText("Permissions denied.");
                Log.d("BLE", "Permissions denied.");
            }
        }
    }
}
