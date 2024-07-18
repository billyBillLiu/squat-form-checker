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

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.example.camera.ml.MovenetSingleposeLightning
import com.example.camera.ml.MovenetSingleposeThunder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2


class CheckingActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var upIndicator: ImageView
    private lateinit var finishButton: Button
    private lateinit var flipCameraButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var facing: CameraSelector
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var lightningModel: MovenetSingleposeLightning
    private lateinit var thunderModel: MovenetSingleposeThunder
    private lateinit var soundPlayer: SoundPlayer

    // Joints Used (Shoulder, Hip, Knee, Ankle)
    private var rightJoints = listOf(18, 36, 42, 48)
    private var leftJoints = listOf(15, 33, 39, 45)
    private var numJoints = 4

    // Status Checkers
    private var rightSide = true
    private var mirrored = false
    private var isUsingThunder = false
    private var barbellMode = false

    //Angles Selected by User in Playback Activity
    private var desiredHipAngle: Float = 0.0f
    private var desiredKneeAngle: Float = 0.0f
    private var tensorSize = 192

    // App
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checking)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        upIndicator = findViewById(R.id.upIndicator)
        finishButton = findViewById(R.id.finishButton)
        flipCameraButton = findViewById(R.id.flipCameraButton)
        cameraExecutor = Executors.newSingleThreadExecutor()
        facing = CameraSelector.DEFAULT_BACK_CAMERA
        lightningModel = MovenetSingleposeLightning.newInstance(this)
        thunderModel = MovenetSingleposeThunder.newInstance(this)
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(tensorSize, tensorSize, ResizeOp.ResizeMethod.BILINEAR)).build()
        soundPlayer = SoundPlayer(this)
        rightSide = intent.getBooleanExtra("SIDE", true)
        desiredHipAngle = intent.getFloatExtra("HIP_ANGLE", 0.0f)
        desiredKneeAngle = intent.getFloatExtra("KNEE_ANGLE", 0.0f)
        isUsingThunder = intent.getBooleanExtra("IS_USING_THUNDER", false)
        tensorSize = if (isUsingThunder) { 256 } else { 192 }
        barbellMode = intent.getBooleanExtra("BARBELL_MODE", false)

        upIndicator.visibility = View.GONE
        upIndicator.setImageResource(R.drawable.up_arrow)

        finishButton.setOnClickListener {
            onFinished()
        }

        flipCameraButton.setOnClickListener {
            flipCamera()
        }

        if (barbellMode) {
            rightJoints = rightJoints.drop(1)
            leftJoints = leftJoints.drop(1)
            numJoints = 3
        }

        buildImageProcessor()
        startCamera()
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
                results.add( Pair(x, y) )
            }
        }

        overlayView.drawJoints(results, mirrored, isUsingThunder, barbellMode)
        checkDepthAndUpdateUI(results)
    }

    private fun checkDepthAndUpdateUI(results: List<Pair<Float, Float>>) {
        if (results.size == numJoints) {
            val depthReached = if (barbellMode) {
                val currentKneeAngle = calculateAngle(results[0], results[1], results[2])
                currentKneeAngle <= desiredKneeAngle
            } else {
                val currentKneeAngle = calculateAngle(results[1], results[2], results[3])
                val currentHipAngle = calculateAngle(results[0], results[1], results[2])
                currentHipAngle <= desiredHipAngle && currentKneeAngle <= desiredKneeAngle
            }
            runOnUiThread {
                if (depthReached) {
                    soundPlayer.playSound()
                    upIndicator.visibility = View.VISIBLE
                } else {
                    upIndicator.visibility = View.GONE
                }
            }
        }
    }

    private fun calculateAngle(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>): Float {
        val radians = atan2((c.second - b.second).toDouble(), (c.first - b.first).toDouble()) -
                atan2((a.second - b.second).toDouble(), (a.first - b.first).toDouble())
        var angle = abs(radians * 180.0 / PI).toFloat()
        if (angle > 180.0) {
            angle = 360 - angle
        }
        return angle
    }

    private fun onFinished() {
        val intent = Intent(this, RecordingActivity::class.java)
        startActivity(intent)
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        soundPlayer.release()
        thunderModel.close()
        lightningModel.close()
    }

}