package com.example.uhfsample;

import com.cipherlab.rfid.ClResult;
import com.cipherlab.rfid.GeneralString;
import com.cipherlab.rfid.RFIDMode;
import com.cipherlab.rfid.ScanMode;
import com.cipherlab.rfidapi.RfidManager;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    RfidManager mRfidManager = null;
    String TAG = "RFID_sample";
    TextView tv1 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRfidManager = RfidManager.InitInstance(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(GeneralString.Intent_RFIDSERVICE_CONNECTED);
        filter.addAction(GeneralString.Intent_RFIDSERVICE_TAG_DATA);
        registerReceiver(myDataReceiver, filter);

        tv1 = findViewById(R.id.textView1);
        Button b1 = findViewById(R.id.button1);
        b1.setOnClickListener(v -> {
            int re = 0;
            //Set ScanMode to Continuous if need to scan multiple tag at once
            re = mRfidManager.SetScanMode(ScanMode.Single);

            if (re != ClResult.S_OK.ordinal()) {
                String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
            } else {
                Log.e(TAG, "SetScanMode Single success");
                ForSetTriggerSwitchModeTest();
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myDataReceiver);
        mRfidManager.Release();
    }

    private void ForEnableDeviceTrigger(boolean value){
        mRfidManager.EnableDeviceTrigger(value);
    }

    public void ForSetTriggerSwitchModeTest() {
        int re = mRfidManager.SetTriggerSwitchMode(false);

        if (re != ClResult.S_OK.ordinal()) {
            String err = mRfidManager.GetLastError();
            Log.e(TAG, "SetChangeSwitchMode (err) = " + err);
        } else {
            Log.e(TAG, "SetTriggerSwitchMode(true) success");
            ForSetRFIDSwitchStatusTest();
        }
    }

    private void ForSetRFIDSwitchStatusTest() {
        int re = mRfidManager.SetRFIDSwitchStatus(true);
        if (re != ClResult.S_OK.ordinal()) {
            String err = mRfidManager.GetLastError();
            Log.e(TAG, "SetRFIDSwitchStatus (err) = " + err);
        } else {
            Log.e(TAG, "SetRFIDSwitchStatus(false) success");
            ForSetRFIDModeTest();
        }
    }

    private void ForSetRFIDModeTest() {
        int re = 0;
        re = mRfidManager.SetRFIDMode(RFIDMode.Inventory);
//        re = mRfidManager.SetRFIDMode(RFIDMode.Inventory_EPC_TID);
        //re =mRfidManager.SetRFIDMode(RFIDMode.ReadTag);
        //re =mRfidManager.SetRFIDMode(RFIDMode.WriteTag);

        if (re != ClResult.S_OK.ordinal()) {
            String m = mRfidManager.GetLastError();
            Log.e(TAG, "GetLastError = " + m);
        } else {
            Log.e(TAG, "SetRFIDMode(RFIDMode.Inventory_EPC_TID) success");
        }
    }

    private final BroadcastReceiver myDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GeneralString.Intent_RFIDSERVICE_CONNECTED)) {
                String PackageName = intent.getStringExtra("PackageName");
                // / make sure this AP does already connect with RFID service (after call RfidManager.InitInstance(this)
                String ver = "";
                ver = mRfidManager.GetServiceVersion();
                String api_ver = mRfidManager.GetAPIVersion();
                tv1.setText(PackageName + "," + ver + " , " + api_ver);
                Toast.makeText(MainActivity.this, "Intent_RFIDSERVICE_CONNECTED", Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(GeneralString.Intent_RFIDSERVICE_TAG_DATA)) {
                // Fetch data from the intent
                int type = intent.getIntExtra(GeneralString.EXTRA_DATA_TYPE, -1);
                int response = intent.getIntExtra(GeneralString.EXTRA_RESPONSE, -1);
                double data_rssi = intent.getDoubleExtra(GeneralString.EXTRA_DATA_RSSI, 0);
                String PC = intent.getStringExtra(GeneralString.EXTRA_PC);
                String EPC = intent.getStringExtra(GeneralString.EXTRA_EPC);
                String TID = intent.getStringExtra(GeneralString.EXTRA_TID);
                String ReadData = intent.getStringExtra(GeneralString.EXTRA_ReadData);
                int EPC_length = intent.getIntExtra(GeneralString.EXTRA_EPC_LENGTH, 0);
                int TID_length = intent.getIntExtra(GeneralString.EXTRA_TID_LENGTH, 0);
                int ReadData_length = intent.getIntExtra(GeneralString.EXTRA_ReadData_LENGTH, 0);

                String Data = "EPC = " + EPC + "\nTID = " + TID;
                tv1.setText(Data);
                Log.w(TAG, "++++ [Intent_RFIDSERVICE_TAG_DATA] ++++");
                Log.i(TAG, "[Intent_RFIDSERVICE_TAG_DATA] type=" + type + ", response=" + response + ", data_rssi=" + data_rssi);
                Log.i(TAG, "[Intent_RFIDSERVICE_TAG_DATA] PC=" + PC);
                Log.i(TAG, "[Intent_RFIDSERVICE_TAG_DATA] EPC=" + EPC);
                Log.i(TAG, "[Intent_RFIDSERVICE_TAG_DATA] EPC_length=" + EPC_length);
                Log.i(TAG, "[Intent_RFIDSERVICE_TAG_DATA] TID=" + TID);
                Log.i(TAG, "[Intent_RFIDSERVICE_TAG_DATA] TID_length=" + TID_length);
                Log.i(TAG, "[Intent_RFIDSERVICE_TAG_DATA] ReadData=" + ReadData);
                Log.i(TAG, "[Intent_RFIDSERVICE_TAG_DATA] ReadData_length=" + ReadData_length);
            }
        }
    };
}

