package com.example.uhfsample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Manifest-registered BroadcastReceiver for CipherLab RS38 2D Barcode Scans.
 * Ensures barcode/QR data is captured and sent to the server even if MainActivity
 * is minimized or in the background.
 */
public class BarcodeReceiver extends BroadcastReceiver {
    private static final String TAG = "BarcodeReceiver";
    private static final String PREFS_NAME = "rfid_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String DEFAULT_SERVER_URL = "http://192.168.88.9:8000/post_fixed_rfid";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String DEFAULT_DEVICE_ID = "dev-cpr-01";

    // Static debounce fields for background deduplication
    private static String sLastBarcode = "";
    private static long sLastBarcodeTime = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "BarcodeReceiver received action: " + action);

        String barcode = extractBarcode(intent);
        if (barcode == null || barcode.trim().isEmpty()) {
            Log.w(TAG, "No barcode data found in intent extras");
            return;
        }

        barcode = barcode.trim();
        // Strip AIM symbology prefix if present (e.g. "]Q1" for QR Code)
        if (barcode.startsWith("]Q1") || barcode.startsWith("]Q2") || barcode.startsWith("]d2") || barcode.startsWith("]C1")) {
            barcode = barcode.substring(3);
        }

        // 1. If MainActivity is alive, delegate to MainActivity's handler
        // which updates the UI, performs deduplication, and posts to FastAPI.
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            String codeType = intent.getStringExtra("Decoder_CodeType_String");
            String label = (codeType != null && !codeType.isEmpty()) ? codeType : "QR/Barcode";
            mainActivity.onExternalBarcodeReceived(barcode, label);
            return;
        }

        // 2. Deduplication check for background scans
        long now = System.currentTimeMillis();
        if (barcode.equals(sLastBarcode) && (now - sLastBarcodeTime < 1000)) {
            Log.d(TAG, "Duplicate background scan ignored: " + barcode);
            return;
        }
        sLastBarcode = barcode;
        sLastBarcodeTime = now;

        String codeType = intent.getStringExtra("Decoder_CodeType_String");
        String label = (codeType != null && !codeType.isEmpty()) ? codeType : "QR/Barcode";

        // 3. Post directly to FastAPI when running in background
        final PendingResult pendingResult = goAsync();
        final String finalBarcode = barcode;

        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
                String deviceId = prefs.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID);

                // Classification based on production rules
                String payloadKey;
                if (finalBarcode.startsWith("WFG")) {
                    payloadKey = "rfidUniqueId";
                } else if (finalBarcode.startsWith("100") || finalBarcode.toUpperCase().startsWith("WAK-MAT-") || finalBarcode.toUpperCase().startsWith("MAT-")) {
                    payloadKey = "materialCode";
                } else if (finalBarcode.startsWith("200") || finalBarcode.startsWith("400") || finalBarcode.toUpperCase().startsWith("WO-")) {
                    payloadKey = "workOrderNo";
                } else {
                    payloadKey = "data";
                }

                JSONObject payload = new JSONObject();
                payload.put(payloadKey, finalBarcode);
                payload.put("deviceId", deviceId);

                byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);

                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(postData.length);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                Log.i(TAG, "Background POST result: HTTP " + responseCode);
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error posting barcode in background", e);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    private String extractBarcode(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return null;

        String[] keys = {
            "Decoder_Data",
            "Original_Decoder_Data",
            "BcReaderData",
            "data_string",
            "com.cipherlab.barcode.GeneralString.EXTRA_DATA_STRING",
            "barcode_data",
            "data",
            "Barcode",
            "barcode",
            "EXTRA_DATA_STRING",
            "decode_data",
            "scan_data",
            "text"
        };

        for (String key : keys) {
            if (bundle.containsKey(key)) {
                Object val = bundle.get(key);
                if (val instanceof String && !((String) val).isEmpty()) {
                    return (String) val;
                } else if (val instanceof byte[] && ((byte[]) val).length > 0) {
                    return new String((byte[]) val, StandardCharsets.UTF_8);
                } else if (val instanceof CharSequence) {
                    return val.toString();
                }
            }
        }

        byte[] bytes = intent.getByteArrayExtra("Decoder_DataArray");
        if (bytes == null || bytes.length == 0) {
            bytes = intent.getByteArrayExtra("data_byte");
        }
        if (bytes != null && bytes.length > 0) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        // Dynamic fallback
        for (String k : bundle.keySet()) {
            if (k.equalsIgnoreCase("action") || k.equalsIgnoreCase("type") || k.equalsIgnoreCase("code") || k.equalsIgnoreCase("Decoder_CodeType")) {
                continue;
            }
            Object v = bundle.get(k);
            if (v instanceof String && !((String) v).isEmpty()) {
                return (String) v;
            } else if (v instanceof byte[] && ((byte[]) v).length > 0) {
                return new String((byte[]) v, StandardCharsets.UTF_8);
            }
        }

        return null;
    }
}

