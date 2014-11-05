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

package com.paycloud.bluetoothapi;

import android.R.integer;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeClass{
    private final static String TAG = BluetoothLeClass.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private Boolean mBusy = false;
    
    private BluetoothAPI mBTApi= null;
    
    private byte[] mSyncData = new byte[144*3 + 5];
    private int mCurrentPacketIndex = 0;
    
	public interface OnConnectListener {
		public void onConnect(BluetoothGatt gatt);
	}
	public interface OnDisconnectListener {
		public void onDisconnect(BluetoothGatt gatt);
	}
	public interface OnServiceDiscoverListener {
		public void onServiceDiscover(BluetoothGatt gatt);
	}
	public interface OnDataAvailableListener {
		 public void onCharacteristicRead(BluetoothGatt gatt,
		            BluetoothGattCharacteristic characteristic,
		            int status);
		 public void onCharacteristicWrite(BluetoothGatt gatt,
	                BluetoothGattCharacteristic characteristic);
	}
    
	private OnConnectListener mOnConnectListener;
	private OnDisconnectListener mOnDisconnectListener;
	private OnServiceDiscoverListener mOnServiceDiscoverListener;
	private OnDataAvailableListener mOnDataAvailableListener;
	private Context mContext;
	public void setOnConnectListener(OnConnectListener l){
		mOnConnectListener = l;
	}
	public void setOnDisconnectListener(OnDisconnectListener l){
		mOnDisconnectListener = l;
	}
	public void setOnServiceDiscoverListener(OnServiceDiscoverListener l){
		mOnServiceDiscoverListener = l;
	}
	public void setOnDataAvailableListener(OnDataAvailableListener l){
		mOnDataAvailableListener = l;
	}
	
	public BluetoothLeClass(Context c, BluetoothAPI BTApi){
		mContext = c;
		mBTApi = BTApi;
	}
	
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
            	if(mOnConnectListener!=null)
            		mOnConnectListener.onConnect(gatt);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if(mOnDisconnectListener!=null)
                	mOnDisconnectListener.onDisconnect(gatt);
                Log.i(TAG, "Disconnected from GATT server."); 
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && mOnServiceDiscoverListener!=null) {
                	mOnServiceDiscoverListener.onServiceDiscover(gatt);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        	if (mOnDataAvailableListener!=null)
        		mOnDataAvailableListener.onCharacteristicRead(gatt, characteristic, status);
        }

//        @Override
//        public void onCharacteristicChanged(BluetoothGatt gatt,
//                                            BluetoothGattCharacteristic characteristic) {
//        	if (mOnDataAvailableListener!=null)
//        		mOnDataAvailableListener.onCharacteristicWrite(gatt, characteristic);
//        }
        
      
    		@Override
    		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    			mBusy = false;
    			byte[] value = characteristic.getValue();
    			Log.d("Got Sync Data", Utils.bytesToHexString(value));

    			if(value.length > 20 || value.length < 3)
    			{
    				mBTApi.cleanupTimeoutProc();
    				mBTApi.setBusyStatus(false);
    				mBTApi.callbackError(BluetoothResponse.ERR_CODE_OP_FAILED, "同步失败！");
    				close();
    				return;
    			}
    			byte b1 = value[0];
    			byte b2 = value[1];
    			byte b3 = value[2];
    			
    			if(b1 == 0x5a && b2 ==4)//testing only
    			{
					BluetoothGattService gattService = gatt.getService(UUID.fromString(BluetoothAPI.UUID_KEY_SERVICE));
    				if (gattService != null) 
    				{
    					BluetoothGattCharacteristic mHRCPcharac2 = gattService.getCharacteristic(UUID.fromString(BluetoothAPI.UUID_KEY_DATA_WIRTE));

    					byte[] temp = new byte[3];
    					temp[0] = 0x5b;
    					temp[1] = 4;
    					temp[2] = value[2];
    					mHRCPcharac2.setValue(temp);
    					//mBLE.mBluetoothGatt.beginReliableWrite();
    					writeCharacteristic(mHRCPcharac2);
    					try {
							Thread.sleep(15);
						} catch (Exception e) {}

    					if(b3 == -1)
    					{
        					mBTApi.cleanupTimeoutProc();
            				mBTApi.setBusyStatus(false);
            				mBTApi.callbackData(mSyncData, null);
            				disconnect();
    					}
    				}
    				
    				return;
    			}
    			
    			if(b1 == 0x5a && b2 == 3)
    			{
        			if(b3 > 8 || b3 < -2)
        			{
        				mBTApi.cleanupTimeoutProc();
        				mBTApi.setBusyStatus(false);
        				mBTApi.callbackError(BluetoothResponse.ERR_CODE_OP_FAILED, "同步数据有问题，失败！");
        				close();
        				return;
        			}

					if(b3 == 1)
					{
						Arrays.fill(mSyncData, (byte) 0);
						mCurrentPacketIndex = 0;
					}

					int pckIndex;
					
					if(b3 == -2 || b3 == -1) pckIndex = mCurrentPacketIndex;
        			else
        			{
        				if(b3 > mCurrentPacketIndex) mCurrentPacketIndex = b3;
        				pckIndex = b3 - 1;
        			}
					
					System.arraycopy(value, 3, mSyncData, pckIndex * 17, value.length - 3);
					mBTApi.callbackProgress(((float)(pckIndex + 1)) / 9.0f);

					BluetoothGattService gattService = gatt.getService(UUID.fromString(BluetoothAPI.UUID_KEY_SERVICE));
    				if (gattService != null) 
    				{
    					BluetoothGattCharacteristic mHRCPcharac2 = gattService.getCharacteristic(UUID.fromString(BluetoothAPI.UUID_KEY_DATA_WIRTE));

    					byte[] temp = new byte[3];
    					temp[0] = 0x5b;
    					temp[1] = 3;
    					temp[2] = value[2];
    					mHRCPcharac2.setValue(temp);
    					//mBLE.mBluetoothGatt.beginReliableWrite();
    					writeCharacteristic(mHRCPcharac2);
    					//try {
							//Thread.sleep(15);
						//} catch (Exception e) {}
    				}

    				if(b3 == -1)
        			{
        				mBTApi.cleanupTimeoutProc();
        				mBTApi.setBusyStatus(false);
        				mBTApi.callbackData(mSyncData, null);
        				disconnect();
        				return;
        			}
    			}
    			else
    			{
    				mBTApi.cleanupTimeoutProc();
    				mBTApi.setBusyStatus(false);
    				mBTApi.callbackData(value, null);
    				disconnect();
    			}
    		}
    		
    	    /**
    	     * Callback indicating the result of a characteristic write operation.
    	     *
    	     * <p>If this callback is invoked while a reliable write transaction is
    	     * in progress, the value of the characteristic represents the value
    	     * reported by the remote device. An application should compare this
    	     * value to the desired value to be written. If the values don't match,
    	     * the application must abort the reliable write transaction.
    	     *
    	     * @param gatt GATT client invoked {@link BluetoothGatt#writeCharacteristic}
    	     * @param characteristic Characteristic that was written to the associated
    	     *                       remote device.
    	     * @param status The result of the write operation
    	     *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
    	     */
    	    public void onCharacteristicWrite(BluetoothGatt gatt,
    	                                      BluetoothGattCharacteristic characteristic, int status) {
    	    	
    	    	//if(status == 0) gatt.readCharacteristic(characteristic);
    	    	mBusy = false;
                Log.e(TAG, "onCharacteristicWrite received: " + status);
    	    }

    	    /**
    	     * Callback indicating the result of a descriptor write operation.
    	     *
    	     * @param gatt GATT client invoked {@link BluetoothGatt#writeDescriptor}
    	     * @param descriptor Descriptor that was writte to the associated
    	     *                   remote device.
    	     * @param status The result of the write operation
    	     *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
    	     */
    	    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
    	                                  int status) {
    	    	mBusy = false;
                Log.e(TAG, "onDescriptorWrite received: " + status);
    	    }
   	    
    };

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if(mBluetoothAdapter != null) mBluetoothAdapter.stopLeScan(null);

        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
    
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
//    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
//                                              boolean enabled) {
//        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
//            Log.w(TAG, "BluetoothAdapter not initialized");
//            return;
//        }
//        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
//    }

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
//		if (!checkGatt())
//			return false;
		System.out.println("1----setCharacteristicNotification");
		if (!mBluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
			Log.w(TAG, "setCharacteristicNotification failed");
			return false;
		}
		
		//mBusy = true;
		//BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(Gatt.CLIENT_CHARACTERISTIC_CONFIG);
		BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));			    
		//BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(UUID.fromString(STEP_COUNT_SERVER_CONF));
		
		if (clientConfig == null){
			System.out.println("---clientConfig == null");
			return false;
		}
		
		if (enable) {
			System.out.println("enable notification");
			clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			//clientConfig.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
		} else {
			System.out.println("disable notification");
			clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
		}
		mBusy = true;
		return mBluetoothGatt.writeDescriptor(clientConfig);
	}

    
    
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic){
    	
    	//characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
    	mBusy = true;
    	mBluetoothGatt.writeCharacteristic(characteristic);
    }
    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
    
    //wait 2 seconds or until idle
    public Boolean waitUntilIdle()
    {
    	for(int i = 0; i < 300; i++)
    	{
    		if(!mBusy) return true;
    		
    		try {
    			Thread.sleep(10);
			} catch (Exception e) {}
    	}
    	
    	return false;
    }
}
