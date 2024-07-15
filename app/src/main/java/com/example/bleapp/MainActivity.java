package com.example.bleapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;

    private TextView statusTextView;
    private RecyclerView recyclerView;
    private Button scanButton;
    private BleDeviceAdapter adapter;

    private BleDevice connectedDevice;
    private BluetoothGattCharacteristic readCharacteristic;

    // Replace with your ESP32 BLE characteristic UUIDs
    private static final UUID SERVICE_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_READ_UUID = UUID.fromString("0000fef4-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_WRITE_UUID = UUID.fromString("0000dead-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        recyclerView = findViewById(R.id.recyclerView);
        scanButton = findViewById(R.id.scanButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BleDeviceAdapter();
        recyclerView.setAdapter(adapter);

        BleManager.getInstance().init(getApplication());

        scanButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startScan();
            }
        });

        adapter.setOnItemClickListener(this::connectToDevice);
    }

    private boolean checkPermissions() {
        if (!BleManager.getInstance().isBlueEnable()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return false;
        }

        return true;
    }

    private void startScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                scanButton.setEnabled(false);
                statusTextView.setText("Scanning...");
                Log.d(TAG, "Scan started");
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                adapter.addDevice(bleDevice);
                Log.d(TAG, "Device found: " + bleDevice.getName());
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                scanButton.setEnabled(true);
                statusTextView.setText("Scan finished.");
                Log.d(TAG, "Scan finished with " + scanResultList.size() + " devices found");
            }
        });
    }

    private void connectToDevice(BleDevice device) {
        BleManager.getInstance().cancelScan();
        BleManager.getInstance().connect(device, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                statusTextView.setText("Connecting...");
                Log.d(TAG, "Connecting to " + device.getName());
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                statusTextView.setText("Connect fail: " + exception.toString());
                Log.e(TAG, "Connection failed: " + exception.getDescription());
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                statusTextView.setText("Connected");
                Log.d(TAG, "Connected to " + bleDevice.getName());

                // Save connected device
                connectedDevice = bleDevice;

                // Discover services and characteristics
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException: " + e.getMessage());
                }
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                statusTextView.setText("Disconnected");
                connectedDevice = null;
                Log.d(TAG, "Disconnected from " + device.getName());
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        readCharacteristic = service.getCharacteristic(CHARACTERISTIC_READ_UUID);
                        enableNotification(service.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID));
                        Log.d(TAG, "Service and characteristics found");
                    } else {
                        Log.e(TAG, "Service not found");
                    }
                } else {
                    Log.e(TAG, "Service discovery failed with status: " + status);
                }
            }
        });
    }

    private void enableNotification(BluetoothGattCharacteristic characteristic) {
        BleManager.getInstance().notify(
                connectedDevice,
                characteristic.getService().getUuid().toString(),
                characteristic.getUuid().toString(),
                new BleNotifyCallback() {
                    @Override
                    public void onNotifySuccess() {
                        Log.d(TAG, "Notification setup successful for characteristic: " + characteristic.getUuid());
                    }

                    @Override
                    public void onNotifyFailure(BleException exception) {
                        Log.e(TAG, "Failed to set up notification for characteristic " + characteristic.getUuid() + ": " + exception.getDescription());
                        // Handle failure scenario, e.g., retry or display error to the user
                    }

                    @Override
                    public void onCharacteristicChanged(byte[] data) {
                        if (data != null && data.length > 0) {
                            String hexString = bytesToHex(data);
                            Log.d(TAG, "Raw data received: " + Arrays.toString(data));
                            Log.d(TAG, "Hex data received: " + hexString);

                            // Update UI with raw hex string (optional)
                            runOnUiThread(() -> {
                                ((TextView) findViewById(R.id.label1)).setText(hexString);
                            });

                            String jsonData = hexToString(hexString);
                            updateLabel1(jsonData);
                        } else {
                            Log.d(TAG, "Received empty data for characteristic " + characteristic.getUuid());
                        }
                    }
                });
    }

    private void updateLabel1(String jsonData) {
        try {
            // Parse JSON data
            JSONObject jsonObject = new JSONObject(jsonData);
            double temperature = jsonObject.getDouble("temperature");
            double humidity = jsonObject.getDouble("humidity");
            int no2 = jsonObject.getInt("no2");
            int c2h5oh = jsonObject.getInt("c2h5oh");
            int voc = jsonObject.getInt("voc");
            int co = jsonObject.getInt("co");
            int pm1 = jsonObject.getInt("pm1");
            int pm2_5 = jsonObject.getInt("pm2_5");
            int pm10 = jsonObject.getInt("pm10");

            // Update UI with parsed values
            runOnUiThread(() -> {
                ((TextView) findViewById(R.id.label1)).setText("Temperature: " + temperature);
                ((TextView) findViewById(R.id.temp_value)).setText(String.valueOf(temperature));
                ((TextView) findViewById(R.id.hum_value)).setText(String.valueOf(humidity));
                ((TextView) findViewById(R.id.no2_value)).setText(String.valueOf(no2));
                ((TextView) findViewById(R.id.c2h5oh_value)).setText(String.valueOf(c2h5oh));
                ((TextView) findViewById(R.id.voc_value)).setText(String.valueOf(voc));
                ((TextView) findViewById(R.id.co_value)).setText(String.valueOf(co));
                ((TextView) findViewById(R.id.pm1_value)).setText(String.valueOf(pm1));
                ((TextView) findViewById(R.id.pm2_value)).setText(String.valueOf(pm2_5));
                ((TextView) findViewById(R.id.pm10_value)).setText(String.valueOf(pm10));
            });
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse JSON data: " + e.getMessage());
        }
    }

    private String hexToString(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length() - 1; i += 2) {
            String output = hex.substring(i, (i + 2));
            int decimal = Integer.parseInt(output, 16);
            sb.append((char) decimal);
        }
        return sb.toString();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                Toast.makeText(this, "Location permission is required to scan for BLE devices", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BleManager.getInstance().disconnectAllDevice();
        BleManager.getInstance().destroy();
    }
}
