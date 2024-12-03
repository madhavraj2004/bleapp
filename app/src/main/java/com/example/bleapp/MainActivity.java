package com.example.bleapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.polidea.rxandroidble3.RxBleClient;
import com.polidea.rxandroidble3.RxBleDevice;
import com.polidea.rxandroidble3.RxBleConnection;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String CHARACTERISTIC_UUID = "0000fef4-0000-1000-8000-00805f9b34fb";
    private static final String TARGET_DEVICE_MAC = "7C:DF:A1:EE:D4:96";
    private TextView label1;
    private TextView tempValue, humValue, co2Value, pressValue, vocValue, coValue, pm1Value, pm2Value, pm10Value;

    private TextView statusTextView, parameterView, valueView;
    private RxBleClient rxBleClient;
    private RxBleDevice selectedDevice;
    private RxBleConnection connection;
    private Disposable connectionDisposable;
    private CompositeDisposable disposables = new CompositeDisposable();

    private Handler handler = new Handler();
    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (connection != null) {
                readCharacteristic();
                handler.postDelayed(this, 5000);
            }
        }
    };

    private File csvFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        label1 = findViewById(R.id.label1);
        tempValue = findViewById(R.id.temp_value);
        humValue = findViewById(R.id.hum_value);
        pressValue = findViewById(R.id.press_value);
        vocValue = findViewById(R.id.voc_value);
        pm1Value = findViewById(R.id.pm1_value);
        pm2Value = findViewById(R.id.pm2_value);
        pm10Value = findViewById(R.id.pm10_value);
        coValue = findViewById(R.id.co_value);
        co2Value = findViewById(R.id.co2_value);


        valueView = findViewById(R.id.valueView);

        Button scanButton = findViewById(R.id.scanButton);
        Button connectButton = findViewById(R.id.connectButton);
        Button exportButton = findViewById(R.id.exportButton);

        scanButton.setOnClickListener(v -> startScan());
        connectButton.setOnClickListener(v -> connectToDevice());
        exportButton.setOnClickListener(v -> exportDataToCSV());

        checkPermissions();
        rxBleClient = RxBleClient.create(this);
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    private void startScan() {
        statusTextView.setText("Scanning...");
        Disposable scanDisposable = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(scanResult -> {
                    if (scanResult.getBleDevice().getMacAddress().equals(TARGET_DEVICE_MAC)) {
                        selectedDevice = scanResult.getBleDevice();
                        statusTextView.setText("Target device found: " + selectedDevice.getName());
                    }
                }, throwable -> {
                    statusTextView.setText("Scan failed.");
                    Log.e("BLE", "Scan failed: " + throwable.toString());
                });
        disposables.add(scanDisposable);
    }

    private void connectToDevice() {
        if (selectedDevice == null) {
            statusTextView.setText("No device selected.");
            return;
        }

        connectionDisposable = selectedDevice.establishConnection(false)
                .doOnDispose(() -> Log.d("BLE", "Disconnected"))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rxBleConnection -> {
                    connection = rxBleConnection;
                    statusTextView.setText("Connected.");
                    handler.postDelayed(updateTask, 5000);
                }, throwable -> {
                    statusTextView.setText("Connection failed.");
                    Log.e("BLE", "Connection failed: " + throwable.toString());
                });
        disposables.add(connectionDisposable);
    }

    private void readCharacteristic() {
        if (connection == null) {
            Log.e("BLE", "Connection is null, cannot read characteristic.");
            return;
        }

        Log.d("BLE", "Attempting to read characteristic with UUID: " + CHARACTERISTIC_UUID);

        Disposable readDisposable = connection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(characteristicValue -> {
                    Log.d("BLE", "Characteristic read successfully. Value length: " + (characteristicValue != null ? characteristicValue.length : 0));

                    if (characteristicValue == null || characteristicValue.length == 0) {
                        Log.w("BLE", "Received characteristic value is null or empty.");
                        statusTextView.setText("Error: Empty data received.");
                        return;
                    }

                    // Convert the characteristic value to a JSON string
                    String jsonString = new String(characteristicValue);
                    Log.d("BLE", "Raw JSON string: " + jsonString);

                    updateUIWithData(jsonString);

                    try {
                        JSONObject jsonObject = new JSONObject(jsonString);
                        Log.d("BLE", "JSON object parsed successfully.");

                        int deviceId = jsonObject.getInt("device_id");
                        Log.d("BLE", "Extracted device_id: " + deviceId);

                        String MQTT_TOPIC = "data/" + deviceId;
                        Log.d("BLE", "MQTT_TOPIC: " + MQTT_TOPIC);

                        //publishMessage(MQTT_TOPIC, jsonString);
                        Log.d("BLE", "Published message to topic: " + MQTT_TOPIC + ", message: " + jsonString);

                    } catch (JSONException e) {
                        Log.e("JSON_ERROR", "Error parsing JSON string or extracting deviceId: " + e.getMessage());
                        statusTextView.setText("Error parsing data.");
                    }
                }, throwable -> {
                    Log.e("BLE", "Read failed: " + throwable.toString());
                    statusTextView.setText("Read failed.");
                });

        disposables.add(readDisposable);
    }


    private void updateUIWithData(String jsonString) {
        try {

            label1.setText(jsonString);
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONObject data = jsonObject.getJSONObject("data");

            tempValue.setText(String.format("%.2f", data.getDouble("temperature")));
            humValue.setText(String.format("%.2f", data.getDouble("humidity")));
            pressValue.setText(String.format("%.2f", data.getDouble("pressure")));
            pm1Value.setText(String.valueOf(data.getInt("pm1")));
            pm2Value.setText(String.valueOf(data.getInt("pm2_5")));
            pm10Value.setText(String.valueOf(data.getInt("pm10")));
            coValue.setText(String.valueOf(data.getInt("co")));
            vocValue.setText(String.valueOf(data.getInt("voc")));
            co2Value.setText(String.valueOf(data.getInt("co2")));



            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());


            valueView.setText("Timestamp: " + timestamp);

            saveDataToCSV(jsonObject, timestamp);
        } catch (JSONException e) {
            statusTextView.setText("Invalid data received.");
            Log.e("BLE", "JSON Parsing error: " + e.toString());
        }
    }

    private void saveDataToCSV(JSONObject jsonObject, String timestamp) {
        try {
            if (csvFile == null) {
                csvFile = new File(getExternalFilesDir(null), "sensor_data.csv");
            }

            boolean isNewFile = !csvFile.exists() || csvFile.length() == 0;

            try (FileWriter writer = new FileWriter(csvFile, true)) {
                if (isNewFile) {
                    // Write headers for a new file including all metrics from data, calculated, predicted, and status
                    writer.append("Timestamp,Temperature,Humidity,Pressure,PM1,PM2.5,PM10,CO,VOC,CO2,aqi_dust,aqi_co,aqi_voc,aqi_co2,aqi_dust_predicted,aqi_co_predicted,dust_status,co_status\n");
                }

                JSONObject data = jsonObject.getJSONObject("data");
                JSONObject calculated = jsonObject.getJSONObject("calculated");
                JSONObject predicted = jsonObject.getJSONObject("predicted");
                JSONObject status = jsonObject.getJSONObject("status");

                // Build the row with values from all parts
                String row = timestamp + "," +
                        data.getDouble("temperature") + "," +
                        data.getDouble("humidity") + "," +
                        data.getDouble("pressure") + "," +
                        data.getInt("pm1") + "," +
                        data.getInt("pm2_5") + "," +
                        data.getInt("pm10") + "," +
                        data.getInt("co") + "," +
                        data.getInt("voc") + "," +
                        data.getInt("co2") + "," +
                        calculated.getInt("aqi_dust") + "," +
                        calculated.getInt("aqi_co") + "," +
                        calculated.getInt("aqi_voc") + "," +
                        calculated.getInt("aqi_co2") + "," +
                        predicted.getInt("aqi_dust") + "," +
                        predicted.getInt("aqi_co") + "," +
                        status.getInt("dust") + "," +
                        status.getInt("co") + "\n";

                writer.append(row);
            }
        } catch (IOException | JSONException e) {
            Log.e("CSV", "Error writing to CSV: " + e.toString());
        }
    }



    private void exportDataToCSV() {
        if (csvFile != null && csvFile.exists()) {
            Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", csvFile);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            startActivity(Intent.createChooser(intent, "Share CSV File"));
        } else {
            Toast.makeText(this, "CSV file not available.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTask);
        disposables.clear();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan();
        } else {
            statusTextView.setText("Permissions denied.");
        }
    }
}
