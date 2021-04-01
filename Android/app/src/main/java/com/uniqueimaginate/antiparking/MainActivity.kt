package com.uniqueimaginate.antiparking

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import kotlinx.android.synthetic.main.activity_main.view.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    var holder: SurfaceHolder? = null
    var surfaceView: SurfaceView? = null
    var canvas: Canvas? = null
    var paint: Paint? = null
    var xOffset = 0
    var yOffset = 0
    var boxWidth = 0
    var boxHeight = 0
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var retrofit: Retrofit
    private lateinit var service: AntiparkingService
    private lateinit var checkButton: Button
    private lateinit var addButton: Button
    private lateinit var resultText: TextView
    private lateinit var textView: TextView
    private lateinit var checkTextView: TextView
    private lateinit var mCameraView: PreviewView

    private fun degreesToFirebaseRotation(degrees: Int): Int {
        return when (degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw IllegalArgumentException(
                    "Rotation must be 0, 90, 180, or 270.")
        }
    }

    private fun startCamera() {
        mCameraView = findViewById(R.id.previewView)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture!!.addListener({
            try {
                val cameraProvider = cameraProviderFuture!!.get()
                bindPreview(cameraProvider)
            } catch (e: ExecutionException) {
                // This should never be reached.
            } catch (e: InterruptedException) {
            }
        }, ContextCompat.getMainExecutor(this))
    }


    @SuppressLint("UnsafeExperimentalUsageError", "ClickableViewAccessibility")
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
                .build()
        val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

        preview.setSurfaceProvider(mCameraView.surfaceProvider)


        val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(720, 1488))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer { image ->
            val rotationDegrees = degreesToFirebaseRotation(image.imageInfo.rotationDegrees)
            if (image.image == null) {
                return@Analyzer
            }
            val mediaImage = image.image
            val images = FirebaseVisionImage.fromMediaImage(mediaImage!!, rotationDegrees)
            val bmp = images.bitmap
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val height = bmp.height
            val width = bmp.width
            val left: Int
            val top: Int
            var diameter: Int
            diameter = width
            if (height < width) {
                diameter = height
            }
            val offset = (0.05 * diameter).toInt()
            diameter -= offset
            left = width / 2 - diameter / 3
            top = height / 2 - diameter / 6

            xOffset = left
            yOffset = top

            val bitmap = Bitmap.createBitmap(bmp, left, top, boxWidth, boxHeight)
            val detector = FirebaseVision.getInstance().cloudTextRecognizer


            detector.processImage(FirebaseVisionImage.fromBitmap(bitmap))
                    .addOnSuccessListener { firebaseVisionText ->

                        val text = firebaseVisionText.text
                        val regex = "[^0-9가-힣]".toRegex()
                        val diffText = regex.replace(text, "")

                        textView.text = diffText

                        image.close()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Error", e.toString())
                        image.close()
                    }
        })
        val camera = cameraProvider.bindToLifecycle((this as LifecycleOwner), cameraSelector, imageAnalysis, preview)

        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio: Float = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1F

                val delta = detector.scaleFactor

                camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        }

        val scaleGestureDetector = ScaleGestureDetector(this, listener)

        mCameraView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Start Camera
        startCamera()
        retrofit = Retrofit.Builder().baseUrl(getString(R.string.server_url)).addConverterFactory(GsonConverterFactory.create()).build()
        service = retrofit.create(AntiparkingService::class.java)
        checkButton = findViewById(R.id.check_button)
        addButton = findViewById(R.id.add_button)
        resultText = findViewById(R.id.result_text)
        surfaceView = findViewById(R.id.overlay)
        surfaceView?.setZOrderOnTop(true)

        textView = findViewById(R.id.text)
        checkTextView = findViewById(R.id.recognized_text)
        checkButton.setOnClickListener {
            if (checkTextView.text.toString() == "") {
                changeResultText(0, getString(R.string.no_input))
            } else {
                getOneCar(checkTextView.text.toString())
                changeResultText(1)
            }
        }
        addButton.setOnClickListener {
            if (checkTextView.text.toString() == "") {
                changeResultText(0, getString(R.string.no_input))
            } else {
                addCar(checkTextView.text.toString())
                changeResultText(1)
            }

        }
        textView.setOnClickListener { view ->
            val regex = "[^0-9가-힣]".toRegex()
            val diffText = regex.replace(view.text.text, "")
            checkTextView.text = diffText
        }

        holder = surfaceView?.holder
        holder?.setFormat(PixelFormat.TRANSPARENT)
        holder?.addCallback(this)
    }


    private fun drawFocusRect(color: Int) {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = mCameraView.height
        val width = mCameraView.width

        val left: Int
        val right: Int
        val top: Int
        val bottom: Int
        var diameter: Int
        diameter = width
        if (height < width) {
            diameter = height
        }
        val offset = (0.05 * diameter).toInt()
        diameter -= offset
        canvas = holder!!.lockCanvas()
        canvas?.drawColor(0, PorterDuff.Mode.CLEAR)
        paint = Paint()
        paint!!.style = Paint.Style.STROKE
        paint!!.color = color
        paint!!.strokeWidth = 5f
        left = width / 2 - diameter / 3
        top = height / 2 - diameter / 10
        right = width / 2 + diameter / 3
        bottom = height / 2 + diameter / 10
        xOffset = left
        yOffset = top
        boxHeight = bottom - top
        boxWidth = right - left
        canvas?.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint!!)
        holder!!.unlockCanvasAndPost(canvas)
    }

    private fun getOneCar(carPlate: String) {
        service.getOneCar(carPlate).enqueue(object : Callback<ResultCar> {
            override fun onResponse(call: Call<ResultCar>, response: Response<ResultCar>) {
                if (response.isSuccessful) {
                    Log.d("Retrofit getOneCar()", "${response.body()?.carPlate}")
                    response.body()?.let { changeResultText(4, it.carPlate) }
                } else {
                    if (response.body() == null) {
                        changeResultText(5, carPlate)
                    }
                }

            }

            override fun onFailure(call: Call<ResultCar>, t: Throwable) {
                Log.d("Retrofit onFailure", "getOneCar() onFailure")
                changeResultText(0, getString(R.string.connection_failure))
            }
        })
    }

    private fun addCar(carPlate: String) {
        service.addCar(AddCar(carPlate)).enqueue(object : Callback<AddCar> {
            override fun onResponse(call: Call<AddCar>, response: Response<AddCar>) {

                if (response.isSuccessful) {
                    Log.d("Retrofit addCar()", "${response.body()}")
                    response.body()?.let { changeResultText(2, it.carPlate) }
                } else {
                    response.errorBody()?.let {
                        val json = JSONObject(it.string())
                        Log.d("Retrofit addCar()Error", json.getString("message"))
                        changeResultText(3)
                    }
                }

            }

            override fun onFailure(call: Call<AddCar>, t: Throwable) {
                Log.d("Retrofit onFailure", "addCar() onFailure")
                changeResultText(0, getString(R.string.connection_failure))
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun changeResultText(input: Int, carPlate: String = "") {
        when (input) {
            1 -> {
                resultText.text = getString(R.string.connecting)
                resultText.setTextColor(Color.GRAY)
            }
            2 -> {
                resultText.text = "성공적으로 $carPlate 를 등록했습니다."
                resultText.setTextColor(Color.GREEN)
                shakeAnimation()
                fadeinAnimation()
            }
            3 -> {
                resultText.text = getString(R.string.add_failure)
                resultText.setTextColor(Color.RED)
                shakeAnimation()
            }
            4 -> {
                resultText.text = carPlate + getString(R.string.exist_car_plate)
                resultText.setTextColor(Color.GREEN)
                fadeinAnimation()
            }
            5 -> {
                resultText.text = carPlate + getString(R.string.no_car_plate)
                resultText.setTextColor(Color.RED)
                shakeAnimation()
            }
            else -> {
                resultText.text = carPlate
                resultText.setTextColor(Color.RED)
                shakeAnimation()
            }
        }
    }

    private fun shakeAnimation() {
        resultText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun fadeinAnimation(){
        resultText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fadein))
    }


    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawFocusRect(Color.parseColor("#b3dabb"))
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

}