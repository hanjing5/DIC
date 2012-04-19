/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.dic.BTMesh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BTMeshService {
    // Debugging
    private static final String TAG = "BTMeshService";
    private static final boolean D = true;

    private static final String NAME = "BTMesh";

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;

    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private ArrayList<String> mDeviceAddresses;
    public ArrayList<ConnectedThread> mConnectedThreads;
    private ArrayList<BluetoothSocket> mSockets;
    
    private ArrayList<UUID> mUuids;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_BROADCASTING = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int STATE_SEARCHING = 4;

    private BTMeshState BTMState;
    
    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BTMeshService(Context context, Handler handler, BTMeshState s) {
    	BTMState = s;
    	mAdapter = BluetoothAdapter.getDefaultAdapter();
        BTMState.setConnectionState(STATE_NONE);
        mHandler = handler;
        mDeviceAddresses = new ArrayList<String>();
        mConnectedThreads = new ArrayList<ConnectedThread>();
        mSockets = new ArrayList<BluetoothSocket>();
        mUuids = new ArrayList<UUID>();
        mUuids.add(UUID.fromString("249c81e0-7129-11e1-b0c4-0800200c9a66"));
        mUuids.add(UUID.fromString("249c81e1-7129-11e1-b0c4-0800200c9a66"));
        mUuids.add(UUID.fromString("249c81e2-7129-11e1-b0c4-0800200c9a66"));
        mUuids.add(UUID.fromString("249c81e3-7129-11e1-b0c4-0800200c9a66"));
        mUuids.add(UUID.fromString("249c81e4-7129-11e1-b0c4-0800200c9a66"));
        mUuids.add(UUID.fromString("249c81e5-7129-11e1-b0c4-0800200c9a66"));
        mUuids.add(UUID.fromString("249c81e6-7129-11e1-b0c4-0800200c9a66"));
    }


    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        if (mAcceptThread == null) {
        	mAcceptThread = new AcceptThread();
        	mAcceptThread.start();
        }
        BTMState.setConnectionState(STATE_NONE);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (BTMState.getConnectionState() == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start a thread and try to connect to each UUID
        for (int i = 0; i < 7; i++){
        	try {
        		mConnectThread = new ConnectThread(device, mUuids.get(i), i);
        		mConnectThread.start();
        		BTMState.setConnectionState(STATE_CONNECTING);
        	} catch (Exception e) {
        	}
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, int index) {
        if (D) Log.d(TAG, "connected");

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, device.getName(), index);
        mConnectedThread.start();
        
        //Add to array
        mConnectedThreads.add(mConnectedThread);

        BTMState.setConnectionState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
        	mAcceptThread.cancel();
        	mAcceptThread = null;
        }

        BTMState.setConnectionState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
    	for (int i = 0; i < mConnectedThreads.size(); i++) {
    		try {
    			ConnectedThread r;
    			synchronized (this) {
    				if (BTMState.getConnectionState() != STATE_CONNECTED) return;
    				r = mConnectedThreads.get(i);
    			}
    			r.write(out);
    		} catch (Exception e) {
    		}
    	}
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
    	// should probably do things here
    	BTMState.setConnectionState(STATE_NONE);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
    	// should also do things here
    	BTMState.setConnectionState(STATE_NONE);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket

    	BluetoothServerSocket serverSocket = null;
    	public AcceptThread() {
    	}
    	
    	public void run() {
    		if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
    		setName("AcceptThread");
    		BluetoothSocket socket = null;
    		try {
    			for (int i = 0; i < 7; i++) {
    				serverSocket = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, mUuids.get(i));
    				socket = serverSocket.accept();
    				if (socket != null) {
    					String address = socket.getRemoteDevice().getAddress();
    					mSockets.add(socket);
    					mDeviceAddresses.add(address);
    					connected(socket, socket.getRemoteDevice(), i);
    				}
    			}
    		} catch (IOException e) {
    			Log.e(TAG, "accept() failed", e);
    		}
    		if (D) Log.i(TAG, "END mAcceptThread");
    	}

        public void cancel() {
            if (D) Log.d(TAG, "cancel " + this);
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final int index;
        private UUID currUuid;

        public ConnectThread(BluetoothDevice device, UUID inUuid, int i) {
        	index = i;
            mmDevice = device;
            BluetoothSocket tmp = null;
            currUuid = inUuid;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
            	tmp = device.createInsecureRfcommSocketToServiceRecord(currUuid);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
            	mmSocket.connect();
            } catch (IOException e) {
            	if (currUuid.toString().contentEquals(mUuids.get(6).toString())){
            		connectionFailed();
                }
            	// Close the socket
            	try {
            		mmSocket.close();
            	} catch (IOException e2) {
            		Log.e(TAG, "unable to close() socket during connection failure", e2);
            	}
            	BTMeshService.this.start();
            	return;
            }

            // Reset the ConnectThread because we're done
        	synchronized (BTMeshService.this) {
        		mConnectThread = null;
        	}

        	// Start the connected thread
        	connected(mmSocket, mmDevice, index);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    public class ConnectedThread extends Thread {
    	public final String deviceName;
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final int index;

        public ConnectedThread(BluetoothSocket socket, String name, int i) {
            Log.d(TAG, "create ConnectedThread");
            index = i;
            deviceName = name;
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(BTMesh.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BTMesh.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
