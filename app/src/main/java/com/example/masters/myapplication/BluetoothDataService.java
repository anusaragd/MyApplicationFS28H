package com.example.masters.myapplication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

//import futronictech.com.ftrwsqandroidhelper;

public class BluetoothDataService {
	
    // Debugging
    private static final String TAG = "BluetoothDataService";
    private static final boolean D = true;

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private CaptureThread mCaptureThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    
    private BluetoothSocket mConnectedSocket;
    
    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothDataService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void startCapture() {
    	mCaptureThread = new CaptureThread(mConnectedSocket);
    	mCaptureThread.start();
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }
        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        try {
			Thread.sleep(5000);		//need at least 5 seconds for the FAM to boot up and get ready.
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        setState(STATE_CONNECTED);
        mConnectedSocket = socket;
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mCaptureThread != null) {mCaptureThread.cancel(); mCaptureThread = null;}
        setState(STATE_NONE);
    }

     /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_LISTEN);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

        	Method m = null;
			try {
				m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
            try {
				tmp = (BluetoothSocket) m.invoke(device, 1);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
            mmSocket = tmp;
        }

        @Override
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
                connectionFailed();
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothDataService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
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
    private class CaptureThread extends Thread {
        private FamBTComm m_commFam = null;       
        private int m_nWsqBirate = 0x96; 
		private int m_nWsqSize[] = new int[1];

        public CaptureThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            m_commFam = new FamBTComm(socket, mHandler);
         }

        @Override

		public void run() {
            Log.i(TAG, "BEGIN mCaptureThread");
            byte nRet = 1;                        
           	
        	while (!MainActivity.mStop)
            {
        		mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, "Put finger on FS28").sendToTarget();
                nRet = m_commFam.FamIsFingerPresent();
                if (nRet == 0)
                    break;
                else if (nRet != FamBTComm.RET_NO_IMAGE)
                {
                	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, m_commFam.GetErrorMessage()).sendToTarget();                   
                	mHandler.obtainMessage(MainActivity.MESSAGE_ENABLE_BUTTONS).sendToTarget();
                    return;
                }
                try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }
            if (MainActivity.mStop)
            {
            	mHandler.obtainMessage(MainActivity.MESSAGE_STOP).sendToTarget();
            }
            else
            {
            	mHandler.obtainMessage(MainActivity.MESSAGE_DISABLE_STOP).sendToTarget();	//disable stop button
            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, "Finger detected, hold on ...").sendToTarget();
            	if(MainActivity.mCaptureType == MainActivity.CAPTURE_RAW)
            		CaptureRAWImage();
            	else if(MainActivity.mCaptureType == MainActivity.CAPTURE_WSQ)
            		CaptureWSQImage();
            }
            MainActivity.mStep = 0;      
            mHandler.obtainMessage(MainActivity.MESSAGE_ENABLE_BUTTONS).sendToTarget();
        }
        
        public void CaptureRAWImage()
        {
        	boolean bPIV = true;
        	String strTimeMsg = null;
        	byte nRet = m_commFam.FamCaptureImage(bPIV);
            if (nRet == 0)
            {
            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, "Take off your finger, downloading image...").sendToTarget();
                long nT1 = SystemClock.uptimeMillis();
            	nRet = m_commFam.FamDownloadRAWImage(MainActivity.mImageFP);
                if( nRet == 0 )
                {
                    long nT2 = SystemClock.uptimeMillis();
                    ftrwsqandroidhelper helper = new ftrwsqandroidhelper();
                	int nfiq = helper.GetRawImageNFIQ(MainActivity.mImageFP, 320, 480);
                	long nT3 = SystemClock.uptimeMillis();
                	strTimeMsg = String.format(Locale.getDefault(),"TDn:%d(ms), TNfiq:%d(ms), NFIQ: %d", nT2-nT1, nT3-nT2, nfiq);
                	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, strTimeMsg).sendToTarget();
                	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_IMAGE).sendToTarget();
                }
                else
                	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, m_commFam.GetErrorMessage()).sendToTarget();
            }
            else
            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, m_commFam.GetErrorMessage()).sendToTarget();
        }

        public void CaptureWSQImage()
        {
        	boolean bPIV = true;
        	String strTimeMsg = null;
        	long nT1 = SystemClock.uptimeMillis();
        	m_nWsqSize[0] = 0;
        	byte nRet = m_commFam.FamCaptureImage(bPIV);
            if (nRet == 0)
            {
            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, "Take off your finger, converting image to wsq...").sendToTarget();
	        	nRet = m_commFam.FamConvertWSQ(m_nWsqBirate, m_nWsqSize);
	        	if (nRet == 0)
	            {
		        	byte[] wsqImage = new byte[m_nWsqSize[0] + 2];
	        		long nT2 = SystemClock.uptimeMillis();
	            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, "Downloading image...").sendToTarget();
	            	Log.i(TAG, "FamDownload_WSQ, size: " + m_nWsqSize[0] );
		        	nRet = m_commFam.FamDownload_WSQ(wsqImage, m_nWsqSize[0]);
		        	if( nRet == 0 )
	                {
		        		MainActivity.mWsqImageFP = new byte[m_nWsqSize[0]];
		        		System.arraycopy(wsqImage, 0, MainActivity.mWsqImageFP, 0, m_nWsqSize[0]);
		            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, "Convert WSQ to RAW image...").sendToTarget();
	                	ftrwsqandroidhelper helper = new ftrwsqandroidhelper();
	                	int[] wRaw = new int[1];
	                	int[] hRaw = new int[1];
	                	wRaw[0] = hRaw[0] = 0;
		        		long nT3 = SystemClock.uptimeMillis();
	                	int sizeRaw = helper.GetWsqImageRawSize(MainActivity.mWsqImageFP, wRaw, hRaw);
	                	if( sizeRaw > 0 )
	                	{
	                		byte[] rawImg = new byte[sizeRaw];
	                    	if( helper.ConvertWsqToRaw(MainActivity.mWsqImageFP, rawImg) )
	                    	{
	                    		long nT4 = SystemClock.uptimeMillis();
	                    		System.arraycopy(rawImg, 0, MainActivity.mImageFP, 0, sizeRaw);
	                        	int nfiq = helper.GetRawImageNFIQ(rawImg, wRaw[0], hRaw[0]);
	                        	long nT5 = SystemClock.uptimeMillis();
	                        	strTimeMsg = String.format(Locale.getDefault(), "TWsq1:%d, TDn:%d, TWsq2:%d, TNfiq:%d (ms), NFIQ=%d",nT2-nT1, nT3-nT2, nT4-nT3, nT5-nT4, nfiq);
	                    	}
	                    	else
	                    		strTimeMsg = "Failed to convert wsq to raw!";
	                	}
	                	else
	                		strTimeMsg = "Invalid WSQ Image!";
	                	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, strTimeMsg).sendToTarget();
	                	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_IMAGE).sendToTarget();
	                }
	                else
	                	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, m_commFam.GetErrorMessage()).sendToTarget();
	            }
	            else
	            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, m_commFam.GetErrorMessage()).sendToTarget();
            }
            else
            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_MSG, m_commFam.GetErrorMessage()).sendToTarget();
        }
        
        public void cancel() {
        	FamBTComm.CloseSocket();
        }        
    }
}
