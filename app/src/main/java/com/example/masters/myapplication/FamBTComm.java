package com.example.masters.myapplication;

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FamBTComm {
	
	private static final String TAG = "FS28SlaveModeAndroidDemo";
    private static final byte COMMAND_CAPTURE_IMAGE = 0x49;
    private static final byte COMMAND_CHECK_FINGER = 0x4B;
    private static final byte COMMAND_CONVERT_TO_WSQ = 0x36; //convert the captured image into WSQ image
    private static final byte COMMAND_DOWNLOAD_WSQ_IMAGE = 0x0F;
    private static final byte COMMAND_DOWNLOAD_RAW_IMAGE = 0x44;
    
    public static final byte RET_OK = 0x40;
    public static final byte RET_NO_IMAGE = 0x41;
    public static final byte RET_BAD_QUALITY = 0x42;
    public static final byte RET_TOO_LITTLE_POINTS = 0x43;
    public static final byte RET_EMPTY_BASE = 0x44;
    public static final byte RET_UNKNOWN_USER = 0x45;
    public static final byte RET_NO_SPACE = 0x46;
    public static final byte RET_BAD_ARGUMENT = 0x47;
    public static final byte RET_CRC_ERROR = 0x49;
    public static final byte RET_RXD_TIMEOUT = 0x4A;
    public static final byte RET_USER_ID_IS_ABSENT = 0x4D;
    public static final byte RET_USER_ID_IS_USED = 0x4E;
    public static final byte RET_VERY_SIMILAR_SAMPLE = 0x4F;
    public static final byte RET_USER_SUSPENDED = 0x54;
    public static final byte RET_UNKNOWN_COMMAND = 0x55;
    public static final byte RET_INVALID_STOP_BYTE = 0x57;
    public static final byte RET_HARDWARE_ERROR = 0x58;
    public static final byte RET_BAD_TEST_OBJECT = 0x59;
    public static final byte RET_BAD_FLASH = 0x5A;
    public static final byte RET_TOO_MANY_VIP = 0x5B;
    //
    public static final byte RET_WINSOCK_ERROR = 0x30;
    public static final byte RET_CONNECT_TIMEOUT = 0x31;
    public static final byte RET_FLAG_ZERO = 0x32;
    //FS28 State
    public static final byte RET_FLAG_FS28_BUSY = (byte) 0xB0;
    public static final byte RET_FLAG_FS28_SLEEP = (byte) 0xB1;
    public static final byte RET_FLAG_FS28_IN_SECURITY_LOCKED = (byte) 0xB2;
    //FS28 Error
    public static final byte RET_FLAG_9866B_OFF = (byte) 0xE0; //Fingeprint scanner module (9866B) off 
      
    public static final int TIMEOUT_1 = 6000;  // 6sec timeout
    public static final int TIMEOUT_2 = 3000;  // 3sec timeout

    public static final int ERROR_TIMEOUT = 0;
    private Handler mHandlerTimer = new Handler();
    
    private static byte m_nErrorCode;
    private byte[] m_RxCmd = new byte[13];
    
    private static BluetoothSocket mmSocket = null;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private final Handler mHandler;
    
    public FamBTComm(BluetoothSocket socket, Handler handler)
    {
    	mmSocket = socket;
    	mHandler = handler;
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
        MainActivity.mConnected = true;    	
    }
    
    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    public static void CloseSocket()
    {
    	if(mmSocket != null)
			try {
				mmSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
    		
    }
    
    private void StartTimer(long Interval)
    {
		mHandlerTimer.removeCallbacks(mUpdateTimeTask);
   		mHandlerTimer.postDelayed(mUpdateTimeTask, Interval);  
    }
    
    private void StopTimer()
    {
    	mHandlerTimer.removeCallbacks(mUpdateTimeTask);   
    }
       
    private Runnable mUpdateTimeTask = new Runnable() {
    	@Override
		public void run() { 
     		try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
     		// send message to stop the connected thread and reconnect.
     		mHandler.obtainMessage(MainActivity.MESSAGE_DATA_ERROR, ERROR_TIMEOUT, -1).sendToTarget();
    		}
    };
    
	public byte FamIsFingerPresent()
    {
        return CommunicateWithFAM(COMMAND_CHECK_FINGER, 0, 0, (byte) 0, m_RxCmd, null, null);
    }
	
	public byte FamCaptureImage(boolean bPIV)
    {
        int nParam1 = 0;
        if (bPIV)
            nParam1 = 0x08;
        m_nErrorCode = CommunicateWithFAM(COMMAND_CAPTURE_IMAGE, nParam1, 0, (byte) 0, m_RxCmd, null, null);
        return m_nErrorCode;
    }

    public byte FamConvertWSQ(int bitrate, int[] WSQ_size) // (bitrate 0x4B-0xFF), to compress RAW image to WSQ image. 0x4B for 0.75(15:1), 0x96 for 1.5 (8.69:1) 
    {
        m_nErrorCode = CommunicateWithFAM(COMMAND_CONVERT_TO_WSQ, bitrate, 0, (byte) 1, m_RxCmd, null, null); //flag is set to 1 for WSQ.
        if (m_nErrorCode == 0)
        {
        	WSQ_size[0] = 0;
        	short unsignedByte;
        	for(int i=9; i>5; i--)
        	{
        		if( m_RxCmd[i] < 0 )	// Java does not have unsigned type. 
        			unsignedByte = (short) (256 + m_RxCmd[i]);
        		else
        			unsignedByte = m_RxCmd[i];
        		WSQ_size[0] |= unsignedByte;
        		if( i > 6 )
        			WSQ_size[0] = WSQ_size[0] * 0x100;    		
        	}
        }
        return m_nErrorCode;
    }

    public byte FamDownload_WSQ(byte[] pImage, int WSQ_size)
    {
        return CommunicateWithFAM(COMMAND_DOWNLOAD_WSQ_IMAGE, 0, WSQ_size, (byte) 0, m_RxCmd, null, pImage); 
    }

    public byte FamDownloadRAWImage(byte[] pImage)
    {
        return CommunicateWithFAM(COMMAND_DOWNLOAD_RAW_IMAGE, 0, 320 * 480, (byte) 0, m_RxCmd, null, pImage);
    }

    public byte FamDownloadRAWImage_Size_Offset(byte[] pImage, int nImgSize, int nOffset)
    {
        return CommunicateWithFAM(COMMAND_DOWNLOAD_RAW_IMAGE, nOffset, nImgSize, (byte) 0, m_RxCmd, null, pImage);
    }
 
    private boolean ReadByBlock( byte[] lpBuffer, int nNumberOfBytesToRead )
    {
        int nTotal = nNumberOfBytesToRead;
        int nBytesToRead; 
        int nTotalBytesRead = 0;
        int nBytesRead;
        int nCurrentStep = 0;

        while( nTotal > 0 )
        {
        	StartTimer(TIMEOUT_1);	
	        if( nTotal >= 2048 )
		        nBytesToRead = 2048;
	        else
		        nBytesToRead = nTotal;
        	try {    
        		nBytesRead = mmInStream.read(lpBuffer, nTotalBytesRead, nBytesToRead);
                //Log.i(TAG, "Received: " + nBytesRead + ", TotalBytesRead: " + nTotalBytesRead); 
                StopTimer();
            } catch (IOException e3) {
                Log.e(TAG, "disconnected");
                connectionLost();
                break; 
            } 
	        nTotal -= nBytesRead;
	        nTotalBytesRead += nBytesRead; 
	        nCurrentStep = nTotalBytesRead * 100 / (nNumberOfBytesToRead);
            if( nCurrentStep > MainActivity.mStep)
            {
            	MainActivity.mStep = nCurrentStep;
            	mHandler.obtainMessage(MainActivity.MESSAGE_SHOW_PROGRESSBAR).sendToTarget();
            }
        }
        StopTimer();
        return true;
    }
    
    private boolean Read_13B_Command(byte[] RxCmd)
    {
    	int nTotal = 13;
        int nBytesToRead; 
        int nTotalBytesRead = 0;
        int nBytesRead;
        while( nTotal > 0 )
        {
        	StartTimer(TIMEOUT_1);	
        	if( nTotal >= 13 )
		        nBytesToRead = 13;
	        else
		        nBytesToRead = nTotal;
	    	try {    
	    		nBytesRead = mmInStream.read(RxCmd, nTotalBytesRead, nBytesToRead);
	            //Log.i(TAG, "Received: " + nBytesRead); 
	            StopTimer();
	        } catch (IOException e3) {
	            Log.e(TAG, "disconnected");
	            connectionLost();
	            StopTimer();
	            return false; 
	        } 
	    	if( nBytesRead >= 13 )
	    	{
	    		StopTimer();
	    		return true;
	    	}
	    	nTotal -= nBytesRead;
	        nTotalBytesRead += nBytesRead;
        }
        StopTimer();
	    return true;
    }
    
    /**
     * Write to the connected OutStream.
     * @param buffer  The bytes to write
     */
    private void write(byte[] buffer) {
        try {
            mmOutStream.write(buffer);
			mmOutStream.flush();
            // Share the sent message back to the UI Activity
            /*mHandler.obtainMessage(FS28DemoActivity.MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget();*/
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }
    
    private byte CommunicateWithFAM(byte nCommand, int param1, int param2, byte nFlag, byte[] RxCmd, byte[] TxBuf, byte[] RxBuf)
    {
        byte[] CommandBuf = { 0x40,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x0d } ; 
        int nChksum = 0;
        int nIndex;

        CommandBuf[1] = nCommand;
        CommandBuf[2] = (byte)(param1 & 0xff);
        CommandBuf[3] = (byte)(param1 >> 8);
        CommandBuf[4] = (byte)(param1 >> 16);
        CommandBuf[5] = (byte)(param1 >> 24);
        CommandBuf[6] = (byte)(param2);
        CommandBuf[7] = (byte)(param2 >> 8);
        CommandBuf[8] = (byte)(param2 >> 16);
        CommandBuf[9] = (byte)(param2 >> 24);
        CommandBuf[10] = nFlag;
        for( nIndex=0; nIndex<11; nIndex++)
	        nChksum += CommandBuf[nIndex];
        CommandBuf[11] = (byte) ( nChksum & 0xff );

        // send command to FS28
        write( CommandBuf );

        if( TxBuf != null ) //with host data to FAM after sending the command
        {
	        //checksum of data
	        nChksum = 0;
	        for( nIndex=0; nIndex<param2; nIndex++ )
		        nChksum += TxBuf[nIndex];
	        TxBuf[param2] = (byte) ( nChksum ); 	
	        param2++;
            try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}//This delay is need for FS28 to process the command and prepare buffer for reciving the data
	        write(TxBuf);
        }
        //wait until the number of bytes to read is >= 13.
        if( !Read_13B_Command(RxCmd) )
        {
    		return 1;
        }
    	if(RxCmd[10] == 0x40)//if flag is 0x40 ok, then check whether there is any data need to receive from FS28.
    	{
    		if ((nCommand == COMMAND_DOWNLOAD_RAW_IMAGE || nCommand == COMMAND_DOWNLOAD_WSQ_IMAGE ) &&( RxBuf != null ))
    		{
				if (!ReadByBlock(RxBuf, param2+2 ))
                    return 1;
    		}
    		return 0;
    	}
        return RxCmd[10];
    }
    
    public String GetErrorMessage()
    {
    	String strErrorMessage;
	    switch (m_nErrorCode)//error code from flag of FAM Respond
	    {
	        case RET_NO_IMAGE:
	            strErrorMessage = "Respond Fail or Not Image!";
	            break;
	        case RET_BAD_QUALITY:
	            strErrorMessage = "Bad Quality!";
	            break;
	        case RET_TOO_LITTLE_POINTS:
	            strErrorMessage = "Too littlt points!";
	            break;
	        case RET_EMPTY_BASE:
	            strErrorMessage = "Empty database!";
	            break;
	        case RET_UNKNOWN_USER:
	            strErrorMessage = "Unknown user!";
	            break;
	        case RET_NO_SPACE:
	            strErrorMessage = "Not enough memory!";
	            break;
	        case RET_BAD_ARGUMENT:
	            strErrorMessage = "Bad argument!";
	            break;
	        case RET_CRC_ERROR:
	            strErrorMessage = "CRC error!";
	            break;
	        case RET_RXD_TIMEOUT:
	            strErrorMessage = "Rx data time out!";
	            break;
	        case RET_USER_ID_IS_ABSENT:
	            strErrorMessage = "User id does NOT existed!";
	            break;
	        case RET_USER_ID_IS_USED:
	            strErrorMessage = "User id existed!";
	            break;
	        case RET_VERY_SIMILAR_SAMPLE:
	            strErrorMessage = "Sample is very similar!";
	            break;
	        case RET_USER_SUSPENDED:
	            strErrorMessage = "User is suspended!";
	            break;
	        case RET_UNKNOWN_COMMAND:
	            strErrorMessage = "Unknown command!";
	            break;
	        case RET_INVALID_STOP_BYTE:
	            strErrorMessage = "Invalid stop byte!";
	            break;
	        case RET_HARDWARE_ERROR:
	            strErrorMessage = "Hardware error!";
	            break;
	        case RET_BAD_FLASH:
	            strErrorMessage = "Bad flash!";
	            break;
	        case RET_TOO_MANY_VIP:
	            strErrorMessage = "Too many VIP!";
	            break;
	        case RET_CONNECT_TIMEOUT:
	            strErrorMessage = "Time out to connect to FAM!";
	            break;
	        case RET_FLAG_FS28_BUSY:
	            strErrorMessage = "FS28 is Busy! Pls Try again later";
	            break;
	        case RET_FLAG_FS28_SLEEP:
	            strErrorMessage = "FS28 just weak up from sleep!Pls Try 5 sec later";
	            break;
	        case RET_FLAG_FS28_IN_SECURITY_LOCKED:
	            strErrorMessage = "FS28 is security locked!Pls unlock it";
	            break;                       
	        case RET_FLAG_9866B_OFF:
	            strErrorMessage = "9866B off. Pls reset the COM";
	            break;
	        default:
	            strErrorMessage = String.format("Unknown error code 0x: {%x}", m_nErrorCode);
	            break;
	    }
        return strErrorMessage;
    }
}
