package com.example.bleapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.polidea.rxandroidble3.RxBleClient;
import com.polidea.rxandroidble3.RxBleConnection;
import com.polidea.rxandroidble3.RxBleDevice;

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

public class BluetoothService extends Service {

    private static final String TAG = "BluetoothService";
    private static final String CHANNEL_ID = "BluetoothServiceChannel";
    private static final String CHARACTERISTIC_UUID = "0000fef4-0000-1000-8000-00805f9b34fb";
    private static final String TARGET_DEVICE_MAC = "7C:DF:A1:EE:D4:96";

    private RxBleClient rxBleClient;
    private RxBleDevice selectedDevice;
    private RxBleConnection connection;
    private CompositeDisposable disposables = new CompositeDisposable();

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateTask;
    private File csvFile;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification("Bluetooth Service is running..."));

        rxBleClient = RxBleClient.create(this);
        initializeCSVFile();
        connectToDevice();
    }

    private void initializeCSVFile() {
        csvFile = new File(getExternalFilesDir(null), "sensor_data.csv");
        if (!csvFile.exists()) {
            try (FileWriter writer = new FileWriter(csvFile)) {
                writer.append("Timestamp,Temperature,Humidity,Pressure,PM1,PM2.5,PM10,CO,VOC,CO2\n");
            } catch (IOException e) {
                Log.e(TAG, "Error initializing CSV file: " + e.toString());
            }
        }
    }

    private void connectToDevice() {
        selectedDevice = rxBleClient.getBleDevice(TARGET_DEVICE_MAC);

        Disposable connectionDisposable = selectedDevice.establishConnection(false)
                .doOnDispose(() -> Log.d(TAG, "Disconnected"))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rxBleConnection -> {
                    connection = rxBleConnection;
                    Log.d(TAG, "Connected to device");
                    startReadingData();
                }, throwable -> {
                    Log.e(TAG, "Connection failed: " + throwable.toString());
                });

        disposables.add(connectionDisposable);
    }

    private void startReadingData() {
        updateTask = new Runnable() {
            @Override
            public void run() {
                if (connection != null) {
                    readCharacteristicData();
                }
                handler.postDelayed(this, 5000); // Repeat every 5 seconds
            }
        };
        handler.post(updateTask);
    }

    private void readCharacteristicData() {
        Disposable readDisposable = connection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(characteristicValue -> {
                    String jsonString = new String(characteristicValue);
                    saveDataToCSV(jsonString);
                }, throwable -> {
                    Log.e(TAG, "Read failed: " + throwable.toString());
                });

        disposables.add(readDisposable);
    }

    private void saveDataToCSV(String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONObject data = jsonObject.getJSONObject("data");

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            String row = timestamp + ","
                    + data.getDouble("temperature") + ","
                    + data.getDouble("humidity") + ","
                    + data.getDouble("pressure") + ","
                    + data.getInt("pm1") + ","
                    + data.getInt("pm2_5") + ","
                    + data.getInt("pm10") + ","
                    + data.getInt("co") + ","
                    + data.getInt("voc") + ","
                    + data.getInt("co2") + "\n";

            try (FileWriter writer = new FileWriter(csvFile, true)) {
                writer.append(row);
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error saving data to CSV: " + e.toString());
        }
    }

    private Notification createNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Service")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTask);
        disposables.clear();
        Log.d(TAG, "Bluetooth Service stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
