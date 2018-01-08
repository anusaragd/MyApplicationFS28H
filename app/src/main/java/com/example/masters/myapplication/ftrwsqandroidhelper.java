package com.example.masters.myapplication;


public class ftrwsqandroidhelper {
	//public FtrImgParms mParams = null;
	public int mWidth;       // image Width
    public int mHeight;      // image Height
    public int mDPI;         // resolution Dots per inch
    public int mRAW_size;    // size of RAW image
    public int mBMP_size;    // size of BMP image
    public int mWSQ_size;    // size of WSQ image
    public float mBitrate;     // compression
    public int mNFIQ;		
	
	static {    
		System.loadLibrary("ftrwsqandroid");
		}   
	static {    
		System.loadLibrary("ftrnfiqapiandroid");
		} 
	static {    
		System.loadLibrary("ftrwsqandroidjni");
		}   
	
	private native boolean JNIGetImageParameters(byte[] wsqImg);
	private native boolean JNIWsqToRawImage(byte[] wsqImg, byte[] rawImg);
	private native boolean JNIGetNfiq(byte[] rawImg, int nWidth, int nHeight);
	
	public ftrwsqandroidhelper()
	{
		mWidth = mHeight = 0;
		mDPI = mRAW_size = mBMP_size = mWSQ_size = 0;
		mBitrate = 0.75f;	}
	
	public int GetWsqImageRawSize(byte[] wsqImg, int[] nWidth, int[] nHeight)
	{
		if( !JNIGetImageParameters(wsqImg) )
			return 0;
		nWidth[0] = mWidth;
		nHeight[0] = mHeight;
		return (mWidth * mHeight);
	}
	
	public boolean ConvertWsqToRaw(byte[] wsqImg, byte[] rawImg)
	{
		if(rawImg.length < (mWidth*mHeight))
			return false;
		return JNIWsqToRawImage(wsqImg, rawImg);			
	}
	
	public int GetRawImageNFIQ(byte[] rawImg, int nWidth, int nHeight)
	{
		if( JNIGetNfiq(rawImg, nWidth, nHeight) )
			return mNFIQ;
		return 0;
	}
}


