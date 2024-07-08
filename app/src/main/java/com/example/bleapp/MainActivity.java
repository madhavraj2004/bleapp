package com.example.bleapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;
    private static final UUID SERVICE_UUID = UUID.fromString("00000180-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000FEF4-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_UUID = UUID.fromString("0000DEAD-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private List<BluetoothDevice> deviceList;
    private ArrayAdapter<BluetoothDevice> deviceListAdapter;
    private TextView statusTextView;
    private BluetoothGatt bluetoothGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnScan = findViewById(R.id.btn_scan);
        ListView listViewDevices = findViewById(R.id.listViewDevices);
        statusTextView = findViewById(R.id.statusTextView);

        deviceList = new ArrayList<>();
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        listViewDevices.setAdapter(deviceListAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        btnScan.setOnClickListener(v -> startScan());

        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = deviceList.get(position);
            connectToDevice(device);
        });

        checkPermissions();
    }

    private void checkPermissions() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        }
    }

    private void startScan() {
        statusTextView.setText("Status: Scanning");
        deviceList.clear();
        deviceListAdapter.notifyDataSetChanged();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                if (!deviceList.contains(device)) {
                    deviceList.add(device);
                    deviceListAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                statusTextView.setText("Status: Scan Failed");
            }
        };

        bluetoothLeScanner.startScan(scanCallback);
    }

    private void stopScan() {
        bluetoothLeScanner.stopScan(scanCallback);
    }

    private void connectToDevice(BluetoothDevice device) {
        stopScan();
        statusTextView.setText("Status: Connecting to " + device.getName());

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread(() -> statusTextView.setText("Status: Connected"));
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread(() -> statusTextView.setText("Status: Disconnected"));
                    // Handle reconnection or other logic here
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                        if (characteristic != null) {
                            gatt.setCharacteristicNotification(characteristic, true);

                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID);
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }
                        }
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                byte[] data = characteristic.getValue();
                runOnUiThread(() -> updateTextView(data));
            }
        });
    }

    private void updateTextView(byte[] data) {
        String dataString = new String(data);
        statusTextView.setText("Data: " + dataString);

        // Update specific TextViews with parsed data
        // Example:
        // tempValueTextView.setText(parsedTemperature);
        // humValueTextView.setText(parsedHumidity);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission is required for BLE scanning", Toast.LENGTH_SHORT).show();
            } else {
                startScan();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Bluetooth must be enabled to use this app", Toast.LENGTH_SHORT).show();
            } else {
                startScan();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
