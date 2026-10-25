
package com.deva.smartpurse;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvMessage;
    private Button btnScan, btnConnect, btnStopAlarm;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice foundDevice;
    private TextView tvProximity;
    private android.widget.ProgressBar progressSignal;

    private MediaPlayer alarmPlayer;
    private TextView tvRssi;
    private android.os.Handler rssiHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private boolean isReadingRssi = false;

    private static final int REQUEST_BLUETOOTH_PERMISSION = 101;

    private boolean isScanning = false;

    private final android.os.Handler scanHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable scanTimeoutRunnable = () -> {
        stopScan();

        if (foundDevice == null) {
            tvStatus.setText("Status: Not Found");
            tvMessage.setText(
                    "SmartPurse not detected. Keep the tracker powered on and scan again."
            );
        } else {
            tvMessage.setText("Tracker found. Tap Connect.");
        }
    };

    private android.os.Handler reconnectHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private boolean isReconnecting = false;
    private int reconnectAttempts = 0;

    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvMessage = findViewById(R.id.tvMessage);
        btnScan = findViewById(R.id.btnScan);
        btnConnect = findViewById(R.id.btnConnect);
        tvRssi = findViewById(R.id.tvRssi);

        btnStopAlarm = findViewById(R.id.btnStopAlarm);

        Button btnOpenMap = findViewById(R.id.btnOpenMap);
        tvProximity = findViewById(R.id.tvProximity);
        progressSignal = findViewById(R.id.progressSignal);

        btnOpenMap.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    MapActivity.class
            );

            startActivity(intent);
        });

        btnStopAlarm.setOnClickListener(v -> {
            stopAlarm();
            tvMessage.setText("Alarm stopped.");
        });



        BluetoothManager manager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        bluetoothAdapter = manager.getAdapter();

        btnScan.setOnClickListener(v -> {
            stopAlarm();
            isReconnecting = false;
            reconnectHandler.removeCallbacksAndMessages(null);

            if (bluetoothGatt != null) {
                try {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                } catch (SecurityException ignored) {
                }

                bluetoothGatt = null;
            }

            isReadingRssi = false;
            rssiHandler.removeCallbacks(rssiRunnable);

            checkPermissionsAndScan();
        });
        btnConnect.setOnClickListener(v -> connectToTracker());
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private void readRssi() {

        if (bluetoothGatt == null || !isReadingRssi) {
            return;
        }

        if (!hasBluetoothPermissions()) {
            tvRssi.setText("Signal: Bluetooth permission required");
            return;
        }

        try {
            boolean started = bluetoothGatt.readRemoteRssi();

            if (!started) {
                tvRssi.setText("Signal: Request could not start");
            }

        } catch (SecurityException e) {
            tvRssi.setText("Signal: Permission required");
        }
    }

    private final Runnable rssiRunnable = new Runnable() {
        @Override
        public void run() {
            if (isReadingRssi && bluetoothGatt != null) {
                readRssi();
                rssiHandler.postDelayed(this, 2000);
            }
        }
    };

    private void checkPermissionsAndScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            tvMessage.setText("Please turn on Bluetooth on your phone.");
            return;
        }

        if (!hasBluetoothPermissions()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    REQUEST_BLUETOOTH_PERMISSION
            );
            return;
        }

        startScan();
    }

    private void startScan() {
        if (!hasBluetoothPermissions()) {
            tvMessage.setText("Bluetooth permission required.");
            return;
        }
        btnConnect.setVisibility(View.VISIBLE);

        try {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

            if (bluetoothLeScanner == null) {
                tvMessage.setText("Bluetooth scanner unavailable.");
                return;
            }

            // Stop any previous scan before starting a new one.
            stopScan();

            foundDevice = null;

            tvStatus.setText("Status: Searching...");
            tvMessage.setText("Looking for SmartPurse...");

            bluetoothLeScanner.startScan(scanCallback);
            isScanning = true;

            scanHandler.removeCallbacks(scanTimeoutRunnable);
            scanHandler.postDelayed(scanTimeoutRunnable, 15000);

        } catch (SecurityException e) {
            tvMessage.setText("Bluetooth permission required.");
        } catch (IllegalStateException e) {
            tvMessage.setText("Unable to start scanning. Try again.");
        }
    }

    private void stopScan() {
        scanHandler.removeCallbacks(scanTimeoutRunnable);

        if (bluetoothLeScanner != null && isScanning) {
            try {
                if (hasBluetoothPermissions()) {
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            } catch (SecurityException ignored) {
            }
        }

        isScanning = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!hasBluetoothPermissions()) {
                return;
            }

            try {
                String name = result.getDevice().getName();

                if (name == null && result.getScanRecord() != null) {
                    name = result.getScanRecord().getDeviceName();
                }

                if ("SmartPurse".equals(name)) {
                    foundDevice = result.getDevice();

                    runOnUiThread(() -> {
                        tvStatus.setText("Status: Tracker Found");
                        tvMessage.setText("SmartPurse detected nearby!");

                        btnConnect.setVisibility(View.VISIBLE);
                        btnConnect.setEnabled(true);

                        if (isReconnecting) {
                            isReconnecting = false;
                            reconnectHandler.removeCallbacksAndMessages(null);
                            connectToTracker();
                        }
                    });
                }

            } catch (SecurityException ignored) {
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(() ->
                    tvMessage.setText("Bluetooth scan failed: " + errorCode));
        }
    };

    private void connectToTracker() {
        if (!hasBluetoothPermissions()) {
            checkPermissionsAndScan();
            return;
        }

        if (foundDevice == null) {
            tvMessage.setText("Scan first to find SmartPurse.");
            return;
        }

        try {
            stopAlarm();

            tvStatus.setText("Status: Connecting...");
            tvMessage.setText("Connecting to SmartPurse...");

            bluetoothGatt = foundDevice.connectGatt(
                    this,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
            );

        } catch (SecurityException e) {
            tvMessage.setText("Bluetooth permission required.");
        }
    }

    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {

                @Override
                public void onReadRemoteRssi(
                        BluetoothGatt gatt,
                        int rssi,
                        int status) {

                    runOnUiThread(() -> {
                        if (status == BluetoothGatt.GATT_SUCCESS) {

                            int signalStrength = Math.max(0,
                                    Math.min(100, (rssi + 100) * 2));

                            String level;

                            if (rssi >= -50) {
                                level = "VERY NEAR";
                            } else if (rssi >= -60) {
                                level = "NEAR";
                            } else if (rssi >= -75) {
                                level = "MEDIUM";
                            } else {
                                level = "FAR";
                            }

                            tvRssi.setText("Bluetooth Signal: " + rssi + " dBm");
                            tvProximity.setText("Purse Status: " + level);
                            progressSignal.setProgress(signalStrength);

                        } else {
                            tvRssi.setText("Signal: Read failed");
                        }
                    });
                }

                @Override
                public void onConnectionStateChange(
                        BluetoothGatt gatt,
                        int status,
                        int newState) {

                    if (status == BluetoothGatt.GATT_SUCCESS
                            && newState == BluetoothProfile.STATE_CONNECTED) {

                        bluetoothGatt = gatt;

                        runOnUiThread(() -> {
                            tvStatus.setText("Status: Connected");
                            tvMessage.setText("SmartPurse is connected!");
                            stopAlarm();

                            tvRssi.setText("Signal: Reading...");
                        });

                        isReadingRssi = true;
                        rssiHandler.removeCallbacks(rssiRunnable);
                        rssiHandler.post(rssiRunnable);

                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                        isReadingRssi = false;
                        rssiHandler.removeCallbacks(rssiRunnable);

                        runOnUiThread(() -> {
                            tvStatus.setText("Status: Disconnected");
                            tvMessage.setText("Tracker connection lost!");
                            tvRssi.setText("Signal: Disconnected");
                            playAlarm();
                        });

                        try {
                            gatt.close();
                        } catch (SecurityException ignored) {
                        }

                        if (!isFinishing()) {
                            reconnectHandler.postDelayed(
                                    () -> MainActivity.this.startReconnect(),
                                    2000
                            );
                        }

                        if (bluetoothGatt == gatt) {
                            bluetoothGatt = null;
                        }


                        runOnUiThread(() -> {
                            btnConnect.setEnabled(false);

                            tvMessage.setText(
                                    "Tracker disconnected. Alarm playing. Tap Scan to find it again."
                            );
                            tvProximity.setText("Purse Status: DISCONNECTED");
                            progressSignal.setProgress(0);
                        });


                    }
                }
            };



    private void playAlarm() {
        try {
            if (alarmPlayer == null) {
                alarmPlayer = MediaPlayer.create(
                        this,
                        Settings.System.DEFAULT_ALARM_ALERT_URI
                );
            }

            if (alarmPlayer != null && !alarmPlayer.isPlaying()) {
                alarmPlayer.setLooping(true);
                alarmPlayer.start();
            } else if (alarmPlayer == null) {
                tvMessage.setText("Tracker disconnected. Alarm sound unavailable.");
            }

        } catch (Exception e) {
            tvMessage.setText("Tracker disconnected. Could not play alarm.");
        }
    }

    private void stopAlarm() {
        if (alarmPlayer != null) {
            try {
                if (alarmPlayer.isPlaying()) {
                    alarmPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
            }

            alarmPlayer.release();
            alarmPlayer = null;
        }
    }


    @Override
    protected void onDestroy() {
        stopAlarm();

        isReadingRssi = false;
        rssiHandler.removeCallbacks(rssiRunnable);

        isReconnecting = false;
        reconnectHandler.removeCallbacksAndMessages(null);

        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (SecurityException ignored) {
            }

            bluetoothGatt = null;
        }
        stopScan();
        scanHandler.removeCallbacks(scanTimeoutRunnable);

        super.onDestroy();
    }

    private void startReconnect() {

        if (isReconnecting) {
            return;
        }

        isReconnecting = true;
        reconnectAttempts = 0;

        tvMessage.setText("Connection lost. Searching for SmartPurse...");
        tvStatus.setText("Status: Reconnecting");

        attemptReconnect();
    }

    private void attemptReconnect() {

        if (!isReconnecting || isFinishing()) {
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            isReconnecting = false;
            tvMessage.setText("Turn on Bluetooth to reconnect.");
            return;
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            isReconnecting = false;
            tvStatus.setText("Status: Disconnected");
            tvMessage.setText("Reconnect failed. Tap Scan to try again.");
            return;
        }

        reconnectAttempts++;

        runOnUiThread(() -> {
            tvStatus.setText("Status: Reconnecting");
            tvMessage.setText(
                    "Searching for SmartPurse: attempt "
                            + reconnectAttempts + " of "
                            + MAX_RECONNECT_ATTEMPTS
            );
        });

        reconnectHandler.postDelayed(() -> {
            if (!isReconnecting) {
                return;
            }

            if (isScanning) {
                stopScan();
            }

            startScan();

            // If this attempt does not connect, schedule another attempt.
            reconnectHandler.postDelayed(() -> {
                if (isReconnecting) {
                    stopScan();
                    attemptReconnect();
                }
            }, 16000);

        }, 2000);
    }
}

