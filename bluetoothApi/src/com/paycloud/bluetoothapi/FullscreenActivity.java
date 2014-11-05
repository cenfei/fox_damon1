package com.paycloud.bluetoothapi;

import java.util.Calendar;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

public class FullscreenActivity extends Activity implements BluetoothResponse{

	String mAddresString;
	TextView textView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_fullscreen);
		
		textView = (TextView) findViewById(R.id.textView1);
		textView.setText("abcd");
	}

	public void onButton1(View view) {
	    // Do something in response to button click
		BluetoothAPI api = BluetoothAPI.getInstance(this);
		api.findFirstDevice(this);
	}	

	public void onButton2(View view) {
	    // Do something in response to button click
		BluetoothAPI.stopBLESession();
	}	

	public void onButton3(View view) {
	    // Do something in response to button click
		BluetoothAPI api = BluetoothAPI.getInstance(this);
		//api.setDeviceAddress("08:7C:BE:22:BF:15");
		//api.setDeviceAddress("08:7C:BE:22:BE:A3");
		//api.setDeviceAddress("08:7C:BE:22:B4:D6");
		api.getVersion(this);
	}	
	
	public void onButton4(View view) {
		textView.setText("");
	    // Do something in response to button click
		BluetoothAPI api = BluetoothAPI.getInstance(this);
		//api.setDeviceAddress("08:7C:BE:22:BF:15");
		//api.setDeviceAddress("08:7C:BE:22:BE:A3");
		//api.setDeviceAddress("08:7C:BE:24:D2:8A");
		api.getDataByDate(this, 2014, 10, 1);
	}	

	public void onButton5(View view) {
	    // Do something in response to button click
		BluetoothAPI api = BluetoothAPI.getInstance(this);
		//api.setDeviceAddress("08:7C:BE:22:BF:15");
		//api.setDeviceAddress("08:7C:BE:24:D2:8A");
		Calendar dateTime = Calendar.getInstance();
		//dateTime.set(Calendar.HOUR_OF_DAY, 4);
		api.setDeviceProperty(this, dateTime, 8000);
	}	
	
	public void onButton6(View view) {
	    // Do something in response to button click
		BluetoothAPI api = BluetoothAPI.getInstance(this);

		api.getAllData(this);
	}	

	public void onData(byte[] data, String dataStr)
	{
		if(data != null) 
		{
			Log.d("SAMPLE_On_Data data", data.toString());
			//BluetoothAPI.stopBLESession();
			textView.setText(data.toString());
		}
		if(dataStr != null)
		{
			Log.d("SAMPLE_On_Data str", dataStr);
			mAddresString = dataStr;

			textView.setText(dataStr);
			//BluetoothAPI api = BluetoothAPI.getInstance(this);
			//api.setTimeInterval(this, 10);
		}
	}

	public void onProgress(float percent)
	{
		Log.e("SAMPLE_On_percent", String.format("progress at %d%%...", (int)(percent * 100.0f)));
	}
	
	public void onError(int errCode, String errMsg)
	{
		Log.e("SAMPLE_On_Error", errMsg);
		textView.setText(errMsg);
	}
}

