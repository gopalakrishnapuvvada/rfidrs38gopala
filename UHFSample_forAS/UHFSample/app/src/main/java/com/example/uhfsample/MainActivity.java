package com.example.uhfsample;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
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

    // Known CipherLab Barcode Broadcast Actions
    private static final String ACTION_CIPHERLAB_BARCODE_1 = "com.cipherlab.barcode.GeneralString.Intent_BARCODE_SERVICE_BROADCAST";
    private static final String ACTION_CIPHERLAB_BARCODE_2 = "com.cipherlab.barcode.action.BARCODE_DATA";
    private static final String ACTION_CIPHERLAB_BARCODE_3 = "action.reader.decode_data";
    private static final String ACTION_CIPHERLAB_BARCODE_4 = "com.cipherlab.barcode.action.DECODE_DATA";
    private static final String ACTION_CIPHERLAB_SOFTTRIGGER = "com.cipherlab.barcode.GeneralString.Intent_SOFTTRIGGER_DATA";
    private static final String EXTRA_CIPHERLAB_SOFTTRIGGER_INT = "com.cipherlab.barcode.GeneralString.EXTRA_DATA_INT";

    // CipherLab RFID API Manager
    private RfidManager mRfidManager = null;

    // Background Thread Pool & Handlers
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // Buffering for keyboard-wedge barcode events
    private final StringBuilder barcodeKeyBuffer = new StringBuilder();
    private boolean isProcessingInput = false;

    // UI Views
    private TextView tvServiceStatus;
    private EditText edtServerUrl;
    private Button btnSaveUrl;
    private TextView tvEpc;
    private TextView tvTid;
    private TextView tvRssi;
    private TextView tvQrData;
    private TextView tvQrTime;
    private EditText edtQrInput;
    private TextView tvApiStatus;
    private Button btnTriggerRfid;
    private Button btnTriggerQr;
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
        tvQrData = findViewById(R.id.tv_qr_data);
        tvQrTime = findViewById(R.id.tv_qr_time);
        edtQrInput = findViewById(R.id.edt_qr_input);
        tvApiStatus = findViewById(R.id.tv_api_status);
        btnTriggerRfid = findViewById(R.id.btn_trigger_rfid);
        btnTriggerQr = findViewById(R.id.btn_trigger_qr);
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

        // Trigger RFID Scan button
        btnTriggerRfid.setOnClickListener(v -> {
            if (mRfidManager != null) {
                appendLog("[ACTION] Triggering RFID scan...");
                int result = mRfidManager.SoftScanTrigger(true);
                if (result != ClResult.S_OK.ordinal()) {
                    String err = mRfidManager.GetLastError();
                    appendLog("[ERROR] RFID SoftScanTrigger failed: " + err);
                }
            } else {
                Toast.makeText(MainActivity.this, "RFID Service connecting...", Toast.LENGTH_SHORT).show();
            }
        });

        // Trigger QR / Barcode Scan button
        btnTriggerQr.setOnClickListener(v -> {
            appendLog("[ACTION] 2D Barcode scanner activated (aim at QR code)...");
            edtQrInput.requestFocus();
            triggerBarcodeScanner();
        });

        // Clear Log button
        btnClearLog.setOnClickListener(v -> tvLog.setText("Log cleared.\n"));

        // Setup QR code input text watcher (supports Keyboard Emulation mode seamlessly)
        setupQrInputWatcher();

        // Register CipherLab RFID & Barcode Broadcast Receivers
        IntentFilter filter = new IntentFilter();
        // RFID actions
        filter.addAction(GeneralString.Intent_RFIDSERVICE_CONNECTED);
        filter.addAction(GeneralString.Intent_RFIDSERVICE_TAG_DATA);
        // 2D Barcode actions
        filter.addAction(ACTION_CIPHERLAB_BARCODE_1);
        filter.addAction(ACTION_CIPHERLAB_BARCODE_2);
        filter.addAction(ACTION_CIPHERLAB_BARCODE_3);
        filter.addAction(ACTION_CIPHERLAB_BARCODE_4);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(myDataReceiver, filter, 2); // 2 = RECEIVER_EXPORTED
        } else {
            registerReceiver(myDataReceiver, filter);
        }

        // Initialize RfidManager
        tvServiceStatus.setText("RFID: Connecting... | 2D Barcode: Ready");
        mRfidManager = RfidManager.InitInstance(this);
        appendLog("[INIT] Dual Scanner (RFID + QR) initialized.");
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
     * Sets up TextWatcher on edtQrInput. When the 2D imager outputs characters (keyboard wedge),
     * this captures the QR code instantly, sends it, and clears the input box for the next scan.
     */
    private void setupQrInputWatcher() {
        final Runnable processInputRunnable = () -> {
            if (isProcessingInput) return;
            String text = edtQrInput.getText().toString().trim();
            if (!text.isEmpty()) {
                isProcessingInput = true;
                edtQrInput.setText("");
                handleQrCodeScanned(text, "HardwareScan");
                isProcessingInput = false;
            }
        };

        edtQrInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isProcessingInput) return;
                mainHandler.removeCallbacks(processInputRunnable);
                // When scanner inputs characters, debounce 120ms to allow all chars to arrive
                if (s.length() > 0) {
                    mainHandler.postDelayed(processInputRunnable, 120);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Also trigger immediately if scanner sends Enter key
        edtQrInput.setOnEditorActionListener((v, actionId, event) -> {
            mainHandler.removeCallbacks(processInputRunnable);
            processInputRunnable.run();
            return true;
        });
    }

    /**
     * Triggers the CipherLab 2D Barcode imager via Broadcast Intent.
     */
    private void triggerBarcodeScanner() {
        try {
            Intent triggerIntent = new Intent(ACTION_CIPHERLAB_SOFTTRIGGER);
            triggerIntent.putExtra(EXTRA_CIPHERLAB_SOFTTRIGGER_INT, 1);
            sendBroadcast(triggerIntent);

            Intent altIntent = new Intent("action.reader.trigger");
            altIntent.putExtra("trigger", true);
            sendBroadcast(altIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error triggering barcode scanner", e);
        }
    }

    /**
     * Configures reader parameters suited for CipherLab RS38 (E310 Module).
     */
    private void setupRfidParameters() {
        if (mRfidManager == null) return;

        int reScan = mRfidManager.SetScanMode(ScanMode.Single);
        if (reScan != ClResult.S_OK.ordinal()) {
            Log.e(TAG, "SetScanMode failed: " + mRfidManager.GetLastError());
        }

        int reMode = mRfidManager.SetRFIDMode(RFIDMode.Inventory);
        if (reMode != ClResult.S_OK.ordinal()) {
            Log.e(TAG, "SetRFIDMode failed: " + mRfidManager.GetLastError());
        }
    }

    /**
     * BroadcastReceiver for CipherLab RFID & 2D Barcode/QR events
     */
    private final BroadcastReceiver myDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            // 1. RFID SERVICE CONNECTED
            if (action.equals(GeneralString.Intent_RFIDSERVICE_CONNECTED)) {
                String pkgName = intent.getStringExtra("PackageName");
                String srvVer = mRfidManager != null ? mRfidManager.GetServiceVersion() : "N/A";

                tvServiceStatus.setText("RFID: Connected (v" + srvVer + ") | 2D Barcode: Ready");
                tvServiceStatus.setTextColor(0xFF2E7D32);
                appendLog("[CONNECTED] RFID Service connected (" + pkgName + ", v" + srvVer + ")");
                setupRfidParameters();
            }

            // 2. RFID TAG SCANNED
            else if (action.equals(GeneralString.Intent_RFIDSERVICE_TAG_DATA)) {
                double rssi = intent.getDoubleExtra(GeneralString.EXTRA_DATA_RSSI, 0.0);
                String pc = intent.getStringExtra(GeneralString.EXTRA_PC);
                String epc = intent.getStringExtra(GeneralString.EXTRA_EPC);
                String tid = intent.getStringExtra(GeneralString.EXTRA_TID);
                String readData = intent.getStringExtra(GeneralString.EXTRA_ReadData);

                handleRfidScanned(epc, tid, rssi, pc, readData);
            }

            // 3. 2D BARCODE / QR CODE SCANNED (Intent)
            else if (action.equals(ACTION_CIPHERLAB_BARCODE_1) || 
                     action.equals(ACTION_CIPHERLAB_BARCODE_2) || 
                     action.equals(ACTION_CIPHERLAB_BARCODE_3) ||
                     action.equals(ACTION_CIPHERLAB_BARCODE_4)) {
                
                String qrData = extractBarcodeData(intent);
                if (qrData != null && !qrData.isEmpty()) {
                    handleQrCodeScanned(qrData, "Intent");
                }
            }
        }
    };

    /**
     * Extracts barcode text from known CipherLab intent extras.
     */
    private String extractBarcodeData(Intent intent) {
        String data = intent.getStringExtra("com.cipherlab.barcode.GeneralString.EXTRA_DATA_STRING");
        if (data == null) data = intent.getStringExtra("data_string");
        if (data == null) data = intent.getStringExtra("barcode_data");
        if (data == null) data = intent.getStringExtra("data");
        if (data == null) data = intent.getStringExtra("Barcode");
        if (data == null) {
            byte[] bytes = intent.getByteArrayExtra("data_byte");
            if (bytes != null && bytes.length > 0) {
                data = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        return data;
    }

    /**
     * Global key event listener to capture QR codes even when no input box has focus.
     */
    private final Runnable flushBarcodeBufferRunnable = () -> {
        if (barcodeKeyBuffer.length() > 0) {
            String scanned = barcodeKeyBuffer.toString().trim();
            barcodeKeyBuffer.setLength(0);
            if (!scanned.isEmpty() && scanned.length() > 1) {
                handleQrCodeScanned(scanned, "KeyCapture");
            }
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // Ignore navigation keys
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                return super.dispatchKeyEvent(event);
            }

            // Hardware scan key or enter key finishes scan
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                mainHandler.removeCallbacks(flushBarcodeBufferRunnable);
                mainHandler.post(flushBarcodeBufferRunnable);
                return true;
            }

            char unicodeChar = (char) event.getUnicodeChar();
            if (unicodeChar >= 32 && unicodeChar <= 126) {
                barcodeKeyBuffer.append(unicodeChar);
                mainHandler.removeCallbacks(flushBarcodeBufferRunnable);
                // Debounce: if no character comes in next 100ms, process buffer
                mainHandler.postDelayed(flushBarcodeBufferRunnable, 100);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Processes and uploads an RFID tag scan.
     */
    private void handleRfidScanned(String epc, String tid, double rssi, String pc, String readData) {
        tvEpc.setText("EPC: " + (epc != null && !epc.isEmpty() ? epc : "(Empty EPC)"));
        tvTid.setText("TID: " + (tid != null && !tid.isEmpty() ? tid : "-"));
        tvRssi.setText(String.format(Locale.US, "RSSI: %.1f dBm", rssi));

        appendLog(String.format(Locale.US, "[RFID SCAN] EPC: %s (RSSI: %.1f dBm)", epc, rssi));

        try {
            JSONObject payload = new JSONObject();
            payload.put("scan_type", "RFID");
            payload.put("epc", epc != null ? epc : "");
            payload.put("tid", tid != null ? tid : "");
            payload.put("rssi", rssi);
            payload.put("pc", pc != null ? pc : "");
            payload.put("read_data", readData != null ? readData : "");
            payload.put("device_model", "RS38");
            payload.put("timestamp", System.currentTimeMillis());

            postJsonToFastAPI(payload, "RFID: " + epc);
        } catch (Exception e) {
            Log.e(TAG, "Error creating RFID payload", e);
        }
    }

    /**
     * Processes and uploads a 2D QR code scan.
     */
    private void handleQrCodeScanned(String qrContent, String source) {
        String timestamp = timeFormat.format(new Date());
        tvQrData.setText("Data: " + qrContent);
        tvQrTime.setText("Time: " + timestamp + " (via " + source + ")");

        appendLog("[QR SCAN] " + qrContent);

        try {
            JSONObject payload = new JSONObject();
            payload.put("scan_type", "QR");
            payload.put("data", qrContent);
            payload.put("device_model", "RS38");
            payload.put("timestamp", System.currentTimeMillis());

            postJsonToFastAPI(payload, "QR: " + qrContent);
        } catch (Exception e) {
            Log.e(TAG, "Error creating QR payload", e);
        }
    }

    /**
     * Posts a JSON payload asynchronously to the configured FastAPI endpoint.
     */
    private void postJsonToFastAPI(JSONObject payload, String logSummary) {
        String endpointUrl = edtServerUrl.getText().toString().trim();
        if (endpointUrl.isEmpty()) {
            endpointUrl = DEFAULT_SERVER_URL;
        }

        final String targetUrl = endpointUrl;
        tvApiStatus.setText("API Status: Posting " + logSummary + "...");
        tvApiStatus.setTextColor(0xFF1976D2);

        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);

                URL url = new URL(targetUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(postData.length);

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
                        tvApiStatus.setTextColor(0xFF2E7D32);
                        appendLog("[HTTP " + finalStatusCode + " OK] " + logSummary);
                    } else {
                        tvApiStatus.setText("API Status: [HTTP " + finalStatusCode + " ERROR] " + finalResponse);
                        tvApiStatus.setTextColor(0xFFD32F2F);
                        appendLog("[HTTP " + finalStatusCode + " FAIL] " + logSummary + " -> " + finalResponse);
                    }
                });

            } catch (Exception e) {
                final String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    tvApiStatus.setText("API Error: " + errorMessage);
                    tvApiStatus.setTextColor(0xFFD32F2F);
                    appendLog("[NET ERROR] " + errorMessage);
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

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

    private void appendLog(String message) {
        String timestamp = timeFormat.format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";
        tvLog.append(logLine);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}
