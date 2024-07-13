package com.example.bleapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;

    private TextView statusTextView;
    private RecyclerView recyclerView;
    private Button scanButton;
    private BleDeviceAdapter adapter;
    private TextView label1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        recyclerView = findViewById(R.id.recyclerView);
        scanButton = findViewById(R.id.scanButton);
        label1 = findViewById(R.id.label1);

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
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
                return false;
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return false;
        }

        return true;
    }

    private void startScan() {
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                scanButton.setEnabled(false);
                statusTextView.setText("Scanning...");
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                adapter.addDevice(bleDevice);
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                scanButton.setEnabled(true);
                statusTextView.setText("Scan finished.");
            }
        });
    }

    private void connectToDevice(BleDevice device) {
        BleManager.getInstance().connect(device, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                statusTextView.setText("Connecting...");
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                statusTextView.setText("Connect fail: " + exception.toString());
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                statusTextView.setText("Connected");
                // Add code to handle data reception here
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                statusTextView.setText("Disconnected");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                startScan();
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method to update label1 with received JSON data
    private void updateLabel1(String jsonData) {
        try {
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

            String labelText = String.format("Temperature: %.2f\nHumidity: %.2f\nNO2: %d\nC2H5OH: %d\nVOC: %d\nCO: %d\nPM1: %d\nPM2.5: %d\nPM10: %d",
                    temperature, humidity, no2, c2h5oh, voc, co, pm1, pm2_5, pm10);

            label1.setText(labelText);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
