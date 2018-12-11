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

    private byte[] cmd = {(byte)0x10};
    private byte[][] deviceList = new byte[8][9];
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
            }
        }
    };

    private Handler handler = new Handler( );
    private Runnable runnable = new Runnable( ) {
        public void run ( ) {
            Toast myToast = Toast.makeText(DeviceControlActivity.this, "Handler read",Toast.LENGTH_LONG);
            myToast.show() ;
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
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                }
            }
            //handler.postDelayed(this,1000);
            //handler.postDelayed(runnable,1000); // start Timer
            //handler.removeCallbacks(runnable); //stop Timer
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
                    if(true) {
                        byte[][] d_list = {
                                {(byte)0x00,(byte)0x8A,(byte)0x7F,(byte)0x7B,(byte)0xFE,(byte)0xFF,(byte)0x9F,(byte)0xFD,(byte)0x90},
                                {(byte)0x00,(byte)0x50,(byte)0x81,(byte)0x7B,(byte)0xFE,(byte)0xFF,(byte)0x9F,(byte)0xFD,(byte)0x90},
                                {(byte)0x00,(byte)0xC2,(byte)0x53,(byte)0xBE,(byte)0xFE,(byte)0xFF,(byte)0x57,(byte)0x0B,(byte)0x00},
                        };

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
                                Toast myToast = Toast.makeText(DeviceControlActivity.this, "groupPosition", Toast.LENGTH_LONG);
                                myToast.show();
                                byte[] dataByte = mBluetoothLeService.getCharacteristicData();
                                System.arraycopy(d_list[groupPosition], 0, dataByte, 0, d_list[groupPosition].length);
                                dataByte[9] = 0x10;
                                mBluetoothLeService.writeCharacteristic(characteristic, dataByte);
                                //timer.schedule(task,100);
                                handler.postDelayed(runnable, 100);
                            }
                            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                mNotifyCharacteristic = characteristic;
                                mBluetoothLeService.setCharacteristicNotification(
                                        characteristic, true);
                            }
                        }
                    }else {
                        if (mGattCharacteristics != null) {
                            final BluetoothGattCharacteristic characteristic =
                                    mGattCharacteristics.get(groupPosition).get(childPosition);
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
                            }
                            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                mNotifyCharacteristic = characteristic;
                                mBluetoothLeService.setCharacteristicNotification(
                                        characteristic, true);
                            }
                            return true;
                        }
                    }
                    return false;
                }

    };


    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
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
            mButton_r.setBackgroundColor(0xFFFF0000);
            Toast myToast = Toast.makeText(DeviceControlActivity.this, "Characteristic read",Toast.LENGTH_LONG);
            myToast.show() ;
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
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                }
            }
        }
    };

    Button.OnClickListener listener_w = new Button.OnClickListener() {
        public void onClick(View v) {
            mButton_w.setBackgroundColor(0xFF00FF00);
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
                    Toast myToast = Toast.makeText(DeviceControlActivity.this, "Characteristic write" + mBluetoothLeService.byteArrayToString(cmd),Toast.LENGTH_LONG);
                    myToast.show() ;
                    byte[] dataByte = mBluetoothLeService.getCharacteristicData();
                    dataByte[9] = cmd[0];
                    if(cmd[0]++ > 0x1A)
                        cmd[0] = 0x10;
                    mBluetoothLeService.writeCharacteristic(characteristic,dataByte);
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                }
            }
        }
    };

    Button.OnClickListener listener_n = new Button.OnClickListener() {
        public void onClick(View v) {
            mButton_n.setBackgroundColor(0xFF0000FF);
            Toast myToast = Toast.makeText(DeviceControlActivity.this, "Characteristic noticfy",Toast.LENGTH_LONG);
            myToast.show() ;
            if(true) {
                //Create Menu
                String[] names = {"12345678", "abcdefgh", "wxyz4321"};
                //Create subMenu
                String[] child_names = {"Temperatrue: 32 'C", "Humidity: 50%", "Pressure:343", "Amblight:453", "UV index: 2", "Mic: ", "IAQ Tvoc:3445", "IAQ eco2:222","Hall status: Open","Hall value:1234"};
                device_list.clear();
                sensor_items.clear();
                for (int i = 0; i < names.length; i++) {
                    Map<String, String> namedata = new HashMap<String, String>();
                    namedata.put("names", names[i]);
                    device_list.add(namedata);

                    List<Map<String, String>> child_map = new ArrayList<Map<String, String>>();
                    for (int j = 0; j < child_names.length; j++) {
                        Map<String, String> mapcs = new HashMap<String, String>();
                        mapcs.put("child_names", child_names[j]);
                        child_map.add(mapcs);
                    }
                    sensor_items.add(child_map);
                }
            }
        }
    };

    Button.OnClickListener listener_dmp = new Button.OnClickListener() {
        public void onClick(View v) {
            mButton_dmp.setBackgroundColor(0xFF0000FF);

            Toast myToast = Toast.makeText(DeviceControlActivity.this, "Characteristic dmp",Toast.LENGTH_LONG);
            myToast.show() ;
            if(true)
            {
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
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
