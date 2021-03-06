package com.lambdazhao.myipcamera;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.lambdazhao.common.NetworkProfiler;
import com.lambdazhao.common.TimeProfile;
import com.lambdazhao.common.media.CameraHelper;
import com.lambdazhao.common.media.DropFrameNetworkBufferManager;  
import com.lambdazhao.common.media.DropFrameThreadPool;
import com.lambdazhao.myipcamera.libjpeg.JavaLibjpeg;

import com.lambdazhao.myipcamera.libjpeg.CompressJpegParam;
import com.lambdazhao.myipcamera.libjpeg.JavaLibjpeg.JpegColorSpace;
import com.lambdazhao.myipcamera.libjpeg.JavaMemDest;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity implements Camera.PreviewCallback {
	Button btnTrigger=null;
	
    private Camera mCamera;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder;

    private boolean isRecording = false;
    private static final String TAG = "Recorder";
    private Button captureButton;
    private ConcurrentLinkedQueue<byte[]> mBuffer=null;
    private int concurrentNum=4;

    DropFrameThreadPool compressPool=new DropFrameThreadPool("compressPool",concurrentNum,concurrentNum);
    DropFrameNetworkBufferManager networkBufferManager=new DropFrameNetworkBufferManager(1);
    NetworkStreamingServer networkStreamingServer=new NetworkStreamingServer(networkBufferManager);
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	     mPreview = (TextureView) findViewById(R.id.surface_view);
	     
		btnTrigger=(Button)this.findViewById(R.id.btnTrigger);
		btnTrigger.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
			
				 new MediaPrepareTask().execute(null, null, null);
//
//				
//				byte array[]=new byte[640*480*3];
//				int k=0;
//				for(int j=0;j<480;j++)
//				{
//					for(int i=0;i<640;i++)
//					{
//						int clr=i*j;
//						
//						array[k++]=(byte) (clr&0xFF);
//						array[k++]=(byte) ((clr>>8)&0xFF);
//						array[k++]=(byte) ((clr>>16)&0xFF);
//						
//					}
//				}
//				JavaMemDest javaMemDest=new JavaMemDest(10000);
//				CompressJpegParam param=new CompressJpegParam();
//				param.width=640;
//				param.height=480;
//				param.in_color_space=JpegColorSpace.JCS_EXT_BGR.value;
//				param.inputComponents=3;
//				param.quality=90;
//				//for(int i=0;i<100;i++)
//				{
//					JavaLibjpeg.compressJpeg(array, javaMemDest,param);
//				}
//				File file=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/fuck1.jpg");
//				
//				FileOutputStream fos;
//				try {
//					fos = new FileOutputStream(file);
//					fos.write(javaMemDest.GetBuffer(),0, javaMemDest.GetOutSize());
//				} catch (FileNotFoundException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			    
//				//test1(Environment.getExternalStorageDirectory().getAbsolutePath()+"/fuck1.jpg");
			}
        	
        });
		
	}
	Camera.Parameters parameters;
	CamcorderProfile profile ;
	ThreadLocal<Integer>  convertBuffer=new ThreadLocal<Integer>()
			{
			    protected Integer initialValue() {
			        return JavaLibjpeg.newBuffer(profile.videoFrameWidth*profile.videoFrameHeight*3);
			    }
			};
    private boolean prepareVideoRecorder() throws IOException {
       
//        try {
//            socket=new ServerSocket(8081);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        client=socket.accept();
//        PrintWriter pw = new PrintWriter(client.getOutputStream());
//        pw.print("HTTP/1.1 " + 200 + " \r\n");
//        pw.print("Connection: close\r\n");
//        pw.write("Content-Type: multipart/x-mixed-replace;boundary=Ba4oTvQMY8ew04N8dcnM\r\n");
//        pw.flush();
        // BEGIN_INCLUDE (configure_preview)
    	networkStreamingServer.start();
    	
        mCamera = CameraHelper.getDefaultCameraInstance();

        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        parameters = mCamera.getParameters();
        
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize(mSupportedPreviewSizes,
                mPreview.getWidth(), mPreview.getHeight());

        // Use the same size for recording profile.
        profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        profile.videoFrameWidth = 1280;
        profile.videoFrameHeight = 720;


        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        List<int[]> mSupportedPreviewFPS=parameters.getSupportedPreviewFpsRange();
        parameters.setPreviewFpsRange(24000,24000);
        parameters.setPreviewFormat(ImageFormat.NV21);

        List<Integer> wt= parameters.getSupportedPreviewFormats();
      //  convertBuffer=JavaLibjpeg.newBuffer(profile.videoFrameWidth*profile.videoFrameHeight*3);
        try{
        mCamera.setParameters(parameters);
        mCamera.setDisplayOrientation(90);
        }
        catch (Exception e)
        {
        	e.printStackTrace();
        }
        int size = profile.videoFrameWidth * profile.videoFrameHeight *
                ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
        mBuffer = new ConcurrentLinkedQueue<byte[]>();
        for(int i=0;i<concurrentNum+5;i++)
        {
        	mBuffer.add(new byte[size]);
        }
        mCamera.addCallbackBuffer(mBuffer.poll());
        mCamera.setPreviewCallbackWithBuffer(this);
        try {
                // Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
                // with {@link SurfaceView}
                mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            return false;
        }
        // END_INCLUDE (configure_preview)

        // BEGIN_INCLUDE (configure_media_recorder)
    //    mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder


        try {
            mCamera.startPreview();
        }
        catch (Exception ex) {
            Log.d("fuck",ex.getMessage());
        }
          //  mCamera.unlock();
        return true;
//        mCamera.unlock();
//
//        mMediaRecorder.setCamera(mCamera);
//
//        // Step 2: Set sources
//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT );
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//
//        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
//        mMediaRecorder.setProfile(profile);
//
//        // Step 4: Set output file
//        mMediaRecorder.setOutputFile(CameraHelper.getOutputMediaFile(
//                CameraHelper.MEDIA_TYPE_VIDEO).toString());
//        // END_INCLUDE (configure_media_recorder)
//
//        // Step 5: Prepare configured MediaRecorder
//        try {
//            mMediaRecorder.prepare();
//        } catch (IllegalStateException e) {
//            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
//            releaseMediaRecorder();
//            return false;
//        } catch (IOException e) {
//            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
//            releaseMediaRecorder();
//            return false;
//        }
//        return true;
    }
	 /**
     * Asynchronous task for preparing the {@link android.media.MediaRecorder} since it's a long blocking
     * operation.
     */
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            try {
                if (prepareVideoRecorder()) {
                    // Camera is available and unlocked, MediaRecorder is prepared,
                    // now you can start recording
    //                mMediaRecorder.start();

                    isRecording = true;
                } else {
                    // prepare didn't work, release the camera
                   // releaseMediaRecorder();
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                MainActivity.this.finish();
            }
            // inform the user that recording has started
          //  setCaptureButtonText("Stop");

        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	public native void test1(String path);
	static {
		JavaLibjpeg.loadLibrary();
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	 
	int count=0;	
	TimeProfile profileFrame=null;
   @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
	   
		if(profileFrame!=null)
			profileFrame.End();

		profileFrame=TimeProfile.Start("MyIpCamera", "frameBetween");

		final byte[] buffer=data;
		
		boolean isScheduled=compressPool.Schedule(new Runnable(){
			@Override
			public void run() {
				Log.d("MyIpCamera","srcbuffer using"+buffer.hashCode());
				CompressJpegParam param=new CompressJpegParam();
				param.width=profile.videoFrameWidth;
				param.height=profile.videoFrameHeight;
				param.in_color_space=JpegColorSpace.JCS_NV12.value;
				param.inputComponents=3;
				param.quality=25;
				param.row_stride=profile.videoFrameWidth ;
				param.image_convert_buffer=convertBuffer.get();
				TimeProfile profileCompress=TimeProfile.Start("MyIpCamera", "jpegCompress");
				JavaMemDest javaMemDest=networkBufferManager.GetWriteBuffer();
				JavaLibjpeg.compressJpeg(buffer, javaMemDest,param);
				try {
					networkBufferManager.ScheduleWriteBuffer();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					Log.d("MyIpCamera","ScheduleWriteBuffer Error");
				}
				profileCompress.End();
				
				Log.d("MyIpCamera","srcbuffer pushback"+buffer.hashCode());
				mBuffer.add(buffer);
			}
		});
		if(!isScheduled)
		{
			
			mBuffer.add(buffer);
		}

        
        byte[] nextBuffer=mBuffer.poll();
        if(nextBuffer==null)
        	Log.e("MyIpCamera","warning: null buffer!");
        Log.d("MyIpCamera","srcbuffer addCallbackBuffer"+buffer.hashCode());
	    camera.addCallbackBuffer(nextBuffer);

   }
   


}
