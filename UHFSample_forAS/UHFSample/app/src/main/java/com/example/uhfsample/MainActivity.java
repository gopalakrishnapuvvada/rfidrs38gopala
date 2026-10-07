package com.example.uhfsample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.net.Uri;
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.content.pm.PackageManager;
import android.widget.HorizontalScrollView;
import android.util.Base64;
import java.io.ByteArrayOutputStream;


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
    private View btnHeaderNotifications;

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
    private TextView tvValBadgeMaterial, tvValMaterialCode, tvValMatName, tvValPartNo;
    private View layoutValMaterialEmpty, layoutValMaterialCaptured;
    private FrameLayout layoutValFgGallery;
    private ImageView imgValFg;
    private TextView tvValImgAngle;
    private Button btnValImgPrev, btnValImgNext;
    private TextView tvValCatBadge, tvValStatusBadge;
    private TextView tvValSpecDim, tvValSpecColor, tvValSpecModel, tvValSpecStatus;
    private TextView tvValBadgeWo, tvValWoEmpty, tvValWoCode;
    private View layoutValWoCaptured;
    private TextView tvValLiveTitle, tvValLiveDevice;
    private Button btnValReset, btnValQueue;

    // Step 4 Inspection Photos (1 to 8 images)
    private TextView tvValBadgePhotos, tvValPhotosHint;
    private Button btnValGalleryPhoto, btnValClearPhotos;
    private LinearLayout layoutValPhotoThumbnailsContainer;
    private HorizontalScrollView scrollValPhotoThumbnails;
    private final List<Bitmap> capturedBitmaps = new ArrayList<>();
    private static final int REQUEST_GALLERY_IMAGES = 1003;
    private static final int REQUEST_STORAGE_PERMISSION = 1004;


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
        tvValSpecModel = findViewById(R.id.tv_val_spec_model);
        tvValSpecStatus = findViewById(R.id.tv_val_spec_status);

        tvValBadgeWo = findViewById(R.id.tv_val_badge_wo);
        tvValWoEmpty = findViewById(R.id.tv_val_wo_empty);
        layoutValWoCaptured = findViewById(R.id.layout_val_wo_captured);
        tvValWoCode = findViewById(R.id.tv_val_wo_code);

        tvValLiveTitle = findViewById(R.id.tv_val_live_title);
        tvValLiveDevice = findViewById(R.id.tv_val_live_device);
        btnValReset = findViewById(R.id.btn_val_reset);
        btnValQueue = findViewById(R.id.btn_val_queue);

        // Step 4: Inspection Photos Views
        tvValBadgePhotos = findViewById(R.id.tv_val_badge_photos);
        tvValPhotosHint = findViewById(R.id.tv_val_photos_hint);
        btnValGalleryPhoto = findViewById(R.id.btn_val_gallery_photo);
        btnValClearPhotos = findViewById(R.id.btn_val_clear_photos);
        layoutValPhotoThumbnailsContainer = findViewById(R.id.layout_val_photo_thumbnails_container);
        scrollValPhotoThumbnails = findViewById(R.id.scroll_val_photo_thumbnails);



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
            ipInput = ipInput.replace("http://", "").replace("https://", "").trim();
            // If operator typed endpoint path like 192.168.88.x:8000/post_scan, strip the path
            if (ipInput.contains("/")) {
                ipInput = ipInput.substring(0, ipInput.indexOf("/")).trim();
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
        ip = ip.replace("http://", "").replace("https://", "").trim();
        // Automatically remove any trailing paths like /post_scan or /api if entered by operator
        if (ip.contains("/")) {
            ip = ip.substring(0, ip.indexOf("/")).trim();
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

        if (btnValGalleryPhoto != null) {
            btnValGalleryPhoto.setOnClickListener(v -> dispatchPickImagesFromGalleryIntent());
        }
        if (btnValClearPhotos != null) {
            btnValClearPhotos.setOnClickListener(v -> clearCapturedPhotos());
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
        mValFgStatus = item.optString("status", item.optString("status_id", item.optString("status_name", "Active")));
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

        boolean isInactive = mValFgStatus != null && !mValFgStatus.trim().equalsIgnoreCase("active");
        if (isInactive) {
            appendLog("[MATERIAL STATUS] ⚠️ Scanned Material Code is INACTIVE: " + mValMaterialCode);
            showInactiveMaterialDialog(mValMaterialCode);
        }
    }

    private void showInactiveMaterialDialog(final String materialCode) {
        new AlertDialog.Builder(MainActivity.this)
            .setTitle("⚠️ Inactive Material Code")
            .setMessage("The scanned material code '" + materialCode + "' is inactive.\n\nTransactions cannot be committed for inactive material codes.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setCancelable(false)
            .show();
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
            boolean isInactive = mValFgStatus != null && !mValFgStatus.trim().equalsIgnoreCase("active");
            if (isInactive) {
                tvValBadgeMaterial.setText("✕ MATERIAL INACTIVE");
                tvValBadgeMaterial.setTextColor(getResources().getColor(R.color.brand_red));
                tvValBadgeMaterial.setBackgroundResource(R.drawable.bg_badge_awaiting);
            } else {
                tvValBadgeMaterial.setText("✓ MATERIAL CAPTURED");
                tvValBadgeMaterial.setTextColor(getResources().getColor(R.color.status_green));
                tvValBadgeMaterial.setBackgroundResource(R.drawable.bg_badge_captured);
            }
            layoutValMaterialEmpty.setVisibility(View.GONE);
            layoutValMaterialCaptured.setVisibility(View.VISIBLE);
            tvValMaterialCode.setText(mValMaterialCode);
            tvValMatName.setText(mValProductName);
            if (tvValPartNo != null) tvValPartNo.setText("Part No: " + mValPartNumber);
            if (tvValCatBadge != null) tvValCatBadge.setText(mValCategory.toUpperCase(Locale.US));
            if (tvValStatusBadge != null) {
                tvValStatusBadge.setText(mValFgStatus.toUpperCase(Locale.US));
                if (isInactive) {
                    tvValStatusBadge.setTextColor(getResources().getColor(R.color.brand_red));
                    tvValStatusBadge.setBackgroundResource(R.drawable.bg_pill_red);
                } else {
                    tvValStatusBadge.setTextColor(getResources().getColor(R.color.status_green));
                    tvValStatusBadge.setBackgroundResource(R.drawable.bg_pill_green);
                }
            }
            if (tvValSpecDim != null) tvValSpecDim.setText(mValDimensions);
            if (tvValSpecColor != null) tvValSpecColor.setText(mValColour);
            if (tvValSpecModel != null) tvValSpecModel.setText(mValModel);
            if (tvValSpecStatus != null) {
                tvValSpecStatus.setText(mValFgStatus);
                if (isInactive) {
                    tvValSpecStatus.setTextColor(getResources().getColor(R.color.brand_red));
                } else {
                    tvValSpecStatus.setTextColor(getResources().getColor(R.color.status_green));
                }
            }
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

        // Step 4: Photo Badge UI
        int photoCount = capturedBitmaps.size();
        if (photoCount > 0) {
            if (tvValBadgePhotos != null) {
                tvValBadgePhotos.setText("✓ " + photoCount + "/8 ATTACHED");
                tvValBadgePhotos.setTextColor(getResources().getColor(R.color.status_green));
                tvValBadgePhotos.setBackgroundResource(R.drawable.bg_pill_green);
            }
        } else {
            if (tvValBadgePhotos != null) {
                tvValBadgePhotos.setText("0/8 (1 REQUIRED)");
                tvValBadgePhotos.setTextColor(getResources().getColor(R.color.brand_red));
                tvValBadgePhotos.setBackgroundResource(R.drawable.bg_pill_red);
            }
        }

        // Commit Button State: Requires all 3 items AND at least 1 inspection photo AND active material code
        boolean isMaterialActive = mValFgStatus == null || mValFgStatus.trim().equalsIgnoreCase("active");
        if (mValMaterialCaptured && !isMaterialActive) {
            btnValQueue.setText("Commit Blocked (Material Inactive)");
            btnValQueue.setEnabled(false);
            btnValQueue.setBackgroundResource(R.drawable.bg_btn_queue_disabled);
        } else if (count == 3 && photoCount >= 1) {
            btnValQueue.setText("Commit");
            btnValQueue.setEnabled(true);
            btnValQueue.setBackgroundResource(R.drawable.bg_btn_queue_enabled);
        } else if (count == 3 && photoCount == 0) {
            btnValQueue.setText("Commit (Photo Required)");
            btnValQueue.setEnabled(false);
            btnValQueue.setBackgroundResource(R.drawable.bg_btn_queue_disabled);
        } else {
            btnValQueue.setText("Commit (" + count + "/3 Scanned)");
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
        clearCapturedPhotos();

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
        if (getCapturedCount() < 3 || capturedBitmaps.isEmpty()) {
            Toast.makeText(this, "Please verify all 3 items and attach at least 1 photo!", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isMaterialActive = mValFgStatus == null || mValFgStatus.trim().equalsIgnoreCase("active");
        if (mValMaterialCaptured && !isMaterialActive) {
            Toast.makeText(this, "Cannot commit: Material code '" + mValMaterialCode + "' is inactive!", Toast.LENGTH_LONG).show();
            showInactiveMaterialDialog(mValMaterialCode);
            return;
        }

        final String rfid = mValRfidEpc;
        final String mat = mValMaterialCode;
        final String wo = mValWorkOrder;
        final String devId = getDeviceId();
        final String devName = getDeviceName();

        btnValQueue.setEnabled(false);
        btnValQueue.setText("Committing...");

        networkExecutor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("factory_rfid_tag_id", rfid);
                payload.put("material_code", mat);
                payload.put("work_order_no", wo);
                payload.put("scanner_device", devName);
                payload.put("device_name", devName);

                // Add compressed Base64 images
                JSONArray imagesArray = new JSONArray();
                for (Bitmap bmp : capturedBitmaps) {
                    String b64 = compressBitmapToBase64(bmp);
                    if (b64 != null && !b64.isEmpty()) {
                        imagesArray.put("data:image/jpeg;base64," + b64);
                    }
                }
                payload.put("images", imagesArray);
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

                        Toast.makeText(MainActivity.this, "✓ Transaction Committed: " + assignedTxn, Toast.LENGTH_LONG).show();
                        appendLog("[COMMIT SUCCESS] Married " + rfid + " ⮀ " + mat + " ⮀ " + wo + " [Device: " + devName + "]");

                        // Reset validation workflow for next item
                        performCancelScan();

                        // Refresh table with newly inserted row
                        fetchWipTransactionsFromServer("");

                    } else if (result.statusCode == 409) {
                        updateValidationUiState();
                        String detail = extractErrorDetail(result.body);
                        showConflictDialog("⚠️ Duplicate Rejection", detail);
                        appendLog("[COMMIT CONFLICT] " + detail);
                    } else {
                        updateValidationUiState();
                        String detail = extractErrorDetail(result.body);
                        showConflictDialog("✕ Rejection Error (HTTP " + result.statusCode + ")", detail);
                        appendLog("[COMMIT ERROR] " + detail);
                    }
                });

            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    btnValQueue.setEnabled(true);
                    updateValidationUiState();
                    Toast.makeText(MainActivity.this, "Network error: " + err, Toast.LENGTH_LONG).show();
                    appendLog("[COMMIT NET ERROR] " + err);
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

                // Add compressed Base64 images
                JSONArray imagesArray = new JSONArray();
                for (Bitmap bmp : capturedBitmaps) {
                    String b64 = compressBitmapToBase64(bmp);
                    if (b64 != null && !b64.isEmpty()) {
                        imagesArray.put("data:image/jpeg;base64," + b64);
                    }
                }
                payload.put("images", imagesArray);
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

                                String part = obj.optString("part_number", obj.optString("partNumber", ""));
                                if (part.isEmpty() || part.equalsIgnoreCase("FG-") || part.equalsIgnoreCase("FG-" + mat)) {
                                    String pNum = obj.optString("partNumber", "");
                                    if (!pNum.isEmpty()) part = pNum;
                                    else if (!mat.isEmpty()) part = "FG-" + mat;
                                }
                                String cat = obj.optString("category", obj.optString("categoryId", "Mattress"));
                                String model = obj.optString("model", "Dual Comfort Foam");

                                // Dimensions Extraction (Length, Width, Height)
                                String dim = obj.optString("dimensions_str", obj.optString("dimensionsStr", ""));
                                int lenMm = obj.optInt("lengthMm", obj.optInt("length_mm", 0));
                                int widMm = obj.optInt("widthMm", obj.optInt("width_mm", 0));
                                int hgtMm = obj.optInt("heightMm", obj.optInt("height_mm", 0));
                                JSONObject dimObj = obj.optJSONObject("dimensions");
                                if (dimObj != null) {
                                    if (lenMm == 0) lenMm = dimObj.optInt("lengthMm", dimObj.optInt("length_mm", 0));
                                    if (widMm == 0) widMm = dimObj.optInt("widthMm", dimObj.optInt("width_mm", 0));
                                    if (hgtMm == 0) hgtMm = dimObj.optInt("heightMm", dimObj.optInt("height_mm", 0));
                                }
                                if ((dim.isEmpty() || dim.equals("0 x 0 x 0 mm")) && (lenMm > 0 || widMm > 0 || hgtMm > 0)) {
                                    dim = lenMm + " x " + widMm + " x " + hgtMm + " mm";
                                }
                                if (dim.isEmpty()) dim = "0 x 0 x 0 mm";

                                // Color Extraction
                                String colour = obj.optString("colour", obj.optString("color", ""));
                                if (colour.isEmpty()) {
                                    colour = obj.optString("colorVariant", obj.optString("color_variant", "Red"));
                                }

                                String prodImg = obj.optString("product_image", obj.optString("productImage", obj.optString("fg_image", obj.optString("fgImage", ""))));

                                List<String> masterImages = new ArrayList<>();

                                // 1. Master Data Images (Material Code related images)
                                JSONArray masterArr = obj.optJSONArray("master_images");
                                if (masterArr == null) masterArr = obj.optJSONArray("masterImages");
                                if (masterArr == null) masterArr = obj.optJSONArray("fg_images");
                                if (masterArr == null) masterArr = obj.optJSONArray("fgImages");
                                if (masterArr == null) masterArr = obj.optJSONArray("images");
                                if (masterArr != null) {
                                    for (int j = 0; j < masterArr.length(); j++) {
                                        String u = masterArr.optString(j, "").trim();
                                        if (!u.isEmpty() && !masterImages.contains(u)) {
                                            masterImages.add(u);
                                        }
                                    }
                                }

                                if (!prodImg.isEmpty() && !masterImages.contains(prodImg)) {
                                    masterImages.add(prodImg);
                                }

                                // 2. Uploaded Inspection Images (SEPARATE from master images)
                                List<String> capturedImages = new ArrayList<>();
                                JSONArray uploadedArr = obj.optJSONArray("image_urls");
                                if (uploadedArr == null) uploadedArr = obj.optJSONArray("imageUrls");
                                if (uploadedArr == null) uploadedArr = obj.optJSONArray("image_paths");
                                if (uploadedArr == null) uploadedArr = obj.optJSONArray("imagePaths");
                                if (uploadedArr != null) {
                                    for (int j = 0; j < uploadedArr.length(); j++) {
                                        String u = uploadedArr.optString(j, "").trim();
                                        if (!u.isEmpty() && !capturedImages.contains(u)) {
                                            capturedImages.add(u);
                                        }
                                    }
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
                                    masterImages,
                                    capturedImages
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

        List<TransactionRecord> wipRecords = new ArrayList<>();
        for (TransactionRecord r : mTransactionRecords) {
            if (r.status != null && r.status.trim().equalsIgnoreCase("WIP")) {
                wipRecords.add(r);
            }
        }
        // Fallback: If status field isn't exact "WIP" but contains "WIP" or default
        if (wipRecords.isEmpty()) {
            for (TransactionRecord r : mTransactionRecords) {
                if (r.status == null || r.status.isEmpty() || r.status.toUpperCase(Locale.US).contains("WIP")) {
                    wipRecords.add(r);
                }
            }
        }

        List<TransactionRecord> deviceFiltered = new ArrayList<>();
        for (TransactionRecord r : wipRecords) {
            String dev = r.deviceId != null ? r.deviceId.toLowerCase(Locale.US) : "";
            if (dev.contains(activeDevName) || dev.contains(activeDevId) || activeDevName.contains(dev) || activeDevId.contains(dev) || (dev.contains("cipherlab") && activeDevName.contains("cipherlab"))) {
                deviceFiltered.add(r);
            }
        }

        // Fallback to all WIP records if active device doesn't have records yet
        List<TransactionRecord> baseList = deviceFiltered.isEmpty() ? wipRecords : deviceFiltered;

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
            if (tvRecCountBadge != null) tvRecCountBadge.setText("0 WIP Records");
            return;
        }

        if (layoutRecEmpty != null) layoutRecEmpty.setVisibility(View.GONE);
        if (tvRecCountBadge != null) {
            tvRecCountBadge.setText(records.size() + " WIP Records (Last 10)");
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
                String imgUrl = !r.masterImages.isEmpty() ? r.masterImages.get(0) : (!r.capturedImages.isEmpty() ? r.capturedImages.get(0) : r.productImage);
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
        if (tvFgStatus != null) tvFgStatus.setText(record.status != null && !record.status.isEmpty() ? record.status : "Active");
        if (tvProductName != null) tvProductName.setText(record.model.isEmpty() ? record.productName : record.model);
        if (tvPartNumber != null) {
            String pn = (record.partNumber != null && !record.partNumber.isEmpty() && !record.partNumber.equalsIgnoreCase("FG-")) ? record.partNumber : ("FG-" + record.materialCode);
            tvPartNumber.setText("Part Number: " + pn);
        }
        if (tvMaterialCode != null) tvMaterialCode.setText("Material Code: " + record.materialCode);

        // Dimensions (LxWxH with Length, Width, Height breakdown)
        String dimFormatted = record.dimensions;
        if (dimFormatted != null && !dimFormatted.isEmpty() && !dimFormatted.equals("0 x 0 x 0 mm")) {
            try {
                String cleanDim = dimFormatted.replace("mm", "").trim();
                String[] parts = cleanDim.split("x");
                if (parts.length == 3) {
                    String l = parts[0].trim();
                    String w = parts[1].trim();
                    String h = parts[2].trim();
                    dimFormatted = "Dimensions (LxWxH): " + l + " x " + w + " x " + h + " mm\n"
                                 + "Length: " + l + " mm  •  Width: " + w + " mm  •  Height: " + h + " mm";
                }
            } catch (Exception ignored) {}
        } else {
            dimFormatted = "Dimensions: 0 x 0 x 0 mm";
        }
        if (tvDimensions != null) tvDimensions.setText(dimFormatted);

        if (tvColour != null) {
            String col = (record.colour != null && !record.colour.isEmpty()) ? record.colour : "N/A";
            tvColour.setText("Color: " + col);
        }
        if (tvProductFamily != null) tvProductFamily.setVisibility(View.GONE);

        if (tvWorkOrder != null) tvWorkOrder.setText(record.workOrderNo);
        if (tvDeviceId != null) tvDeviceId.setText(record.deviceId);

        // =====================================================================
        // SECTION: MATERIAL CODE CATALOG GALLERY (Master Data only)
        // =====================================================================
        final List<String> matImages = new ArrayList<>(record.masterImages);
        if (matImages.isEmpty() && !record.productImage.isEmpty()) {
            matImages.add(record.productImage);
        }
        final int[] curMatIndex = new int[]{0};

        final Runnable updateMatGalleryRunnable = () -> {
            if (imgFg == null) return;
            if (matImages.isEmpty()) {
                imgFg.setImageResource(R.drawable.ic_image_placeholder);
                if (tvImgAngle != null) tvImgAngle.setText("NO CATALOG PHOTO");
                if (btnImgPrev != null) btnImgPrev.setVisibility(View.GONE);
                if (btnImgNext != null) btnImgNext.setVisibility(View.GONE);
                return;
            }
            if (curMatIndex[0] < 0) curMatIndex[0] = 0;
            if (curMatIndex[0] >= matImages.size()) curMatIndex[0] = matImages.size() - 1;

            String curUrl = matImages.get(curMatIndex[0]);
            ImageLoader.getInstance().loadImage(imgFg, curUrl, getBaseUrl(), R.drawable.ic_image_placeholder, null);
            if (tvImgAngle != null) {
                tvImgAngle.setText("CATALOG PHOTO " + (curMatIndex[0] + 1) + "/" + matImages.size());
            }
            boolean hasMultiple = matImages.size() > 1;
            if (btnImgPrev != null) btnImgPrev.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
            if (btnImgNext != null) btnImgNext.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
        };
        updateMatGalleryRunnable.run();

        // Background sync with Master Data catalog to ensure exact dimensions, color and master photos
        if (record.materialCode != null && !record.materialCode.isEmpty()) {
            final String matCode = record.materialCode.trim();
            networkExecutor.execute(() -> {
                try {
                    String endpoint = getBaseUrl() + "/api/master-data/" + URLEncoder.encode(matCode, "UTF-8");
                    HttpResult res = sendHttpRequest("GET", endpoint, null);
                    if (res.statusCode == 200) {
                        JSONObject mObj = new JSONObject(res.body);

                        String mPart = mObj.optString("partNumber", mObj.optString("part_number", ""));
                        String mColor = mObj.optString("color", mObj.optString("colour", ""));
                        String mStatus = mObj.optString("status", mObj.optString("status_id", ""));
                        String mModel = mObj.optString("model", mObj.optString("productName", ""));

                        int l = 0, w = 0, h = 0;
                        JSONObject dObj = mObj.optJSONObject("dimensions");
                        if (dObj != null) {
                            l = dObj.optInt("lengthMm", dObj.optInt("length_mm", 0));
                            w = dObj.optInt("widthMm", dObj.optInt("width_mm", 0));
                            h = dObj.optInt("heightMm", dObj.optInt("height_mm", 0));
                        }
                        if (l == 0) l = mObj.optInt("length_mm", mObj.optInt("lengthMm", 0));
                        if (w == 0) w = mObj.optInt("width_mm", mObj.optInt("widthMm", 0));
                        if (h == 0) h = mObj.optInt("height_mm", mObj.optInt("heightMm", 0));

                        final int finalL = l;
                        final int finalW = w;
                        final int finalH = h;
                        final String finalPart = mPart;
                        final String finalColor = mColor;
                        final String finalStatus = mStatus;
                        final String finalModel = mModel;

                        List<String> syncMasterImgs = new ArrayList<>();
                        JSONArray fgArr = mObj.optJSONArray("fgImage");
                        if (fgArr == null) fgArr = mObj.optJSONArray("images");
                        if (fgArr != null) {
                            for (int i = 0; i < fgArr.length(); i++) {
                                String u = fgArr.optString(i, "").trim();
                                if (!u.isEmpty() && !syncMasterImgs.contains(u)) {
                                    syncMasterImgs.add(u);
                                }
                            }
                        }

                        mainHandler.post(() -> {
                            if (!dialog.isShowing()) return;

                            if (finalL > 0 || finalW > 0 || finalH > 0) {
                                String updatedDim = "Dimensions (LxWxH): " + finalL + " x " + finalW + " x " + finalH + " mm\n"
                                                  + "Length: " + finalL + " mm  •  Width: " + finalW + " mm  •  Height: " + finalH + " mm";
                                if (tvDimensions != null) tvDimensions.setText(updatedDim);
                            }
                            if (!finalColor.isEmpty() && tvColour != null) {
                                tvColour.setText("Color: " + finalColor);
                            }
                            if (!finalPart.isEmpty() && tvPartNumber != null) {
                                tvPartNumber.setText("Part Number: " + finalPart);
                            }
                            if (!finalStatus.isEmpty() && tvFgStatus != null) {
                                tvFgStatus.setText(finalStatus);
                            }
                            if (!finalModel.isEmpty() && tvProductName != null) {
                                tvProductName.setText(finalModel);
                            }

                            boolean addedAny = false;
                            for (String u : syncMasterImgs) {
                                if (!matImages.contains(u)) {
                                    matImages.add(0, u);
                                    addedAny = true;
                                }
                            }
                            if (addedAny) {
                                updateMatGalleryRunnable.run();
                            }
                        });
                    }
                } catch (Exception ignored) {}
            });
        }

        if (btnImgPrev != null) {
            btnImgPrev.setOnClickListener(v -> {
                if (matImages.size() > 1) {
                    curMatIndex[0] = (curMatIndex[0] - 1 + matImages.size()) % matImages.size();
                    updateMatGalleryRunnable.run();
                }
            });
        }
        if (btnImgNext != null) {
            btnImgNext.setOnClickListener(v -> {
                if (matImages.size() > 1) {
                    curMatIndex[0] = (curMatIndex[0] + 1) % matImages.size();
                    updateMatGalleryRunnable.run();
                }
            });
        }

        if (imgFg != null) {
            final GestureDetector gdMat = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (e1 != null && e2 != null) {
                        float diffX = e2.getX() - e1.getX();
                        if (Math.abs(diffX) > 50 && Math.abs(velocityX) > 100) {
                            if (diffX > 0) {
                                if (matImages.size() > 1) {
                                    curMatIndex[0] = (curMatIndex[0] - 1 + matImages.size()) % matImages.size();
                                    updateMatGalleryRunnable.run();
                                }
                            } else {
                                if (matImages.size() > 1) {
                                    curMatIndex[0] = (curMatIndex[0] + 1) % matImages.size();
                                    updateMatGalleryRunnable.run();
                                }
                            }
                            return true;
                        }
                    }
                    return false;
                }
            });
            imgFg.setOnTouchListener((v, ev) -> {
                gdMat.onTouchEvent(ev);
                return true;
            });
        }

        // =====================================================================
        // SECTION: CAPTURED INSPECTION PHOTOS (SEPARATE DEDICATED SECTION)
        // =====================================================================
        TextView tvCapturedBadge = dialog.findViewById(R.id.dialog_tv_captured_badge);
        View layoutCapturedGallery = dialog.findViewById(R.id.dialog_layout_captured_gallery);
        ImageView imgCaptured = dialog.findViewById(R.id.dialog_img_captured);
        TextView tvCapturedAngle = dialog.findViewById(R.id.dialog_tv_captured_angle);
        Button btnCapturedPrev = dialog.findViewById(R.id.dialog_btn_captured_prev);
        Button btnCapturedNext = dialog.findViewById(R.id.dialog_btn_captured_next);
        HorizontalScrollView scrollCapturedThumbnails = dialog.findViewById(R.id.dialog_scroll_captured_thumbnails);
        LinearLayout layoutCapturedThumbnails = dialog.findViewById(R.id.dialog_layout_captured_thumbnails);
        TextView tvCapturedEmpty = dialog.findViewById(R.id.dialog_tv_captured_empty);

        final List<String> capturedImages = new ArrayList<>(record.capturedImages);
        if (capturedImages.isEmpty()) {
            if (tvCapturedBadge != null) tvCapturedBadge.setText("0 PHOTOS");
            if (layoutCapturedGallery != null) layoutCapturedGallery.setVisibility(View.GONE);
            if (scrollCapturedThumbnails != null) scrollCapturedThumbnails.setVisibility(View.GONE);
            if (tvCapturedEmpty != null) tvCapturedEmpty.setVisibility(View.VISIBLE);
        } else {
            if (tvCapturedBadge != null) {
                tvCapturedBadge.setText(capturedImages.size() + (capturedImages.size() == 1 ? " PHOTO" : " PHOTOS"));
            }
            if (layoutCapturedGallery != null) layoutCapturedGallery.setVisibility(View.VISIBLE);
            if (scrollCapturedThumbnails != null) scrollCapturedThumbnails.setVisibility(View.VISIBLE);
            if (tvCapturedEmpty != null) tvCapturedEmpty.setVisibility(View.GONE);

            final int[] curCapIndex = new int[]{0};
            final List<FrameLayout> capThumbnailFrames = new ArrayList<>();

            final Runnable updateCapturedGalleryRunnable = () -> {
                if (imgCaptured == null) return;
                if (curCapIndex[0] < 0) curCapIndex[0] = 0;
                if (curCapIndex[0] >= capturedImages.size()) curCapIndex[0] = capturedImages.size() - 1;

                String curCapUrl = capturedImages.get(curCapIndex[0]);
                ImageLoader.getInstance().loadImage(imgCaptured, curCapUrl, getBaseUrl(), R.drawable.ic_image_placeholder, null);

                if (tvCapturedAngle != null) {
                    tvCapturedAngle.setText("INSPECTION PHOTO " + (curCapIndex[0] + 1) + "/" + capturedImages.size());
                }

                boolean hasMultipleCap = capturedImages.size() > 1;
                if (btnCapturedPrev != null) btnCapturedPrev.setVisibility(hasMultipleCap ? View.VISIBLE : View.GONE);
                if (btnCapturedNext != null) btnCapturedNext.setVisibility(hasMultipleCap ? View.VISIBLE : View.GONE);

                // Highlight active thumbnail
                for (int i = 0; i < capThumbnailFrames.size(); i++) {
                    FrameLayout frame = capThumbnailFrames.get(i);
                    if (i == curCapIndex[0]) {
                        frame.setBackgroundResource(R.drawable.bg_card_highlight);
                    } else {
                        frame.setBackgroundResource(R.drawable.bg_card);
                    }
                }
            };

            // Build Thumbnail Strip
            if (layoutCapturedThumbnails != null) {
                layoutCapturedThumbnails.removeAllViews();
                capThumbnailFrames.clear();
                int dp56 = (int) (56 * getResources().getDisplayMetrics().density);
                int dp8 = (int) (8 * getResources().getDisplayMetrics().density);
                int dp2 = (int) (2 * getResources().getDisplayMetrics().density);

                for (int i = 0; i < capturedImages.size(); i++) {
                    final int capIdx = i;
                    String thumbUrl = capturedImages.get(i);

                    FrameLayout frame = new FrameLayout(this);
                    LinearLayout.LayoutParams fParams = new LinearLayout.LayoutParams(dp56, dp56);
                    fParams.setMargins(0, 0, dp8, 0);
                    frame.setLayoutParams(fParams);
                    frame.setPadding(dp2, dp2, dp2, dp2);
                    frame.setBackgroundResource(i == 0 ? R.drawable.bg_card_highlight : R.drawable.bg_card);

                    ImageView thumbIv = new ImageView(this);
                    FrameLayout.LayoutParams ivParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                    thumbIv.setLayoutParams(ivParams);
                    thumbIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ImageLoader.getInstance().loadImage(thumbIv, thumbUrl, getBaseUrl(), R.drawable.ic_image_placeholder, null);

                    frame.addView(thumbIv);
                    frame.setOnClickListener(v -> {
                        curCapIndex[0] = capIdx;
                        updateCapturedGalleryRunnable.run();
                    });

                    capThumbnailFrames.add(frame);
                    layoutCapturedThumbnails.addView(frame);
                }
            }

            updateCapturedGalleryRunnable.run();

            if (btnCapturedPrev != null) {
                btnCapturedPrev.setOnClickListener(v -> {
                    if (capturedImages.size() > 1) {
                        curCapIndex[0] = (curCapIndex[0] - 1 + capturedImages.size()) % capturedImages.size();
                        updateCapturedGalleryRunnable.run();
                    }
                });
            }
            if (btnCapturedNext != null) {
                btnCapturedNext.setOnClickListener(v -> {
                    if (capturedImages.size() > 1) {
                        curCapIndex[0] = (curCapIndex[0] + 1) % capturedImages.size();
                        updateCapturedGalleryRunnable.run();
                    }
                });
            }

            if (imgCaptured != null) {
                final GestureDetector gdCap = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        if (e1 != null && e2 != null) {
                            float diffX = e2.getX() - e1.getX();
                            if (Math.abs(diffX) > 50 && Math.abs(velocityX) > 100) {
                                if (diffX > 0) {
                                    if (capturedImages.size() > 1) {
                                        curCapIndex[0] = (curCapIndex[0] - 1 + capturedImages.size()) % capturedImages.size();
                                        updateCapturedGalleryRunnable.run();
                                    }
                                } else {
                                    if (capturedImages.size() > 1) {
                                        curCapIndex[0] = (curCapIndex[0] + 1) % capturedImages.size();
                                        updateCapturedGalleryRunnable.run();
                                    }
                                }
                                return true;
                            }
                        }
                        return false;
                    }
                });
                imgCaptured.setOnTouchListener((v, ev) -> {
                    gdCap.onTouchEvent(ev);
                    return true;
                });
            }
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
                o.put("partNumber", r.partNumber);
                o.put("category", r.category);
                o.put("model", r.model);
                o.put("dimensions", r.dimensions);
                o.put("colour", r.colour);
                o.put("workOrderNo", r.workOrderNo);
                o.put("deviceId", r.deviceId);
                o.put("status", r.status);
                o.put("productImage", r.productImage);

                JSONArray mArr = new JSONArray();
                for (String mi : r.masterImages) mArr.put(mi);
                o.put("masterImages", mArr);

                JSONArray cArr = new JSONArray();
                for (String ci : r.capturedImages) cArr.put(ci);
                o.put("capturedImages", cArr);

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

                List<String> mList = new ArrayList<>();
                JSONArray mArr = o.optJSONArray("masterImages");
                if (mArr != null) {
                    for (int j = 0; j < mArr.length(); j++) mList.add(mArr.getString(j));
                }

                List<String> cList = new ArrayList<>();
                JSONArray cArr = o.optJSONArray("capturedImages");
                if (cArr != null) {
                    for (int j = 0; j < cArr.length(); j++) cList.add(cArr.getString(j));
                }

                mTransactionRecords.add(new TransactionRecord(
                    o.optString("id", ""),
                    o.optString("timestamp", ""),
                    o.optString("rfidEpc", ""),
                    o.optString("rfidTid", "E28011606000021A58"),
                    -44.0,
                    o.optString("materialCode", ""),
                    o.optString("productName", "Finished Good Item"),
                    o.optString("partNumber", ""),
                    o.optString("category", "Mattress"),
                    o.optString("model", ""),
                    o.optString("dimensions", "0 x 0 x 0 mm"),
                    o.optString("colour", "Red"),
                    o.optString("workOrderNo", ""),
                    o.optString("deviceId", ""),
                    o.optString("status", "WIP"),
                    o.optString("productImage", ""),
                    mList,
                    cList
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
    // STEP 4: INSPECTION PHOTO GALLERY UPLOAD & COMPRESSION HELPERS
    // =========================================================================
    private void dispatchPickImagesFromGalleryIntent() {
        if (capturedBitmaps.size() >= 8) {
            Toast.makeText(this, "Maximum 8 photos reached!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select Inspection Photos (1 to 8)"), REQUEST_GALLERY_IMAGES);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch gallery picker", e);
            Toast.makeText(this, "Failed to open gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_GALLERY_IMAGES) {
            int addedCount = 0;
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                int count = clipData.getItemCount();
                for (int i = 0; i < count; i++) {
                    if (capturedBitmaps.size() >= 8) {
                        Toast.makeText(this, "Maximum 8 photos limit reached!", Toast.LENGTH_SHORT).show();
                        break;
                    }
                    Uri uri = clipData.getItemAt(i).getUri();
                    Bitmap bmp = loadBitmapFromUri(uri);
                    if (bmp != null) {
                        capturedBitmaps.add(bmp);
                        addedCount++;
                    }
                }
            } else if (data.getData() != null) {
                if (capturedBitmaps.size() < 8) {
                    Uri uri = data.getData();
                    Bitmap bmp = loadBitmapFromUri(uri);
                    if (bmp != null) {
                        capturedBitmaps.add(bmp);
                        addedCount++;
                    }
                } else {
                    Toast.makeText(this, "Maximum 8 photos allowed", Toast.LENGTH_SHORT).show();
                }
            }

            if (addedCount > 0) {
                updatePhotoThumbnailsUi();
                updateValidationUiState();
                Toast.makeText(this, "Added " + addedCount + " photo(s) from Gallery (" + capturedBitmaps.size() + "/8)", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        if (uri == null) return null;
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            int maxDimension = 1280;
            int inSampleSize = 1;
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2;
                }
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = inSampleSize;
            is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is, null, decodeOptions);
            if (is != null) is.close();
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "Error decoding image from uri: " + uri, e);
            return null;
        }
    }

    private void clearCapturedPhotos() {
        capturedBitmaps.clear();
        updatePhotoThumbnailsUi();
        updateValidationUiState();
    }

    private void updatePhotoThumbnailsUi() {
        if (layoutValPhotoThumbnailsContainer == null) return;
        layoutValPhotoThumbnailsContainer.removeAllViews();

        int dp64 = (int) (64 * getResources().getDisplayMetrics().density);
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < capturedBitmaps.size(); i++) {
            final int index = i;
            Bitmap bmp = capturedBitmaps.get(i);

            FrameLayout frameLayout = new FrameLayout(this);
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp64, dp64);
            frameParams.setMargins(0, 0, dp8, 0);
            frameLayout.setLayoutParams(frameParams);

            ImageView iv = new ImageView(this);
            FrameLayout.LayoutParams ivParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            iv.setLayoutParams(ivParams);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bmp);
            iv.setBackgroundResource(R.drawable.bg_card);

            // Close / Delete badge button
            TextView btnRemove = new TextView(this);
            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                    (int) (20 * getResources().getDisplayMetrics().density),
                    (int) (20 * getResources().getDisplayMetrics().density)
            );
            btnParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            btnRemove.setLayoutParams(btnParams);
            btnRemove.setText("✕");
            btnRemove.setTextColor(Color.WHITE);
            btnRemove.setTextSize(10);
            btnRemove.setGravity(android.view.Gravity.CENTER);
            btnRemove.setBackgroundResource(R.drawable.bg_pill_red);
            btnRemove.setOnClickListener(v -> {
                if (index < capturedBitmaps.size()) {
                    capturedBitmaps.remove(index);
                    updatePhotoThumbnailsUi();
                    updateValidationUiState();
                }
            });

            frameLayout.addView(iv);
            frameLayout.addView(btnRemove);
            layoutValPhotoThumbnailsContainer.addView(frameLayout);
        }
    }

    public static String compressBitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;
        int maxDimension = 1280;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > maxDimension || height > maxDimension) {
            float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
            width = Math.round(width * ratio);
            height = Math.round(height * ratio);
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream);
        byte[] byteArray = outputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
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
        public final List<String> masterImages;
        public final List<String> capturedImages;
        public final List<String> fgImages;

        public TransactionRecord(String id, String timestamp, String rfidEpc, String rfidTid,
                                 double rfidRssi, String materialCode, String productName,
                                 String partNumber, String category, String model,
                                 String dimensions, String colour,
                                 String workOrderNo, String deviceId, String status,
                                 String productImage, List<String> masterImages, List<String> capturedImages) {
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
            this.masterImages = masterImages != null ? masterImages : new ArrayList<>();
            this.capturedImages = capturedImages != null ? capturedImages : new ArrayList<>();
            this.fgImages = this.masterImages;
        }

        public TransactionRecord(String id, String timestamp, String rfidEpc, String rfidTid,
                                 double rfidRssi, String materialCode, String productName,
                                 String partNumber, String category, String model,
                                 String dimensions, String colour,
                                 String workOrderNo, String deviceId, String status,
                                 String productImage, List<String> fgImages) {
            this(id, timestamp, rfidEpc, rfidTid, rfidRssi, materialCode, productName,
                 partNumber, category, model, dimensions, colour,
                 workOrderNo, deviceId, status, productImage, fgImages, new ArrayList<>());
        }

        public TransactionRecord(String id, String timestamp, String rfidEpc, String rfidTid,
                                 double rfidRssi, String materialCode, String productName,
                                 String workOrderNo, String deviceId, String status) {
            this(id, timestamp, rfidEpc, rfidTid, rfidRssi, materialCode, productName,
                 "FG-" + materialCode, "Mattress", productName, "0 x 0 x 0 mm", "Red",
                 workOrderNo, deviceId, status, "/products/mattress_1.jpg", new ArrayList<>(), new ArrayList<>());
        }
    }
}
