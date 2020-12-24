package com.ankh.baarcodescanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
typealias LumaListener = (luma: String)->Unit

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var barcodeOptions: BarcodeScannerOptions
    private lateinit var scanner: BarcodeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(hasAllPermission()){
            startCamera()
        }else{
            ActivityCompat.requestPermissions(this,
                    REQUEST_PERMISSION_LIST, REQUEST_CODE_PERMISSION
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        barcodeOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_CODE_128)
                .build()

        scanner = BarcodeScanning.getClient(barcodeOptions)

    }

    private fun hasAllPermission() = REQUEST_PERMISSION_LIST.all{
        ContextCompat.checkSelfPermission(baseContext,it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_CODE_PERMISSION){
            if(hasAllPermission()){
                startCamera()
            }else{
                Toast.makeText(this,"Permission not granted",Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCamera(){
        ProcessCameraProvider.getInstance(this).apply {
            addListener({
                val cameraProvider: ProcessCameraProvider = this.get()

                val preview = Preview.Builder()
                        .build()
                        .also { preview ->
                            preview.setSurfaceProvider(cameraPreview.createSurfaceProvider())
                        }

                val imageAnalyzer = ImageAnalysis.Builder()
                        .build()
                        .also { analyzer->
                            analyzer.setAnalyzer(cameraExecutor, LuminosityAnalyzer(scanner){lumna ->
                                Toast.makeText(this@MainActivity,"Barcode: $lumna",Toast.LENGTH_LONG).show()
                            })
                        }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                            this@MainActivity, cameraSelector, preview, imageAnalyzer
                    )
                } catch (ex: Exception) {
                    Log.v(TAG,"Start camera Exception ${ex.message}")
                }


            }, ContextCompat.getMainExecutor(this@MainActivity))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class LuminosityAnalyzer(private val scanner: BarcodeScanner,private val listener: LumaListener ): ImageAnalysis.Analyzer{

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            mediaImage?.let{_image->
                val imageToAnalyze = InputImage.fromMediaImage(_image,imageProxy.imageInfo.rotationDegrees)
                scanner.process(imageToAnalyze)
                        .addOnSuccessListener { barcodes->
                            for(barcode in barcodes){
                                listener(barcode.rawValue!!)
                            }
                        }
                        .addOnFailureListener {
                            Log.v(TAG,"Image Processing Exception: ${it.message}")
                        }
                        .addOnCompleteListener {
                            mediaImage.close()
                            imageProxy.close()
                        }
            }


        }
    }

    companion object{
        private const val TAG = "Chatunga"
        private const val REQUEST_CODE_PERMISSION = 100
        private val REQUEST_PERMISSION_LIST = arrayOf(Manifest.permission.CAMERA)
    }
}

