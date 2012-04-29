package com.dic.BTMesh;

import java.util.ArrayList;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

public class BTMeshState extends Application {
  private static final String TAG = "BTMeshState";
  private static final boolean D = false;
  // Constants that indicate the current connection state
  public static final int STATE_NONE = 0;       // we're doing nothing
  public static final int STATE_LISTEN = 1;     // now listening for incoming connections
  public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
  public static final int STATE_CONNECTED = 3;  // now connected to a remote device
	
  private BTMeshService mService;
  private BluetoothAdapter mBluetoothAdapter = null;
  
  private int mConnectionState;

  
  private ArrayList<BTStateEdge> BTSEdges;
  
  
  public void newService(Handler mHandler){
	  if (D) Log.d(TAG, "Creating BTMService");
      mService = new BTMeshService(this, mHandler, this);
  }
  
  public void setup(){
      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
      BTSEdges = new ArrayList<BTStateEdge>();
  }
  
  public String edgesToString(){
	  String s = "@EDGES";
	  for (int i = 0; i < BTSEdges.size(); i++) {
		  BTStateEdge e = BTSEdges.get(i);
		  s += "@start@addr1" + e.address1 + "@name1" + e.name1 + "@addr2" + e.address2 + "@name2" + e.name2 + "@end";
	  }
	  return s;
  }
  
  public BTMeshService getService(){
	  return mService;
  }
  
  public BluetoothAdapter getBluetoothAdapter(){
	  return mBluetoothAdapter;
  }

  public int getNumLocalDevices(){
	return mService.numConnections();
  }
		
  public int getNumGlobalDevices(){
	return -1;
  }
  
  public synchronized void updateConnected(){
	  setConnectionState(STATE_CONNECTED);
  }
  
  public synchronized int getConnectionState(){
	  return mConnectionState;
  }
  
  public synchronized void refreshConnectionState() {
	  setConnectionState(mConnectionState);
  }
  
  public synchronized void setConnectionState(int s){
	  if (D) Log.d(TAG, "setConnectionState to " + Integer.toString(s));
	  if (getConnectionState() == STATE_CONNECTED && s != STATE_CONNECTED) {
		  if (D) Log.d(TAG, "setConnectionState returning, already connected");
		  return;
	  }
	  mConnectionState = s;
  	  Intent i = new Intent();
  	  i.setAction("com.dic.BTMesh.updatestatus");
	  switch(mConnectionState){
	  case STATE_NONE:
		  i.putExtra("status", getString(R.string.title_not_connected));
		  break;
	  case STATE_CONNECTING:
		  i.putExtra("status", getString(R.string.title_connecting));
		  break;
	  case STATE_CONNECTED:
		  String fullStr = getString(R.string.title_connected) + ": "
				  	+ Integer.toString(getNumGlobalDevices()) + " ("
				  	+ Integer.toString(getNumLocalDevices()) + ")";
		  i.putExtra("status", fullStr);
          Intent j = new Intent();
      	  j.setAction("com.dic.BTMesh.updateCM");
      	  sendBroadcast(j);
      	  break;
	  case STATE_LISTEN:
		  i.putExtra("status", getString(R.string.title_searching));
		  break;
	  }
	  if (D) Log.d(TAG, "sending state broadcast " + Integer.toString(s));
  	  sendBroadcast(i);
  }
}