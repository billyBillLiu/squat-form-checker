    package com.example.camera

    import android.content.Intent
    import android.graphics.Color
    import android.os.Bundle
    import androidx.appcompat.app.AppCompatActivity
    import android.widget.Button
    import android.util.Log
    import android.widget.ImageView
    import android.widget.SeekBar
    import kotlin.math.*

    class PlaybackActivity : AppCompatActivity() {

        private lateinit var playbackView: ImageView
        private lateinit var backButton: Button
        private lateinit var selectPoseButton: Button
        private lateinit var frameSeekBar: SeekBar
        private var capturedFrames: MutableList<FrameData> = mutableListOf()
        private var frameIndex = 0
        private var barbellMode = false
        private var numJoints = 4

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_playback)

            playbackView = findViewById(R.id.playbackView)
            backButton = findViewById(R.id.backButton)
            selectPoseButton = findViewById(R.id.selectPoseButton)
            frameSeekBar = findViewById(R.id.frameSeekBar)

            capturedFrames = RecordingActivity.FrameDataCache.capturedFrames

            barbellMode = this.intent.getBooleanExtra("BARBELL_MODE", false)
            numJoints = if (barbellMode) 3 else 4

            backButton.setOnClickListener {
                restartRecording()
            }

            selectPoseButton.setBackgroundColor(Color.DKGRAY)
            selectPoseButton.setOnClickListener {
                onPoseSelected()
            }

            frameSeekBar.max = capturedFrames.size - 1
            frameSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    frameIndex = progress
                    if (capturedFrames[frameIndex].coordinates.size < numJoints) {
                        selectPoseButton.setBackgroundColor(Color.DKGRAY)
                    } else {
                        selectPoseButton.setBackgroundColor(Color.GREEN)
                    }
                    playFrame()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            playFrame()
        }

        private fun restartRecording() {
            RecordingActivity.FrameDataCache.capturedFrames.clear()
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
        }

        private fun onPoseSelected() {
            val frameData = capturedFrames[frameIndex]
            val coordinates = frameData.coordinates
            if (coordinates.size == numJoints) {
                if (barbellMode) {
                    val kneeAngle = calculateAngle(coordinates[0], coordinates[1], coordinates[2])
                    startChecking(kneeAngle, kneeAngle)
                } else {
                    val hipAngle = calculateAngle(coordinates[0], coordinates[1], coordinates[2])
                    val kneeAngle = calculateAngle(coordinates[1], coordinates[2], coordinates[3])
                    startChecking(hipAngle, kneeAngle)
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

        private fun playFrame() {
            val frameData = capturedFrames[frameIndex]
            playbackView.setImageBitmap(frameData.bitmap)
            Log.d("playFrame", "${frameData.coordinates}")
        }

        private fun startChecking(hipAngle: Float, kneeAngle: Float) {
            val intent = Intent(this, CheckingActivity::class.java)
                .putExtra("HIP_ANGLE", hipAngle)
                .putExtra("KNEE_ANGLE", kneeAngle)
                .putExtra("SIDE", this.intent.getBooleanExtra("SIDE", true))
                .putExtra("IS_USING_THUNDER", this.intent.getBooleanExtra("IS_USING_THUNDER", false))
                .putExtra("BARBELL_MODE", barbellMode)
            startActivity(intent)
        }
    }