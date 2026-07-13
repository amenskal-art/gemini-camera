package com.example.usbcamera

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera

class MainActivity : AppCompatActivity() {

    private lateinit var usbMonitor: USBMonitor
    private var uvcCamera: UVCCamera? = null
    private lateinit var cameraSurfaceView: SurfaceView
    private var isPreviewing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraSurfaceView = findViewById(R.id.cameraSurfaceView)

        usbMonitor = USBMonitor(this, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                // Camera plugged in! Requesting permission from user.
                usbMonitor.requestPermission(device)
            }

            override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
                // Permission granted. Opening the camera feed.
                uvcCamera = UVCCamera().apply {
                    open(ctrlBlock)
                    // You can change resolution here. 640x480 is universally supported.
                    setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG) 
                    setPreviewDisplay(cameraSurfaceView.holder)
                    startPreview()
                }
                isPreviewing = true
                
                // Switch to UI thread to hook up the sliders
                runOnUiThread { bindControls() }
            }

            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                releaseCamera()
            }

            override fun onDettach(device: UsbDevice?) {
                Toast.makeText(this@MainActivity, "Camera disconnected", Toast.LENGTH_SHORT).show()
            }
            
            override fun onCancel(device: UsbDevice?) {}
        })

        // Handle surface lifecycle so we don't draw to a dead view
        cameraSurfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (isPreviewing) uvcCamera?.stopPreview()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        usbMonitor.register()
    }

    override fun onStop() {
        super.onStop()
        usbMonitor.unregister()
        releaseCamera()
    }

    private fun releaseCamera() {
        isPreviewing = false
        uvcCamera?.stopPreview()
        uvcCamera?.destroy()
        uvcCamera = null
    }

    private fun bindControls() {
        uvcCamera?.let { camera ->
            // Note: Auto-mode often overrides manual settings. If your camera supports it, 
            // you might need to turn off Auto Exposure / Auto White Balance here first:
            // camera.autoExposure = false
            // camera.autoWhitebalance = false

            setupSeekBar(R.id.seekBrightness, camera, { camera.brightness = it }, { camera.brightness })
            setupSeekBar(R.id.seekContrast, camera, { camera.contrast = it }, { camera.contrast })
            setupSeekBar(R.id.seekSharpness, camera, { camera.sharpness = it }, { camera.sharpness })
            setupSeekBar(R.id.seekZoom, camera, { camera.zoom = it }, { camera.zoom })
            setupSeekBar(R.id.seekExposure, camera, { camera.exposure = it }, { camera.exposure })
            setupSeekBar(R.id.seekWhiteBalance, camera, { camera.whitebalance = it }, { camera.whitebalance })
        }
    }

    private fun setupSeekBar(id: Int, camera: UVCCamera, setter: (Int) -> Unit, getter: () -> Int) {
        val seekBar = findViewById<SeekBar>(id)
        try {
            // Attempt to read current value from hardware. If it fails, disable the slider.
            seekBar.progress = getter()
        } catch (e: Exception) {
            Log.e("USBCamera", "Parameter not supported by this hardware: ${e.message}")
            seekBar.isEnabled = false
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        setter(progress)
                        // Critical step: Push the register changes to the hardware.
                        camera.updateCameraParams() 
                    } catch (e: Exception) {
                        Log.e("USBCamera", "Failed to set parameter: ${e.message}")
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}