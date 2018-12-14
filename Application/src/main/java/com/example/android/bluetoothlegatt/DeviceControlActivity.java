/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    /*
    public static final int D_Temperatrue   = (1 << 0);
    public static final int D_Humidity      = (1 << 1);
    public static final int D_Pressure      = (1 << 2);
    public static final int D_Amblight      = (1 << 3);
    public static final int D_UV_index      = (1 << 4);
    public static final int D_Mic           = (1 << 5);
    public static final int D_IAQ_Tvoc      = (1 << 6);
    public static final int D_IAQ_eco2      = (1 << 7);
    public static final int D_Hall_status   = (1 << 8);
    public static final int D_Hall_value    = (1 << 9);
    public static final int D_WMeter        = (1 << 10);
    public static final int D_NONE          = 0;
    public static final int D_ALL_SENSOR    = (D_Temperatrue|D_Humidity|D_Pressure|D_Amblight|D_UV_index|D_Mic|D_IAQ_Tvoc|D_IAQ_eco2|D_Hall_status|D_Hall_value);
    */
    public static final byte D_deviceMaxNone = 127;
	
    public static final byte D_packetTemperature = 0;
    public static final byte D_packetHumidity = 1;
    public static final byte D_packetPressure = 2;
    public static final byte D_packetAmbLight = 3;
    public static final byte D_packetUVIndex = 4;
    public static final byte D_packetMic = 5;
    public static final byte D_packetECO2 = 6;
    public static final byte D_packetTVOC = 7;
    public static final byte D_packetHallState = 8;
    public static final byte D_packetHallMagneticField = 9;
    public static final byte D_packetLinkInfo = 10;
    public static final byte D_packetErrorInfo = 11;

    public static final byte M_SENSOR_TEMPERATURE = 16;
    public static final byte M_SENSOR_HUMIDITY = 17;
    public static final byte M_SENSOR_PRESSURE = 18;
    public static final byte M_SENSOR_AMBLIGHT = 19;
    public static final byte M_SENSOR_UVINDEX = 20;
    public static final byte M_SENSOR_MIC = 21;
    public static final byte M_SENSOR_ECO2 = 22;
    public static final byte M_SENSOR_TVOC = 23;
    public static final byte M_SENSOR_HALLSTATE = 24;
    public static final byte M_SENSOR_HALLMAGNETICFIELD = 25;
    public static final byte M_APP_REQUEST_LINK_NODE = 26;
    public static final byte M_APP_ERROR_INFO = 27;

    public static final int D_RetryTimeOut  = 3;

    private byte[] cmd = new byte[16];
    private byte[][] deviceList = new byte[8][10];
    private byte deviceIndex = 0;
    private byte deviceMax = D_deviceMaxNone;
    private int deviceDataReadIndex = 0;
    private byte characteristicReadRetry;

    private int [] temperature = {25000,25000,25000,25000,25000,25000,25000,25000};
    private long [] humidity = {50000,50000,50000,50000,50000,50000,50000,50000};
    private float [] pressure = {0,0,0,0,0,0,0,0};
    private long [] ambLight = {0,0,0,0,0,0,0,0};
    private int [] uvIndex = {0,0,0,0,0,0,0,0};
    private float [] mic = {0,0,0,0,0,0,0,0};
    private int [] eco2 = {0,0,0,0,0,0,0,0};
    private int [] tvoc = {0,0,0,0,0,0,0,0};
    private byte [] hallState = {1,1,1,1,1,1,1,1};
    private float [] hallMagneticField = {0,0,0,0,0,0,0,0};
    //Create Menu
    private String[] names = new String[8];
    //Create subMenu
    String child_names[][] = new String[8][10];

    private Button mButton_r;
    private Button mButton_w;
    private Button mButton_n;
    private Button mButton_dmp;
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    List<Map<String, String>> device_list = new ArrayList<Map<String, String>>();
    List<List<Map<String, String>>> sensor_items = new ArrayList<List<Map<String, String>>>();

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
		        displayDeviceData(mBluetoothLeService.getCharacteristicData());
            }
        }
    };

    private Handler characteristicHandler = new Handler( );
    private Runnable runnableWrite = new Runnable( ) {
        public void run ( ) {
            //Toast myToast = Toast.makeText(DeviceControlActivity.this, "Handler write",Toast.LENGTH_LONG);
            //myToast.show() ;
            if(cmd[9] == M_APP_REQUEST_LINK_NODE){
                if((deviceMax > 8)&&(deviceMax != D_deviceMaxNone))
                    return;
                //if(deviceMax == 0)
                //    return;
                //if(deviceIndex == deviceMax)
                //    return;
                cmd[10] = deviceIndex;
                cmd[11] = 0;
            }
            if (mGattCharacteristics != null) {
                final BluetoothGattCharacteristic characteristic =
                        mGattCharacteristics.get(3).get(0);
                final int charaProp = characteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    // If there is an active notification on a characteristic, clear
                    // it first so it doesn't update the data field on the user interface.
                    if (mNotifyCharacteristic != null) {
                        mBluetoothLeService.setCharacteristicNotification(
                                mNotifyCharacteristic, false);
                        mNotifyCharacteristic = null;
                    }
			/*
                    switch(cmd[9])
                    {
                        case M_SENSOR_TEMPERATURE:break;
                        case M_SENSOR_HUMIDITY:break;
                        case M_SENSOR_PRESSURE:break;
                        case M_SENSOR_AMBLIGHT:break;
                        case M_SENSOR_UVINDEX:break;
                        case M_SENSOR_MIC:break;
                        case M_SENSOR_ECO2:break;
                        case M_SENSOR_TVOC:break;
                        case M_SENSOR_HALLSTATE:break;
                        case M_SENSOR_HALLMAGNETICFIELD:break;
                        case M_APP_REQUEST_LINK_NODE:break;
                        //case M_APP_ERROR_INFO:break;
                        default:
                            //return;
                            cmd[9] = M_SENSOR_TEMPERATURE;
                            break;
                    }
					*/
                    mBluetoothLeService.writeCharacteristic(characteristic, cmd);
                    characteristicHandler.removeCallbacks(runnableWrite);
                    characteristicHandler.removeCallbacks(runnableRead);
                    characteristicHandler.postDelayed(runnableRead, 100);
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                }
            }
        }
    };
	
    private Runnable runnableRead = new Runnable( ) {
        public void run ( ) {
            //Toast myToast = Toast.makeText(DeviceControlActivity.this, "Handler read",Toast.LENGTH_LONG);
            //myToast.show() ;
            if (mGattCharacteristics != null) {
                final BluetoothGattCharacteristic characteristic =
                        mGattCharacteristics.get(3).get(0);
                final int charaProp = characteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    // If there is an active notification on a characteristic, clear
                    // it first so it doesn't update the data field on the user interface.
                    if (mNotifyCharacteristic != null) {
                        mBluetoothLeService.setCharacteristicNotification(
                                mNotifyCharacteristic, false);
                        mNotifyCharacteristic = null;
                    }

                    mBluetoothLeService.readCharacteristic(characteristic);
			        if(cmd[9] < M_APP_REQUEST_LINK_NODE) {
						if(false){
                            cmd[9]++;
                            characteristicHandler.removeCallbacks(runnableWrite);
                            characteristicHandler.removeCallbacks(runnableRead);
                            characteristicHandler.postDelayed(runnableWrite, 100);
                        }else{
                            characteristicHandler.postDelayed(runnableRead, 100);
                        }
                    }
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                }
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(3).get(0);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            //Toast myToast = Toast.makeText(DeviceControlActivity.this, "groupPosition", Toast.LENGTH_LONG);
                            //myToast.show();
                            //cmd = mBluetoothLeService.getCharacteristicData();
                            deviceDataReadIndex = groupPosition;
                            System.arraycopy(deviceList[deviceDataReadIndex], 0, cmd, 0, deviceList[deviceDataReadIndex].length);
                            cmd[9] = M_SENSOR_TEMPERATURE;
                            characteristicHandler.removeCallbacks(runnableWrite);
                            characteristicHandler.removeCallbacks(runnableRead);
                            characteristicHandler.postDelayed(runnableWrite, 100);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                    }
                    return false;
                }

    };

    private final ExpandableListView.OnGroupClickListener deviceListClickListner =
            new ExpandableListView.OnGroupClickListener() {
                @Override
                public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                    characteristicHandler.removeCallbacks(runnableWrite);
                    characteristicHandler.removeCallbacks(runnableRead);
                    //mGattServicesList.expandGroup(groupPosition);
                    return false;
                }

    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
        characteristicHandler.removeCallbacks(runnableWrite);
        characteristicHandler.removeCallbacks(runnableRead);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
		mGattServicesList.setOnGroupClickListener(deviceListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        mButton_r = (Button) findViewById(R.id.button_r);
        mButton_w = (Button) findViewById(R.id.button_w);
        mButton_n = (Button) findViewById(R.id.button_n);
        mButton_dmp = (Button) findViewById(R.id.button_dmp);
        mButton_r.setOnClickListener(listener_r);
        mButton_w.setOnClickListener(listener_w);
        mButton_n.setOnClickListener(listener_n);
        mButton_dmp.setOnClickListener(listener_dmp);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    Button.OnClickListener listener_r = new Button.OnClickListener() {
        public void onClick(View v) {
            //mButton_r.setBackgroundColor(0xFFFF0000);
            Toast myToast = Toast.makeText(DeviceControlActivity.this, "read",Toast.LENGTH_LONG);
            myToast.show();
        }
    };

    Button.OnClickListener listener_w = new Button.OnClickListener() {
        public void onClick(View v) {
            //mButton_w.setBackgroundColor(0xFF00FF00);
            Toast myToast = Toast.makeText(DeviceControlActivity.this, "writes",Toast.LENGTH_LONG);
            myToast.show() ;
        }
    };

    Button.OnClickListener listener_n = new Button.OnClickListener() {
        public void onClick(View v) {
            //mButton_n.setBackgroundColor(0xFF0000FF);
            Toast myToast = Toast.makeText(DeviceControlActivity.this, "noticfy",Toast.LENGTH_LONG);
            myToast.show() ;
        }
    };

    Button.OnClickListener listener_dmp = new Button.OnClickListener() {
        public void onClick(View v) {
            //mButton_dmp.setBackgroundColor(0xFF0000FF);
            //Toast myToast = Toast.makeText(DeviceControlActivity.this, "dmp",Toast.LENGTH_LONG);
            //myToast.show() ;
            if(mConnected){
				deviceIndex = 0;
				deviceMax = D_deviceMaxNone;
				// Clear list
                mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);

                child_names[0] = new String[10];
                child_names[1] = new String[10];
                child_names[2] = new String[10];
                child_names[3] = new String[10];
                child_names[4] = new String[10];
                child_names[5] = new String[10];
                child_names[6] = new String[10];
                child_names[7] = new String[10];

			    cmd[9] = M_APP_REQUEST_LINK_NODE;
				characteristicHandler.removeCallbacks(runnableWrite);
				characteristicHandler.removeCallbacks(runnableRead);
				characteristicHandler.postDelayed(runnableWrite, 100);
            }else{
                SimpleExpandableListAdapter sela = new SimpleExpandableListAdapter(
                        DeviceControlActivity.this,
                        device_list,
                        R.layout.device_list,
                        new String[]{"names"},
                        new int[]{R.id.textGroup},
                        sensor_items,
                        R.layout.sensor_items,
                        new String[]{"child_names"},
                        new int[]{R.id.textChild});
                // Clear list
                mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
                // enqueue
                mGattServicesList.setAdapter(sela);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        characteristicHandler.removeCallbacks(runnableWrite);
        characteristicHandler.removeCallbacks(runnableRead);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        characteristicHandler.removeCallbacks(runnableWrite);
        characteristicHandler.removeCallbacks(runnableRead);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayDeviceData(byte[] data) {
        if (data == null)
            return;
        if(data[9] > D_packetErrorInfo)
            return;

		switch(data[9]){
			case D_packetTemperature:
                temperature[deviceDataReadIndex] = mBluetoothLeService.byte2Int(data,10);
			    break;
			case D_packetHumidity:
                humidity[deviceDataReadIndex] = mBluetoothLeService.getUnsignedIntt(mBluetoothLeService.byte2Int(data,10));
			    break;
			case D_packetPressure:
                pressure[deviceDataReadIndex] = mBluetoothLeService.byte2float(data,10);
			    break;
			case D_packetAmbLight:
                ambLight[deviceDataReadIndex] = mBluetoothLeService.getUnsignedIntt(mBluetoothLeService.byte2Int(data,10));
			    break;
			case D_packetUVIndex:
                uvIndex[deviceDataReadIndex] = mBluetoothLeService.getUnsignedByte(data[10]);
			    break;
			case D_packetMic:
			    mic[deviceDataReadIndex] = mBluetoothLeService.byte2float(data,10);
			    break;
			case D_packetECO2:
                eco2[deviceDataReadIndex] = mBluetoothLeService.byte2UnsignedShort(data,10)&0xFFFF;
			    break;
			case D_packetTVOC:
                tvoc[deviceDataReadIndex] = mBluetoothLeService.byte2UnsignedShort(data,10)&0xFFFF;
			    break;
			case D_packetHallState:
                hallState[deviceDataReadIndex] = data[10];
			    break;
			case D_packetHallMagneticField:
                hallMagneticField[deviceDataReadIndex] = mBluetoothLeService.byte2float(data,10);
			    break;
			case D_packetLinkInfo:

				deviceIndex = data[10];
				deviceMax = data[11];
				if((deviceIndex == 0)||(deviceMax == 0)||(deviceMax > 8)){
					deviceMax = D_deviceMaxNone - 1;
					characteristicHandler.removeCallbacks(runnableWrite);
		                    characteristicHandler.removeCallbacks(runnableRead);
		                    characteristicHandler.postDelayed(runnableRead, 100);
					return;
				}
				System.arraycopy(data, 0, deviceList[deviceIndex - 1], 0, 9);
				if(deviceIndex < deviceMax){
                    characteristicHandler.removeCallbacks(runnableWrite);
                    characteristicHandler.removeCallbacks(runnableRead);
                    characteristicHandler.postDelayed(runnableWrite, 100);
                }
			    break;
			case D_packetErrorInfo:
				
			    break;
            case M_SENSOR_TEMPERATURE:
            case M_SENSOR_HUMIDITY:
            case M_SENSOR_PRESSURE:
            case M_SENSOR_AMBLIGHT:
            case M_SENSOR_UVINDEX:
            case M_SENSOR_MIC:
            case M_SENSOR_ECO2:
            case M_SENSOR_TVOC:
            case M_SENSOR_HALLSTATE:
            case M_SENSOR_HALLMAGNETICFIELD:
                if(cmd[9] != data[9])
                    break;
                characteristicHandler.removeCallbacks(runnableWrite);
                characteristicHandler.removeCallbacks(runnableRead);
                characteristicHandler.postDelayed(runnableRead, 100);
                break;
		}

        //if (data[9] == D_packetLinkInfo) {
        //    for (int i = 0; i < deviceIndex; i++) {
        //        names[i] = "Device" + Integer.toString(i) + " " + mBluetoothLeService.byteArray2String(deviceList[i]).substring(3,27);
        //    }
        //}else 
        if(data[9] < D_packetLinkInfo){
            cmd[9] = (byte)(data[9] + M_SENSOR_TEMPERATURE + 1);
            if(cmd[9] > M_SENSOR_HALLMAGNETICFIELD) {
                cmd[9] = M_SENSOR_TEMPERATURE;
            }
            characteristicHandler.removeCallbacks(runnableWrite);
            characteristicHandler.removeCallbacks(runnableRead);
            characteristicHandler.postDelayed(runnableWrite, 100);

		}
	    for (int i = 0; i < deviceIndex; i++) {
            names[i] = "Device" + Integer.toString(i) + " " + mBluetoothLeService.byteArray2String(deviceList[i]).substring(3,27);
            	
            child_names[i][0] = "Temperatrue:" + Float.toString((float) temperature[i] / 1000.0f) + "℃";
            child_names[i][1] = "Humidity:" + Float.toString((float) humidity[i] / 1000.0f) + "%RH";
            child_names[i][2] = "Pressure:" + Float.toString(pressure[i]) + "mbar";
            child_names[i][3] = "AmbLight:" + Float.toString((float) ambLight[i] / 100.0f) + "Lux";
            child_names[i][4] = "UVIndex:" + Integer.toString(uvIndex[i]);
            child_names[i][5] = "Sound level:" + Float.toString(mic[i]) + "dB";
            child_names[i][6] = "IAQ eCO2:" + Integer.toString(eco2[i]);
            child_names[i][7] = "IAQ TVOC:" + Integer.toString(tvoc[i]);
            child_names[i][8] = "Hall state: " + ((hallState[i] == 0) ? "Close" : "Open");
            child_names[i][9] = "Hall magnetic flux:" + Float.toString(hallMagneticField[i])+ "mT";
        }
		device_list.clear();
        sensor_items.clear();
        for (int i = 0; i < deviceIndex/*names.length*/; i++) {
            Map<String, String> namedata = new HashMap<String, String>();
            namedata.put("names", names[i]);
            device_list.add(namedata);

            List<Map<String, String>> child_map = new ArrayList<Map<String, String>>();
            for (int j = 0; j < child_names[i].length; j++) {
                Map<String, String> mapcs = new HashMap<String, String>();
                mapcs.put("child_names", child_names[i][j]);
                child_map.add(mapcs);
            }
            sensor_items.add(child_map);
        }

        SimpleExpandableListAdapter sela = new SimpleExpandableListAdapter(
                DeviceControlActivity.this,
                device_list,
                R.layout.device_list,
                new String[]{"names"},
                new int[]{R.id.textGroup},
                sensor_items,
                R.layout.sensor_items,
                new String[]{"child_names"},
                new int[]{R.id.textChild});
        // Clear list
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        // enqueue
        mGattServicesList.setAdapter(sela);
        //for(int i = 0;i< deviceIndex;i++ ) {
        //    mGattServicesList.collapseGroup(i);
        //}
        if (data[9] < D_packetLinkInfo) {
            mGattServicesList.expandGroup(deviceDataReadIndex);
        }
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
        if(false) {
        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
