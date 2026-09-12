package com.example.uhfsample;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.cipherlab.rfid.ClResult;
import com.cipherlab.rfid.GeneralString;
import com.cipherlab.rfid.RFIDMode;
import com.cipherlab.rfid.ScanMode;
import com.cipherlab.rfidapi.RfidManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "RFID_RS38";
    private static final String PREFS_NAME = "rfid_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String DEFAULT_SERVER_URL = "http://127.0.0.1:8000/post_fixed_rfid";

    // CipherLab RFID API Manager
    private RfidManager mRfidManager = null;

    // Background Thread Pool for HTTP requests
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // UI Views
    private TextView tvServiceStatus;
    private EditText edtServerUrl;
    private Button btnSaveUrl;
    private TextView tvEpc;
    private TextView tvTid;
    private TextView tvRssi;
    private TextView tvApiStatus;
    private Button btnTriggerScan;
    private Button btnClearLog;
    private TextView tvLog;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind Views
        tvServiceStatus = findViewById(R.id.tv_service_status);
        edtServerUrl = findViewById(R.id.edt_server_url);
        btnSaveUrl = findViewById(R.id.btn_save_url);
        tvEpc = findViewById(R.id.tv_epc);
        tvTid = findViewById(R.id.tv_tid);
        tvRssi = findViewById(R.id.tv_rssi);
        tvApiStatus = findViewById(R.id.tv_api_status);
        btnTriggerScan = findViewById(R.id.btn_trigger_scan);
        btnClearLog = findViewById(R.id.btn_clear_log);
        tvLog = findViewById(R.id.tv_log);
        scrollView = findViewById(R.id.scroll_view);

        // Load saved server URL or default
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
        edtServerUrl.setText(savedUrl);

        // Save URL button listener
        btnSaveUrl.setOnClickListener(v -> {
            String newUrl = edtServerUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                prefs.edit().putString(KEY_SERVER_URL, newUrl).apply();
                Toast.makeText(MainActivity.this, "Server URL saved!", Toast.LENGTH_SHORT).show();
                appendLog("[CONFIG] Saved endpoint: " + newUrl);
            }
        });

        // Soft Scan Trigger button
        btnTriggerScan.setOnClickListener(v -> {
            if (mRfidManager != null) {
                appendLog("[ACTION] Soft Scan Trigger activated");
                int result = mRfidManager.SoftScanTrigger(true);
                if (result != ClResult.S_OK.ordinal()) {
                    String err = mRfidManager.GetLastError();
                    appendLog("[ERROR] SoftScanTrigger failed: " + err);
                }
            } else {
                Toast.makeText(MainActivity.this, "RFID Service not initialized yet", Toast.LENGTH_SHORT).show();
            }
        });

        // Clear Log button
        btnClearLog.setOnClickListener(v -> tvLog.setText("Log cleared.\n"));

        // Register CipherLab RFID Broadcast Receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(GeneralString.Intent_RFIDSERVICE_CONNECTED);
        filter.addAction(GeneralString.Intent_RFIDSERVICE_TAG_DATA);
        registerReceiver(myDataReceiver, filter);

        // Initialize RfidManager
        tvServiceStatus.setText("RFID Service: Connecting...");
        mRfidManager = RfidManager.InitInstance(this);
        appendLog("[INIT] RfidManager initialized, awaiting service connection...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(myDataReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver already unregistered", e);
        }

        if (mRfidManager != null) {
            mRfidManager.Release();
            mRfidManager = null;
        }

        networkExecutor.shutdown();
    }

    /**
     * Configures reader parameters suited for CipherLab RS38 (E310 Module).
     */
    private void setupRfidParameters() {
        if (mRfidManager == null) return;

        // Set Scan Mode to Single tag or Continuous
        int reScan = mRfidManager.SetScanMode(ScanMode.Single);
        if (reScan != ClResult.S_OK.ordinal()) {
            Log.e(TAG, "SetScanMode failed: " + mRfidManager.GetLastError());
        } else {
            Log.i(TAG, "SetScanMode(Single) succeeded");
        }

        // Set RFID Mode to Inventory
        int reMode = mRfidManager.SetRFIDMode(RFIDMode.Inventory);
        if (reMode != ClResult.S_OK.ordinal()) {
            Log.e(TAG, "SetRFIDMode failed: " + mRfidManager.GetLastError());
        } else {
            Log.i(TAG, "SetRFIDMode(Inventory) succeeded");
        }
    }

    /**
     * BroadcastReceiver for CipherLab RFID events
     */
    private final BroadcastReceiver myDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            // 1. Connection established with RfidService
            if (action.equals(GeneralString.Intent_RFIDSERVICE_CONNECTED)) {
                String pkgName = intent.getStringExtra("PackageName");
                String srvVer = mRfidManager != null ? mRfidManager.GetServiceVersion() : "N/A";
                String apiVer = mRfidManager != null ? mRfidManager.GetAPIVersion() : "N/A";

                tvServiceStatus.setText("RFID Service: Connected (Srv: " + srvVer + " | API: " + apiVer + ")");
                tvServiceStatus.setTextColor(0xFF2E7D32); // Dark Green
                appendLog("[CONNECTED] Service bound (" + pkgName + ", Srv: " + srvVer + ", API: " + apiVer + ")");

                // Configure reader settings for RS38
                setupRfidParameters();
            }

            // 2. RFID Tag Data Received
            else if (action.equals(GeneralString.Intent_RFIDSERVICE_TAG_DATA)) {
                int type = intent.getIntExtra(GeneralString.EXTRA_DATA_TYPE, -1);
                int response = intent.getIntExtra(GeneralString.EXTRA_RESPONSE, -1);
                double rssi = intent.getDoubleExtra(GeneralString.EXTRA_DATA_RSSI, 0.0);
                String pc = intent.getStringExtra(GeneralString.EXTRA_PC);
                String epc = intent.getStringExtra(GeneralString.EXTRA_EPC);
                String tid = intent.getStringExtra(GeneralString.EXTRA_TID);
                String readData = intent.getStringExtra(GeneralString.EXTRA_ReadData);

                Log.d(TAG, "Tag detected: EPC=" + epc + ", TID=" + tid + ", RSSI=" + rssi);

                // Update UI on screen
                tvEpc.setText("EPC: " + (epc != null && !epc.isEmpty() ? epc : "(Empty EPC)"));
                tvTid.setText("TID: " + (tid != null && !tid.isEmpty() ? tid : "-"));
                tvRssi.setText(String.format(Locale.US, "RSSI: %.1f dBm", rssi));

                appendLog(String.format(Locale.US, "[SCAN] EPC: %s (RSSI: %.1f dBm)", epc, rssi));

                // Send to FastAPI
                String targetUrl = edtServerUrl.getText().toString().trim();
                if (targetUrl.isEmpty()) {
                    targetUrl = DEFAULT_SERVER_URL;
                }

                postScanToFastAPI(targetUrl, epc, tid, rssi, pc, readData);
            }
        }
    };

    /**
     * Asynchronously sends the RFID scan data to the specified FastAPI endpoint.
     */
    private void postScanToFastAPI(String endpointUrl, String epc, String tid, double rssi, String pc, String readData) {
        tvApiStatus.setText("API Status: Posting to " + endpointUrl + "...");
        tvApiStatus.setTextColor(0xFF1976D2); // Blue

        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Construct JSON payload
                JSONObject payload = new JSONObject();
                payload.put("epc", epc != null ? epc : "");
                payload.put("tid", tid != null ? tid : "");
                payload.put("rssi", rssi);
                payload.put("pc", pc != null ? pc : "");
                payload.put("read_data", readData != null ? readData : "");
                payload.put("device_model", "RS38");
                payload.put("timestamp", System.currentTimeMillis());

                byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);

                URL url = new URL(endpointUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(postData.length);

                // Write payload
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }

                int statusCode = connection.getResponseCode();
                String responseBody = readStream(statusCode >= 200 && statusCode < 300 
                        ? connection.getInputStream() 
                        : connection.getErrorStream());

                final String finalResponse = responseBody;
                final int finalStatusCode = statusCode;

                mainHandler.post(() -> {
                    if (finalStatusCode >= 200 && finalStatusCode < 300) {
                        tvApiStatus.setText("API Status: [HTTP " + finalStatusCode + " SUCCESS] " + finalResponse);
                        tvApiStatus.setTextColor(0xFF2E7D32); // Green
                        appendLog("[HTTP " + finalStatusCode + " OK] Server responded: " + finalResponse);
                    } else {
                        tvApiStatus.setText("API Status: [HTTP " + finalStatusCode + " ERROR] " + finalResponse);
                        tvApiStatus.setTextColor(0xFFD32F2F); // Red
                        appendLog("[HTTP " + finalStatusCode + " FAIL] Response: " + finalResponse);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to send scan to FastAPI", e);
                final String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    tvApiStatus.setText("API Error: " + errorMessage);
                    tvApiStatus.setTextColor(0xFFD32F2F); // Red
                    appendLog("[NET ERROR] " + errorMessage);
                    appendLog("  * Tip: If FastAPI is running on PC via USB, run: adb reverse tcp:8000 tcp:8000");
                    appendLog("  * Tip: If over Wi-Fi, change 127.0.0.1 to your PC's IP address (e.g. 192.168.x.x)");
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Helper to read InputStream into String
     */
    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "(Failed to parse response)";
        }
    }

    /**
     * Appends a line with timestamp to the on-screen activity log.
     */
    private void appendLog(String message) {
        String timestamp = timeFormat.format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";
        tvLog.append(logLine);

        // Auto scroll to bottom
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}
