package com.example.masters.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    // Debugging
    private static final String TAG = "FS28SlaveModeAndroidDemo";
    private static final boolean D = false;
    // Message types sent from the BluetoothDataService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_SHOW_MSG = 6;
    public static final int MESSAGE_SHOW_IMAGE = 7;
    public static final int MESSAGE_SHOW_PROGRESSBAR = 8;
    public static final int MESSAGE_STOP = 9;
    public static final int MESSAGE_DATA_ERROR = 10;
    public static final int MESSAGE_EXIT_PROGRAM = 11;
    public static final int MESSAGE_ENABLE_BUTTONS = 12;
    public static final int MESSAGE_DISABLE_STOP = 13;
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_FILE_FORMAT = 3;
    // Capture image type
    public static final int CAPTURE_RAW = 1;
    public static final int CAPTURE_WSQ = 2;

    // Key names received from the BluetoothDataService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String SHOW_MESSAGE = "show_message";
    public static final String TOAST = "toast";

    private Button mButtonOpen;
    private Button mButtonCaptureRAW;
    private Button mButtonCaptureWSQ;
    private Button mButtonStop;
    private Button mButtonSave;
    private TextView mMessage;
    private ImageView mFingerImage;
    private ProgressBar mProgressbar1;
    public static boolean mStop = true;
    public static boolean mConnected = false;
    public static int mStep = 0;
    public static int mCaptureType = 0;
    public static boolean mOpened = false;
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothDataService mBTService = null;

    public static byte[] mImageFP = new byte[153602];
    private static Bitmap mBitmapFP;
    public static byte[] mWsqImageFP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mMessage = (TextView) findViewById(R.id.tvMessage);
        mFingerImage = (ImageView) findViewById(R.id.imageFinger);
        mProgressbar1 = (ProgressBar) findViewById(R.id.progressBar1);
        mProgressbar1.setMax(100);
        // Initialize the send button with a listener that for click events
        mButtonOpen = (Button) findViewById(R.id.btn_open_bt);
        mButtonCaptureRAW = (Button) findViewById(R.id.btn_capture_raw);
        mButtonCaptureWSQ = (Button) findViewById(R.id.btn_capture_wsq);
        mButtonStop = (Button) findViewById(R.id.btn_stop);
        mButtonSave = (Button) findViewById(R.id.btn_save);
        mButtonCaptureRAW.setEnabled(false);
        mButtonCaptureWSQ.setEnabled(false);
        mButtonSave.setEnabled(false);
        mButtonStop.setEnabled(false);

        mButtonOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mOpened)
                {
                    startDeviceListActivity();
                }
                else
                {
                    mStop = true;
                    if( mBTService != null )
                    {
                        mBTService.stop();
                        mBTService = null;
                    }
                    mButtonOpen.setText("Open BT Comm");
                    mOpened = false;
                    mButtonCaptureRAW.setEnabled(false);
                    mButtonCaptureWSQ.setEnabled(false);
                    mButtonStop.setEnabled(false);
                    mButtonSave.setEnabled(false);
                }
            }
        });

        mButtonCaptureRAW.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCaptureType = CAPTURE_RAW;
                mButtonCaptureRAW.setEnabled(false);
                mButtonCaptureWSQ.setEnabled(false);
                mButtonSave.setEnabled(false);
                mButtonStop.setEnabled(true);
                startCapture();
            }
        });

        mButtonCaptureWSQ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCaptureType = CAPTURE_WSQ;
                mButtonCaptureRAW.setEnabled(false);
                mButtonCaptureWSQ.setEnabled(false);
                mButtonSave.setEnabled(false);
                mButtonStop.setEnabled(true);
                startCapture();
            }
        });

        mButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopCapture();
            }
        });

        mButtonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SaveImage();
            }
        });

    }

    public void startDeviceListActivity()
    {
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    public void startCapture()
    {
        mStop = false;
        if (mBTService != null) {
            mBTService.startCapture();
        }
        else
        {
            mBTService = new BluetoothDataService(this, mHandler);
            mBTService.startCapture();
        }
    }

    public void stopCapture()
    {
        mStop = true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mStop = true;
        if (mBTService != null) {
            mBTService.stop();
            mBTService = null;
        }
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    @Override
    public void onBackPressed() {
        //super.OnBackPressed();
        new AlertDialog.Builder(this)
                .setTitle("Exit")
                .setMessage("Do you want to exit this program?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        //send message to exit
                        mHandler.obtainMessage(MESSAGE_EXIT_PROGRAM).sendToTarget();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .setCancelable(false)
                .show();
    }

    public void ExitProgram()
    {
        onDestroy();
        System.exit(0);
    }

    private void SaveImage()
    {
        Intent serverIntent = new Intent(this, SelectFileFormatActivity.class);
        startActivityForResult(serverIntent, REQUEST_FILE_FORMAT);
    }

    private void SaveImageByFileFormat(String fileFormat, String fileName)
    {
        if( fileFormat.compareTo("WSQ") == 0 )	//save wsq file
        {
            if( mWsqImageFP != null )
            {
                File file = new File(fileName);
                try {
                    FileOutputStream out = new FileOutputStream(file);
                    out.write(mWsqImageFP, 0, mWsqImageFP.length);	// save the wsq_size bytes data to file
                    out.close();
                    mMessage.setText("Image is saved as " + fileName);
                } catch (Exception e) {
                    mMessage.setText("Exception in saving file");
                }
            }
            else
                mMessage.setText("Invalid WSQ image!");
            return;
        }
        // 0 - save bitmap file
        File file = new File(fileName);
        try {
            FileOutputStream out = new FileOutputStream(file);
            MyBitmapFile fileBMP = new MyBitmapFile(320, 480, mImageFP);
            out.write(fileBMP.toBytes());
            out.close();
            mMessage.setText("Image is saved as " + fileName);
        } catch (Exception e) {
            mMessage.setText("Exception in saving file");
        }
    }

    // The Handler that gets information back from the BluetoothChatService
    @SuppressLint("HandlerLeak") private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothDataService.STATE_CONNECTED:
                            mMessage.setText("Connected ");
                            mMessage.append(mConnectedDeviceName);
                            mOpened = true;
                            mButtonCaptureRAW.setEnabled(true);
                            mButtonCaptureWSQ.setEnabled(true);
                            break;
                        case BluetoothDataService.STATE_CONNECTING:
                            mMessage.setText("Connecting...");
                            break;
                        case BluetoothDataService.STATE_LISTEN:
                        case BluetoothDataService.STATE_NONE:
                            mMessage.setText("Not connected.");
                            mOpened = false;
                            if( mConnected )
                            {
                                mConnected = false;
                                if( !mStop )
                                {
                                    if( mBTService != null )
                                    {
                                        mBTService.stop();
                                        mBTService = null;
                                    }
                                }
                            }
                            else
                                mButtonOpen.setEnabled(true);
                            break;
                    }
                    break;
                case MESSAGE_SHOW_MSG:
                    String showMsg = (String) msg.obj;
                    mMessage.setText(showMsg);
                    break;
                case MESSAGE_SHOW_PROGRESSBAR:
                    mButtonOpen.setEnabled(false);
                    mButtonSave.setEnabled(false);
                    mProgressbar1.setProgress(mStep);
                    break;
                case MESSAGE_SHOW_IMAGE:
                    mProgressbar1.setProgress(0);
                    ShowBitmap();
                    mButtonSave.setEnabled(true);
                    mButtonOpen.setEnabled(true);
                    mButtonCaptureRAW.setEnabled(true);
                    mButtonCaptureWSQ.setEnabled(true);
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_STOP:
                    mMessage.setText("Cancelled by user.");
                    mButtonSave.setEnabled(true);
                    mButtonOpen.setEnabled(true);
                    mButtonCaptureRAW.setEnabled(true);
                    mButtonCaptureWSQ.setEnabled(true);
                    break;
                case MESSAGE_DATA_ERROR:
                    mButtonOpen.setEnabled(true);
                    switch (msg.arg1) {
                        case FamBTComm.ERROR_TIMEOUT:
                            mStep = 0;
                            mProgressbar1.setProgress(0);
                            mMessage.setText("Time out to receive data!");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            if( mConnected )
                            {
                                mConnected = false;
                                if( !mStop )
                                {
                                    if( mBTService != null )
                                    {
                                        mBTService.stop();
                                        mBTService = null;
                                    }
                                    mStop = true;
                                    mButtonOpen.setText("Open BT Comm");
                                }
                            }
                            break;
                    }
                    break;
                case MESSAGE_EXIT_PROGRAM:
                    ExitProgram();
                    break;
                case MESSAGE_ENABLE_BUTTONS:
                    mButtonCaptureRAW.setEnabled(true);
                    mButtonCaptureWSQ.setEnabled(true);
                    mButtonStop.setEnabled(false);
                    break;
                case MESSAGE_DISABLE_STOP:
                    mButtonOpen.setEnabled(false);
                    mButtonStop.setEnabled(false);
                    break;
            }
        }
    };

    private void ShowBitmap()
    {
        int[] pixels = new int[153600];
        for( int i=0; i<153600; i++)
            pixels[i] = mImageFP[i];
        Bitmap emptyBmp = Bitmap.createBitmap(pixels, 320, 480, Bitmap.Config.RGB_565);

        int width, height;
        height = emptyBmp.getHeight();
        width = emptyBmp.getWidth();

        mBitmapFP = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(mBitmapFP);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(emptyBmp, 0, 0, paint);

        mFingerImage.setImageBitmap(mBitmapFP);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a session
                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.d(TAG, "BT is not enabled");
                    Toast.makeText(this, "BT is not enabled", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    mStop = false;
                    mButtonOpen.setText("Close BT Comm");
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mBTService = new BluetoothDataService(this, mHandler);
                    mBTService.connect(device);
                }
                else
                    mMessage.setText("Not connected");
                break;
            case REQUEST_FILE_FORMAT:
                if (resultCode == Activity.RESULT_OK) {
                    // Get the file format
                    String[] extraString = data.getExtras().getStringArray(SelectFileFormatActivity.EXTRA_FILE_FORMAT);
                    String fileFormat = extraString[0];
                    String fileName = extraString[1];
                    SaveImageByFileFormat(fileFormat, fileName);
                }
                else
                    mMessage.setText("Cancelled!");
                break;
        }
    }
}

