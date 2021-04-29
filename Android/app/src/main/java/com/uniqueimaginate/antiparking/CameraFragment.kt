package com.uniqueimaginate.antiparking

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.fragment.app.Fragment
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.uniqueimaginate.antiparking.databinding.FragmentCameraBinding
import com.uniqueimaginate.antiparking.retrofit.AddCar
import com.uniqueimaginate.antiparking.retrofit.AntiParkingService
import com.uniqueimaginate.antiparking.retrofit.ResultCar
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors


class CameraFragment : Fragment(), SurfaceHolder.Callback {
    var canvas: Canvas? = null
    var boxWidth = 0
    var boxHeight = 0
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var holder: SurfaceHolder
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var retrofit: Retrofit
    private lateinit var service: AntiParkingService
    private lateinit var binding: FragmentCameraBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_camera,
            container,
            false
        )

        startCamera()
        retrofit = Retrofit.Builder().baseUrl(getString(R.string.server_url))
            .addConverterFactory(GsonConverterFactory.create()).build()
        service = retrofit.create(AntiParkingService::class.java)

        binding.apply {
            overlay.setZOrderOnTop(true)
            checkButton.setOnClickListener {
                if (binding.recognizedText.text.toString() == "") {
                    changeResultText(0, getString(R.string.no_input))
                } else {
                    getOneCar(binding.recognizedText.text.toString())
                    changeResultText(1)
                }
            }
            addButton.setOnClickListener {
                if (binding.recognizedText.text.toString() == "") {
                    changeResultText(0, getString(R.string.no_input))
                } else {
                    addCar(binding.recognizedText.text.toString())
                    changeResultText(1)
                }
            }
            text.setOnClickListener { view ->
                view as TextView
                val regex = "[^0-9가-힣]".toRegex()
                val diffText = regex.replace(view.text, "")
                binding.recognizedText.setText(diffText)
            }
            holder = overlay.holder
        }

        holder.setFormat(PixelFormat.TRANSPARENT)
        holder.addCallback(this)
        return binding.root
    }

    private fun degreesToFirebaseRotation(degrees: Int): Int {
        return when (degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw IllegalArgumentException(
                "Rotation must be 0, 90, 180, or 270."
            )
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this.requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            } catch (e: ExecutionException) {
                // This should never be reached.
            } catch (e: InterruptedException) {
            }
        }, ContextCompat.getMainExecutor(this.requireContext()))
    }

    @SuppressLint("UnsafeExperimentalUsageError", "ClickableViewAccessibility",
        "UnsafeOptInUsageError"
    )
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(binding.previewView.surfaceProvider)


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
            val height = bmp.height
            val width = bmp.width

            var diameter: Int = if (height < width) height else width
            val left: Int = width / 2 - diameter / 3
            val top: Int = height / 2 - diameter / 6
            val offset = (0.05 * diameter).toInt()
            diameter -= offset

            val bitmap = Bitmap.createBitmap(bmp, left, top, boxWidth, boxHeight)
            val detector = FirebaseVision.getInstance().cloudTextRecognizer


            detector.processImage(FirebaseVisionImage.fromBitmap(bitmap))
                .addOnSuccessListener { firebaseVisionText ->

                    val text = firebaseVisionText.text
                    val regex = "[^0-9가-힣]".toRegex()
                    val diffText = regex.replace(text, "")

                    binding.text.text = diffText

                    image.close()
                }
                .addOnFailureListener { e ->
                    Log.e("Error", e.toString())
                    image.close()
                }
        })
        val camera = cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector,
            imageAnalysis,
            preview
        )

        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio: Float = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1F
                val delta = detector.scaleFactor
                camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        }

        val scaleGestureDetector = ScaleGestureDetector(this.requireContext(), listener)

        binding.previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    private fun drawFocusRect() {
        val height = binding.previewView.height
        val width = binding.previewView.width
        var diameter: Int = if (height < width) height else width
        val offset = (0.05 * diameter).toInt()
        diameter -= offset
        canvas = holder.lockCanvas()
        canvas?.drawColor(0, PorterDuff.Mode.CLEAR)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                resources.getColor(R.color.green_box, null)
            } else{
                ContextCompat.getColor(requireContext(), R.color.green_box)
            }
        }

        val left: Int = width / 2 - diameter / 3
        val right: Int = height / 2 - diameter / 10
        val top: Int = width / 2 + diameter / 3
        val bottom: Int = height / 2 + diameter / 10

        boxHeight = bottom - top
        boxWidth = right - left
        canvas?.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        holder.unlockCanvasAndPost(canvas)
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
                binding.resultText.text = getString(R.string.connecting)
                binding.resultText.setTextColor(Color.GRAY)
            }
            2 -> {
                binding.resultText.text = "성공적으로 $carPlate 를 등록했습니다."
                binding.resultText.setTextColor(Color.GREEN)
                shakeAnimation()
                fadeInAnimation()
            }
            3 -> {
                binding.resultText.text = getString(R.string.add_failure)
                binding.resultText.setTextColor(Color.RED)
                shakeAnimation()
            }
            4 -> {
                binding.resultText.text = carPlate + getString(R.string.exist_car_plate)
                binding.resultText.setTextColor(Color.GREEN)
                fadeInAnimation()
            }
            5 -> {
                binding.resultText.text = carPlate + getString(R.string.no_car_plate)
                binding.resultText.setTextColor(Color.RED)
                shakeAnimation()
            }
            else -> {
                binding.resultText.text = carPlate
                binding.resultText.setTextColor(Color.RED)
                shakeAnimation()
            }
        }
    }

    private fun shakeAnimation() {
        binding.resultText.startAnimation(AnimationUtils.loadAnimation(this.requireContext(), R.anim.shake))
    }

    private fun fadeInAnimation() {
        binding.resultText.startAnimation(AnimationUtils.loadAnimation(this.requireContext(), R.anim.fadein))
    }




    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawFocusRect()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

}