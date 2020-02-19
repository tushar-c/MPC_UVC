/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest8;

import android.animation.Animator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.serenegiant.common.BaseActivity;

import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.utils.ViewAnimationHelper;
import com.serenegiant.widget.CameraViewInterface;

import java.util.ArrayList;

public final class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "MainActivity";

	/**
	 * set true if you want to record movie using MediaSurfaceEncoder
	 * (writing frame data into Surface camera from MediaCodec
	 *  by almost same way as USBCameratest2)
	 * set false if you want to record movie using MediaVideoEncoder
	 */
    private static final boolean USE_SURFACE_ENCODER = false;

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 1280;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 720;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = 1;

	protected static final int SETTINGS_HIDE_DELAY_MS = 2500;

	/**
	 * for accessing USB
	 */
	private USBMonitor mUSBMonitor;
	/**
	 * Handler to execute camera related methods sequentially on private thread
	 */
	private UVCCameraHandler mCameraHandler;
	/**
	 * for camera preview display
	 */
	private CameraViewInterface mUVCCameraView;
	/**
	 * for open&start / stop&close camera preview
	 */
	private ToggleButton mCameraButton;
	/**
	 * button for start/stop recording
	 */
	private ImageButton mCaptureButton;

	private ImageButton mScreenshotButton;

	private ImageButton filters_button;

	private RelativeLayout mainPageLayout;

	private View mBrightnessButton, mContrastButton;
	private View mResetButton;
	private View mToolsLayout, mValueLayout;
	private SeekBar mSettingSeekbar;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

		setContentView(R.layout.activity_main);

		mCameraButton = (ToggleButton)findViewById(R.id.camera_button);
		mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);

		mainPageLayout = findViewById(R.id.mainPageLayout);

		mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
		mCaptureButton.setOnClickListener(mOnClickListener);
		mCaptureButton.setVisibility(View.INVISIBLE);

		mScreenshotButton = (ImageButton)findViewById(R.id.image_capture_button);
		mScreenshotButton.setOnClickListener(mOnClickListener);
		mScreenshotButton.setVisibility(View.INVISIBLE);

		final View view = findViewById(R.id.camera_view);
		view.setOnLongClickListener(mOnLongClickListener);
		mUVCCameraView = (CameraViewInterface)view;
		mUVCCameraView.setAspectRatio((float) getScreenWidth()/getScreenHeight());

		mResetButton = findViewById(R.id.reset_button);
		mResetButton.setOnClickListener(mOnClickListener);

		mSettingSeekbar = (SeekBar)findViewById(R.id.setting_seekbar);
		mSettingSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

		mToolsLayout = findViewById(R.id.tools_layout);
		mToolsLayout.setVisibility(View.INVISIBLE);

		mValueLayout = findViewById(R.id.value_layout);
		mValueLayout.setVisibility(View.INVISIBLE);

		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
		mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
			USE_SURFACE_ENCODER ? 0 : 1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);

		final Dialog dialog = new Dialog(MainActivity.this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.filter_list_dialog);

		filters_button = findViewById(R.id.filters_button);

		filters_button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				dialog.show();

				String[] filters_array = new String[]{"Brightness","Contrast","Sharpness","Saturation"};
				ListView listView = (ListView) dialog.findViewById(R.id.filters_list);

				ArrayAdapter adapter = new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1,filters_array);

				listView.setAdapter(adapter);

				listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						switch (position) {
							case 0:
								dialog.cancel();
								showSettings(UVCCamera.PU_BRIGHTNESS);
								break;
							case 1:
								dialog.cancel();
								showSettings(UVCCamera.PU_CONTRAST);
								break;
							case 2:
								dialog.cancel();
								showSettings(UVCCamera.PU_SHARPNESS);
								break;
							case 3:
								dialog.cancel();
								showSettings(UVCCamera.PU_SATURATION);
								break;

						}
					}
				});
			}
		});

	}

	public static int getScreenWidth() {
		return Resources.getSystem().getDisplayMetrics().widthPixels;
	}

	public static int getScreenHeight() {
		return Resources.getSystem().getDisplayMetrics().heightPixels;
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");
		mUSBMonitor.register();
		if (mUVCCameraView != null)
			mUVCCameraView.onResume();

		Window window = getWindow();


	}

	@Override
	protected void onStop() {
		if (DEBUG) Log.v(TAG, "onStop:");
		mCameraHandler.close();
		if (mUVCCameraView != null)
			mUVCCameraView.onPause();
		setCameraButton(false);
		super.onStop();
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
        if (mCameraHandler != null) {
	        mCameraHandler.release();
	        mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
	        mUSBMonitor.destroy();
	        mUSBMonitor = null;
        }
        mUVCCameraView = null;
        mCameraButton = null;
        mCaptureButton = null;
        mScreenshotButton = null;
        filters_button = null;
		super.onDestroy();
	}

	/**
	 * event handler when click camera / capture button
	 */
	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			switch (view.getId()) {
			case R.id.capture_button:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
						if (!mCameraHandler.isRecording()) {
							mCaptureButton.setColorFilter(0xffff0000);	// turn red
							mCameraHandler.startRecording();
						} else {
							mCaptureButton.setColorFilter(0);	// return to default color
							mCameraHandler.stopRecording();
							Toast.makeText(MainActivity.this, String.valueOf("Path : " + mCameraHandler.getPath()), Toast.LENGTH_SHORT).show();
						}
					}
				}
				break;
			case R.id.image_capture_button:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
						mCameraHandler.captureStill();
						Toast.makeText(MainActivity.this, "Image saved in your gallery", Toast.LENGTH_SHORT).show();
						showControls();
					}
				}
			case R.id.camera_view:
				if (mCameraHandler.isOpened()) {
					if (mCameraButton.getVisibility() == View.VISIBLE) {
						hideControls();
					}else {
						showControls();
					}
				}
			case R.id.reset_button:
				resetSettings();
				break;
			}
		}
	};

	private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
		= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
			switch (compoundButton.getId()) {
			case R.id.camera_button:
				if (isChecked && !mCameraHandler.isOpened()) {
					CameraDialog.showDialog(MainActivity.this);
				} else {
					mCameraHandler.close();
					setCameraButton(false);
				}
				break;
			}
		}
	};

	private void hideControls(){

		mCameraButton.animate().alpha(0f).setDuration(500).start();
		mCaptureButton.animate().alpha(0f).setDuration(500).start();
		mScreenshotButton.animate().alpha(0f).setDuration(500).start();
		filters_button.animate().alpha(0f).setDuration(500).start();

		final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				mCameraButton.setVisibility(View.INVISIBLE);
				mCaptureButton.setVisibility(View.INVISIBLE);
				mScreenshotButton.setVisibility(View.INVISIBLE);
				filters_button.setVisibility(View.INVISIBLE);
			}
		}, 500);
		
    }

    private void showControls(){

		mCameraButton.setVisibility(View.VISIBLE);
		mCaptureButton.setVisibility(View.VISIBLE);
		mScreenshotButton.setVisibility(View.VISIBLE);
		filters_button.setVisibility(View.VISIBLE);

		mCameraButton.animate().alpha(1.0f).setDuration(500).start();
		mCaptureButton.animate().alpha(1.0f).setDuration(500).start();
		mScreenshotButton.animate().alpha(1.0f).setDuration(500).start();
		filters_button.animate().alpha(1.0f).setDuration(500).start();

    }

	/**
	 * capture still image when you long click on preview image(not on buttons)
     */
	private final OnLongClickListener mOnLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(final View view) {
			switch (view.getId()) {
			case R.id.camera_view:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage()) {
//						mCameraHandler.captureStill();
					}
					return true;
				}
			}
			return false;
		}
	};

	private void setCameraButton(final boolean isOn) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mCameraButton != null) {
					try {
						mCameraButton.setOnCheckedChangeListener(null);
						mCameraButton.setChecked(isOn);
					} finally {
						mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
					}
				}
				if (!isOn && (mCaptureButton != null)) {
					mCaptureButton.setVisibility(View.INVISIBLE);
					mScreenshotButton.setVisibility(View.INVISIBLE);
					filters_button.setVisibility(View.INVISIBLE);
					mainPageLayout.setVisibility(View.VISIBLE);
					View view = findViewById(R.id.camera_view);
					view.setOnClickListener(null);
				}
			}
		}, 0);
		updateItems();
	}

	public CameraViewInterface getmUVCCameraView() {
		return mUVCCameraView;
	}

	private void startPreview() {
		final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
		mCameraHandler.startPreview(new Surface(st));

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mCaptureButton.setVisibility(View.VISIBLE);
				mScreenshotButton.setVisibility(View.VISIBLE);
				filters_button.setVisibility(View.VISIBLE);
				mainPageLayout.setVisibility(View.INVISIBLE);
				hideControls();
				View view = findViewById(R.id.camera_view);
				view.setOnClickListener(mOnClickListener);
			}
		});
		updateItems();
	}

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB Device Attached", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "onConnect:");
			mCameraHandler.open(ctrlBlock);
			startPreview();
			updateItems();
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			if (mCameraHandler != null) {
				queueEvent(new Runnable() {
					@Override
					public void run() {
						mCameraHandler.close();
					}
				}, 0);
				setCameraButton(false);
				updateItems();
			}
		}
		@Override
		public void onDettach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB Device Detached", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
			setCameraButton(false);
		}
	};

	/**
	 * to access from CameraDialog
	 * @return
	 */
	@Override
	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	@Override
	public void onDialogResult(boolean canceled) {
		if (DEBUG) Log.v(TAG, "onDialogResult:canceled=" + canceled);
		if (canceled) {
			setCameraButton(false);
		}
	}

//================================================================================
	private boolean isActive() {
		return mCameraHandler != null && mCameraHandler.isOpened();
	}

	private boolean checkSupportFlag(final int flag) {
		return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
	}

	private int getValue(final int flag) {
		return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
	}

	private int setValue(final int flag, final int value) {
		return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
	}

	private int resetValue(final int flag) {
		return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
	}

	private void updateItems() {
		runOnUiThread(mUpdateItemsOnUITask, 100);
	}

	private final Runnable mUpdateItemsOnUITask = new Runnable() {
		@Override
		public void run() {
			if (isFinishing()) return;
			final int visible_active = isActive() ? View.VISIBLE : View.INVISIBLE;
			mToolsLayout.setVisibility(visible_active);
		}
	};

	private int mSettingMode = -1;
	/**
	 * 設定画面を表示
	 * @param mode
	 */
	private final void showSettings(final int mode) {
		if (DEBUG) Log.v(TAG, String.format("showSettings:%08x", mode));
		hideSetting(false);
		if (isActive()) {
			switch (mode) {
            case UVCCamera.PU_WB_TEMP:
			case UVCCamera.PU_HUE:
			case UVCCamera.PU_SHARPNESS:
			case UVCCamera.PU_BRIGHTNESS:
			case UVCCamera.PU_CONTRAST:
			case UVCCamera.PU_SATURATION:
				mSettingMode = mode;
				mSettingSeekbar.setProgress(getValue(mode));
				ViewAnimationHelper.fadeIn(mValueLayout, -1, 0, mViewAnimationListener);
				break;
			}
		}
	}

	private void resetSettings() {
		if (isActive()) {
			switch (mSettingMode) {
			case UVCCamera.PU_HUE:
			case UVCCamera.PU_SATURATION:
			case UVCCamera.PU_SHARPNESS:
			case UVCCamera.PU_BRIGHTNESS:
			case UVCCamera.PU_CONTRAST:
				mSettingSeekbar.setProgress(resetValue(mSettingMode));
				break;
			}
		}
		mSettingMode = -1;
		ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
	}

	/**
	 * 設定画面を非表示にする
	 * @param fadeOut trueならばフェードアウトさせる, falseなら即座に非表示にする
	 */
	protected final void hideSetting(final boolean fadeOut) {
		removeFromUiThread(mSettingHideTask);
		if (fadeOut) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
				}
			}, 0);
		} else {
			try {
				mValueLayout.setVisibility(View.GONE);
			} catch (final Exception e) {
				// ignore
			}
			mSettingMode = -1;
		}
	}

	protected final Runnable mSettingHideTask = new Runnable() {
		@Override
		public void run() {
			hideSetting(true);
		}
	};

	/**
	 * 設定値変更用のシークバーのコールバックリスナー
	 */
	private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
			// 設定が変更された時はシークバーの非表示までの時間を延長する
			if (fromUser) {
				runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
			}

			if (isActive() && checkSupportFlag(mSettingMode)) {
				switch (mSettingMode) {
					case UVCCamera.PU_WB_TEMP:
					case UVCCamera.PU_HUE:
					case UVCCamera.PU_SHARPNESS:
					case UVCCamera.PU_BRIGHTNESS:
					case UVCCamera.PU_SATURATION:
					case UVCCamera.PU_CONTRAST:
						setValue(mSettingMode, seekBar.getProgress());
//						Toast.makeText(MainActivity.this, String.valueOf("Value : " + getValue(mSettingMode)), Toast.LENGTH_SHORT).show();
						break;
				}
			}
		}

		@Override
		public void onStartTrackingTouch(final SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(final SeekBar seekBar) {

			runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
			if (isActive() && checkSupportFlag(mSettingMode)) {
				switch (mSettingMode) {
				case UVCCamera.PU_WB_TEMP:
				case UVCCamera.PU_HUE:
				case UVCCamera.PU_SHARPNESS:
				case UVCCamera.PU_BRIGHTNESS:
				case UVCCamera.PU_SATURATION:
				case UVCCamera.PU_CONTRAST:
					setValue(mSettingMode, seekBar.getProgress());
//					Toast.makeText(MainActivity.this, String.valueOf("Value : " + getValue(mSettingMode)), Toast.LENGTH_SHORT).show();
					break;
				}
			}	// if (active)
		}
	};

	private final ViewAnimationHelper.ViewAnimationListener
		mViewAnimationListener = new ViewAnimationHelper.ViewAnimationListener() {
		@Override
		public void onAnimationStart(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
		}

		@Override
		public void onAnimationEnd(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
			final int id = target.getId();
			switch (animationType) {
			case ViewAnimationHelper.ANIMATION_FADE_IN:
			case ViewAnimationHelper.ANIMATION_FADE_OUT:
			{
				final boolean fadeIn = animationType == ViewAnimationHelper.ANIMATION_FADE_IN;
				if (id == R.id.value_layout) {
					if (fadeIn) {
						runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
					} else {
						mValueLayout.setVisibility(View.GONE);
						mSettingMode = -1;
					}
				} else if (!fadeIn) {
//					target.setVisibility(View.GONE);
				}
				break;
			}
			}
		}

		@Override
		public void onAnimationCancel(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
		}
	};

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
							| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN
							| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

}
