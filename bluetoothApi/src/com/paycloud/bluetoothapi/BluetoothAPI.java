package com.paycloud.bluetoothapi;

import android.R.bool;
import android.R.integer;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.paycloud.bluetoothapi.BluetoothLeClass;
import com.paycloud.bluetoothapi.BluetoothLeClass.OnDataAvailableListener;
import com.paycloud.bluetoothapi.BluetoothLeClass.OnServiceDiscoverListener;

public class BluetoothAPI
{
	private final static String TAG = BluetoothAPI.class.getSimpleName();
	protected final static String UUID_KEY_SERVICE = "0000cc01-0000-1000-8000-00805f9b34fb";
	protected final static String UUID_KEY_DATA = "0000cd01-0000-1000-8000-00805f9b34fb";
	protected final static String UUID_KEY_DATA_WIRTE = "0000cd20-0000-1000-8000-00805f9b34fb";
	// 00002a05-0000-1000-8000-00805f9b34fb
	// 0000ffe1-0000-1000-8000-00805f9b34fb
	
	private final static String BLE_DEVICE_NAME_STRING = "Quintic BLE";

	/** 搜索BLE终端 */
	private BluetoothAdapter mBluetoothAdapter;
	/** 读写BLE终端 */
	private BluetoothLeClass mBLE;
	private boolean mScanning;
	private Handler mHandler;
	private boolean mBusy = false;
	private boolean mConnecting = false;

	private byte mFunction;
	private byte mYear, mMonth, mDay, mHour, mMinute, mSecond, mInterval, mTargetH, mTargetL;

	// Stops scanning after 10 seconds.
	private static final long SCAN_PERIOD = 16000;
	
	private static BluetoothAPI mInstatnceApi;

	private Context mContext = null;
	private String mDeviceAddresString = null;
	
	private BluetoothResponse mCBBluetoothResponse;
	
	//获得BLE session, 如果BLE服务启动失败，有可能返回null
	public static BluetoothAPI getInstance(Context context)
	{
		if(mInstatnceApi == null)
		{
			mInstatnceApi = new BluetoothAPI();
			mInstatnceApi.mContext = context.getApplicationContext();
			mInstatnceApi.mHandler = new Handler();
			if(mInstatnceApi.initializeBLE() < 0) mInstatnceApi = null;
		}
		
		return mInstatnceApi;
	}
	
	//停止BLE服务
	public static void stopBLESession()
	{
		if(mInstatnceApi != null)
		{
			mInstatnceApi.mBLE.close();
			mInstatnceApi.mBLE = null;
			mInstatnceApi.cleanupTimeoutProc();

			final BluetoothManager bluetoothManager = (BluetoothManager) mInstatnceApi.mContext.getSystemService(Context.BLUETOOTH_SERVICE);
			final BluetoothAdapter adapter = bluetoothManager.getAdapter();

			// Checks if Bluetooth is supported on the device.
			if (adapter != null) {
				adapter.disable();
				
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						for(int i =0; i < 10; i++)
						{
							if(adapter.getState() == BluetoothAdapter.STATE_OFF)
							{
								adapter.enable();
								break;
							}
							try {
								Thread.sleep(1000);
							} catch (Exception e) {
								// TODO: handle exception
							}
						}
					}
				}).start();
			}
			
			mInstatnceApi = null;
		}
	}
	
	//新App或解绑后，重新获取第一个找到的设备，如果成功，在cb的onData将返回地址码，调用这个函数就不用再调setDeviceAddress
	//需要检查调用是否成功（可能busy）
	public boolean findFirstDevice(BluetoothResponse cb)
	{
		if(mBusy || cb == null) return false;
		
		mDeviceAddresString = null;
		mBusy = true;
		mCBBluetoothResponse = cb;
		scanLeDevice(true);
		return true;
	}
	
	//已经有绑定Address的时候只需调用这个，不用findFirstDevice
	public void setDeviceAddress(String address)
	{
		mDeviceAddresString = address;
	}

	//获取硬件版本
	public boolean getVersion(BluetoothResponse cb)
	{
		if(mBusy || cb == null) return false;
		if(mDeviceAddresString == null) return false;
		
		mBusy = true;
		mCBBluetoothResponse = cb;
		mFunction = 0x10;
		connectToDevice();
		
		return true;
	}
	
	//获取某天的数据
	public boolean getDataByDate(BluetoothResponse cb, int year, int month, int day)
	{
		if(mBusy || cb == null) return false;
		if(mDeviceAddresString == null) return false;
		
		mBusy = true;
		mCBBluetoothResponse = cb;
		mFunction = 0x03;
		mYear = (byte)(year - 2000);
		mMonth = (byte) month;
		mDay = (byte) day;
		connectToDevice();
		
		return true;
	}
	
	//设置计步数据时间间隔，10-30分钟。目前必须传10。
	public boolean setTimeInterval(BluetoothResponse cb, int interval)
	{
		if(mBusy || cb == null) return false;
		if(mDeviceAddresString == null) return false;
		if(interval < 10 || interval > 30) return false;
		
		mBusy = true;
		mCBBluetoothResponse = cb;
		mFunction = 0x02;
		mInterval = (byte)interval;
		connectToDevice();
		
		return true;
	}

	//设置设备参数，目前仅传时间和目标即可，其他参数都在API内部设固定值。
	//dateTime是系统当前时间， target是运动目标步数，100-200000
	public boolean setDeviceProperty(BluetoothResponse cb, Calendar dateTime, int target)
	{
		if(mBusy || cb == null) return false;
		if(mDeviceAddresString == null) return false;
		if(dateTime == null) return false;
		if(target < 100 || target > 200000) return false;

		int iYear = dateTime.get(Calendar.YEAR);
		if(iYear < 2000 || iYear > 2100) return false;
		mYear = (byte)(iYear - 2000);
		mMonth = (byte)(dateTime.get(Calendar.MONTH) +1);
		mDay = (byte)dateTime.get(Calendar.DATE);
		mHour = (byte)dateTime.get(Calendar.HOUR_OF_DAY);
		mMinute = (byte)dateTime.get(Calendar.MINUTE);
		mSecond = (byte)dateTime.get(Calendar.SECOND);

		target /= 100;
        mTargetH = ((byte)(target >> 8 & 0xff));
        mTargetL = ((byte)(target & 0xff));
		
		mBusy = true;
		mCBBluetoothResponse = cb;
		mFunction = 0x01;
		connectToDevice();
		
		return true;
	}
	
	//注意：获取全部的数据，目前仅测试用，回调数据无用！
	public boolean getAllData(BluetoothResponse cb)
	{
		if(mBusy || cb == null) return false;
		if(mDeviceAddresString == null) return false;
		
		mBusy = true;
		mCBBluetoothResponse = cb;
		mFunction = 0x04;
		connectToDevice();
		
		return true;
	}

	Handler mMainLoopHandler = null;
	
	//internal use, do not call
	protected void callbackData(final byte[] data, final String dataStr)
	{
		if(mMainLoopHandler == null) mMainLoopHandler = new Handler(mContext.getMainLooper());

		Runnable runOnUI = new Runnable()
		{
			@Override
			public void run() {
				mCBBluetoothResponse.onData(data, dataStr);
			}
		};
		mMainLoopHandler.post(runOnUI);	
	}
	
	//internal use, do not call
	protected void callbackError(final int errCode, final String errMsg)
	{
		if(mMainLoopHandler == null) mMainLoopHandler = new Handler(mContext.getMainLooper());

		Runnable runOnUI = new Runnable()
		{
			@Override
			public void run() {
				mCBBluetoothResponse.onError(errCode, errMsg);
			}
		};
		mMainLoopHandler.post(runOnUI);	
	}
	
	//internal use, do not call
	protected void callbackProgress(final float percent)
	{
		if(mMainLoopHandler == null) mMainLoopHandler = new Handler(mContext.getMainLooper());
		
		Runnable runOnUI = new Runnable()
		{
			@Override
			public void run() {
				mCBBluetoothResponse.onProgress(percent);
			}
		};
		mMainLoopHandler.post(runOnUI);	
		
	}

	//internal use, do not call
	protected void setBusyStatus(boolean bBusy)
	{
		mBusy = bBusy;
	}

	private BluetoothAPI(){}
	
	//return -1 if failed, 0 success
	private int initializeBLE()
	{
		
		// Use this check to determine whether BLE is supported on the
		// device.
		// Then you can
		// selectively disable BLE-related features.
		if (!mContext.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			return -1;
		}

		// Initializes a Bluetooth adapter. For API level 18 and above,
		// get a
		// reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			return -1;
		}
		// 开启蓝牙
		mBluetoothAdapter.enable();

		mBLE = new BluetoothLeClass(mContext, this);
		if (!mBLE.initialize()) {
			Log.e(TAG, "Unable to initialize Bluetooth");
			return -1;
		}
		// 发现BLE终端的Service时回调

		// 收到BLE终端数据交互的事件

		// // TODO Auto-generated method stub
		mBLE.setOnServiceDiscoverListener(mOnServiceDiscover);
		mBLE.setOnDataAvailableListener(mOnDataAvailable);

		return 0;
	}
	
	protected void cleanupTimeoutProc()
	{
		synchronized (mHandler) {
			mHandler.removeCallbacksAndMessages(null);
		}
	}

	private void connectToDevice()
	{
		if (mScanning) {
			scanLeDevice(false);
		}

		synchronized (mHandler) {
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					mBusy = false;
					mConnecting = false;
					callbackError(BluetoothResponse.ERR_CODE_TIEMOUT, "设备连接超时！");
					if(mBLE != null) mBLE.close();
				}
			}, 20 * 1000);
		}

		mConnecting = true;

		Runnable run1 = new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				for(int i = 0; i < 1; i++)
				{
					if(mConnecting) mBLE.connect(mDeviceAddresString);
					else return;
					try {
						Thread.sleep(15000);
						if(mConnecting)
						{
							Log.d(TAG, "Disconct and Retrying...");
							if(mBLE != null) mBLE.disconnect();
						}
					} catch (Exception e) {}
				}
			}
		};
		
		new Thread(run1).start();
	}

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			synchronized (mHandler) {
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						mScanning = false;
						mBluetoothAdapter.stopLeScan(mLeScanCallback);
						mBusy = false;
						callbackError(BluetoothResponse.ERR_CODE_TIEMOUT, "没有找到设备！");
					}
				}, SCAN_PERIOD);
			}

			mScanning = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		} else {
			mScanning = false;
			cleanupTimeoutProc();
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}
	}

	/**
	 * 搜索到BLE终端服务的事件
	 */
	private BluetoothLeClass.OnServiceDiscoverListener mOnServiceDiscover = new OnServiceDiscoverListener() {

		@Override
		public void onServiceDiscover(BluetoothGatt gatt) {
			mConnecting = false;
			cleanupTimeoutProc();
			List<BluetoothGattService> gattServices = mBLE.getSupportedGattServices();
			if(gatt == null) callbackError(BluetoothResponse.ERR_CODE_OP_FAILED, "操作失败！");
			else displayGattServices(gatt);
		}
	};

	/**
	 * 收到BLE终端数据交互的事件
	 */
	private BluetoothLeClass.OnDataAvailableListener mOnDataAvailable = new OnDataAvailableListener() {

		/**
		 * BLE终端数据被读的事件
		 */
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS)
				Log.e(TAG,
						"onCharRead "
								+ gatt.getDevice().getName()
								+ " read "
								+ characteristic.getUuid().toString()
								+ " -> "
								+ Utils.bytesToHexString(characteristic
										.getValue()));
		}

		/**
		 * 收到BLE终端写入数据回调
		 */
		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			Log.e(TAG, "onCharWrite " + gatt.getDevice().getName() + " write "
					+ characteristic.getUuid().toString() + " -> "
					+ new String(characteristic.getValue()));
		}
	};

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, int rssi,
				byte[] scanRecord) {
			
			Log.d(TAG, "Got device add:" +device.getAddress());
			Log.d(TAG, "Got device name:" +device.getName());
			
			if(rssi < -91) return;

			if(mDeviceAddresString == null)
			{
				if(device.getName().equals(BLE_DEVICE_NAME_STRING))
				{
					mDeviceAddresString = device.getAddress();
					mBusy = false;
					scanLeDevice(false);
					callbackData(null, mDeviceAddresString);
				}
			}
		}
	};
	
	BluetoothGattCharacteristic mHRCPcharac2 = null;
	BluetoothGattCharacteristic mHRMcharac = null;

	private void displayGattServices(BluetoothGatt gatt) {
		
		//for (BluetoothGattService gattService : gattServices) {
			
		BluetoothGattService gattService = gatt.getService(UUID.fromString(UUID_KEY_SERVICE));
			if (gattService != null) 
			{
				mHRCPcharac2 = gattService.getCharacteristic(UUID.fromString(UUID_KEY_DATA_WIRTE));
				mHRMcharac =  gattService.getCharacteristic(UUID.fromString(UUID_KEY_DATA));

				byte[] temp = null;
				boolean bRequireResponse = false;
				
				switch (mFunction) {
				case 0x10: //version
					temp = new byte[3];
					temp[0] = 0x5A;
					temp[1] = 0x10;
					temp[2] = 0x00;
					bRequireResponse = true;
					break;
					
				case 0x02: //set time interval
					temp = new byte[4];
					temp[0] = 0x5A;
					temp[1] = 0x02;
					temp[2] = 0x00;
					temp[3] = mInterval;
					break;

				case 0x01: //set properties
					temp = new byte[13];
					temp[0] = 0x5A;
					temp[1] = 0x01;
					temp[2] = 0x00;
					temp[3] = mYear;
					temp[4] = mMonth;
					temp[5] = mDay;
					temp[6] = mHour;
					temp[7] = mMinute;
					temp[8] = mSecond;
					temp[9] = mTargetH;
					temp[10] = mTargetL;
					temp[11] = 0;
					temp[12] = 0;
					//temp[13] = 0;
					break;

				case 0x03: //sync data by dates
					temp = new byte[9];
					temp[0] = 0x5A;
					temp[1] = 0x03;
					temp[2] = 0x00;
					temp[3] = mYear;
					temp[4] = mMonth;
					temp[5] = mDay;
					temp[6] = mYear;
					temp[7] = mMonth;
					temp[8] = mDay;
					bRequireResponse = true;
					callbackProgress(0.01f);
					break;

				case 0x04: //sync all data
					temp = new byte[3];
					temp[0] = 0x5A;
					temp[1] = 0x04;
					temp[2] = 0x00;
					bRequireResponse = true;
					break;

				default:
					break;
				}
				
				if(mHRCPcharac2 == null || mHRMcharac == null || temp == null)
				{
					callbackError(BluetoothResponse.ERR_CODE_UNKOWN, "未知错误");
					if(mBLE != null) mBLE.close();
					return;
				}

				final byte[] temp1 = temp;
				final boolean blockbRequireResponse = bRequireResponse;
				
				// 接受Characteristic被写的通知,收到蓝牙模块的数据后会触发mOnDataAvailable.aracteristicWrite()
				boolean x = mBLE.setCharacteristicNotification(mHRMcharac, true);
				
				if(x) Log.e(TAG, "setCharacteristicNotification OK");
				else Log.e(TAG, "setCharacteristicNotification FAILED!");


				Runnable run1 = new Runnable() {
					
					@Override
					public void run() {
						// TODO Auto-generated method stub


						mBLE.waitUntilIdle();

						mHRCPcharac2.setValue(temp1);
						//mBLE.mBluetoothGatt.beginReliableWrite();
						mBLE.writeCharacteristic(mHRCPcharac2);

						mBLE.waitUntilIdle();
						
						
						if(!blockbRequireResponse) 
						{
							mBusy = false;
							callbackData(temp1, null);
							//mBLE.disconnect();
						}

						/*
						Runnable run2 = new Runnable() {
							
							@Override
							public void run() {
								
								mBLE.waitUntilIdle();
								
								// 接受Characteristic被写的通知,收到蓝牙模块的数据后会触发mOnDataAvailable.aracteristicWrite()
								boolean x = mBLE.setCharacteristicNotification(mHRMcharac, true);
								
								if(x) Log.e(TAG, "setCharacteristicNotification OK");
								else Log.e(TAG, "setCharacteristicNotification FAILED!");

								mBLE.waitUntilIdle();
								
								if(!blockbRequireResponse) 
								{
									mBusy = false;
									callbackData(temp1, null);
									mBLE.disconnect();
								}

								// 接受Characteristic被写的通知,收到蓝牙模块的数据后会触发mOnDataAvailable.aracteristicWrite()
								//mBLE.setCharacteristicNotification(mHRMcharac, true);
							}
						};
						new Thread(run2).start();
						*/
					}
				};
				
				new Thread(run1).start();

				if(!bRequireResponse) 
				{
				}
				else
				{
					synchronized (mHandler) {
						mHandler.postDelayed(new Runnable() {
							@Override
							public void run() {
								mBusy = false;
								callbackError(BluetoothResponse.ERR_CODE_TIEMOUT, "同步数据超时！");
								if(mBLE != null) mBLE.disconnect();
							}
						}, 20 * 1000);
					}
				}
				
				//break;
			}
		//}
	}
}