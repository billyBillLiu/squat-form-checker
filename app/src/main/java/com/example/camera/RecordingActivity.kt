package com.example.camera

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat

import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.widget.SwitchCompat

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.camera.ml.MovenetSingleposeLightning
import com.example.camera.ml.MovenetSingleposeThunder


class RecordingActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var recordButton: Button
    private lateinit var flipCameraButton: Button
    private lateinit var helpButton: Button
    private lateinit var sideSelectionSwitch: ConstraintLayout
    private lateinit var rightIndicator: ImageView
    private lateinit var leftIndicator: ImageView
    private lateinit var helpView: CardView
    private lateinit var thunderSwitch: SwitchCompat
    private lateinit var barbellSwitch: SwitchCompat

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var facing: CameraSelector
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var lightningModel: MovenetSingleposeLightning
    private lateinit var thunderModel: MovenetSingleposeThunder

    // Joints Used (Shoulder, Hip, Knee, Ankle)
    private var rightJoints = listOf(18, 36, 42, 48)
    private var leftJoints = listOf(15, 33, 39, 45) //Shoulder, Hip, Knee, Ankle
    private var numJoints = 4
    private val capturedFrames = mutableListOf<FrameData>()
    object FrameDataCache {
        var capturedFrames: MutableList<FrameData> = mutableListOf()
    }

    // Variables
    private var helpShowing = false
    private var rightSide = true
    private var mirrored = false
    private var recording = false
    private var isUsingThunder = false // if false, then we are using lightning
    private var barbellMode = false

    private var tensorSize = 192 //192 for lightning, 256 for thunder
    // App
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        recordButton = findViewById(R.id.recordButton)
        flipCameraButton = findViewById(R.id.flipCameraButton)
        helpButton = findViewById(R.id.helpButton)
        sideSelectionSwitch = findViewById(R.id.sideSelectionSwitch)
        rightIndicator = findViewById(R.id.rightIndicator)
        leftIndicator = findViewById(R.id.leftIndicator)
        helpView = findViewById(R.id.helpView)
        thunderSwitch = findViewById(R.id.thunderModelSwitch)
        barbellSwitch = findViewById(R.id.barbellModeSwitch)

        cameraExecutor = Executors.newSingleThreadExecutor()
        facing = CameraSelector.DEFAULT_BACK_CAMERA
        lightningModel = MovenetSingleposeLightning.newInstance(this)
        thunderModel = MovenetSingleposeThunder.newInstance(this)
    
        recordButton.setOnClickListener {
            handleRecording()
        }

        flipCameraButton.setOnClickListener {
            flipCamera()
        }

        helpButton.setOnClickListener{
            toggleHelpOverlay()
        }

        sideSelectionSwitch.setOnClickListener{
            toggleIndicators()
        }

        thunderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                isUsingThunder = true
                tensorSize = 256
                buildImageProcessor()
            } else {
                isUsingThunder = false
                tensorSize = 192
                buildImageProcessor()
            }
        }

        barbellSwitch.setOnCheckedChangeListener { _, isChecked ->
            barbellMode = isChecked
            if (isChecked) {
                rightJoints = rightJoints.drop(1)
                leftJoints = leftJoints.drop(1)
                numJoints = 3
            } else {
                rightJoints = listOf(18) + rightJoints
                leftJoints = listOf(15) + leftJoints
                numJoints = 4
            }
        }

        leftIndicator.visibility = View.GONE
        helpView.visibility = View.GONE

        buildImageProcessor()
        startCamera()
    }



    private fun handleRecording() {
        if (recording) {
            recording = false
            startPlayback()
        } else {
            recordButton.setBackgroundColor(Color.RED)
            recording = true
        }
    }

    private fun flipCamera() {
        if (facing == CameraSelector.DEFAULT_BACK_CAMERA) {
            facing = CameraSelector.DEFAULT_FRONT_CAMERA
            mirrored = true
        } else {
            facing = CameraSelector.DEFAULT_BACK_CAMERA
            mirrored = false
        }
        startCamera()
    }

    private fun toggleHelpOverlay() {
        helpShowing = !helpShowing
        helpView.visibility = if (helpShowing) View.VISIBLE else View.GONE
    }

    private fun toggleIndicators() {
        if (rightSide) {
            showLeftIndicator()
        } else {
            showRightIndicator()
        }
        rightSide = !rightSide
    }

    private fun showLeftIndicator() {
        rightIndicator.visibility = View.GONE
        leftIndicator.visibility = View.VISIBLE
    }

    private fun showRightIndicator() {
        rightIndicator.visibility = View.VISIBLE
        leftIndicator.visibility = View.GONE
    }

    private fun buildImageProcessor() {
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(tensorSize, tensorSize, ResizeOp.ResizeMethod.BILINEAR)).build()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageAnalysis = ImageAnalysis.Builder().build()
            try {
                imageAnalysis.setAnalyzer(cameraExecutor) { image ->
                    val bitmap = image.toBitmap()
                    processFrame(bitmap)
                    image.close()
                }
            } catch (exc: Exception) {
                Log.e("startCamera", "Frame Processing Failed", exc)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, facing, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("startCamera", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(bitmap: Bitmap) {

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, tensorSize, tensorSize, 3), DataType.FLOAT32)
        inputFeature0.loadBuffer(tensorImage.buffer)

        val outputFeature0: FloatArray = if (isUsingThunder) {
            val outputs = thunderModel.process(inputFeature0)
            outputs.outputFeature0AsTensorBuffer.floatArray
        } else {
            val outputs = lightningModel.process(inputFeature0)
            outputs.outputFeature0AsTensorBuffer.floatArray
        }

        val results = mutableListOf<Pair<Float, Float>>()

        val joints = if (rightSide) rightJoints else leftJoints
        for (index in joints) {
            val x = outputFeature0[index]
            val y = outputFeature0[index+1]
            val confidence = outputFeature0[index + 2]
            if (confidence > 0.30) {
                // Log.d("processFrame", "Index: $index | Coordinates: $x, $y | Confidence: $confidence")
                results.add( Pair(x, y) )
            }
        }

        overlayView.drawJoints(results, mirrored, isUsingThunder, barbellMode)

        if (recording) {
            captureFrame(bitmap, results, mirrored)
        }

    }

    private fun captureFrame(bitmap: Bitmap, results: List<Pair<Float, Float>>, mirrored: Boolean) {
        // rotate bitmap
        val matrix = Matrix()
        matrix.postRotate(if (mirrored) -90f else 90f)

        val newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val canvas = Canvas(newBitmap)

        val width = newBitmap.width
        val height = newBitmap.height


        val linePaint = Paint().apply {
            color = Color.argb(128, 0, 255, 0)
            style = Paint.Style.STROKE
            strokeWidth = 15f
        }

        if (results.size == numJoints) {
            for (i in 0 until results.size - 1) {
                val (x1, y1) = results[i]
                val (x2, y2) = results[i + 1]
                val startX = if (mirrored) x1 * width else width - (x1 * width)
                val startY = if (mirrored) height - (y1 * height) else y1 * height
                val endX = if (mirrored) x2 * width else width - (x2 * width)
                val endY = if (mirrored) height - (y2 * height) else y2 * height
                canvas.drawLine(startX, startY, endX, endY, linePaint)
            }
        }

        capturedFrames.add(FrameData(newBitmap, results))
    }

    private fun startPlayback() {
        FrameDataCache.capturedFrames = capturedFrames
        val intent = Intent(this, PlaybackActivity::class.java)
            .putExtra("SIDE", rightSide)
            .putExtra("IS_USING_THUNDER", isUsingThunder)
            .putExtra("BARBELL_MODE", barbellMode)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        thunderModel.close()
        lightningModel.close()
    }

}