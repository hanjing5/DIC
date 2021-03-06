package com.dic.BTMesh;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import com.dic.BTMesh.BTChat.BTChatListener;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

public class BTConnectionManager extends Activity {
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    private static final String TAG = "BTConnectionManager";
    private static final boolean D = true;
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    
    private BTMeshState BTMState;
    private BTCMListener BTMListener;
    private boolean listenerRegistered = false;
    private boolean showLocal = false;
	/** Called when the activity is first created. */
    
    public boolean timerRunning = false;
    
    private long START_TIME = 1000;
    private long RETRY_TIME = 60000;
    
    private Timer acTimer;
    private Set<BluetoothDevice> devicesToTry;
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
		BTMState = ((BTMeshState)getApplicationContext());
		BTMListener = new BTCMListener();
		
    	devicesToTry = BTMState.getBluetoothAdapter().getBondedDevices();
		
        if (!listenerRegistered) {
            registerReceiver(BTMListener, new IntentFilter("com.dic.BTMesh.updateCM"));
            listenerRegistered = true;
        }
		updateView();

	}
	
	public void onResume() {
		super.onResume();
	    updateView();
	}
	
	public void enableAutoConnect() {
        acTimer = new Timer();
        acTimer.scheduleAtFixedRate(new autoConnectTask(), START_TIME, RETRY_TIME);
        timerRunning = true;
        updateView();
	}
	
	public void disableAutoConnect() {
		acTimer.cancel();
		timerRunning = false;
		updateView();
	}
	
	public void updateView() {
		if (showLocal || BTMState.BTSEdges.size() == 0) {
		    TextView textview = new TextView(this);
		    String showText = "";
		    showText += "My Name: " + BTMState.getBluetoothAdapter().getName() + "\n\n";
		    if (timerRunning) {
		    	showText += "Auto-Connection Mode Enabled\n";
		    }
		    else {
		    	showText += "Auto-Connection Mode Disabled\n";
		    }
		   
		    showText += "\n\n\n";
		    showText += "Local Connections:\n";
		    ArrayList<String> connections = BTMState.getService().mLocalConnections;
		    for (int i = 0; i < 7; i++) {
		    	if (connections.get(i) != null) {
		    		showText += (connections.get(i) + "\n");
		    	} 
		    	else if (BTMState.getService().mAcceptThreads.get(i) != null && 
		    			BTMState.getService().mAcceptThreads.get(i).isRunning()) {
		    		showText += ("listening...\n");
		    	}
		    	else if (BTMState.getService().mAcceptThreads.get(i) != null &&
		    			!BTMState.getService().mAcceptThreads.get(i).isRunning()){
		    		showText += ("not listening...\n");
		    	}
		    	else {
		    		showText += ("no connection\n");
		    	}
		    }
		    showText += "\n\n";
		    showText += Integer.toString(BTMState.BTSEdges.size()) + " Edges\n";
		    for (int i = 0; i < BTMState.BTSEdges.size(); i++) {
		    	BTStateEdge e = BTMState.BTSEdges.get(i);
		    	showText += "\n" + e.name1 + "---" + e.name2;
		    }
		    textview.setText(showText);
		    setContentView(textview);
		}
		else {
		    BTDrawGraph connectionGraph = new BTDrawGraph(this, BTMState.BTSEdges);
	        connectionGraph.setBackgroundColor(Color.BLACK);
	        setContentView(connectionGraph);
		}
	}

	private void doDiscovery() {
        if (BTMState.getBluetoothAdapter().isDiscovering()) {
            BTMState.getBluetoothAdapter().cancelDiscovery();
        }
        // Request discover from BluetoothAdapter
        BTMState.getBluetoothAdapter().startDiscovery();
	}
    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (BTMState.getBluetoothAdapter().getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            //BTMState.setConnectionState(BTMesh.STATE_BROADCASTING);
            startActivity(discoverableIntent);
        }
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
            	String address = data.getExtras()
            						.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                BluetoothDevice device = BTMState.getBluetoothAdapter().getRemoteDevice(address);
                BTMState.getService().connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
            	// Generalize to set up connection or something
            } else {
                // User did not enable Bluetooth or an error occured
            	// Do some sort of failure thing, ideally message and quit
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                BTMState.setConnectionState(STATE_NONE);
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "options item selected");
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.scan:
            // Launch the DeviceListActivity to see devices and do scan
        	//BTMState.setConnectionState(STATE_SEARCHING);
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        case R.id.toggleautoconnect:
        	if (timerRunning) {
        		disableAutoConnect();
        	}
        	else {
        		enableAutoConnect();
        	}
        	return true;
        case R.id.switchcmview:
        	showLocal = !showLocal;
        	updateView();
        	return true;
        }
        return false;
    }
    
    public class autoConnectTask extends TimerTask {
	    public void run() {
	    	devicesToTry = BTMState.getBluetoothAdapter().getBondedDevices();
	    	ensureDiscoverable();
	    	doDiscovery();
	        Iterator<BluetoothDevice> iter = devicesToTry.iterator();
	        while (iter.hasNext()) {
	            BTMState.getService().connect(iter.next());
	        }
	        
	    }
	}
    
    protected class BTCMListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("com.dic.BTMesh.updateCM")) {
            	if (D) Log.d(TAG, "BTCMListener");
            	updateView();
                // Do something
            }
            else if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    devicesToTry.add(device);
                }
            }
        }
    }

}
