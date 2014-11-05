package com.paycloud.bluetoothapi;

import android.R.integer;

public interface BluetoothResponse {

	public static final int ERR_CODE_TIEMOUT = 1; 
	public static final int ERR_CODE_OP_FAILED = 2; 
	public static final int ERR_CODE_UNKOWN = 3; 
	
	
	//findFirstDevice 会返回str，data是null；其他接口都是返回data，str是null
	//getDataByDate 返回结构：年／月／日／间隔／条数（共5字节），接着是144组数据，每组3字节。
	public void onData(byte[] data, String dataStr);

	public void onProgress(float percent);
	
	public void onError(int errCode, String errMsg);
}
