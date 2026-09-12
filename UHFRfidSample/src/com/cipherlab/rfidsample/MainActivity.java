package com.cipherlab.rfidsample;


import com.cipherlab.rfid.AllGen2Settings;
import com.cipherlab.rfid.AllQValue;
import com.cipherlab.rfid.AllRFLink;
import com.cipherlab.rfid.AuthenticateIncRepLen;
import com.cipherlab.rfid.AuthenticateSenRep;
/*import com.cipherlab.rfid.AuthenticateIncRepLen;
import com.cipherlab.rfid.AuthenticateSenRep;*/
import com.cipherlab.rfid.BeepType;
import com.cipherlab.rfid.ClResult;
import com.cipherlab.rfid.ContinuousInventoryTime;
//import com.cipherlab.rfid.ContinuousInventoryTime;
import com.cipherlab.rfid.DeviceEvent;
import com.cipherlab.rfid.DeviceInfo;
import com.cipherlab.rfid.DeviceResponse;
import com.cipherlab.rfid.DeviceVoltageInfo;
import com.cipherlab.rfid.EPCEncodingScheme;
import com.cipherlab.rfid.Enable_State;
//import com.cipherlab.rfid.FWUpdateErrorCode;
import com.cipherlab.rfid.Gen2Settings;
import com.cipherlab.rfid.GeneralString;
import com.cipherlab.rfid.InventoryStatusSettings;
import com.cipherlab.rfid.InventoryType;
import com.cipherlab.rfid.JapanChannel;
//import com.cipherlab.rfid.JapanChannel;
import com.cipherlab.rfid.LockTarget;
import com.cipherlab.rfid.ModuleTemperature;
import com.cipherlab.rfid.NotificationParams;
import com.cipherlab.rfid.PowerMode;
//import com.cipherlab.rfid.PowerMode;
import com.cipherlab.rfid.QValue;
import com.cipherlab.rfid.RFIDMemoryBank;
import com.cipherlab.rfid.RFIDMode;
import com.cipherlab.rfid.RFLink;
import com.cipherlab.rfid.RfidEpcFilter;
import com.cipherlab.rfid.RfidOutputConfiguration;
//import com.cipherlab.rfid.RfidOutputConfiguration;
import com.cipherlab.rfid.SLFlagSettings;
import com.cipherlab.rfid.ScanMode;
import com.cipherlab.rfid.SessionSettings;
import com.cipherlab.rfid.SwitchMode;
import com.cipherlab.rfid.TriggerSwitchMode;
import com.cipherlab.rfid.UntraceableRange;
import com.cipherlab.rfid.UntraceableTID;
import com.cipherlab.rfid.UntraceableU;
import com.cipherlab.rfid.UntraceableUser;
/*import com.cipherlab.rfid.UntraceableRange;
import com.cipherlab.rfid.UntraceableTID;
import com.cipherlab.rfid.UntraceableU;
import com.cipherlab.rfid.UntraceableUser;*/
import com.cipherlab.rfid.WorkMode;
import com.cipherlab.rfidapi.RfidManager;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
		filter.addAction(GeneralString.Intent_RFIDSERVICE_EVENT);
		filter.addAction(GeneralString.Intent_FWUpdate_ErrorMessage);
		filter.addAction(GeneralString.Intent_FWUpdate_Percent);
		filter.addAction(GeneralString.Intent_FWUpdate_Finish);
		filter.addAction(GeneralString.Intent_GUN_Attached);
		filter.addAction(GeneralString.Intent_GUN_Unattached);
		filter.addAction(GeneralString.Intent_GUN_Power);
		registerReceiver(myDataReceiver, filter);		
		
		final EditText e1 = (EditText) findViewById(R.id.editText1);
		
		tv1 = (TextView) findViewById(R.id.textView1);
		
		// GetDeviceInfo
		Button b1 = (Button) findViewById(R.id.button1);
		b1.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				ForTest();
			}
		});
		
		// GetDevicePowerSavingState
		Button b2 = (Button) findViewById(R.id.button2);
		b2.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				int time = mRfidManager.GetDevicePowerSavingState();
				if(time==-1)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				
				Log.w(TAG, "GetDevicePowerSavingState = " + time );
			}
		});
		
		// SetDevicePowerSavingState
		Button b3 = (Button) findViewById(R.id.button3);
		b3.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				int time = Integer.valueOf(e1.getText().toString());
						
				int re = mRfidManager.SetDevicePowerSavingState(1);
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				
				Log.w(TAG, "SetDevicePowerSavingState = " + re );
			}
		});
		
		// KeepDeviceAlive
		Button b4 = (Button) findViewById(R.id.button4);
		b4.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				Log.w(TAG, "KeepDeviceAlive" );
				mRfidManager.KeepDeviceAlive();
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
				Log.w(TAG, "KeepDeviceAlive" );
			}
		});
		

		// GetBatteryLifePercent
		Button b5 = (Button) findViewById(R.id.button5);
		b5.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				DeviceVoltageInfo info = new DeviceVoltageInfo();	
				int re = mRfidManager.GetBatteryLifePercent(info);
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "info Percentage = " + info.Percentage );
				Log.w(TAG, "info Voltage = " + info.Voltage );
				Log.w(TAG, "info ChargeStatus = " + info.ChargeStatus );
				Log.w(TAG, "info re = " + re );
				
				tv1.setText(Integer.toString(info.Percentage) + "," + Integer.toString(info.ChargeStatus) + " , " + info.Voltage);
			}
		});
		
		// ResetToDefault
		Button b6 = (Button) findViewById(R.id.button6);
		b6.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				ResetToDefault();
			}
		});
		
		// DeviceTriggerStatus // v0.0.11
		Button b7 = (Button) findViewById(R.id.button7);
		b7.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				ForDeviceTriggerStatus();
			}
		});
		
		// EnableDeviceTrigger // v0.0.11
		Button b8 = (Button) findViewById(R.id.button8);
		b8.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				ForEnableDeviceTrigger(false);
			}
		});
		
		// FirmwareUpdate 
		Button b9 = (Button) findViewById(R.id.button9);
		b9.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String path = Environment.getExternalStorageDirectory().getPath();
				path = path + "/PIS_S_v1.01a.SHX";//"/PIS_S_v0.01o_DVT3.SHX";
				
				int re = mRfidManager.FirmwareUpdate(path);
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}

			}
		});
		
		// ShutdownDevice
		Button b10 = (Button) findViewById(R.id.button10);
		b10.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int re = mRfidManager.ShutdownDevice();
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
			}
		});
		
		// GetConnectionStatus
		Button b11 = (Button) findViewById(R.id.button11);
		b11.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				ForGetConnectionStatusTest();
			}
		});
		
		// GetRFIDSwitchStatus
		Button b12 = (Button) findViewById(R.id.button12);
		b12.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int status = mRfidManager.GetRFIDSwitchStatus();
				if(status == -1)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "GetRFIDSwitchStatus = " + status );
			}
		});
		
		// GetScanMode
		Button b13 = (Button) findViewById(R.id.button13);
		b13.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				ScanMode mode =  mRfidManager.GetScanMode();
				if(mode == ScanMode.Err)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "GetScanMode = " + mode );
			}
		});
		
		// SetScanMode
		Button b14 = (Button) findViewById(R.id.button14);
		b14.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				

				int re = 0;
				re = mRfidManager.SetScanMode(ScanMode.Single);
				//re = mRfidManager.SetScanMode(ScanMode.Alternate);
				//re = mRfidManager.SetScanMode(ScanMode.Continuous);
				//re = mRfidManager.SetScanMode(ScanMode.Err);
				
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				
				Log.w(TAG, "SetScanMode = " + ScanMode.Single );
				Log.w(TAG, "SetScanMode = " + ScanMode.Test );
				Log.w(TAG, "SetScanMode = " + ScanMode.Continuous );
			}
		});
		
		// GetRFLink add v1.0.6
		Button b15 = (Button) findViewById(R.id.button15);
		b15.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				RFLink link =  mRfidManager.GetRFLink();
				if(link == RFLink.Err)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "GetRFLink = " + link );
			}
		});
		
		// SetRFLink add v1.0.6
		Button b16 = (Button) findViewById(R.id.button16);
		b16.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int re = mRfidManager.SetRFLink(RFLink.PR_ASK_Miller4_300KHz);
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
			}
		});
		
		// GetRFIDMode
		Button b17 = (Button) findViewById(R.id.button17);
		b17.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				RFIDMode mode = mRfidManager.GetRFIDMode();
				
				if(mode==RFIDMode.Err)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "GetRFIDMode = " + mode  );
			
			}
		});
		
		// SetRFIDMode
		Button b18 = (Button) findViewById(R.id.button18);
		b18.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int re = 0;
				//re =mRfidManager.SetRFIDMode(RFIDMode.Inventory);
				//re =mRfidManager.SetRFIDMode(RFIDMode.Inventory_EPC_TID);
				re =mRfidManager.SetRFIDMode(RFIDMode.ReadTag);
				//re =mRfidManager.SetRFIDMode(RFIDMode.WriteTag);
				
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
			}
		});
		
		// GetSelectedMemoryBank
		Button b19 = (Button) findViewById(R.id.button19);
		b19.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				RFIDMemoryBank bank = mRfidManager.GetSelectedMemoryBank();
				if(bank==RFIDMemoryBank.Err)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "GetSelectedMemoryBank = " + bank  );
			}
		});
		
		// SelectMemoryBank
		Button b20 = (Button) findViewById(R.id.button20);
		b20.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int re = 0;
				//re =mRfidManager.SelectMemoryBank(RFIDMemoryBank.Reserved);
				//re =mRfidManager.SelectMemoryBank(RFIDMemoryBank.EPC);
				//re =mRfidManager.SelectMemoryBank(RFIDMemoryBank.TID);
				re =mRfidManager.SelectMemoryBank(RFIDMemoryBank.User);
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
			}
		});
		
		// GetQValue
		Button b21 = (Button) findViewById(R.id.button21);
		b21.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				QValue q = mRfidManager.GetQValue();
				if(q==null)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				else
				Log.w(TAG, "GetQValue = " + q.Dynamic + "," + q.value + "," + q.Min + "," + q.Max  );
			}
		});
		
		// SetQValue
		Button b22 = (Button) findViewById(R.id.button22);
		b22.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				QValue q =new QValue();
				q.Dynamic=false;
				q.value=5;
				q.Min = 4;
				q.Max = 15;
				int re = mRfidManager.SetQValue(q);
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "SetQValue (re) = " + re);
			}
		});
		
		// GetTxPower
		Button b23 = (Button) findViewById(R.id.button23);
		b23.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int tx = mRfidManager.GetTxPower();
				if(tx==-1)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "GetTxPower = " + tx);
			}
		});
		
		// SetTxPower
		Button b24 = (Button) findViewById(R.id.button24);
		b24.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int re = mRfidManager.SetTxPower(3);
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
			}
		});
		
		// RFIDDirectStartInventoryRound
		Button b25 = (Button) findViewById(R.id.button25);
		b25.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int re = mRfidManager.RFIDDirectStartInventoryRound(InventoryType.EPC_AND_TID ,10);
				Log.e(TAG, "re = " + re);
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
			}
		});
		
		// RFIDDirectCancelInventoryRound
		Button b26 = (Button) findViewById(R.id.button26);
		b26.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				int re = mRfidManager.RFIDDirectCancelInventoryRound();
				if(re!=ClResult.S_OK.ordinal())
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
			}
		});
		
		// v1.0.1
		// RFIDReadTagMassive
		Button b27 = (Button) findViewById(R.id.button27);
		b27.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				byte[] password = new byte[] { (byte)0x61, (byte)0x61 ,(byte)0x61, (byte)0x61};
				int re = mRfidManager.RFIDReadTagMassive(null, RFIDMemoryBank.EPC, 0,0);
				if(re!=ClResult.S_OK.ordinal())
				{
					String err = mRfidManager.GetLastError();
	                Log.e(TAG, "RFIDReadTagMassive (re) = " + err);
				}
				
			}
		});
		
		// RFIDWriteTagMassive
		Button b28 = (Button) findViewById(R.id.button28);
		b28.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String Data = "aaaa";
				int re = mRfidManager.RFIDWriteTagMassive(null, RFIDMemoryBank.User, Data.getBytes(), 0, 4);
				/*byte[] Data = {0x00,0x00,0x00,0x00};
				int re = mRfidManager.RFIDWriteTagMassive(null, RFIDMemoryBank.User, Data, 0, 4);*/
				Log.w(TAG, "RFIDWriteTagMassive (re) = " + re);
				if(re!=ClResult.S_OK.ordinal())
				{
					String err = mRfidManager.GetLastError();
					Log.e(TAG, "RFIDWriteTagMassive (err) = " + err);
				}	
			}
		});
		
		// RFIDDirectReadTagByEPC
		Button b29 = (Button) findViewById(R.id.button29);
		b29.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				byte[] password = new byte[] { (byte)0x61, (byte)0x61 ,(byte)0x61, (byte)0x61};
				byte[] password0 = new byte[] { (byte)0x11, (byte)0x11 ,(byte)0x11, (byte)0x11};
				//byte[] EPCByteArray = new byte[] {  (byte)0x34, (byte)0x50, (byte) 0xaf, (byte) 0xec, (byte)0x2b, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01 };
				byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x30, (byte) 0x98, (byte)0x06, (byte)0x02, (byte)0x01, (byte)0x98, (byte)0x06, (byte)0x50, (byte)0xd7, (byte)0x4f };
				//byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x10, (byte) 0x42, (byte)0x16, (byte)0x0d, (byte)0x01, (byte)0x59, (byte)0x04, (byte)0x60, (byte)0xe0, (byte)0xbe };
				//byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x3a, (byte)0x1e, (byte)0x33, (byte)0xe1, (byte)0x2b };
				//int re = mRfidManager.RFIDDirectReadTagByEPC(password, EPCByteArray, RFIDMemoryBank.EPC, 2, 2, 3);
				//int re = mRfidManager.RFIDDirectReadTagByEPC(null, EPCByteArray, RFIDMemoryBank.Reserved, 4, 4, 3); // get access password
				//int re = mRfidManager.RFIDDirectReadTagByEPC(null, EPCByteArray, RFIDMemoryBank.Reserved, 0, 0, 3);
				int re = mRfidManager.RFIDDirectReadTagByEPC(null, EPCByteArray, RFIDMemoryBank.User, 0, 0, 3); 
				if(re!=ClResult.S_OK.ordinal())
				{
					String err = mRfidManager.GetLastError();
					Log.e(TAG, "RFIDDirectReadTagByEPC (err) = " + err);
				}
			}
		});
		
		// RFIDDirectWriteTagByEPC
		Button b30 = (Button) findViewById(R.id.button30);
		b30.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				byte[] password = new byte[] { (byte)0x61, (byte)0x61, (byte)0x61, (byte)0x61};
				byte[] DataArray = new byte[] { (byte)0x61, (byte)0x61, (byte)0x61, (byte)0x61 };
				byte[] DataArray_0 = new byte[] { (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};
				byte[] WriteDataArray = new byte[] { (byte)0x30, (byte)0x30, (byte)0x30, (byte)0x34 };
                //byte[] EPCByteArray = new byte[] { (byte)0x35, (byte)0x15, (byte)0xFD, (byte)0x85, (byte)0x60, (byte)0x08, (byte)0x23, (byte)0x50, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x02 };
				//byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x30, (byte) 0x98, (byte)0x06, (byte)0x02, (byte)0x01, (byte)0x98, (byte)0x06, (byte)0x50, (byte)0xd7, (byte)0x4f };
				//byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x10, (byte) 0x42, (byte)0x16, (byte)0x0d, (byte)0x01, (byte)0x59, (byte)0x04, (byte)0x60, (byte)0xe0, (byte)0xbe };
				byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x30, (byte) 0x98, (byte)0x06, (byte)0x02, (byte)0x01, (byte)0x98, (byte)0x06, (byte)0x50, (byte)0xd7, (byte)0x4f };
				//DeviceResponse re = mRfidManager.RFIDDirectWriteTagByEPC(DataArray, EPCByteArray, RFIDMemoryBank.User, 0, 3, WriteDataArray); // set access password
				//DeviceResponse re = mRfidManager.RFIDDirectWriteTagByEPC(null, EPCByteArray, RFIDMemoryBank.Reserved, 4, 3, DataArray); // set access password
				//DeviceResponse re = mRfidManager.RFIDDirectWriteTagByEPC(null, EPCByteArray, RFIDMemoryBank.Reserved, 0, 3, DataArray); // set kill password
				//DeviceResponse re = mRfidManager.RFIDDirectWriteTagByEPC(null, EPCByteArray, RFIDMemoryBank.Reserved, 0, 3, DataArray); // set kill password
				
				byte[] EPCByteArray_Change = new byte[] {  (byte)0x12, (byte)0x34, (byte) 0x30, (byte) 0x98, (byte)0x06, (byte)0x02, (byte)0x01, (byte)0x98, (byte)0x06, (byte)0x50, (byte)0xd7, (byte)0x4f };
				DeviceResponse re = mRfidManager.RFIDDirectWriteTagByEPC(null, EPCByteArray_Change, RFIDMemoryBank.EPC, 4, 3, EPCByteArray); // set the EPC
				Log.e(TAG, "RFIDDirectWriteTagByEPC(re) = " + re);
			}
		});
		
		// RFIDDirectReadTagByTID
		Button b31 = (Button) findViewById(R.id.button31);
		b31.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				//byte[] TIDByteArray = new byte[] { (byte)0xe2, (byte)0x80, (byte)0x11, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x5b, (byte)0x14, (byte)0x03, (byte)0x3c, (byte)0x01, (byte)0xf2 };
				byte[] TIDByteArray = new byte[] { (byte)0xe2, (byte)0x00, (byte)0x34, (byte)0x12, (byte)0x01, (byte)0x72, (byte)0xfa, (byte)0x00, (byte)0x02, (byte)0x34, (byte)0xd7, (byte)0x4f };
				int re = mRfidManager.RFIDDirectReadTagByTID(null, TIDByteArray, RFIDMemoryBank.User, 0, 4, 3);
				if(re!=ClResult.S_OK.ordinal())
				{
					String err = mRfidManager.GetLastError();
					Log.e(TAG, "RFIDDirectReadTagByTID (err) = " + err);
				}
			}
		});
		
		// RFIDDirectWriteTagByTID
		Button b32 = (Button) findViewById(R.id.button32);
		b32.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				byte[] DataArray = new byte[] { (byte)0x31, (byte)0x32, (byte)0x33, (byte)0x34 };
				//byte[] TIDByteArray = new byte[] { (byte)0xe2, (byte)0x80, (byte)0x11, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x5b, (byte)0x14, (byte)0x03, (byte)0x3c, (byte)0x01, (byte)0xf2 };
				byte[] TIDByteArray = new byte[] { (byte)0xe2, (byte)0x00, (byte)0x34, (byte)0x12, (byte)0x01, (byte)0x72, (byte)0xfa, (byte)0x00, (byte)0x02, (byte)0x34, (byte)0xd7, (byte)0x4f };
				DeviceResponse re = mRfidManager.RFIDDirectWriteTagByTID(null, TIDByteArray, RFIDMemoryBank.User, 0, 3, DataArray);
				Log.e(TAG, "RFIDDirectWriteTagByTID(re) = " + re);
			}
		});
		
		// GetIncludedEPCFilter
		Button b33 = (Button) findViewById(R.id.button33);
		b33.setOnClickListener(new OnClickListener() 
		{

			@Override
			public void onClick(View v) 
			{
				RfidEpcFilter f = new RfidEpcFilter();
				
				int re = mRfidManager.GetIncludedEPCFilter(f);
				Log.e(TAG, "GetIncludedEPCFilter(re) = " + re);
				Log.e(TAG, "GetIncludedEPCFilter(Enable) = " + f.Enable);
				Log.e(TAG, "GetIncludedEPCFilter(EPCPattern1) = " + f.EPCPattern1);
				Log.e(TAG, "GetIncludedEPCFilter(EPCPattern2) = " + f.EPCPattern2);
				Log.e(TAG, "GetIncludedEPCFilter(Startbit_LSB) = " + f.Startbit_LSB);
				Log.e(TAG, "GetIncludedEPCFilter(Startbit_MSB) = " + f.Startbit_MSB);
				Log.e(TAG, "GetIncludedEPCFilter(PatternLength_LSB) = " + f.PatternLength_LSB);
				Log.e(TAG, "GetIncludedEPCFilter(PatternLength_MSB) = " + f.PatternLength_MSB);
				Log.e(TAG, "GetIncludedEPCFilter(Scheme) = " + f.Scheme);
				
				if(re!=ClResult.S_OK.ordinal())
				{
					String err = mRfidManager.GetLastError();
					Log.e(TAG, "GetIncludedEPCFilter (err) = " + err);
				}
				
			}
		});

		// SetIncludedEPCFilter
		Button b34 = (Button) findViewById(R.id.button34);
		b34.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) 
			{
				//byte[] EPCByteArray = new byte[] {  (byte)0x34, (byte)0x50, (byte) 0xaf, (byte) 0xec, (byte)0x2b, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01 };
				byte[] EPCByteArray = new byte[] {  (byte)0x33, (byte)0x30, (byte) 0xaf, (byte) 0xec, (byte)0x2b, (byte)0x01, (byte)0x15, (byte)0xc0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01 };

				RfidEpcFilter f = new RfidEpcFilter();

				/*[SetIncludedEPCFilter] Enable = 1
				[SetIncludedEPCFilter] EPCPattern1 = 00
				[SetIncludedEPCFilter] EPCPattern2 = null
				[SetIncludedEPCFilter] Startbit_LSB = 8
				[SetIncludedEPCFilter] Startbit_MSB = 0
				[SetIncludedEPCFilter] PatternLength_LSB = 3
				[SetIncludedEPCFilter] PatternLength_MSB = 0
				[SetIncludedEPCFilter] Scheme = 48*/

				/*f.Enable = 1;
				f.Startbit_LSB = ((byte)(0x08));
				f.Startbit_MSB = ((byte)(0));
				f.EPCPattern1 = "1234";
				f.EPCPattern2 ="";
				f.PatternLength_LSB = ((byte)(0x03));
				f.PatternLength_MSB = ((byte)(0));
				f.Scheme = (byte) 0x30;
				*/
				
				
				
				f.Enable = 1;
				f.Startbit_LSB = ((byte)(0x00));
				f.Startbit_MSB = ((byte)(0));
				//f.EPCPattern1 = "1e2400";
				f.EPCPattern1 = "1234";
				f.EPCPattern2 =null;
				f.PatternLength_LSB = ((byte)(0x60));
				f.PatternLength_MSB = ((byte)(0));
				f.Scheme = (byte) 0x33;
				
				/*f.Enable = 1;
				f.Startbit_LSB = ((byte)(0x0e));
				f.Startbit_MSB = ((byte)(0));
				f.EPCPattern1 = "aaaaaa";
				f.EPCPattern2 ="";
				f.PatternLength_LSB = ((byte)(0x28));
				f.PatternLength_MSB = ((byte)(0));
				f.Scheme = (byte) 0x3b;*/
				int re = 0;
				try {
					re = mRfidManager.SetIncludedEPCFilter(f);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.e(TAG, "Exception  = " + e.toString());
				}
				
				Log.e(TAG, "SetIncludedEPCFilter(re) = " + re);
				if(re != ClResult.S_OK.ordinal())
				{
					Log.e(TAG, "SetIncludedEPCFilter(err) = " + mRfidManager.GetLastError());
				}
			}
		});
		
		// GetExcludedEPCFilter
		Button b35 = (Button) findViewById(R.id.button35);
		b35.setOnClickListener(new OnClickListener() 
		{

			@Override
			public void onClick(View v) 
			{
				RfidEpcFilter f = new RfidEpcFilter();
				
				int re = mRfidManager.GetExcludedEPCFilter(f);
				Log.e(TAG, "GetExcludedEPCFilter(re) = " + re);
				Log.e(TAG, "GetExcludedEPCFilter(Enable) = " + f.Enable);
				Log.e(TAG, "GetExcludedEPCFilter(EPCPattern1) = " + f.EPCPattern1);
				Log.e(TAG, "GetExcludedEPCFilter(EPCPattern2) = " + f.EPCPattern2);
				Log.e(TAG, "GetExcludedEPCFilter(Startbit_LSB) = " + f.Startbit_LSB);
				Log.e(TAG, "GetExcludedEPCFilter(Startbit_MSB) = " + f.Startbit_MSB);
				Log.e(TAG, "GetExcludedEPCFilter(PatternLength_LSB) = " + f.PatternLength_LSB);
				Log.e(TAG, "GetExcludedEPCFilter(PatternLength_MSB) = " + f.PatternLength_MSB);
				Log.e(TAG, "GetExcludedEPCFilter(Scheme) = " + f.Scheme);
				
				if(re!=ClResult.S_OK.ordinal())
				{
					String err = mRfidManager.GetLastError();
					Log.e(TAG, "GetExcludedEPCFilter (err) = " + err);
				}
			}
		});

		// SetExcludedEPCFilter
		Button b36 = (Button) findViewById(R.id.button36);
		b36.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) 
			{

				/*[SetExcludedEPCFilter] Enable = 1
				[SetExcludedEPCFilter] EPCPattern1 = 00000050
				[SetExcludedEPCFilter] EPCPattern2 = 
				[SetExcludedEPCFilter] Startbit_LSB = 8
				[SetExcludedEPCFilter] Startbit_MSB = 0
				[SetExcludedEPCFilter] PatternLength_LSB = 28
				[SetExcludedEPCFilter] PatternLength_MSB = 0
				[SetExcludedEPCFilter] Scheme = 53*/

				RfidEpcFilter f = new RfidEpcFilter();
				f.Enable =1;
				f.Startbit_LSB = ((byte)(0x08));
				f.Startbit_MSB = ((byte)(0));
				f.EPCPattern1 = "00000050";
				f.EPCPattern2 = "";
				f.PatternLength_LSB = ((byte)(0x1c));
				f.PatternLength_MSB = ((byte)(0));
				f.Scheme = (byte) 0x35;
				
				int re = mRfidManager.SetExcludedEPCFilter(f);
				
				if(re!=ClResult.S_OK.ordinal())
				{
					String err = mRfidManager.GetLastError();
					Log.e(TAG, "SetExcludedEPCFilter (err) = " + err);
				}
			}
		});
		
		// SetRFIDSwitchStatus
		Button b37 = (Button) findViewById(R.id.button37);
		b37.setOnClickListener(new OnClickListener() 
		{

			@Override
			public void onClick(View v) {

				int re = mRfidManager.SetRFIDSwitchStatus(false);
				if(re!=ClResult.S_OK.ordinal())
				{
					String err = mRfidManager.GetLastError();
					Log.e(TAG, "SetRFIDSwitchStatus (err) = " + err);
				}
			}
		});
	
	}
	
	public void ForTest()
	  {
		try {
            DeviceInfo info = mRfidManager.GetDeviceInfo();
            String m = mRfidManager.GetLastError();
            Log.e(TAG, "GetLastError = " + m);
            Log.w(TAG, "deviceInfo.SerialNumber = " + info.SerialNumber );
            Log.w(TAG, "deviceInfo.Region = " + info.Region );
            Log.w(TAG, "deviceInfo.KernelVersion = " + info.KernelVersion );
            Log.w(TAG, "deviceInfo.UserVersion = " + info.UserVersion );
            Log.w(TAG, "deviceInfo.RFIDModuleVersion = " + info.RFIDModuleVersion );
        }catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Exception = " + e.getMessage());
        }
	  }
	
	// GetModuleTemperature
	public void ForGetModuleTemperatureTest() {
		try {
			ModuleTemperature t = new ModuleTemperature();
			int re = mRfidManager.GetModuleTemperature(t);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}
			//Log.w(TAG, "GetModuleTemperature [ModuleTemperature] = " + t.GunModuleTemperature);
			//Log.w(TAG, "GetModuleTemperature [TemperatureProtection] = " + t.GunProtectTemperature);
			//tv1.setText(String.valueOf(t.GunModuleTemperature));
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	
	// GetAllQValue
	public void ForGetAllQValueTest() {
		try {
			AllQValue q = new AllQValue();
			int re = mRfidManager.GetAllQValue(q);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}
			
			for(int i=0; i<8 ;i++)
			{
				Log.w(TAG, "GetAllQValue [value] = " + q.Q_all.get(i).value);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
		}
	
	// SetAllQValue
	public void ForSetAllQValueTest() {
		try {
			AllQValue q = new AllQValue();
			int re = mRfidManager.GetAllQValue(q);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
				return;
			}

			QValue MultiTag = new QValue();
			MultiTag.Dynamic = q.Q_all.get(WorkMode.MultiTagMode.ordinal()).Dynamic;
			MultiTag.value = 9;
			MultiTag.Max = q.Q_all.get(WorkMode.MultiTagMode.ordinal()).Max;
			MultiTag.Min = q.Q_all.get(WorkMode.MultiTagMode.ordinal()).Min;
			q.Q_all.set(WorkMode.MultiTagMode.ordinal(),MultiTag);
			
			
			QValue user1 = new QValue();
			user1.Dynamic = q.Q_all.get(WorkMode.UserDefine1.ordinal()).Dynamic;
			user1.value = 10;
			user1.Max = q.Q_all.get(WorkMode.UserDefine1.ordinal()).Max;
			user1.Min = q.Q_all.get(WorkMode.UserDefine1.ordinal()).Min;
			q.Q_all.set(WorkMode.UserDefine1.ordinal(),user1);
			
			re = mRfidManager.SetAllQValue(q);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// GetAllRFLink
	public void ForGetAllRFLinkTest() {
		try {
			AllRFLink rf = new AllRFLink();
			int re = mRfidManager.GetAllRFLink(rf);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}

			for (int i = 0; i < 8; i++) {
				Log.w(TAG, "GetAllRFLink [RFLink] = "+ rf.RFLink_all.get(i));
			}

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}

	// SetAllRFLink
	public void ForSetAllRFLinkTest() {
		try {
			AllRFLink rf = new AllRFLink();
			int re = mRfidManager.GetAllRFLink(rf);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
				return;
			}

			rf.RFLink_all.set(WorkMode.UserDefine4.ordinal(), RFLink.PR_ASK_Miller4_300KHz.ordinal());

			rf.RFLink_all.set(WorkMode.UserDefine1.ordinal(), RFLink.DSB_ASK_FM0_40KHz.ordinal());
			
			re = mRfidManager.SetAllRFLink(rf);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// GetGen2
	public void ForGetGen2Test() {
		try {
			Gen2Settings settings = new Gen2Settings();
			int re = mRfidManager.GetGen2(settings);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}

			Log.i(TAG, "InventoryStatus_Action = " + settings.InventoryStatus_Action);
			Log.i(TAG, "SL_Flag = " + settings.SL_Flag);
			Log.i(TAG, "Session = " + settings.Session);

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// SetGen2
	public void ForSetGen2Test() {
		try {
			Gen2Settings settings = new Gen2Settings();
			settings.Session = SessionSettings.S1;
			settings.InventoryStatus_Action = InventoryStatusSettings.AB_FLIP;
			settings.SL_Flag =SLFlagSettings.Asserted;
			
			
			Log.i(TAG, "settings.Session = " + settings.Session);
			Log.i(TAG, "settings.InventoryStatus_Action = " + settings.InventoryStatus_Action);
			Log.i(TAG, "settings.SL_Flag = " + settings.SL_Flag);
			
			int re = mRfidManager.SetGen2(settings);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}					

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	
	// GetAllGen2
	public void ForGetAllGen2Test() 
	{
		try {
			AllGen2Settings allsettings = new AllGen2Settings();
			int re = mRfidManager.GetAllGen2(allsettings);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}

			for (int i = 0; i < 8; i++) {
				Log.i(TAG,"GetAllGen2 [Session]  ("+ i + ") = " + allsettings.Gen2_all.get(i).Session);
				Log.i(TAG,"GetAllGen2 [SL_Flag] ("+ i + ") = " + allsettings.Gen2_all.get(i).SL_Flag);
				Log.i(TAG,"GetAllGen2 [InventoryStatus_Action] ("+ i + ") = " + allsettings.Gen2_all.get(i).InventoryStatus_Action);
			}

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	
	// SetAllGen2
		public void ForSetAllGen2Test() {
			try {
				AllGen2Settings allsettings = new AllGen2Settings();
				int re = mRfidManager.GetAllGen2(allsettings);
				if (re != ClResult.S_OK.ordinal()) {
					String m = mRfidManager.GetLastError();
					Log.e(TAG, "GetLastError = " + m);
					return;
				}
				
				Gen2Settings settings_multi = new Gen2Settings();
				settings_multi.Session = SessionSettings.S1;
				settings_multi.SL_Flag =  SLFlagSettings.Asserted;
				settings_multi.InventoryStatus_Action = InventoryStatusSettings.STATE_B;
				allsettings.Gen2_all.set(WorkMode.MultiTagMode.ordinal(), settings_multi);
				
				Gen2Settings settings_ComprehensiveMode = new Gen2Settings();
				settings_ComprehensiveMode.Session = SessionSettings.S2;
				settings_ComprehensiveMode.SL_Flag =  SLFlagSettings.Asserted;
				settings_ComprehensiveMode.InventoryStatus_Action = InventoryStatusSettings.AB_FLIP;
				allsettings.Gen2_all.set(WorkMode.ComprehensiveMode.ordinal(), settings_ComprehensiveMode);

				Gen2Settings settings_df1 = new Gen2Settings();
				settings_df1.Session = SessionSettings.S3;
				settings_df1.SL_Flag =  SLFlagSettings.Deasserted;
				settings_df1.InventoryStatus_Action = InventoryStatusSettings.STATE_B;
				allsettings.Gen2_all.set(WorkMode.UserDefine1.ordinal(), settings_df1);
				
				
				re = mRfidManager.SetAllGen2(allsettings);
				if (re != ClResult.S_OK.ordinal()) {
					String m = mRfidManager.GetLastError();
					Log.e(TAG, "GetLastError = " + m);
				}

			} catch (Exception e) {
				e.printStackTrace();
				Log.e(TAG, "Exception = " + e.getMessage());
			}
		}

	// GetNotification
	public void ForGetNotificationTest() {
		try {
			NotificationParams settings = new NotificationParams();
			int re = mRfidManager.GetNotification(settings);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}
			
			Log.i(TAG, "settings.ReaderBeep = " + settings.ReaderBeep);
			Log.i(TAG, "settings.BatteryLED = " + settings.BatteryLED);
			Log.i(TAG, "settings.BatteryBeep = " + settings.BatteryBeep);
			Log.i(TAG, "settings.ModuleTemperature = " + settings.ModuleTemperature);
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// SetNotification
	public void ForSetNotificationTest() {
		try {
			NotificationParams settings = new NotificationParams();
			mRfidManager.GetNotification(settings);
			
			settings.ReaderBeep = BeepType.Ringtone4;
			settings.BatteryLED = Enable_State.TRUE;
			settings.BatteryBeep = Enable_State.TRUE;
			settings.ModuleTemperature = Enable_State.TRUE;
			
			int re = mRfidManager.SetNotification(settings);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	//ResetToDefault
	public void ResetToDefault() {
		try {
			int re = mRfidManager.ResetToDefault();
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// SoftScanTrigger on
	public void SoftScanTrigger_on() {
		try {
			int re = mRfidManager.SoftScanTrigger(true);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// SoftScanTrigger off
		public void SoftScanTrigger_off() {
			try {
				int re = mRfidManager.SoftScanTrigger(false);
				if (re != ClResult.S_OK.ordinal()) {
					String m = mRfidManager.GetLastError();
					Log.e(TAG, "GetLastError = " + m);
				}
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(TAG, "Exception = " + e.getMessage());
			}
		}
		
		// GetWorkMode
		public void ForGetWorkModeTest() {
			try {
				WorkMode mode =  mRfidManager.GetWorkMode();
				if(mode == WorkMode.Err)
				{
					String m = mRfidManager.GetLastError();
	                Log.e(TAG, "GetLastError = " + m);
				}
				Log.w(TAG, "GetWorkMode = " + mode );
				tv1.setText(mode.toString());
				//tv1.setText(mode.toString());
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(TAG, "Exception = " + e.getMessage());
			}
		}
		
		// SetWorkMode
		public void ForSetWorkModeTest() {
			try {
				int re = 0;
			//re = mRfidManager.SetWorkMode(WorkMode.MultiTagMode);
			//re = mRfidManager.SetWorkMode(WorkMode.SingleTagMode);
			re = mRfidManager.SetWorkMode(WorkMode.ComprehensiveMode);
				//re = mRfidManager.SetWorkMode(WorkMode.UserDefine1);
				//re = mRfidManager.SetWorkMode(WorkMode.UserDefine2);
				//re = mRfidManager.SetWorkMode(WorkMode.UserDefine3);
				//re = mRfidManager.SetWorkMode(WorkMode.UserDefine4);
				//re = mRfidManager.SetWorkMode(WorkMode.UserDefine5);
			//re = mRfidManager.SetWorkMode(WorkMode.Err);
			
			if(re!=ClResult.S_OK.ordinal())
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			
				}
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(TAG, "Exception = " + e.getMessage());
			}
		}
		
	// GetConnectionStatus
	public void ForGetConnectionStatusTest() 
	{
		try {
			Boolean Status = mRfidManager.GetConnectionStatus();
			Log.w(TAG, "GetConnectionStatus = " + Status);
			String ms = "GetConnectionStatus = " + Status;
			Toast.makeText(MainActivity.this,  ms, Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// DeviceTriggerStatus  // add v1.0.11
	public void ForDeviceTriggerStatus() 
	{
		int Status = mRfidManager.DeviceTriggerStatus();
		if (Status == -1) {
			String m = mRfidManager.GetLastError();
			Log.e(TAG, "GetLastError = " + m);
		}
		else
			Log.i(TAG, "DeviceTriggerStatus = " + Status);
	}
	
	// EnableDeviceTrigger  // add v1.0.11
	public void ForEnableDeviceTrigger(boolean s) 
	{
		int re = mRfidManager.EnableDeviceTrigger(s);
		if (re != ClResult.S_OK.ordinal()) {
			String m = mRfidManager.GetLastError();
			Log.e(TAG, "GetLastError = " + m);
		}
	}
	
	// RFIDDirectKillTag
	public void ForRFIDDirectKillTagTest() 
	{
		byte[] password = new byte[] { (byte)0x61, (byte)0x61, (byte)0x61, (byte)0x61 };
		byte[] TIDByteArray = new byte[] { (byte)0xe2, (byte)0x00, (byte)0x34, (byte)0x12, (byte)0x01, (byte)0x72, (byte)0xfa, (byte)0x00, (byte)0x02, (byte)0x34, (byte)0xd7, (byte)0x4f };
		//byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x30, (byte) 0x98, (byte)0x06, (byte)0x02, (byte)0x01, (byte)0x98, (byte)0x06, (byte)0x50, (byte)0xd7, (byte)0x4f };
		byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x10, (byte) 0x42, (byte)0x16, (byte)0x0d, (byte)0x01, (byte)0x59, (byte)0x04, (byte)0x60, (byte)0xe0, (byte)0xbe };
		DeviceResponse re = mRfidManager.RFIDDirectKillTag(password, EPCByteArray);
		Log.i(TAG, "ForRFIDDirectKillTagTest(re) = " + re);
	}
	
	// RFIDDirectUnlockTag
	public void ForRFIDDirectUnlockTagTest() 
	{
		byte[] password = new byte[] { (byte)0x61, (byte)0x61, (byte)0x61, (byte)0x61 };
		byte[] TIDByteArray = new byte[] { (byte)0xe2, (byte)0x00, (byte)0x34, (byte)0x12, (byte)0x01, (byte)0x72, (byte)0xfa, (byte)0x00, (byte)0x02, (byte)0x34, (byte)0xd7, (byte)0x4f };
		//byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x30, (byte) 0x98, (byte)0x06, (byte)0x02, (byte)0x01, (byte)0x98, (byte)0x06, (byte)0x50, (byte)0xd7, (byte)0x4f };
		byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x10, (byte) 0x42, (byte)0x16, (byte)0x0d, (byte)0x01, (byte)0x59, (byte)0x04, (byte)0x60, (byte)0xe0, (byte)0xbe };
		DeviceResponse re = mRfidManager.RFIDDirectUnlockTag(password, EPCByteArray , LockTarget.UserBank);
		Log.i(TAG, "RFIDDirectUnlockTag(re) = " + re);
	}
	
	// RFIDDirectLockTag
	public void ForRFIDDirectLockTagTest() 
	{
		byte[] password = new byte[] { (byte)0x61, (byte)0x61, (byte)0x61, (byte)0x61 };
		byte[] TIDByteArray = new byte[] { (byte)0xe2, (byte)0x00, (byte)0x34, (byte)0x12, (byte)0x01, (byte)0x72, (byte)0xfa, (byte)0x00, (byte)0x02, (byte)0x34, (byte)0xd7, (byte)0x4f };
		//byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x30, (byte) 0x98, (byte)0x06, (byte)0x02, (byte)0x01, (byte)0x98, (byte)0x06, (byte)0x50, (byte)0xd7, (byte)0x4f };
		//byte[] EPCByteArray = new byte[] {  (byte)0x32, (byte)0x70, (byte) 0xaf, (byte) 0xec, (byte)0x2b, (byte)0x00, (byte)0x02, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01 };
		byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x10, (byte) 0x42, (byte)0x16, (byte)0x0d, (byte)0x01, (byte)0x59, (byte)0x04, (byte)0x60, (byte)0xe0, (byte)0xbe };
		DeviceResponse re = mRfidManager.RFIDDirectLockTag(password, EPCByteArray , LockTarget.UserBank);
		Log.i(TAG, "RFIDDirectLockTag(re) = " + re);
	}
	
	//RFIDDirectPermanentLockTag
	public void ForRFIDDirectPermanentLockTagTest() 
	{
		byte[] password = new byte[] { (byte)0x61, (byte)0x61, (byte)0x61, (byte)0x61 };
		byte[] TIDByteArray = new byte[] { (byte)0xe2, (byte)0x00, (byte)0x34, (byte)0x12, (byte)0x01, (byte)0x72, (byte)0xfa, (byte)0x00, (byte)0x02, (byte)0x34, (byte)0xd7, (byte)0x4f };
		//byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x30, (byte) 0x98, (byte)0x06, (byte)0x02, (byte)0x01, (byte)0x98, (byte)0x06, (byte)0x50, (byte)0xd7, (byte)0x4f };
		byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0x00, (byte) 0x10, (byte) 0x42, (byte)0x16, (byte)0x0d, (byte)0x01, (byte)0x59, (byte)0x04, (byte)0x60, (byte)0xe0, (byte)0xbe };
		DeviceResponse re = mRfidManager.RFIDDirectPermanentLockTag(password, EPCByteArray , LockTarget.UserBank);
		Log.i(TAG, "RFIDDirectPermanentLockTag(re) = " + re);
	}
	
	// GetRecognizedEPCEncoding
	public void ForGetRecognizedEPCEncodingTest()
	{
		EPCEncodingScheme encode = new EPCEncodingScheme();
		int re = mRfidManager.GetRecognizedEPCEncoding(encode);
		if (re != ClResult.S_OK.ordinal()) {
			String m = mRfidManager.GetLastError();
			Log.e(TAG, "GetLastError = " + m);
		}
		else
		{
			Log.i(TAG, "GDTI96 = " + encode.GDTI96);
			Log.i(TAG, "GSRN96 = " + encode.GSRN96);
			Log.i(TAG, "GSRNP = " + encode.GSRNP);
			Log.i(TAG, "USDoD96 = " + encode.USDoD96);
			Log.i(TAG, "SGTIN96 = " + encode.SGTIN96);
			Log.i(TAG, "SSCC96 = " + encode.SSCC96);
			Log.i(TAG, "SGLN96 = " + encode.SGLN96);
			Log.i(TAG, "GRAI96 = " + encode.GRAI96);
			Log.i(TAG, "GIAI96 = " + encode.GIAI96);
			Log.i(TAG, "GID96 = " + encode.GID96);
			Log.i(TAG, "SGTIN198 = " + encode.SGTIN198);
			Log.i(TAG, "GRAI170 = " + encode.GRAI170);
			Log.i(TAG, "GIAI202 = " + encode.GIAI202);
			Log.i(TAG, "SGLN195 = " + encode.SGLN195);
			Log.i(TAG, "GDTI113 = " + encode.GDTI113);
			Log.i(TAG, "ADI = " + encode.ADI);
			Log.i(TAG, "CPI96 = " + encode.CPI96);
			Log.i(TAG, "CPI = " + encode.CPI);
			Log.i(TAG, "GDTI174 = " + encode.GDTI174);
			Log.i(TAG, "SGCN96 = " + encode.SGCN96);
		}
	}
	
	// SetRecognizedEPCEncoding
	public void ForSetRecognizedEPCEncodingTest() 
	{
		EPCEncodingScheme encode = new EPCEncodingScheme();
		int re = mRfidManager.GetRecognizedEPCEncoding(encode);
		/*encode.GDTI96 = false;
		encode.GSRN96 = false;
		encode.GSRNP = false;
		encode.USDoD96 = false;
		encode.SGTIN96 = false;
		encode.SSCC96 = false;
		encode.SGLN96 = false;
		encode.GRAI96 = false;*/
		encode.GIAI96 = false;
		/*encode.GID96 = false;
		encode.SGTIN198 = false;
		encode.GRAI170 = false;
		encode.GIAI202 = false;
		encode.SGLN195 = false;
		encode.GDTI113 = false;
		encode.ADI = false;
		encode.CPI96 = false;
		encode.CPI = false;
		encode.GDTI174 = false;
		encode.SGCN96 = false;*/

		re = mRfidManager.SetRecognizedEPCEncoding(encode);
		if (re != ClResult.S_OK.ordinal()) 
		{
			String m = mRfidManager.GetLastError();
			Log.e(TAG, "GetLastError = " + m);
		} else {
		}
	}
	
	// GetDataOutputSettings
	public void ForGetDataOutputSettingsTest() 
	{
		RfidOutputConfiguration Settings = new RfidOutputConfiguration();
		int re = mRfidManager.GetDataOutputSettings(Settings);
		if (re != ClResult.S_OK.ordinal()) {
			String m = mRfidManager.GetLastError();
			Log.e(TAG, "GetLastError = " + m);
		} 
		else 
		{
			Log.i(TAG, "szEPCPrefixCode = " + Settings.szEPCPrefixCode);
			Log.i(TAG, "szEPCSuffixCode = " + Settings.szEPCSuffixCode);
			Log.i(TAG, "KeyboardOutput = " + Settings.KeyEventOutput);
			Log.i(TAG, "InterCharDelay = " + Settings.InterCharDelay);
			//Log.i(TAG, "AntiReread = " + Settings.AntiReread);
		}
	}
	
	// SetDataOutputSettings
	public void ForSetDataOutputSettingsTest() 
	{
		RfidOutputConfiguration Settings = new RfidOutputConfiguration();
		Settings.szEPCPrefixCode ="AAA";
		Settings.szEPCSuffixCode ="BBB";
		Settings.KeyEventOutput = true;
		Settings.InterCharDelay = 100;
		//Settings.AntiReread = true;
		
		int re = mRfidManager.SetDataOutputSettings(Settings);
		if (re != ClResult.S_OK.ordinal()) {
			String m = mRfidManager.GetLastError();
			Log.e(TAG, "GetLastError = " + m);
		}
	}
	
	//RFIDDirectUntraceableTag
	public void ForRFIDDirectUntraceableTagTest()
	{
		byte[] password = new byte[] { (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};
		
		byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x3a, (byte)0x1e, (byte)0x33, (byte)0xe1, (byte)0x2b };
		
		byte[] EPCByteArray1 = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x3a, (byte)0x1e, (byte)0x33, (byte)0xe1, (byte)0x2a };
		
		byte[] EPCByteArray2 = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x02, (byte)0x1f, (byte)0x9e, (byte)0xbf, (byte)0xfb };
		
		byte[] TIDByteArray2 = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x20, (byte)0x00, (byte)0xaa, (byte)0x02, (byte)0x1f, (byte)0x9e, (byte)0xbf, (byte)0xfb };
		
		byte[] UserByteArray2 = new byte[] {  (byte)0x01, (byte)0x02, (byte) 0x03, (byte) 0x04 };
		
		byte[] SITByteArray2 = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x02, (byte)0x1f, (byte)0x9e, (byte)0xbf, (byte)0xfa  };
		                                                                                   //e2 c0 68 92 00 00 00 02 1f 9e bf fa                                                                             //e2 c0 68 92 00 00 00 3a 1e 33 e1 51 
		DeviceResponse re = mRfidManager.RFIDDirectUntraceableTag(password, RFIDMemoryBank.EPC, 4 , SITByteArray2, UntraceableU.DeassertU, 6 , UntraceableTID.HideNone , UntraceableUser.View ,UntraceableRange.Normal , 5);
		//DeviceResponse re = mRfidManager.RFIDDirectUntraceableTag(password, RFIDMemoryBank.TID, 0 , TIDByteArray2, UntraceableU.DeassertU, 6 , UntraceableTID.HideNone , UntraceableUser.View ,UntraceableRange.Normal , 5);
		//DeviceResponse re = mRfidManager.RFIDDirectUntraceableTag(password, RFIDMemoryBank.User, 0 , UserByteArray2, UntraceableU.DeassertU, 6 , UntraceableTID.HideNone , UntraceableUser.View ,UntraceableRange.Normal , 5);
		
		Log.i(TAG, "RFIDDirectUntraceableTag(re) = " + re);
	}
	
	//RFIDDirectAuthenticateTag
	public void ForRFIDDirectAuthenticateTagTest()
	{
		byte[] password = new byte[] { (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};
		
		byte[] EPCByteArray = new byte[] { (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x3a, (byte)0x1e, (byte)0x33, (byte)0xe1, (byte)0x2b };

		byte[] MessageByteArray = new byte[] { (byte)0x8e, (byte)0x02, (byte) 0x49, (byte) 0x9d, (byte)0x2d, (byte)0x26, (byte)0x03, (byte)0xf9, (byte)0x8f, (byte)0x5c};
		
		byte[] EPCByteArray1 = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x3a, (byte)0x1e, (byte)0x33, (byte)0xe1, (byte)0x2a };
		
		DeviceResponse re = mRfidManager.RFIDDirectAuthenticateTag(password, RFIDMemoryBank.EPC, 4 , EPCByteArray1, AuthenticateSenRep.Send, AuthenticateIncRepLen.Included_Length_From_Reply , MessageByteArray  , 5);
		Log.i(TAG, "RFIDDirectAuthenticateTag(re) = " + re);
	}
	
	public void For_Set_Authenticate_Key0_Test()
	{
		byte[] password = new byte[] { (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};
		byte[] WriteDataArray = new byte[] { (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11 };
		byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x3a, (byte)0x1e, (byte)0x33, (byte)0xe1, (byte)0x2a };
		DeviceResponse re = mRfidManager.RFIDDirectWriteTagByEPC(password, EPCByteArray, RFIDMemoryBank.User, 384, 3, WriteDataArray); 
		Log.i(TAG, "RFIDDirectWriteTagByEPC Set Key0 (re) = " + re);
	}
	
	public void For_Activate_Authenticate_Key0_Test()
	{
		byte[] password = new byte[] { (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};
		byte[] WriteDataArray = new byte[] { (byte)0xe2, (byte)0x00};
		byte[] EPCByteArray = new byte[] {  (byte)0xe2, (byte)0xc0, (byte) 0x68, (byte) 0x92, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x3a, (byte)0x1e, (byte)0x33, (byte)0xe1, (byte)0x2a };
		DeviceResponse re = mRfidManager.RFIDDirectWriteTagByEPC(password, EPCByteArray, RFIDMemoryBank.User, 400, 3, WriteDataArray); 
		Log.i(TAG, "RFIDDirectWriteTagByEPC Activate Key0 (re) = " + re);
	}
	
	// GetJapanChannel
	public void ForGetJapanChannelTest() {
		try {
			JapanChannel channel = new JapanChannel();
			int re = mRfidManager.GetJapanChannel(channel);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}
			else
			{
				Log.i(TAG, "GetJapanChannel (JP_916_8Mhz) = " + channel.JP_916_8Mhz);
				Log.i(TAG, "GetJapanChannel (JP_918_0Mhz) = " + channel.JP_918_0Mhz);
				Log.i(TAG, "GetJapanChannel (JP_919_2Mhz) = " + channel.JP_919_2Mhz);
				Log.i(TAG, "GetJapanChannel (JP_920_4Mhz) = " + channel.JP_920_4Mhz);
				Log.i(TAG, "GetJapanChannel (JP_920_6Mhz) = " + channel.JP_920_6Mhz);
				Log.i(TAG, "GetJapanChannel (JP_920_8Mhz) = " + channel.JP_920_8Mhz);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}

	// SetJapanChannel
	public void ForSetJapanChannelTest() {
		try {
			int re = 0;

			JapanChannel M_channel = new JapanChannel();
			M_channel.JP_916_8Mhz = true;
			M_channel.JP_920_8Mhz = true;
			re = mRfidManager.SetJapanChannel(M_channel);


			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);

			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// GetContinuousInventoryTime
	public void ForGetContinuousInventoryTimeTest() {
		try {
			ContinuousInventoryTime time = new ContinuousInventoryTime();
			int re = mRfidManager.GetContinuousInventoryTime(time);
			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);
			}
			else
			{
				Log.i(TAG, "GetContinuousInventoryTime (InventoryTime) = " + time.InventoryTime);
				Log.i(TAG, "GetContinuousInventoryTime (DelayTime) = " + time.DelayTime);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// SetContinuousInventoryTime
	public void ForSetContinuousInventoryTimeTest() 
	{
		try {
			int re = 0;

			ContinuousInventoryTime time = new ContinuousInventoryTime();
			time.InventoryTime = 500;
			time.DelayTime = 500;

			re = mRfidManager.SetContinuousInventoryTime(time);

			if (re != ClResult.S_OK.ordinal()) {
				String m = mRfidManager.GetLastError();
				Log.e(TAG, "GetLastError = " + m);

			}

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	//GetPowerMode
	public void ForGetPowerModeTest() {
		try {
			PowerMode mode = mRfidManager.GetPowerMode();
			if(mode == PowerMode.Err)
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}
			Log.w(TAG, "ForGetPowerModeTest = " + mode );
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	//SetPowerMode
	public void ForSetPowerModeTest() {
		try {
			int re = mRfidManager.SetPowerMode(PowerMode.Normal);
			if(re!=ClResult.S_OK.ordinal())
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// GetTriggerSwitchMode
	public void ForGetTriggerSwitchModeTest()
	{
		try {
			TriggerSwitchMode TSM = new TriggerSwitchMode();
			TSM = mRfidManager.GetTriggerSwitchMode();
			if(TSM==null)
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}
			else
			{
				Log.w(TAG, "GetTriggerSwitchMode (TriggerSwitchStatus)= " + TSM.TriggerSwitchStatus );
				Log.w(TAG, "GetTriggerSwitchMode (CurrentSwitchMode)= " + TSM.CurrentSwitchMode );
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// SetTriggerSwitchMode
	public void ForSetTriggerSwitchModeTest()
	{
		int re = mRfidManager.SetTriggerSwitchMode(true);
		if(re!=ClResult.S_OK.ordinal())
		{
			String err = mRfidManager.GetLastError();
			Log.e(TAG, "SetChangeSwitchMode (err) = " + err);
		}
	}
	
	// GetSwitchMode
	public void ForGetSwitchModeTest()
	{
		try {
			SwitchMode mode = mRfidManager.GetSwitchMode();
			if(mode==SwitchMode.Err)
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}
			
			Log.w(TAG, "ForGetSwitchModeTest = " + mode );
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// SetSwitchMode
	public void ForSetSwitchModeTest() {
		try {
			//int re = mRfidManager.SetSwitchMode(SwitchMode.UHFRFIDReader);
			int re = mRfidManager.SetSwitchMode(SwitchMode.BarcodeReader);
			//int re = mRfidManager.SetSwitchMode(SwitchMode.UHFRFIDBarcodeReader);
			if(re!=ClResult.S_OK.ordinal())
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	// GetModuleUuniqueID
	public void ForGetModuleUuniqueIDTest()
	{
		try {
			int ID = mRfidManager.GetModuleUniqueID();
			if(ID==-1)
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}
			else
			{
				Log.d(TAG, "GetModuleUuniqueID = " + ID);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	//GetFilterDuplicate
	public void ForGetFilterDuplicateTest()
	{
		try {
			int status = mRfidManager.GetFilterDuplicate();
			if(status==-1)
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}
			else
			{
				Log.d(TAG, "GetFilterDuplicate = " + status);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	
	//SetFilterDuplicate
	public void ForSetFilterDuplicateTest()
	{
		try {
			int status = mRfidManager.SetFilterDuplicate(1);
			if(status!=ClResult.S_OK.getValue())
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}

	// ClearFilterDuplicate
	public void ForClearFilterDuplicateTest()
	{
		try {
			int status = mRfidManager.ClearFilterDuplicate();
			if(status!=ClResult.S_OK.getValue())
			{
				String m = mRfidManager.GetLastError();
                Log.e(TAG, "GetLastError = " + m);
			}

		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Exception = " + e.getMessage());
		}
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		
		unregisterReceiver(myDataReceiver);
		
		mRfidManager.Release();
	}
	
	private final BroadcastReceiver myDataReceiver = new BroadcastReceiver() 
	{
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(GeneralString.Intent_RFIDSERVICE_CONNECTED)) 
			{
				String PackageName = intent.getStringExtra("PackageName");
				
				// / make sure this AP does already connect with RFID service (after call RfidManager.InitInstance(this)
				String ver = "";
				ver = mRfidManager.GetServiceVersion();
				String api_ver = mRfidManager.GetAPIVersion();
				tv1.setText(PackageName + "," + ver + " , " + api_ver);
				ForTest();
				
				String m_PackageName = PackageName;
				Toast.makeText(MainActivity.this,  m_PackageName, Toast.LENGTH_SHORT).show();
				//Toast.makeText(MainActivity.this,  "Intent_RFIDSERVICE_CONNECTED", Toast.LENGTH_SHORT).show();
			}
			else if(intent.getAction().equals(GeneralString.Intent_RFIDSERVICE_TAG_DATA))
			{
				/* 
				 * type : 0=Normal scan (Press Trigger Key to receive the data) ; 1=Inventory EPC ; 2=Inventory ECP TID ; 3=Reader tag ; 5=Write tag ; 6=Lock tag ; 7=Kill tag ; 8=Authenticate tag ; 9=Untraceable tag
				 * response : 0=RESPONSE_OPERATION_SUCCESS ; 1=RESPONSE_OPERATION_FINISH ; 2=RESPONSE_OPERATION_TIMEOUT_FAIL ; 6=RESPONSE_PASSWORD_FAIL ; 7=RESPONSE_OPERATION_FAIL ;251=DEVICE_BUSY
				 * */
				
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
				
				String Data = "response = " + response + " , EPC = " + EPC + "\r TID = " + TID;

				tv1.setText(Data);
				Log.w(TAG, "++++ [Intent_RFIDSERVICE_TAG_DATA] ++++");
				Log.d(TAG, "[Intent_RFIDSERVICE_TAG_DATA] type=" + type + ", response=" + response + ", data_rssi="+data_rssi   );
				Log.d(TAG, "[Intent_RFIDSERVICE_TAG_DATA] PC=" + PC );
				Log.d(TAG, "[Intent_RFIDSERVICE_TAG_DATA] EPC=" + EPC );
				Log.d(TAG, "[Intent_RFIDSERVICE_TAG_DATA] EPC_length=" + EPC_length );
				Log.d(TAG, "[Intent_RFIDSERVICE_TAG_DATA] TID=" + TID );
				Log.d(TAG, "[Intent_RFIDSERVICE_TAG_DATA] TID_length=" + TID_length );
				Log.d(TAG, "[Intent_RFIDSERVICE_TAG_DATA] ReadData=" + ReadData );
				Log.d(TAG, "[Intent_RFIDSERVICE_TAG_DATA] ReadData_length=" + ReadData_length );
				
				// If type=8 ; Authenticate response data in ReadData
				/*if(type==GeneralString.TYPE_AUTHENTICATE_TAG && response==GeneralString.RESPONSE_OPERATION_SUCCESS)
				{
					Log.i(TAG, "Authenticate response data=" + ReadData );
				}*/
			}
			
			//Intent_RFIDSERVICE_EVENT
			else if(intent.getAction().equals(GeneralString.Intent_RFIDSERVICE_EVENT))
			{
				int event  = intent.getIntExtra(GeneralString.EXTRA_EVENT_MASK, -1);
				Log.d(TAG, "[Intent_RFIDSERVICE_EVENT] DeviceEvent=" + event );
				if(event == DeviceEvent.LowBattery.getValue())
				{
					Log.i(GeneralString.TAG, "LowBattery " );
				}
				else if(event == DeviceEvent.PowerSavingMode.getValue() )
				{
					Log.i(GeneralString.TAG, "PowerSavingMode " );
				}
				else if(event == DeviceEvent.OverTemperature.getValue())
				{
					Log.i(GeneralString.TAG, "OverTemperature " );
					
				}
				else if(event == DeviceEvent.ScannerFailure.getValue())
				{
					Log.i(GeneralString.TAG, "ScannerFailure " );
				}
				
			}
			else if(intent.getAction().equals(GeneralString.Intent_FWUpdate_ErrorMessage))
			{
				/*String mse = "";
				mse = intent.getStringExtra(GeneralString.FWUpdate_ErrorMessage);
				int errorcode = intent.getIntExtra(GeneralString.FWUpdate_ErrorCode,-1);
				if(mse!=null)
				{
					Log.d(TAG,  "FWUpdate Error : " + mse  + "(" + errorcode+")");
					Toast.makeText(MainActivity.this,  mse, Toast.LENGTH_SHORT).show();
					
					if(errorcode==FWUpdateErrorCode.SameVersion.getValue())
					{
						Log.d(TAG,  "SameVersion");
					}
				}
				Log.d(TAG,  "Intent_FWUpdate_ErrorMessage" );*/
			}
			else if(intent.getAction().equals(GeneralString.Intent_FWUpdate_Percent))
			{
				int i = intent.getIntExtra(GeneralString.FWUpdate_Percent,0);
				if(i>=0)
				{
					tv1.setText( Integer.toString(i));
				}
				Log.d(TAG,  "Intent_FWUpdate_Percent" );
			}
			else if(intent.getAction().equals(GeneralString.Intent_FWUpdate_Finish))
			{
				Log.d(TAG,  "Intent_FWUpdate_Finish" );
				Toast.makeText(MainActivity.this,  "Intent_FWUpdate_Finish", Toast.LENGTH_SHORT).show();
			}
			else if(intent.getAction().equals(GeneralString.Intent_GUN_Attached))
			{
				Log.d(TAG,  "Intent_GUN_Attached" );
				Toast.makeText(MainActivity.this,  "Intent_GUN_Attached", Toast.LENGTH_SHORT).show();
			}
			else if(intent.getAction().equals(GeneralString.Intent_GUN_Unattached))
			{
				Log.d(TAG,  "Intent_GUN_Unattached" );
				Toast.makeText(MainActivity.this,  "Intent_GUN_Unattached", Toast.LENGTH_SHORT).show();
			}
			else if(intent.getAction().equals(GeneralString.Intent_GUN_Power))
			{
				Log.d(TAG,  "Intent_GUN_Power" );
				boolean AC = intent.getBooleanExtra(GeneralString.Data_GUN_ACPower, false);
				boolean Connect = intent.getBooleanExtra(GeneralString.Data_GUN_Connect, false);
			}
			
		}
	};
	
}
