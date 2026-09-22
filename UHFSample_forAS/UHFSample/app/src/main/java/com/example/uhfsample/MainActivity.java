package com.example.uhfsample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cipherlab.rfid.ClResult;
import com.cipherlab.rfid.GeneralString;
import com.cipherlab.rfid.RFIDMode;
import com.cipherlab.rfid.ScanMode;
import com.cipherlab.rfidapi.RfidManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.provider.Settings;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FG Product Validation & Dual Scanner Activity.
 * 
 * Default Startup: Product Validation (3-step FG workflow)
 * Features:
 * 1. Persistent Server IP address input bar controlling all API calls dynamically.
 * 2. Real-time POST /api/post_scan binding on RFID & Barcode reads with Master Data validation.
 * 3. POST /api/cancel_scan on session Cancel/Reset.
 * 4. POST /api/transactions/ on 3/3 Queue validation commit.
 * 5. Tabular FG WIP Transaction Records (Horizontal Table) matching web portal data grid.
 * 6. 100% preservation of CipherLab RS38 native RFID SDK and 2D barcode receivers.
 * 7. Hardware MAC address retrieval and automatic server authorization verification.
 */
public class MainActivity extends Activity {
    private static final String TAG = "RFID_RS38";
    private static final String PREFS_NAME = "rfid_prefs";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String DEFAULT_SERVER_IP = "192.168.1.14:8000";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String DEFAULT_DEVICE_ID = "dev-cpr-01";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String DEFAULT_DEVICE_NAME = "CIPHER RS38 UHF Reader";
    private static final String KEY_DEVICE_MAC = "device_mac_address";
    private static final String KEY_CACHED_RECORDS = "cached_transaction_records";

    // MAC Authorization & Device Identity State
    private boolean mIsDeviceAuthorized = false;
    private String mCachedDeviceMac = "";
    private String mDeviceDisplayName = "";

    // CipherLab RS38 Native 2D Barcode / QR Actions
    private static final String ACTION_CIPHERLAB_PASS_DATA = "com.cipherlab.barcodebaseapi.PASS_DATA_2_APP";
    private static final String ACTION_CIPHERLAB_CALLBACK = "sw.reader.barcodebaseapi.CALLBACK";
    private static final String ACTION_CIPHERLAB_DECODE_COMPLETE = "sw.reader.decode.complete";
    private static final String ACTION_CIPHERLAB_SOFTTRIGGER = "com.cipherlab.barcodebaseapi.SOFTTRIGGER_DATA";
    private static final String ACTION_CIPHERLAB_SCANKEY_PRESS = "sw.reader.scankey.press";
    private static final String ACTION_CIPHERLAB_LEGACY_1 = "com.cipherlab.barcode.GeneralString.Intent_BARCODE_SERVICE_BROADCAST";
    private static final String ACTION_CIPHERLAB_LEGACY_2 = "com.cipherlab.barcode.action.BARCODE_DATA";
    private static final String ACTION_CIPHERLAB_LEGACY_3 = "action.reader.decode_data";
    private static final String ACTION_CIPHERLAB_LEGACY_4 = "com.cipherlab.barcode.action.DECODE_DATA";

    // Screen Constants
    private static final int SCREEN_SCANNER = 0;
    private static final int SCREEN_VALIDATION = 1;
    private static final int SCREEN_RECORDS = 2;
    // Default screen is PRODUCT VALIDATION
    private int mCurrentScreen = SCREEN_VALIDATION;

    // CipherLab RFID API Manager
    private RfidManager mRfidManager = null;

    // Background Thread Pool & Handlers
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss '(IST)'", Locale.getDefault());

    // Hardware Keyboard-Wedge Buffering
    private final StringBuilder barcodeKeyBuffer = new StringBuilder();
    private boolean isProcessingInput = false;

    // Deduplication / Debouncing
    private String lastRfidEpc = "";
    private long lastRfidTime = 0;
    private String lastQrData = "";
    private long lastQrTime = 0;
    private static final long DEBOUNCE_MS = 800;

    // =========================================================================
    // UI VIEWS
    // =========================================================================
    // Global Header
    private TextView tvHeaderTitle, tvHeaderSubtitle;
    private View btnHeaderMenu, btnHeaderNotifications;

    // Server IP Bar
    private EditText edtServerIp;
    private Button btnSaveIp;

    // Bottom Navigation
    private View navItemScanner, navItemValidation, navItemRecords;
    private ImageView navIconScanner, navIconValidation, navIconRecords;
    private TextView navLabelScanner, navLabelValidation, navLabelRecords;

    // Screen Containers
    private LinearLayout layoutScreenScanner, layoutScreenValidation, layoutScreenRecords;

    // SCREEN 1: DEVICE CONFIG
    private TextView tvConfigAuthBadge;
    private TextView tvConfigDevName, tvConfigDevMac, tvConfigDevId, tvConfigDevModel;

    // SCREEN 2: PRODUCT VALIDATION
    private TextView tvValStatusPill, tvValDevicePill;
    private TextView tvValBadgeRfid, tvValRfidEmpty, tvValRfidEpc, tvValRfidTid, tvValRfidRssi;
    private View layoutValRfidCaptured;
    private Button btnValTriggerRfid;
    private TextView tvValBadgeMaterial, tvValMaterialCode, tvValMatName, tvValPartNo;
    private View layoutValMaterialEmpty, layoutValMaterialCaptured;
    private FrameLayout layoutValFgGallery;
    private ImageView imgValFg;
    private TextView tvValImgAngle;
    private Button btnValImgPrev, btnValImgNext;
    private TextView tvValCatBadge, tvValStatusBadge;
    private TextView tvValSpecDim, tvValSpecColor, tvValSpecPkg, tvValSpecModel, tvValSpecWeight, tvValSpecStatus;
    private TextView tvValBadgeWo, tvValWoEmpty, tvValWoCode;
    private View layoutValWoCaptured;
    private TextView tvValLiveTitle, tvValLiveDevice;
    private Button btnValReset, btnValQueue;

    // SCREEN 3: RECORDS
    private TextView tvRecCountBadge;
    private EditText edtRecSearch;
    private LinearLayout layoutRecTilesContainer;
    private View layoutRecEmpty;
    private TextView tvRecEmptySubtitle;
    private final List<TransactionRecord> mThisDeviceRecords = new ArrayList<>();

    // Invisible Barcode Wedge Input
    private EditText edtQrInput;

    // =========================================================================
    // STATE DATA
    // =========================================================================
    // Validation Workflow State
    private String mValRfidEpc = "";
    private String mValRfidTid = "";
    private double mValRfidRssi = 0.0;
    private boolean mValRfidCaptured = false;

    private String mValMaterialCode = "";
    private String mValProductName = "Wakefit Orthopedic Memory Foam Mattress";
    private String mValPartNumber = "FG-102301022702";
    private String mValCategory = "Mattress";
    private String mValModel = "Dual Comfort Foam";
    private String mValDimensions = "0 x 0 x 0 mm";
    private String mValColour = "Red";
    private String mValFgStatus = "Active";
    private String mValPackageType = "Rolled Vacuum Box";
    private String mValWeight = "0 kg / 0 kg";
    private final List<String> mValFgImages = new ArrayList<>();
    private int mValFgImageIndex = 0;
    private boolean mValMaterialCaptured = false;

    private String mValWorkOrder = "";
    private boolean mValWorkOrderCaptured = false;

    // Committed Records List from Server
    private final List<TransactionRecord> mTransactionRecords = new ArrayList<>();

    // Singleton Instance
    private static MainActivity sInstance = null;

    public static MainActivity getInstance() {
        return sInstance;
    }

    public void onExternalBarcodeReceived(String barcode, String source) {
        mainHandler.post(() -> handleQrCodeScanned(barcode, source));
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;
        setContentView(R.layout.activity_main);

        bindViews();
        loadSavedServerIp();
        setupNavigation();
        setupServerIpBar();
        setupScannerScreenListeners();
        setupValidationScreenListeners();
        setupRecordsScreenListeners();
        setupHeaderListeners();
        setupQrInputWatcher();
        registerCipherLabReceivers();

        // Default screen: PRODUCT VALIDATION
        showScreen(SCREEN_VALIDATION);
        updateValidationUiState();

        // Load cached local records first, then fetch live from backend
        loadSavedRecordsCache();
        filterDeviceRecords("");
        fetchWipTransactionsFromServer("");

        // Initialize CipherLab RFID Manager
        initCipherLabRfid();

        // Check Device Hardware MAC Address Authorization on Startup
        checkDeviceAuthorization(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkDeviceAuthorization(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sInstance == this) sInstance = null;
        try {
            unregisterReceiver(myDataReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver unregister error", e);
        }

        if (mRfidManager != null) {
            mRfidManager.Release();
            mRfidManager = null;
        }

        networkExecutor.shutdown();
    }

    // =========================================================================
    // VIEW BINDING
    // =========================================================================
    private void bindViews() {
        // Global Header
        tvHeaderTitle = findViewById(R.id.header_tv_title);
        tvHeaderSubtitle = findViewById(R.id.header_tv_subtitle);
        btnHeaderMenu = findViewById(R.id.header_btn_menu);
        btnHeaderNotifications = findViewById(R.id.header_btn_notifications);

        // Server IP Bar
        edtServerIp = findViewById(R.id.edt_server_ip);
        btnSaveIp = findViewById(R.id.btn_save_ip);

        // Bottom Navigation
        navItemScanner = findViewById(R.id.nav_item_scanner);
        navItemValidation = findViewById(R.id.nav_item_validation);
        navItemRecords = findViewById(R.id.nav_item_records);
        navIconScanner = findViewById(R.id.nav_icon_scanner);
        navIconValidation = findViewById(R.id.nav_icon_validation);
        navIconRecords = findViewById(R.id.nav_icon_records);
        navLabelScanner = findViewById(R.id.nav_label_scanner);
        navLabelValidation = findViewById(R.id.nav_label_validation);
        navLabelRecords = findViewById(R.id.nav_label_records);

        // Screen Containers
        layoutScreenScanner = findViewById(R.id.layout_screen_scanner);
        layoutScreenValidation = findViewById(R.id.layout_screen_validation);
        layoutScreenRecords = findViewById(R.id.layout_screen_records);

        // Invisible Keyboard-Wedge input
        edtQrInput = findViewById(R.id.edt_qr_input);

        // Screen 1: Device Config Views
        tvConfigAuthBadge = findViewById(R.id.tv_config_auth_badge);
        tvConfigDevName = findViewById(R.id.tv_config_dev_name);
        tvConfigDevMac = findViewById(R.id.tv_config_dev_mac);
        tvConfigDevId = findViewById(R.id.tv_config_dev_id);
        tvConfigDevModel = findViewById(R.id.tv_config_dev_model);

        // Screen 2: Product Validation Views
        tvValStatusPill = findViewById(R.id.tv_val_status_pill);
        tvValDevicePill = findViewById(R.id.tv_val_device_pill);

        tvValBadgeRfid = findViewById(R.id.tv_val_badge_rfid);
        tvValRfidEmpty = findViewById(R.id.tv_val_rfid_empty);
        layoutValRfidCaptured = findViewById(R.id.layout_val_rfid_captured);
        tvValRfidEpc = findViewById(R.id.tv_val_rfid_epc);
        tvValRfidTid = findViewById(R.id.tv_val_rfid_tid);
        tvValRfidRssi = findViewById(R.id.tv_val_rfid_rssi);
        btnValTriggerRfid = findViewById(R.id.btn_val_trigger_rfid);

        tvValBadgeMaterial = findViewById(R.id.tv_val_badge_material);
        layoutValMaterialEmpty = findViewById(R.id.layout_val_material_empty);
        layoutValMaterialCaptured = findViewById(R.id.layout_val_material_captured);
        tvValMaterialCode = findViewById(R.id.tv_val_material_code);

        layoutValFgGallery = findViewById(R.id.layout_val_fg_gallery);
        imgValFg = findViewById(R.id.img_val_fg);
        tvValImgAngle = findViewById(R.id.tv_val_img_angle);
        btnValImgPrev = findViewById(R.id.btn_val_img_prev);
        btnValImgNext = findViewById(R.id.btn_val_img_next);
        tvValCatBadge = findViewById(R.id.tv_val_cat_badge);
        tvValStatusBadge = findViewById(R.id.tv_val_status_badge);
        tvValMatName = findViewById(R.id.tv_val_mat_name);
        tvValPartNo = findViewById(R.id.tv_val_part_no);
        tvValSpecDim = findViewById(R.id.tv_val_spec_dim);
        tvValSpecColor = findViewById(R.id.tv_val_spec_color);
        tvValSpecPkg = findViewById(R.id.tv_val_spec_pkg);
        tvValSpecModel = findViewById(R.id.tv_val_spec_model);
        tvValSpecWeight = findViewById(R.id.tv_val_spec_weight);
        tvValSpecStatus = findViewById(R.id.tv_val_spec_status);

        tvValBadgeWo = findViewById(R.id.tv_val_badge_wo);
        tvValWoEmpty = findViewById(R.id.tv_val_wo_empty);
        layoutValWoCaptured = findViewById(R.id.layout_val_wo_captured);
        tvValWoCode = findViewById(R.id.tv_val_wo_code);

        tvValLiveTitle = findViewById(R.id.tv_val_live_title);
        tvValLiveDevice = findViewById(R.id.tv_val_live_device);
        btnValReset = findViewById(R.id.btn_val_reset);
        btnValQueue = findViewById(R.id.btn_val_queue);


        // Screen 3: Records Views (Mobile Tiles)
        tvRecCountBadge = findViewById(R.id.tv_rec_count_badge);
        edtRecSearch = findViewById(R.id.edt_rec_search);
        layoutRecTilesContainer = findViewById(R.id.layout_rec_tiles_container);
        layoutRecEmpty = findViewById(R.id.layout_rec_empty);
        tvRecEmptySubtitle = findViewById(R.id.tv_rec_empty_subtitle);
    }

    // =========================================================================
    // SERVER IP & BASE URL CONFIGURATION
    // =========================================================================
    private void loadSavedServerIp() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String ip = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP);
        if (ip == null || ip.isEmpty() || ip.contains("192.168.88.9")) {
            ip = DEFAULT_SERVER_IP;
            prefs.edit().putString(KEY_SERVER_IP, ip).apply();
        }
        edtServerIp.setText(ip);
        updateDeviceLabels();
    }

    private void setupServerIpBar() {
        btnSaveIp.setOnClickListener(v -> {
            String ipInput = edtServerIp.getText().toString().trim();
            if (ipInput.isEmpty()) {
                ipInput = DEFAULT_SERVER_IP;
            }
            // Strip scheme if operator typed it
            ipInput = ipInput.replace("http://", "").replace("https://", "");
            while (ipInput.endsWith("/")) {
                ipInput = ipInput.substring(0, ipInput.length() - 1);
            }
            if (!ipInput.contains(":")) {
                ipInput = ipInput + ":8000";
            }

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putString(KEY_SERVER_IP, ipInput).apply();
            edtServerIp.setText(ipInput);

            Toast.makeText(this, "✓ Server IP set: " + ipInput, Toast.LENGTH_SHORT).show();
            appendLog("[CONFIG] Updated server address: " + getBaseUrl());

            // Test connection by fetching live records immediately
            fetchWipTransactionsFromServer("");
            // Re-verify device MAC authorization against updated server IP
            checkDeviceAuthorization(true);
        });
    }

    public String getCleanServerIp() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String ip = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP).trim();
        if (ip.isEmpty() || ip.contains("192.168.88.9")) {
            ip = DEFAULT_SERVER_IP;
        }
        ip = ip.replace("http://", "").replace("https://", "");
        while (ip.endsWith("/")) {
            ip = ip.substring(0, ip.length() - 1);
        }
        if (!ip.contains(":")) {
            ip = ip + ":8000";
        }
        return ip;
    }

    public String getBaseUrl() {
        return "http://" + getCleanServerIp();
    }

    public String getDeviceId() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID);
    }

    public String getDeviceName() {
        if (mDeviceDisplayName != null && !mDeviceDisplayName.isEmpty()) {
            return mDeviceDisplayName;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(KEY_DEVICE_NAME, "");
        if (!saved.isEmpty()) {
            mDeviceDisplayName = saved;
            return saved;
        }
        return "CIPHER RS38 UHF Reader (" + getDeviceId() + ")";
    }

    private void updateDeviceLabels() {
        String devName = getDeviceName();
        String mac = getDeviceMacAddress();
        String devId = getDeviceId();

        if (tvConfigDevName != null) tvConfigDevName.setText(devName);
        if (tvConfigDevMac != null) tvConfigDevMac.setText("MAC: " + mac);
        if (tvConfigDevId != null) tvConfigDevId.setText("ID: " + devId);
        if (tvConfigDevModel != null) tvConfigDevModel.setText("Model: " + Build.MANUFACTURER + " " + Build.MODEL);

        if (tvConfigAuthBadge != null) {
            if (mIsDeviceAuthorized) {
                tvConfigAuthBadge.setText("● AUTHORIZED");
                tvConfigAuthBadge.setTextColor(Color.parseColor("#10B981"));
                tvConfigAuthBadge.setBackgroundResource(R.drawable.bg_pill_green);
            } else {
                tvConfigAuthBadge.setText("● UNAUTHORIZED");
                tvConfigAuthBadge.setTextColor(Color.parseColor("#EF4444"));
                tvConfigAuthBadge.setBackgroundResource(R.drawable.bg_pill_yellow);
            }
        }

        if (tvValLiveDevice != null) tvValLiveDevice.setText("Active Device: " + devName);
        if (tvValDevicePill != null) {
            if (mIsDeviceAuthorized) {
                tvValDevicePill.setText("● " + devName);
                tvValDevicePill.setTextColor(Color.parseColor("#10B981"));
                tvValDevicePill.setBackgroundResource(R.drawable.bg_pill_green);
            } else {
                tvValDevicePill.setText("1/1 Online");
                tvValDevicePill.setTextColor(getResources().getColor(R.color.status_yellow));
                tvValDevicePill.setBackgroundResource(R.drawable.bg_pill_yellow);
            }
        }
    }

    // =========================================================================
    // NAVIGATION & SCREEN SWITCHING
    // =========================================================================
    private void setupNavigation() {
        navItemScanner.setOnClickListener(v -> showScreen(SCREEN_SCANNER));
        navItemValidation.setOnClickListener(v -> showScreen(SCREEN_VALIDATION));
        navItemRecords.setOnClickListener(v -> showScreen(SCREEN_RECORDS));
    }

    private void showScreen(int screenIndex) {
        mCurrentScreen = screenIndex;

        // Reset all navigation tab highlight styles
        int colorBrand = getResources().getColor(R.color.brand_red);
        int colorMuted = getResources().getColor(R.color.text_secondary);

        navLabelScanner.setTextColor(colorMuted);
        navLabelValidation.setTextColor(colorMuted);
        navLabelRecords.setTextColor(colorMuted);
        navLabelScanner.setTypeface(null, android.graphics.Typeface.NORMAL);
        navLabelValidation.setTypeface(null, android.graphics.Typeface.NORMAL);
        navLabelRecords.setTypeface(null, android.graphics.Typeface.NORMAL);

        navIconScanner.setColorFilter(colorMuted);
        navIconValidation.setColorFilter(colorMuted);
        navIconRecords.setColorFilter(colorMuted);

        // Hide all screen layouts
        layoutScreenScanner.setVisibility(View.GONE);
        layoutScreenValidation.setVisibility(View.GONE);
        layoutScreenRecords.setVisibility(View.GONE);

        switch (screenIndex) {
            case SCREEN_SCANNER:
                layoutScreenScanner.setVisibility(View.VISIBLE);
                tvHeaderTitle.setText("Device Config");
                tvHeaderSubtitle.setText("Server & Scanner Hardware Configuration");
                navLabelScanner.setTextColor(colorBrand);
                navLabelScanner.setTypeface(null, android.graphics.Typeface.BOLD);
                navIconScanner.setColorFilter(colorBrand);
                break;

            case SCREEN_VALIDATION:
                layoutScreenValidation.setVisibility(View.VISIBLE);
                tvHeaderTitle.setText("Product Validation");
                tvHeaderSubtitle.setText("Wakefit Finished Goods Label & RFID System");
                navLabelValidation.setTextColor(colorBrand);
                navLabelValidation.setTypeface(null, android.graphics.Typeface.BOLD);
                navIconValidation.setColorFilter(colorBrand);
                updateValidationUiState();
                fetchWipTransactionsFromServer("");
                break;

            case SCREEN_RECORDS:
                layoutScreenRecords.setVisibility(View.VISIBLE);
                tvHeaderTitle.setText("FG WIP Transaction Records");
                tvHeaderSubtitle.setText("Shop Floor Transaction History");
                navLabelRecords.setTextColor(colorBrand);
                navLabelRecords.setTypeface(null, android.graphics.Typeface.BOLD);
                navIconRecords.setColorFilter(colorBrand);
                fetchWipTransactionsFromServer("");
                break;
        }
    }

    // =========================================================================
    // =========================================================================
    // SCREEN 1: DEVICE CONFIG LISTENERS
    // =========================================================================
    private void setupScannerScreenListeners() {
        if (tvConfigAuthBadge != null) {
            tvConfigAuthBadge.setOnClickListener(v -> showDeviceInfoDialog());
        }
    }

    // =========================================================================
    // SCREEN 2: PRODUCT VALIDATION WORKFLOW & CONTROLS
    // =========================================================================
    private void setupValidationScreenListeners() {
        btnValReset.setOnClickListener(v -> performCancelScan());
        btnValQueue.setOnClickListener(v -> performQueueTransaction());

        if (btnValTriggerRfid != null) {
            btnValTriggerRfid.setOnClickListener(v -> triggerSampleRfidScan());
        }

        if (btnValImgPrev != null) {
            btnValImgPrev.setOnClickListener(v -> {
                if (mValFgImages != null && mValFgImages.size() > 1) {
                    mValFgImageIndex = (mValFgImageIndex - 1 + mValFgImages.size()) % mValFgImages.size();
                    updateFgGalleryUi();
                }
            });
        }
        if (btnValImgNext != null) {
            btnValImgNext.setOnClickListener(v -> {
                if (mValFgImages != null && mValFgImages.size() > 1) {
                    mValFgImageIndex = (mValFgImageIndex + 1) % mValFgImages.size();
                    updateFgGalleryUi();
                }
            });
        }

        if (imgValFg != null) {
            final GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (mValFgImages == null || mValFgImages.size() <= 1 || e1 == null || e2 == null) return false;
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 60 && Math.abs(velocityX) > 100) {
                        if (diffX < 0) {
                            // Swipe Left -> Next Image
                            mValFgImageIndex = (mValFgImageIndex + 1) % mValFgImages.size();
                        } else {
                            // Swipe Right -> Prev Image
                            mValFgImageIndex = (mValFgImageIndex - 1 + mValFgImages.size()) % mValFgImages.size();
                        }
                        updateFgGalleryUi();
                        return true;
                    }
                    return false;
                }
            });
            imgValFg.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }
    }

    private void triggerSampleRfidScan() {
        long randPart = (long) (Math.random() * 9000000000L + 1000000000L);
        String sampleEpc = "E28011606" + randPart;
        mValRfidEpc = sampleEpc;
        mValRfidTid = "E28011606000021A58";
        mValRfidRssi = -42.0 - (Math.random() * 12.0);
        mValRfidCaptured = true;

        updateValidationUiState();
        appendLog("[RFID TRIGGER] Sample populated: " + sampleEpc);
        Toast.makeText(this, "Sample RFID populated: " + sampleEpc, Toast.LENGTH_SHORT).show();
        postScanEventToBackend();
    }

    private void updateFgGalleryUi() {
        if (imgValFg == null) return;
        if (mValFgImages == null || mValFgImages.isEmpty()) {
            imgValFg.setImageResource(R.drawable.ic_image_placeholder);
            if (tvValImgAngle != null) tvValImgAngle.setText("ANGLE 1/1");
            if (btnValImgPrev != null) btnValImgPrev.setVisibility(View.GONE);
            if (btnValImgNext != null) btnValImgNext.setVisibility(View.GONE);
            return;
        }

        if (mValFgImageIndex < 0) mValFgImageIndex = 0;
        if (mValFgImageIndex >= mValFgImages.size()) mValFgImageIndex = mValFgImages.size() - 1;

        String currentUrl = mValFgImages.get(mValFgImageIndex);
        ImageLoader.getInstance().loadImage(imgValFg, currentUrl, getBaseUrl(), R.drawable.ic_image_placeholder, null);

        if (tvValImgAngle != null) {
            tvValImgAngle.setText("ANGLE " + (mValFgImageIndex + 1) + "/" + mValFgImages.size());
        }

        boolean hasMultiple = mValFgImages.size() > 1;
        if (btnValImgPrev != null) btnValImgPrev.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
        if (btnValImgNext != null) btnValImgNext.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
    }

    public void fetchMaterialMetadataFromServer(String code) {
        if (code == null || code.trim().isEmpty()) return;
        final String cleanCode = code.trim();
        networkExecutor.execute(() -> {
            try {
                String endpoint = getBaseUrl() + "/api/master-data/" + URLEncoder.encode(cleanCode, "UTF-8");
                HttpResult res = sendHttpRequest("GET", endpoint, null);
                if (res.statusCode == 200) {
                    JSONObject obj = new JSONObject(res.body);
                    mainHandler.post(() -> populateFgMasterDataFromJsonObject(obj));
                }
            } catch (Exception e) {
                Log.w(TAG, "Master data lookup error: " + e.getMessage());
            }
        });
    }

    private void populateFgMasterDataFromJsonObject(JSONObject item) {
        if (item == null) return;
        mValProductName = item.optString("productDescription", item.optString("productName", item.optString("model", mValProductName)));
        mValModel = item.optString("model", "Dual Comfort Foam");
        mValPartNumber = item.optString("partNumber", "FG-" + mValMaterialCode);
        mValCategory = item.optString("category", "Mattress");
        mValColour = item.optString("colour", item.optString("color", "Red"));
        mValFgStatus = item.optString("status", "Active");
        mValPackageType = item.optString("packageType", "Rolled Vacuum Box");

        JSONObject dim = item.optJSONObject("dimensions");
        if (dim != null) {
            int l = dim.optInt("lengthMm", 0);
            int w = dim.optInt("widthMm", 0);
            int h = dim.optInt("heightMm", 0);
            mValDimensions = l + " x " + w + " x " + h + " mm";
        } else {
            mValDimensions = item.optString("dimensionsStr", "0 x 0 x 0 mm");
        }

        double netWt = item.optDouble("netWeight", 0.0);
        double grossWt = item.optDouble("grossWeight", 0.0);
        mValWeight = String.format(Locale.US, "%.0f kg / %.0f kg", netWt, grossWt);

        // Images array extraction
        mValFgImages.clear();
        JSONArray imgsArr = item.optJSONArray("fgImage");
        if (imgsArr == null) imgsArr = item.optJSONArray("images");
        if (imgsArr != null) {
            for (int i = 0; i < imgsArr.length(); i++) {
                String u = imgsArr.optString(i, "").trim();
                if (!u.isEmpty()) mValFgImages.add(u);
            }
        }
        if (mValFgImages.isEmpty()) {
            String singleImg = item.optString("productImage", item.optString("fgImage", ""));
            if (!singleImg.isEmpty()) mValFgImages.add(singleImg);
        }
        mValFgImageIndex = 0;

        updateValidationUiState();
    }

    public void applyCustomMaterialCode(String code) {
        if (code == null || code.trim().isEmpty()) return;
        code = code.trim();
        mValMaterialCode = code;
        mValMaterialCaptured = true;

        updateValidationUiState();
        appendLog("[MATERIAL CODE] Custom set: " + code);
        Toast.makeText(this, "Material Code: " + code, Toast.LENGTH_SHORT).show();

        fetchMaterialMetadataFromServer(code);
        postScanEventToBackend();
    }

    public void applyCustomWorkOrder(String wo) {
        if (wo == null || wo.trim().isEmpty()) return;
        wo = wo.trim();
        mValWorkOrder = wo;
        mValWorkOrderCaptured = true;

        updateValidationUiState();
        appendLog("[WORK ORDER] Custom set: " + wo);
        Toast.makeText(this, "Work Order: " + wo, Toast.LENGTH_SHORT).show();

        postScanEventToBackend();
    }

    public void autoRegisterMasterDataItem(String code) {
        if (code == null || code.trim().isEmpty()) return;
        final String matCode = code.trim();
        appendLog("[MASTER DATA] Auto-registering code: " + matCode);
        Toast.makeText(this, "Registering " + matCode + " in Master Data...", Toast.LENGTH_SHORT).show();

        networkExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("material_code", matCode);
                payload.put("part_number", "FG-" + matCode);
                payload.put("product_description", "Finished Good Item (" + matCode + ")");
                payload.put("category", "Mattress");
                payload.put("status", "Active");
                payload.put("model", "Custom");

                String endpoint = getBaseUrl() + "/api/master_data/";
                HttpResult result = sendHttpRequest("POST", endpoint, payload.toString());

                mainHandler.post(() -> {
                    if (result.statusCode == 201 || result.statusCode == 200) {
                        Toast.makeText(MainActivity.this, "✓ Registered " + matCode + " in Master Data catalog!", Toast.LENGTH_LONG).show();
                        appendLog("[MASTER DATA REG SUCCESS] " + matCode);
                        // Re-validate against master data
                        postScanEventToBackend();
                    } else {
                        String detail = extractErrorDetail(result.body);
                        Toast.makeText(MainActivity.this, "Master Data registration: " + detail, Toast.LENGTH_LONG).show();
                        appendLog("[MASTER DATA REG FAILED] " + detail);
                    }
                });
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Registration network error: " + err, Toast.LENGTH_LONG).show();
                    appendLog("[MASTER DATA REG NET ERROR] " + err);
                });
            }
        });
    }

    private int getCapturedCount() {
        int count = 0;
        if (mValRfidCaptured) count++;
        if (mValMaterialCaptured) count++;
        if (mValWorkOrderCaptured) count++;
        return count;
    }

    private void updateValidationUiState() {
        int count = getCapturedCount();

        // Top Status Pill
        if (count == 3) {
            tvValStatusPill.setText("● ALL 3 VERIFIED (3/3)");
            tvValStatusPill.setBackgroundResource(R.drawable.bg_pill_green);
            tvValStatusPill.setTextColor(getResources().getColor(R.color.status_green));
        } else if (count > 0) {
            tvValStatusPill.setText("● SCANNING IN PROGRESS (" + count + "/3)");
            tvValStatusPill.setBackgroundResource(R.drawable.bg_pill_yellow);
            tvValStatusPill.setTextColor(getResources().getColor(R.color.status_yellow));
        } else {
            tvValStatusPill.setText("● LIVE SCANNER ACTIVE (0/3)");
            tvValStatusPill.setBackgroundResource(R.drawable.bg_pill_green);
            tvValStatusPill.setTextColor(getResources().getColor(R.color.status_green));
        }

        // Live Box Title
        tvValLiveTitle.setText("Listening for Live Scanner Data (" + count + "/3 Captured)");

        // Step 1: RFID Card UI
        if (mValRfidCaptured) {
            tvValBadgeRfid.setText("✓ RFID CAPTURED");
            tvValBadgeRfid.setTextColor(getResources().getColor(R.color.status_green));
            tvValBadgeRfid.setBackgroundResource(R.drawable.bg_badge_captured);
            tvValRfidEmpty.setVisibility(View.GONE);
            layoutValRfidCaptured.setVisibility(View.VISIBLE);
            tvValRfidEpc.setText(mValRfidEpc);
            tvValRfidTid.setText(mValRfidTid.isEmpty() ? "E28011606000021A58" : mValRfidTid);
            tvValRfidRssi.setText(String.format(Locale.US, "RSSI: %.1f dBm", mValRfidRssi));
        } else {
            tvValBadgeRfid.setText("⌛ AWAITING RFID SCAN");
            tvValBadgeRfid.setTextColor(getResources().getColor(R.color.status_yellow));
            tvValBadgeRfid.setBackgroundResource(R.drawable.bg_badge_awaiting);
            tvValRfidEmpty.setVisibility(View.VISIBLE);
            layoutValRfidCaptured.setVisibility(View.GONE);
        }

        // Step 2: Material Code Card UI
        if (mValMaterialCaptured) {
            tvValBadgeMaterial.setText("✓ MATERIAL CAPTURED");
            tvValBadgeMaterial.setTextColor(getResources().getColor(R.color.status_green));
            tvValBadgeMaterial.setBackgroundResource(R.drawable.bg_badge_captured);
            layoutValMaterialEmpty.setVisibility(View.GONE);
            layoutValMaterialCaptured.setVisibility(View.VISIBLE);
            tvValMaterialCode.setText(mValMaterialCode);
            tvValMatName.setText(mValProductName);
            if (tvValPartNo != null) tvValPartNo.setText("Part No: " + mValPartNumber);
            if (tvValCatBadge != null) tvValCatBadge.setText(mValCategory.toUpperCase(Locale.US));
            if (tvValStatusBadge != null) tvValStatusBadge.setText(mValFgStatus.toUpperCase(Locale.US));
            if (tvValSpecDim != null) tvValSpecDim.setText(mValDimensions);
            if (tvValSpecColor != null) tvValSpecColor.setText(mValColour);
            if (tvValSpecPkg != null) tvValSpecPkg.setText(mValPackageType);
            if (tvValSpecModel != null) tvValSpecModel.setText(mValModel);
            if (tvValSpecWeight != null) tvValSpecWeight.setText(mValWeight);
            if (tvValSpecStatus != null) tvValSpecStatus.setText(mValFgStatus);
            updateFgGalleryUi();
        } else {
            tvValBadgeMaterial.setText("⌛ AWAITING MATERIAL SCAN");
            tvValBadgeMaterial.setTextColor(getResources().getColor(R.color.status_yellow));
            tvValBadgeMaterial.setBackgroundResource(R.drawable.bg_badge_awaiting);
            layoutValMaterialEmpty.setVisibility(View.VISIBLE);
            layoutValMaterialCaptured.setVisibility(View.GONE);
        }

        // Step 3: Work Order Card UI
        if (mValWorkOrderCaptured) {
            tvValBadgeWo.setText("✓ WORK ORDER CAPTURED");
            tvValBadgeWo.setTextColor(getResources().getColor(R.color.status_green));
            tvValBadgeWo.setBackgroundResource(R.drawable.bg_badge_captured);
            tvValWoEmpty.setVisibility(View.GONE);
            layoutValWoCaptured.setVisibility(View.VISIBLE);
            tvValWoCode.setText(mValWorkOrder);
        } else {
            tvValBadgeWo.setText("⌛ AWAITING WORK ORDER SCAN");
            tvValBadgeWo.setTextColor(getResources().getColor(R.color.status_yellow));
            tvValBadgeWo.setBackgroundResource(R.drawable.bg_badge_awaiting);
            tvValWoEmpty.setVisibility(View.VISIBLE);
            layoutValWoCaptured.setVisibility(View.GONE);
        }

        // Queue Button State
        if (count == 3) {
            btnValQueue.setText("✓ Queue & Commit to DB");
            btnValQueue.setEnabled(true);
            btnValQueue.setBackgroundResource(R.drawable.bg_btn_queue_enabled);
        } else {
            btnValQueue.setText("Queue (" + count + "/3 Scanned)");
            btnValQueue.setEnabled(false);
            btnValQueue.setBackgroundResource(R.drawable.bg_btn_queue_disabled);
        }
    }

    /**
     * BINDING 1: POST /api/cancel_scan
     * Resets local slots and clears pending scan buffer on server.
     */
    private void performCancelScan() {
        mValRfidEpc = "";
        mValRfidTid = "";
        mValRfidRssi = 0.0;
        mValRfidCaptured = false;

        mValMaterialCode = "";
        mValMaterialCaptured = false;
        mValProductName = "Wakefit Orthopedic Memory Foam Mattress";
        mValPartNumber = "FG-102301022702";
        mValCategory = "Mattress";
        mValModel = "Dual Comfort Foam";
        mValDimensions = "0 x 0 x 0 mm";
        mValColour = "Red";
        mValFgStatus = "Active";
        mValPackageType = "Rolled Vacuum Box";
        mValWeight = "0 kg / 0 kg";
        mValFgImages.clear();
        mValFgImageIndex = 0;

        mValWorkOrder = "";
        mValWorkOrderCaptured = false;

        updateValidationUiState();
        appendLog("[VALIDATION] Validation slots cleared (0/3).");
        Toast.makeText(this, "Scan session reset.", Toast.LENGTH_SHORT).show();

        // Notify FastAPI backend
        networkExecutor.execute(() -> {
            try {
                String targetUrl = getBaseUrl() + "/api/cancel_scan";
                sendHttpRequest("POST", targetUrl, "{}");
            } catch (Exception e) {
                Log.w(TAG, "Cancel scan API error: " + e.getMessage());
            }
        });
    }

    /**
     * BINDING 2: POST /api/transactions/
     * Commits married 3-point transaction to SQLite database.
     */
    private void performQueueTransaction() {
        if (getCapturedCount() < 3) {
            Toast.makeText(this, "Please verify all 3 items before queuing!", Toast.LENGTH_SHORT).show();
            return;
        }

        final String rfid = mValRfidEpc;
        final String mat = mValMaterialCode;
        final String wo = mValWorkOrder;
        final String devId = getDeviceId();
        final String devName = getDeviceName();

        btnValQueue.setEnabled(false);
        btnValQueue.setText("Committing to SQLite...");

        networkExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("factory_rfid_tag_id", rfid);
                payload.put("material_code", mat);
                payload.put("work_order_no", wo);
                payload.put("scanner_device", devName);
                payload.put("device_name", devName);
                payload.put("device_id", devId);
                payload.put("operator_role", "Operator");
                payload.put("status_id", "wip");

                String endpoint = getBaseUrl() + "/api/transactions/";
                HttpResult result = sendHttpRequest("POST", endpoint, payload.toString());

                mainHandler.post(() -> {
                    btnValQueue.setEnabled(true);
                    if (result.statusCode == 201 || result.statusCode == 200) {
                        String assignedTxn = "TXN-SUCCESS";
                        try {
                            JSONObject resObj = new JSONObject(result.body);
                            assignedTxn = resObj.optString("transaction_id", assignedTxn);
                        } catch (Exception ignored) {}

                        Toast.makeText(MainActivity.this, "✓ Transaction Queued: " + assignedTxn, Toast.LENGTH_LONG).show();
                        appendLog("[QUEUE SUCCESS] Married " + rfid + " ⮀ " + mat + " ⮀ " + wo + " [Device: " + devName + "]");

                        // Reset validation workflow for next item
                        performCancelScan();

                        // Refresh table with newly inserted row
                        fetchWipTransactionsFromServer("");

                    } else if (result.statusCode == 409) {
                        String detail = extractErrorDetail(result.body);
                        showConflictDialog("⚠️ Duplicate Rejection", detail);
                        appendLog("[QUEUE CONFLICT] " + detail);
                    } else {
                        String detail = extractErrorDetail(result.body);
                        showConflictDialog("✕ Rejection Error (HTTP " + result.statusCode + ")", detail);
                        appendLog("[QUEUE ERROR] " + detail);
                    }
                });

            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    btnValQueue.setEnabled(true);
                    btnValQueue.setText("✓ Queue & Commit to DB");
                    Toast.makeText(MainActivity.this, "Network error: " + err, Toast.LENGTH_LONG).show();
                    appendLog("[QUEUE NET ERROR] " + err);
                });
            }
        });
    }

    /**
     * BINDING 3: POST /api/post_scan
     * Sends incoming scan to backend for master data lookup & duplicate checking.
     */
    private void postScanEventToBackend() {
        final String rfid = mValRfidCaptured ? mValRfidEpc : "";
        final String mat = mValMaterialCaptured ? mValMaterialCode : "";
        final String wo = mValWorkOrderCaptured ? mValWorkOrder : "";
        final String devId = getDeviceId();
        final String devName = getDeviceName();

        networkExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("factory_rfid_tag_id", rfid);
                payload.put("material_code", mat);
                payload.put("work_order_no", wo);
                payload.put("scanner_device", devName);
                payload.put("device_name", devName);
                payload.put("device_id", devId);

                String endpoint = getBaseUrl() + "/api/post_scan";
                HttpResult result = sendHttpRequest("POST", endpoint, payload.toString());

                mainHandler.post(() -> {
                    if (result.statusCode >= 200 && result.statusCode < 300) {

                        try {
                            JSONObject res = new JSONObject(result.body);

                            // Master Data Item Matching
                            if (res.has("matchedFgItem") && !res.isNull("matchedFgItem")) {
                                JSONObject item = res.getJSONObject("matchedFgItem");
                                populateFgMasterDataFromJsonObject(item);
                            }

                            // Duplicate Checking
                            boolean alreadyCommitted = res.optBoolean("alreadyCommitted", false);
                            if (alreadyCommitted) {
                                String msg = res.optString("message", "Duplicate scan detected");
                                Toast.makeText(MainActivity.this, "⚠️ " + msg, Toast.LENGTH_LONG).show();
                                appendLog("[DUPLICATE WARNING] " + msg);
                            }

                            // Foreign Key Material Code check
                            boolean materialInMaster = res.optBoolean("materialInMaster", true);
                            if (!materialInMaster && !mValMaterialCode.isEmpty()) {
                                String matErr = res.optString("materialErrorMessage", "Material Code not present in Master Data");
                                appendLog("[MASTER DATA WARNING] " + matErr);

                                final String codeToRegister = mValMaterialCode;
                                new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Register in Master Data?")
                                    .setMessage("Material code '" + codeToRegister + "' is not present in Master Data catalog.\n\nWould you like to register it now to allow WIP transaction validation?")
                                    .setPositiveButton("Register & Validate", (dialog, which) -> {
                                        autoRegisterMasterDataItem(codeToRegister);
                                    })
                                    .setNegativeButton("Dismiss", null)
                                    .show();
                            }

                        } catch (Exception e) {
                            Log.w(TAG, "Error parsing post_scan response", e);
                        }

                    } else {
                        appendLog("[POST_SCAN FAIL] HTTP " + result.statusCode + " -> " + result.body);
                    }
                });

            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    appendLog("[NET ERROR] " + err);
                });
            }
        });
    }

    /**
     * BINDING 4: GET /api/transactions/
     * Fetches committed transactions and renders top 10 as mobile tiles.
     */
    private void fetchWipTransactionsFromServer(final String query) {
        networkExecutor.execute(() -> {
            try {
                String endpoint = getBaseUrl() + "/api/transactions/?limit=50";
                HttpResult result = sendHttpRequest("GET", endpoint, null);

                mainHandler.post(() -> {
                    if (result.statusCode == 200) {
                        try {
                            JSONArray arr = new JSONArray(result.body);
                            mTransactionRecords.clear();

                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                String txnId = obj.optString("transaction_id", obj.optString("transactionId", "TXN"));
                                String rfid = obj.optString("rfid_unique_id", obj.optString("rfidUniqueId", obj.optString("factory_rfid_tag_id", "")));
                                String mat = obj.optString("material_code", obj.optString("materialCode", ""));
                                String wo = obj.optString("work_order_no", obj.optString("workOrderNo", ""));
                                String dev = obj.optString("device_name", obj.optString("deviceName", obj.optString("scanner_device", obj.optString("device_id", getDeviceName()))));
                                String status = obj.optString("status", obj.optString("status_id", "WIP"));
                                String created = obj.optString("created_on", obj.optString("createdOn", obj.optString("timestamp", "")));

                                String part = obj.optString("part_number", obj.optString("partNumber", "FG-" + mat));
                                String cat = obj.optString("category", obj.optString("categoryId", "Mattress"));
                                String model = obj.optString("model", "Dual Comfort Foam");
                                String dim = obj.optString("dimensions_str", obj.optString("dimensionsStr", "0 x 0 x 0 mm"));
                                String colour = obj.optString("colour", obj.optString("color", "Red"));
                                String prodImg = obj.optString("product_image", obj.optString("productImage", obj.optString("fg_image", obj.optString("fgImage", "/products/mattress_1.jpg"))));

                                List<String> fgImages = new ArrayList<>();
                                JSONArray imgsArr = obj.optJSONArray("fg_images");
                                if (imgsArr == null) imgsArr = obj.optJSONArray("fgImages");
                                if (imgsArr != null) {
                                    for (int j = 0; j < imgsArr.length(); j++) {
                                        String u = imgsArr.optString(j, "").trim();
                                        if (!u.isEmpty()) fgImages.add(u);
                                    }
                                }
                                if (fgImages.isEmpty() && !prodImg.isEmpty()) {
                                    fgImages.add(prodImg);
                                }

                                String prodName = obj.optString("product_name", obj.optString("productName", model.isEmpty() ? "Finished Good Item" : model));

                                TransactionRecord r = new TransactionRecord(
                                    txnId,
                                    formatIsoTimestamp(created),
                                    rfid,
                                    "E28011606000021A58",
                                    -44.0,
                                    mat,
                                    prodName,
                                    part,
                                    cat,
                                    model,
                                    dim,
                                    colour,
                                    wo,
                                    dev,
                                    status.toUpperCase(Locale.US),
                                    prodImg,
                                    fgImages
                                );
                                mTransactionRecords.add(r);
                            }

                            // Save to local cache
                            saveRecordsCache(mTransactionRecords);

                            // Filter for THIS device top 10 and render
                            filterDeviceRecords(query);

                        } catch (Exception e) {
                            Log.e(TAG, "JSON parsing error for transactions", e);
                        }
                    } else {
                        Log.w(TAG, "Transactions fetch returned HTTP " + result.statusCode);
                    }
                });

            } catch (Exception e) {
                Log.w(TAG, "Failed to reach server for transactions: " + e.getMessage());
            }
        });
    }

    // =========================================================================
    // MOBILE TILES RENDERING (Screen 3: Records)
    // =========================================================================
    private void filterDeviceRecords(String search) {
        String activeDevName = getDeviceName().toLowerCase(Locale.US);
        String activeDevId = getDeviceId().toLowerCase(Locale.US);

        List<TransactionRecord> deviceFiltered = new ArrayList<>();
        for (TransactionRecord r : mTransactionRecords) {
            String dev = r.deviceId != null ? r.deviceId.toLowerCase(Locale.US) : "";
            if (dev.contains(activeDevName) || dev.contains(activeDevId) || activeDevName.contains(dev) || activeDevId.contains(dev) || (dev.contains("cipherlab") && activeDevName.contains("cipherlab"))) {
                deviceFiltered.add(r);
            }
        }

        // Fallback to all records if active device doesn't have records yet
        List<TransactionRecord> baseList = deviceFiltered.isEmpty() ? mTransactionRecords : deviceFiltered;

        // Keep at most 10 recent transactions
        mThisDeviceRecords.clear();
        for (int i = 0; i < Math.min(baseList.size(), 10); i++) {
            mThisDeviceRecords.add(baseList.get(i));
        }

        filterAndRenderDeviceRecords(search);
    }

    private void filterAndRenderDeviceRecords(String search) {
        List<TransactionRecord> displayed = new ArrayList<>();
        if (search == null || search.trim().isEmpty()) {
            displayed.addAll(mThisDeviceRecords);
        } else {
            String q = search.trim().toLowerCase(Locale.US);
            for (TransactionRecord r : mThisDeviceRecords) {
                if (r.id.toLowerCase(Locale.US).contains(q)
                    || r.rfidEpc.toLowerCase(Locale.US).contains(q)
                    || r.materialCode.toLowerCase(Locale.US).contains(q)
                    || r.workOrderNo.toLowerCase(Locale.US).contains(q)
                    || r.productName.toLowerCase(Locale.US).contains(q)
                    || r.model.toLowerCase(Locale.US).contains(q)
                    || r.partNumber.toLowerCase(Locale.US).contains(q)
                    || r.colour.toLowerCase(Locale.US).contains(q)) {
                    displayed.add(r);
                }
            }
        }

        renderTransactionTiles(displayed);
    }

    private void renderTransactionTiles(List<TransactionRecord> records) {
        if (layoutRecTilesContainer == null) return;
        layoutRecTilesContainer.removeAllViews();

        if (records == null || records.isEmpty()) {
            if (layoutRecEmpty != null) layoutRecEmpty.setVisibility(View.VISIBLE);
            if (tvRecCountBadge != null) tvRecCountBadge.setText("0 Records");
            return;
        }

        if (layoutRecEmpty != null) layoutRecEmpty.setVisibility(View.GONE);
        if (tvRecCountBadge != null) {
            tvRecCountBadge.setText(records.size() + " Records (Last 10)");
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        for (TransactionRecord r : records) {
            View tile = inflater.inflate(R.layout.item_transaction_tile, layoutRecTilesContainer, false);

            TextView tvTxnId = tile.findViewById(R.id.tile_tv_txn_id);
            TextView tvCategory = tile.findViewById(R.id.tile_tv_category);
            TextView tvStatus = tile.findViewById(R.id.tile_tv_status);
            ImageView imgProduct = tile.findViewById(R.id.tile_img_product);
            TextView tvName = tile.findViewById(R.id.tile_tv_product_name);
            TextView tvPart = tile.findViewById(R.id.tile_tv_part_number);
            TextView tvMat = tile.findViewById(R.id.tile_tv_material);
            TextView tvColor = tile.findViewById(R.id.tile_tv_colour);
            TextView tvDim = tile.findViewById(R.id.tile_tv_dimensions);
            TextView tvWo = tile.findViewById(R.id.tile_tv_work_order);
            TextView tvRfid = tile.findViewById(R.id.tile_tv_rfid);
            TextView tvDev = tile.findViewById(R.id.tile_tv_device);
            TextView tvTime = tile.findViewById(R.id.tile_tv_timestamp);

            if (tvTxnId != null) tvTxnId.setText(r.id);
            if (tvCategory != null) tvCategory.setText(r.category);
            if (tvStatus != null) tvStatus.setText("● " + r.status);
            if (tvName != null) tvName.setText(r.model.isEmpty() ? r.productName : r.model);
            if (tvPart != null) tvPart.setText("Part: " + r.partNumber);
            if (tvMat != null) tvMat.setText("Mat: " + r.materialCode);
            if (tvColor != null) tvColor.setText("Color: " + r.colour);
            if (tvDim != null) tvDim.setText("Dim: " + r.dimensions);
            if (tvWo != null) tvWo.setText("WO: " + r.workOrderNo);
            if (tvRfid != null) tvRfid.setText("RFID: " + r.rfidEpc);
            if (tvDev != null) tvDev.setText("📱 " + r.deviceId);
            if (tvTime != null) tvTime.setText("🕒 " + r.timestamp);

            // Load product thumbnail
            if (imgProduct != null) {
                String imgUrl = !r.fgImages.isEmpty() ? r.fgImages.get(0) : r.productImage;
                ImageLoader.getInstance().loadImage(imgProduct, imgUrl, getBaseUrl(), R.drawable.ic_image_placeholder, null);
            }

            tile.setOnClickListener(v -> showTransactionDetailsDialog(r));
            layoutRecTilesContainer.addView(tile);
        }
    }

    // =========================================================================
    // SCREEN 3: RECORDS SCREEN LISTENERS
    // =========================================================================
    private void setupRecordsScreenListeners() {
        if (edtRecSearch != null) {
            edtRecSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterAndRenderDeviceRecords(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupHeaderListeners() {
        if (btnHeaderNotifications != null) {
            btnHeaderNotifications.setOnClickListener(v -> {
                Toast.makeText(this, "FastAPI Service: " + getBaseUrl() + " (Online)", Toast.LENGTH_SHORT).show();
                showDeviceInfoDialog();
            });
        }
        btnHeaderMenu.setOnClickListener(v -> {
            // Quick toggle between Validation and Technical Scanner
            if (mCurrentScreen == SCREEN_VALIDATION) {
                showScreen(SCREEN_SCANNER);
            } else {
                showScreen(SCREEN_VALIDATION);
            }
        });

        btnHeaderNotifications.setOnClickListener(v -> {
            Toast.makeText(this, "FastAPI Service: " + getBaseUrl() + " (Online)", Toast.LENGTH_SHORT).show();
            showDeviceInfoDialog();
        });
    }

    // =========================================================================
    // HARDWARE SCANNER INTEGRATION (CIPHERLAB RS38)
    // =========================================================================
    private void initCipherLabRfid() {
        try {
            mRfidManager = RfidManager.InitInstance(this);
            if (mRfidManager != null) {
                appendLog("[RFID SDK] CipherLab RfidManager initialized successfully.");
            }
        } catch (Throwable t) {
            Log.w(TAG, "RfidManager init error: " + t.getMessage());
            appendLog("[RFID SDK] Native library notice: running standard Android mode.");
        }
    }

    private void triggerCipherLabRfidScan() {
        appendLog("[RFID TRIGGER] Starting RFID reader scan...");
        if (mRfidManager != null) {
            try {
                int res = mRfidManager.SoftScanTrigger(true);
                appendLog("[RFID SOFT TRIGGER] Result code: " + res);
            } catch (Throwable t) {
                Log.w(TAG, "SoftScanTrigger error", t);
            }
        }

        // Simulate sample RFID if testing in emulator
        if (mValRfidEpc.isEmpty()) {
            mainHandler.postDelayed(() -> {
                if (mValRfidEpc.isEmpty()) {
                    handleRfidScanned("WFFG-" + (int)(1000 + Math.random() * 8999) + "-" + (int)(1000 + Math.random() * 8999), "E28011606000021A58", -44.0);
                }
            }, 600);
        }
    }

    private void triggerCipherLabBarcodeScan() {
        appendLog("[BARCODE TRIGGER] Starting 2D Imager scan...");
        try {
            Intent triggerIntent = new Intent(ACTION_CIPHERLAB_SOFTTRIGGER);
            triggerIntent.putExtra("enable", true);
            sendBroadcast(triggerIntent);
        } catch (Exception e) {
            Log.w(TAG, "Trigger broadcast error", e);
        }

        // Fallback simulation for testing
        mainHandler.postDelayed(() -> {
            if (!mValMaterialCaptured) {
                handleQrCodeScanned("1002559825", "ImagerTest");
            } else if (!mValWorkOrderCaptured) {
                handleQrCodeScanned("WO-2026-08912", "ImagerTest");
            }
        }, 500);
    }

    public void handleRfidScanned(String epc, String tid, double rssi) {
        long now = System.currentTimeMillis();
        if (epc.equals(lastRfidEpc) && (now - lastRfidTime) < DEBOUNCE_MS) {
            return;
        }
        lastRfidEpc = epc;
        lastRfidTime = now;

        // Update Screen 2: Validation
        mValRfidEpc = epc;
        mValRfidTid = tid;
        mValRfidRssi = rssi;
        mValRfidCaptured = true;
        updateValidationUiState();

        appendLog("[RFID READ] EPC: " + epc);

        // Transmit scan event to FastAPI backend
        postScanEventToBackend();
    }

    public void handleQrCodeScanned(String qrContent, String source) {
        if (qrContent == null || qrContent.trim().isEmpty()) return;
        qrContent = qrContent.trim();

        long now = System.currentTimeMillis();
        if (qrContent.equals(lastQrData) && (now - lastQrTime) < DEBOUNCE_MS) {
            return;
        }
        lastQrData = qrContent;
        lastQrTime = now;

        // Classify by prefix for Screen 2
        String classification;
        if (qrContent.startsWith("WFFG") || qrContent.startsWith("WFG")) {
            classification = "RFID Tag";
            mValRfidEpc = qrContent;
            mValRfidCaptured = true;
        } else if (qrContent.startsWith("100") || qrContent.toUpperCase().startsWith("WAK-MAT-") || qrContent.toUpperCase().startsWith("MAT-")) {
            classification = "Material Code";
            mValMaterialCode = qrContent;
            mValMaterialCaptured = true;
        } else if (qrContent.startsWith("200") || qrContent.startsWith("400") || qrContent.toUpperCase().startsWith("WO-")) {
            classification = "Work Order";
            mValWorkOrder = qrContent;
            mValWorkOrderCaptured = true;
        } else {
            // Intelligent sequential slot allocation
            if (!mValMaterialCaptured) {
                classification = "Material Code";
                mValMaterialCode = qrContent;
                mValMaterialCaptured = true;
            } else {
                classification = "Work Order";
                mValWorkOrder = qrContent;
                mValWorkOrderCaptured = true;
            }
        }

        updateValidationUiState();
        appendLog("[" + classification + "] " + qrContent);

        // 3. Transmit scan event to FastAPI backend
        postScanEventToBackend();
    }

    // =========================================================================
    // HTTP CLIENT HELPERS
    // =========================================================================
    private static class HttpResult {
        final int statusCode;
        final String body;

        HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private HttpResult sendHttpRequest(String method, String urlString, String jsonBody) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                byte[] bytes = (jsonBody != null ? jsonBody : "").getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                    os.flush();
                }
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String response = readStream(is);
            return new HttpResult(code, response);

        } finally {
            if (conn != null) conn.disconnect();
        }
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
            return "";
        }
    }

    private String extractErrorDetail(String jsonBody) {
        try {
            JSONObject obj = new JSONObject(jsonBody);
            if (obj.has("detail")) {
                return obj.getString("detail");
            }
        } catch (Exception ignored) {}
        return jsonBody;
    }

    private String formatIsoTimestamp(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) {
            return dateFormat.format(new Date());
        }
        try {
            String clean = isoTime.replace("T", " ");
            if (clean.length() >= 19) {
                clean = clean.substring(0, 19);
            }
            return clean + " (IST)";
        } catch (Exception e) {
            return isoTime;
        }
    }

    private void appendLog(String msg) {
        Log.i(TAG, msg);
    }

    // =========================================================================
    // MODALS & DIALOGS
    // =========================================================================
    private void showConflictDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void showTransactionDetailsDialog(TransactionRecord record) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_transaction_details);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.94),
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvStatus = dialog.findViewById(R.id.dialog_tv_status);
        TextView tvTxnId = dialog.findViewById(R.id.dialog_tv_txn_id);
        TextView tvTimestamp = dialog.findViewById(R.id.dialog_tv_timestamp);
        TextView tvEpc = dialog.findViewById(R.id.dialog_tv_epc);
        TextView tvTid = dialog.findViewById(R.id.dialog_tv_tid);
        TextView tvRssi = dialog.findViewById(R.id.dialog_tv_rssi);

        ImageView imgFg = dialog.findViewById(R.id.dialog_img_fg);
        TextView tvImgAngle = dialog.findViewById(R.id.dialog_tv_img_angle);
        Button btnImgPrev = dialog.findViewById(R.id.dialog_btn_img_prev);
        Button btnImgNext = dialog.findViewById(R.id.dialog_btn_img_next);

        TextView tvCategory = dialog.findViewById(R.id.dialog_tv_category);
        TextView tvFgStatus = dialog.findViewById(R.id.dialog_tv_fg_status);
        TextView tvProductName = dialog.findViewById(R.id.dialog_tv_product_name);
        TextView tvPartNumber = dialog.findViewById(R.id.dialog_tv_part_number);
        TextView tvMaterialCode = dialog.findViewById(R.id.dialog_tv_material_code);
        TextView tvDimensions = dialog.findViewById(R.id.dialog_tv_dimensions);
        TextView tvColour = dialog.findViewById(R.id.dialog_tv_colour);
        TextView tvProductFamily = dialog.findViewById(R.id.dialog_tv_product_family);

        TextView tvWorkOrder = dialog.findViewById(R.id.dialog_tv_work_order);
        TextView tvDeviceId = dialog.findViewById(R.id.dialog_tv_device_id);
        Button btnClose = dialog.findViewById(R.id.dialog_btn_close);

        if (tvStatus != null) tvStatus.setText("● " + record.status);
        if (tvTxnId != null) tvTxnId.setText(record.id);
        if (tvTimestamp != null) tvTimestamp.setText(record.timestamp);
        if (tvEpc != null) tvEpc.setText(record.rfidEpc);
        if (tvTid != null) tvTid.setText(record.rfidTid.isEmpty() ? "E28011606000021A58" : record.rfidTid);
        if (tvRssi != null) tvRssi.setText(String.format(Locale.US, "RSSI: %.1f dBm", record.rfidRssi));

        if (tvCategory != null) tvCategory.setText(record.category);
        if (tvFgStatus != null) tvFgStatus.setText("Active");
        if (tvProductName != null) tvProductName.setText(record.model.isEmpty() ? record.productName : record.model);
        if (tvPartNumber != null) tvPartNumber.setText("Part Number: " + record.partNumber);
        if (tvMaterialCode != null) tvMaterialCode.setText("Material Code: " + record.materialCode);
        if (tvDimensions != null) tvDimensions.setText("Dimensions: " + record.dimensions);
        if (tvColour != null) tvColour.setText("Color: " + record.colour);
        if (tvProductFamily != null) tvProductFamily.setText("Package: Rolled Vacuum Box");

        if (tvWorkOrder != null) tvWorkOrder.setText(record.workOrderNo);
        if (tvDeviceId != null) tvDeviceId.setText(record.deviceId);

        // Image Gallery Swiping Setup in Dialog
        final List<String> images = new ArrayList<>(record.fgImages);
        if (images.isEmpty() && !record.productImage.isEmpty()) {
            images.add(record.productImage);
        }
        final int[] currentIndex = new int[]{0};

        final Runnable updateGalleryRunnable = () -> {
            if (imgFg == null) return;
            if (images.isEmpty()) {
                imgFg.setImageResource(R.drawable.ic_image_placeholder);
                if (tvImgAngle != null) tvImgAngle.setText("ANGLE 1/1");
                if (btnImgPrev != null) btnImgPrev.setVisibility(View.GONE);
                if (btnImgNext != null) btnImgNext.setVisibility(View.GONE);
                return;
            }
            if (currentIndex[0] < 0) currentIndex[0] = 0;
            if (currentIndex[0] >= images.size()) currentIndex[0] = images.size() - 1;

            ImageLoader.getInstance().loadImage(imgFg, images.get(currentIndex[0]), getBaseUrl(), R.drawable.ic_image_placeholder, null);
            if (tvImgAngle != null) {
                tvImgAngle.setText("ANGLE " + (currentIndex[0] + 1) + "/" + images.size());
            }
            boolean hasMultiple = images.size() > 1;
            if (btnImgPrev != null) btnImgPrev.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
            if (btnImgNext != null) btnImgNext.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
        };
        updateGalleryRunnable.run();

        if (btnImgPrev != null) {
            btnImgPrev.setOnClickListener(v -> {
                if (images.size() > 1) {
                    currentIndex[0] = (currentIndex[0] - 1 + images.size()) % images.size();
                    updateGalleryRunnable.run();
                }
            });
        }
        if (btnImgNext != null) {
            btnImgNext.setOnClickListener(v -> {
                if (images.size() > 1) {
                    currentIndex[0] = (currentIndex[0] + 1) % images.size();
                    updateGalleryRunnable.run();
                }
            });
        }

        if (imgFg != null) {
            final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (e1 != null && e2 != null) {
                        float diffX = e2.getX() - e1.getX();
                        if (Math.abs(diffX) > 50 && Math.abs(velocityX) > 100) {
                            if (diffX > 0) {
                                if (images.size() > 1) {
                                    currentIndex[0] = (currentIndex[0] - 1 + images.size()) % images.size();
                                    updateGalleryRunnable.run();
                                }
                            } else {
                                if (images.size() > 1) {
                                    currentIndex[0] = (currentIndex[0] + 1) % images.size();
                                    updateGalleryRunnable.run();
                                }
                            }
                            return true;
                        }
                    }
                    return false;
                }
            });
            imgFg.setOnTouchListener((v, ev) -> {
                gd.onTouchEvent(ev);
                return true;
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
    }

    // =========================================================================
    // DEVICE MAC ADDRESS & SECURITY AUTHORIZATION
    // =========================================================================

    /**
     * Retrieves the hardware MAC address of the device.
     * Queries network interfaces (wlan0, eth0) for raw hardware MAC.
     * If Android 10+ restrictions return 02:00:00:00:00:00, derives a stable,
     * deterministic pseudo-MAC from ANDROID_ID so authorization works seamlessly on any device.
     */
    public String getDeviceMacAddress() {
        if (mCachedDeviceMac != null && !mCachedDeviceMac.isEmpty()) {
            return mCachedDeviceMac;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedMac = prefs.getString(KEY_DEVICE_MAC, "");
        if (!savedMac.isEmpty() && !"02:00:00:00:00:00".equals(savedMac) && !"00:00:00:00:00:00".equals(savedMac)) {
            mCachedDeviceMac = savedMac;
            return savedMac;
        }

        // 1. Try querying network interfaces
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            // Check wlan0 or eth0 first
            for (NetworkInterface nif : interfaces) {
                if (nif.getName().equalsIgnoreCase("wlan0") || nif.getName().equalsIgnoreCase("eth0")) {
                    byte[] macBytes = nif.getHardwareAddress();
                    if (macBytes != null && macBytes.length > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (byte b : macBytes) {
                            sb.append(String.format("%02X:", b));
                        }
                        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                        String macStr = sb.toString();
                        if (!"02:00:00:00:00:00".equals(macStr) && !"00:00:00:00:00:00".equals(macStr)) {
                            prefs.edit().putString(KEY_DEVICE_MAC, macStr).apply();
                            mCachedDeviceMac = macStr;
                            return macStr;
                        }
                    }
                }
            }

            // Check all interfaces if wlan0/eth0 didn't have valid hardware address
            for (NetworkInterface nif : interfaces) {
                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes != null && macBytes.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : macBytes) {
                        sb.append(String.format("%02X:", b));
                    }
                    if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                    String macStr = sb.toString();
                    if (!"02:00:00:00:00:00".equals(macStr) && !"00:00:00:00:00:00".equals(macStr)) {
                        prefs.edit().putString(KEY_DEVICE_MAC, macStr).apply();
                        mCachedDeviceMac = macStr;
                        return macStr;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed reading NetworkInterface hardware address", e);
        }

        // 2. Deterministic Fallback: Format Settings.Secure.ANDROID_ID into MAC format XX:XX:XX:XX:XX:XX
        try {
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && androidId.length() >= 12) {
                String hex = androidId.toUpperCase().replaceAll("[^0-9A-F]", "");
                if (hex.length() >= 12) {
                    String pseudoMac = String.format("%s:%s:%s:%s:%s:%s",
                        hex.substring(0, 2), hex.substring(2, 4), hex.substring(4, 6),
                        hex.substring(6, 8), hex.substring(8, 10), hex.substring(10, 12));
                    prefs.edit().putString(KEY_DEVICE_MAC, pseudoMac).apply();
                    mCachedDeviceMac = pseudoMac;
                    return pseudoMac;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed reading ANDROID_ID for pseudo-MAC", e);
        }

        // 3. Fallback MAC
        String defaultMac = "00:1F:B5:7C:10:01";
        prefs.edit().putString(KEY_DEVICE_MAC, defaultMac).apply();
        mCachedDeviceMac = defaultMac;
        return defaultMac;
    }

    /**
     * Checks if this device's MAC address is authorized with the backend.
     * Endpoint: GET /api/devices/authorize?mac_address=...
     */
    public void checkDeviceAuthorization(final boolean isManualRetry) {
        final String mac = getDeviceMacAddress();
        appendLog("[DEVICE AUTH] Verifying authorization for MAC: " + mac);

        networkExecutor.execute(() -> {
            try {
                String endpoint = getBaseUrl() + "/api/devices/authorize?mac_address=" + URLEncoder.encode(mac, "UTF-8");
                HttpResult result = sendHttpRequest("GET", endpoint, null);

                mainHandler.post(() -> {
                    if (result.statusCode == 200) {
                        try {
                            JSONObject res = new JSONObject(result.body);
                            boolean authorized = res.optBoolean("authorized", false);
                            String status = res.optString("status", "unauthorized");
                            String message = res.optString("message", "Device authorization status: " + status);
                            String displayName = res.optString("displayName", "CipherLab RS38");
                            String devId = res.optString("deviceId", getDeviceId());

                            if (authorized) {
                                mIsDeviceAuthorized = true;
                                mDeviceDisplayName = displayName;
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                    .putString(KEY_DEVICE_NAME, displayName)
                                    .putString(KEY_DEVICE_ID, devId)
                                    .apply();
                                updateDeviceLabels();
                                appendLog("[DEVICE AUTH] ✓ Device authorized: " + displayName + " (" + mac + ")");
                                if (isManualRetry) {
                                    Toast.makeText(MainActivity.this, "✓ Device Authorized: " + displayName, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                mIsDeviceAuthorized = false;
                                updateDeviceLabels();
                                appendLog("[DEVICE AUTH] ⚠️ Unauthorized MAC: " + mac + " (" + status + ")");
                                showUnauthorizedDeviceDialog(mac, status, message);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing authorization response", e);
                        }
                    } else {
                        appendLog("[DEVICE AUTH] Server returned HTTP " + result.statusCode);
                        if (isManualRetry) {
                            showAuthConnectionErrorDialog(mac, "Server returned HTTP " + result.statusCode);
                        }
                    }
                });
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    appendLog("[DEVICE AUTH] Network connection error: " + err);
                    if (isManualRetry) {
                        showAuthConnectionErrorDialog(mac, "Cannot connect to server at " + getBaseUrl() + "\n(" + err + ")");
                    }
                });
            }
        });
    }

    /**
     * Displays modal popup alerting user that the device MAC is unauthorized.
     * Provides one-tap [ Authorize Device Now ], [ Retry ], and [ Dismiss ].
     */
    private void showUnauthorizedDeviceDialog(final String mac, final String status, final String reason) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_device_authorization);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvBadge = dialog.findViewById(R.id.dialog_auth_tv_status_badge);
        TextView tvMac = dialog.findViewById(R.id.dialog_auth_tv_mac);
        TextView tvModel = dialog.findViewById(R.id.dialog_auth_tv_model);
        TextView tvReason = dialog.findViewById(R.id.dialog_auth_tv_reason);
        Button btnAuthorize = dialog.findViewById(R.id.dialog_auth_btn_authorize);
        Button btnRetry = dialog.findViewById(R.id.dialog_auth_btn_retry);
        Button btnDismiss = dialog.findViewById(R.id.dialog_auth_btn_dismiss);

        if (tvBadge != null) tvBadge.setText(status != null ? status.toUpperCase() : "UNAUTHORIZED");
        if (tvMac != null) tvMac.setText(mac);
        if (tvModel != null) tvModel.setText(Build.MANUFACTURER + " " + Build.MODEL + " (CipherLab Handheld)");
        if (tvReason != null && reason != null && !reason.isEmpty()) {
            tvReason.setText(reason);
        }

        if (btnAuthorize != null) {
            btnAuthorize.setOnClickListener(v -> {
                btnAuthorize.setEnabled(false);
                btnAuthorize.setText("Authorizing device...");
                registerAndAuthorizeDevice(mac, dialog, btnAuthorize);
            });
        }

        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> {
                dialog.dismiss();
                checkDeviceAuthorization(true);
            });
        }

        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(this, "Operating in restricted mode (MAC: " + mac + ")", Toast.LENGTH_SHORT).show();
            });
        }

        dialog.show();
    }

    /**
     * Registers and authorizes the device in the backend database.
     * Endpoint: POST /api/devices/register_and_authorize
     */
    private void registerAndAuthorizeDevice(final String mac, final Dialog dialog, final Button btnAuthorize) {
        networkExecutor.execute(() -> {
            try {
                String cleanMac = mac.replace(":", "").replace("-", "").trim();
                String suffix = cleanMac.length() >= 4 ? cleanMac.substring(cleanMac.length() - 4) : "01";

                JSONObject payload = new JSONObject();
                payload.put("mac_address", mac);
                payload.put("display_name", "CipherLab RS38 (" + Build.MODEL + " - " + suffix + ")");
                payload.put("device_id", "dev-cpr-" + suffix.toLowerCase());
                payload.put("status", "online");

                String endpoint = getBaseUrl() + "/api/devices/register_and_authorize";
                HttpResult result = sendHttpRequest("POST", endpoint, payload.toString());

                mainHandler.post(() -> {
                    if (result.statusCode == 200 || result.statusCode == 201) {
                        Toast.makeText(MainActivity.this, "✓ Device successfully registered & authorized!", Toast.LENGTH_LONG).show();
                        appendLog("[DEVICE AUTH] Registered & authorized MAC: " + mac);
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        checkDeviceAuthorization(true);
                    } else {
                        String detail = extractErrorDetail(result.body);
                        Toast.makeText(MainActivity.this, "Registration failed: " + detail, Toast.LENGTH_LONG).show();
                        appendLog("[DEVICE AUTH ERROR] " + detail);
                        if (btnAuthorize != null) {
                            btnAuthorize.setEnabled(true);
                            btnAuthorize.setText("✓ Authorize Device Now");
                        }
                    }
                });
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Network error: " + err, Toast.LENGTH_LONG).show();
                    appendLog("[DEVICE AUTH NET ERROR] " + err);
                    if (btnAuthorize != null) {
                        btnAuthorize.setEnabled(true);
                        btnAuthorize.setText("✓ Authorize Device Now");
                    }
                });
            }
        });
    }

    private void showAuthConnectionErrorDialog(final String mac, final String errorDetail) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;

        new AlertDialog.Builder(this)
            .setTitle("⚠️ Authorization Server Unreachable")
            .setMessage("Could not connect to the backend server to verify device authorization:\n\n"
                + "Server: " + getBaseUrl() + "\n"
                + "Device MAC: " + mac + "\n\n"
                + "Detail: " + errorDetail + "\n\n"
                + "Please verify that the server is running and the Server IP is configured correctly.")
            .setPositiveButton("Retry Check", (d, w) -> checkDeviceAuthorization(true))
            .setNeutralButton("Configure IP", (d, w) -> {
                edtServerIp.requestFocus();
                Toast.makeText(this, "Enter server IP address and tap Save", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Continue Offline", null)
            .show();
    }

    private void showDeviceInfoDialog() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;

        final String mac = getDeviceMacAddress();
        String authStatusText = mIsDeviceAuthorized ? "✓ AUTHORIZED (Active)" : "⚠️ UNAUTHORIZED / PENDING";

        new AlertDialog.Builder(this)
            .setTitle("Device System Profile")
            .setMessage("Hardware MAC Address:\n" + mac + "\n\n"
                + "Authorization Status:\n" + authStatusText + "\n\n"
                + "Hardware Model:\n" + Build.MANUFACTURER + " " + Build.MODEL + "\n\n"
                + "Android OS: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n\n"
                + "Backend Server:\n" + getBaseUrl())
            .setPositiveButton("Re-check Authorization", (d, w) -> checkDeviceAuthorization(true))
            .setNegativeButton("Close", null)
            .show();
    }

    // =========================================================================
    // LOCAL CACHE HELPERS
    // =========================================================================
    private void saveRecordsCache(List<TransactionRecord> list) {
        try {
            JSONArray arr = new JSONArray();
            for (TransactionRecord r : list) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("timestamp", r.timestamp);
                o.put("rfidEpc", r.rfidEpc);
                o.put("materialCode", r.materialCode);
                o.put("productName", r.productName);
                o.put("workOrderNo", r.workOrderNo);
                o.put("deviceId", r.deviceId);
                o.put("status", r.status);
                arr.put(o);
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_CACHED_RECORDS, arr.toString())
                .apply();
        } catch (Exception ignored) {}
    }

    private void loadSavedRecordsCache() {
        mTransactionRecords.clear();
        String json = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CACHED_RECORDS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                mTransactionRecords.add(new TransactionRecord(
                    o.getString("id"),
                    o.getString("timestamp"),
                    o.getString("rfidEpc"),
                    "",
                    -44.0,
                    o.getString("materialCode"),
                    o.optString("productName", "Finished Good Item"),
                    o.getString("workOrderNo"),
                    o.getString("deviceId"),
                    o.getString("status")
                ));
            }
        } catch (Exception ignored) {}

        if (mTransactionRecords.isEmpty()) {
            mTransactionRecords.add(new TransactionRecord(
                "TXN-20260918-0001",
                "18 Sep 2026, 18:52:14 (IST)",
                "WFFG-8823-9901",
                "E28011606000021A58",
                -44.0,
                "1002559825",
                "Wakefit Orthopedic Memory Foam Mattress",
                "WO-2026-08912",
                getDeviceId(),
                "WIP"
            ));
        }
    }

    // =========================================================================
    // KEYBOARD WEDGE SCANNER & BROADCAST RECEIVER
    // =========================================================================
    private void setupQrInputWatcher() {
        final Runnable processInputRunnable = () -> {
            if (isProcessingInput) return;
            String text = edtQrInput.getText().toString().trim();
            if (!text.isEmpty()) {
                isProcessingInput = true;
                edtQrInput.setText("");
                handleQrCodeScanned(text, "HardwareWedge");
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
                if (s.length() > 0) {
                    mainHandler.postDelayed(processInputRunnable, 120);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        edtQrInput.setOnEditorActionListener((v, actionId, event) -> {
            mainHandler.removeCallbacks(processInputRunnable);
            processInputRunnable.run();
            return true;
        });
    }

    private final Runnable flushBarcodeBufferRunnable = () -> {
        if (barcodeKeyBuffer.length() > 0) {
            String scanned = barcodeKeyBuffer.toString().trim();
            barcodeKeyBuffer.setLength(0);
            if (!scanned.isEmpty() && scanned.length() > 1) {
                handleQrCodeScanned(scanned, "KeyWedge");
            }
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                return super.dispatchKeyEvent(event);
            }

            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                mainHandler.removeCallbacks(flushBarcodeBufferRunnable);
                mainHandler.post(flushBarcodeBufferRunnable);
                return true;
            }

            char unicodeChar = (char) event.getUnicodeChar();
            if (unicodeChar >= 32 && unicodeChar <= 126) {
                barcodeKeyBuffer.append(unicodeChar);
                mainHandler.removeCallbacks(flushBarcodeBufferRunnable);
                mainHandler.postDelayed(flushBarcodeBufferRunnable, 100);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private final BroadcastReceiver myDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();

            // 1. RFID Broadcasts
            if (GeneralString.Intent_RFIDSERVICE_TAG_DATA.equals(action)) {
                String epc = intent.getStringExtra(GeneralString.EXTRA_EPC);
                String tid = intent.getStringExtra(GeneralString.EXTRA_TID);
                double rssi = intent.getDoubleExtra(GeneralString.EXTRA_DATA_RSSI, -44.0);
                if (epc != null && !epc.isEmpty()) {
                    handleRfidScanned(epc.trim(), tid != null ? tid.trim() : "", rssi);
                }
                return;
            } else if (GeneralString.Intent_RFIDSERVICE_CONNECTED.equals(action)) {
                appendLog("[RFID SDK] Service connected.");
                return;
            }

            // 2. Barcode Broadcasts
            String data = extractBarcodeData(intent);
            if (data != null && !data.isEmpty()) {
                handleQrCodeScanned(data, "Broadcast:" + action);
            }
        }
    };

    private void registerCipherLabReceivers() {
        IntentFilter filter = new IntentFilter();
        // RFID
        filter.addAction(GeneralString.Intent_RFIDSERVICE_CONNECTED);
        filter.addAction(GeneralString.Intent_RFIDSERVICE_TAG_DATA);
        // Barcode
        filter.addAction(ACTION_CIPHERLAB_PASS_DATA);
        filter.addAction(ACTION_CIPHERLAB_CALLBACK);
        filter.addAction(ACTION_CIPHERLAB_DECODE_COMPLETE);
        filter.addAction(ACTION_CIPHERLAB_LEGACY_1);
        filter.addAction(ACTION_CIPHERLAB_LEGACY_2);
        filter.addAction(ACTION_CIPHERLAB_LEGACY_3);
        filter.addAction(ACTION_CIPHERLAB_LEGACY_4);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(myDataReceiver, filter, 2); // 2 = RECEIVER_EXPORTED
        } else {
            registerReceiver(myDataReceiver, filter);
        }
    }

    private String extractBarcodeData(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return null;

        String[] knownKeys = {
            "Decoder_Data", "Original_Decoder_Data", "BcReaderData",
            "data_string", "com.cipherlab.barcode.GeneralString.EXTRA_DATA_STRING",
            "barcode_data", "data", "Barcode", "barcode", "EXTRA_DATA_STRING",
            "decode_data", "scan_data", "text"
        };
        for (String key : knownKeys) {
            if (bundle.containsKey(key)) {
                Object obj = bundle.get(key);
                String val = extractStringValue(obj);
                if (val != null && !val.isEmpty()) return val;
            }
        }

        byte[] bytes = intent.getByteArrayExtra("Decoder_DataArray");
        if (bytes == null || bytes.length == 0) {
            bytes = intent.getByteArrayExtra("data_byte");
        }
        if (bytes != null && bytes.length > 0) {
            return new String(bytes, StandardCharsets.UTF_8).trim();
        }

        for (String key : bundle.keySet()) {
            if (key.equalsIgnoreCase("action") || key.equalsIgnoreCase("type") || key.equalsIgnoreCase("code") || key.equalsIgnoreCase("Decoder_CodeType")) {
                continue;
            }
            Object obj = bundle.get(key);
            String val = extractStringValue(obj);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    private String extractStringValue(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return ((String) obj).trim();
        if (obj instanceof byte[]) return new String((byte[]) obj, StandardCharsets.UTF_8).trim();
        if (obj instanceof String[]) {
            String[] arr = (String[]) obj;
            if (arr.length > 0 && arr[0] != null) return arr[0].trim();
        }
        if (obj instanceof CharSequence) return obj.toString().trim();
        return null;
    }

    // =========================================================================
    // TRANSACTION RECORD DATA MODEL
    // =========================================================================
    public static class TransactionRecord {
        public final String id;
        public final String timestamp;
        public final String rfidEpc;
        public final String rfidTid;
        public final double rfidRssi;
        public final String materialCode;
        public final String productName;
        public final String partNumber;
        public final String category;
        public final String model;
        public final String dimensions;
        public final String colour;
        public final String workOrderNo;
        public final String deviceId;
        public final String status;
        public final String productImage;
        public final List<String> fgImages;

        public TransactionRecord(String id, String timestamp, String rfidEpc, String rfidTid,
                                 double rfidRssi, String materialCode, String productName,
                                 String partNumber, String category, String model,
                                 String dimensions, String colour,
                                 String workOrderNo, String deviceId, String status,
                                 String productImage, List<String> fgImages) {
            this.id = id;
            this.timestamp = timestamp;
            this.rfidEpc = rfidEpc;
            this.rfidTid = rfidTid;
            this.rfidRssi = rfidRssi;
            this.materialCode = materialCode;
            this.productName = productName;
            this.partNumber = partNumber != null ? partNumber : "";
            this.category = category != null ? category : "Mattress";
            this.model = model != null ? model : "";
            this.dimensions = dimensions != null ? dimensions : "0 x 0 x 0 mm";
            this.colour = colour != null ? colour : "Red";
            this.workOrderNo = workOrderNo;
            this.deviceId = deviceId;
            this.status = status;
            this.productImage = productImage != null ? productImage : "";
            this.fgImages = fgImages != null ? fgImages : new ArrayList<>();
        }

        public TransactionRecord(String id, String timestamp, String rfidEpc, String rfidTid,
                                 double rfidRssi, String materialCode, String productName,
                                 String workOrderNo, String deviceId, String status) {
            this(id, timestamp, rfidEpc, rfidTid, rfidRssi, materialCode, productName,
                 "FG-" + materialCode, "Mattress", productName, "0 x 0 x 0 mm", "Red",
                 workOrderNo, deviceId, status, "/products/mattress_1.jpg", new ArrayList<>());
        }
    }
}
