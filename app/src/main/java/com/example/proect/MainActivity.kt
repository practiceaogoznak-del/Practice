package com.example.proect

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.applyCanvas
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import android.widget.EditText
import android.text.InputType
import android.view.KeyEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.camera.core.Camera
import com.example.proect.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@androidx.camera.core.ExperimentalGetImage
class MainActivity : AppCompatActivity() {
    private lateinit var Spinner : Spinner
    private lateinit var binding: ActivityMainBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var workersMap: Map<String, String>
    private var currentWorkerId: String = ""
    private var currentWorkerFio: String = ""
    private lateinit var cameraExecutor: ExecutorService
    private var lastImageProxy: ImageProxy? = null
    private var camera: Camera? = null
    private var part3Digits: String = ""
    private var isScanning: Boolean = false
    private var lastScanTimestamp: Long = 0
    private var isResultShown = false
    private var isDialogShown = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private val scanRunnable = object : Runnable {
        override fun run() {
            autoScanRedRectangle()
            scanHandler.postDelayed(this, 500)
        }
    }
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()

    private val ciMap = mapOf(
        "титул" to "03",
        "книжка" to "05",
        "вн.лист" to "53"
    )


    private var selectedCiCode: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        databaseHelper = DatabaseHelper(this)
        workersMap = loadWorkersData()
        Spinner = findViewById(R.id.resultStatus)
        lockUI()
        showWorkerIdDialog()
        binding.scanButton.isEnabled = false
        binding.scanButton.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
        binding.resultCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        cameraExecutor = Executors.newSingleThreadExecutor()
        CoroutineScope(Dispatchers.Main).launch {
            val status = databaseHelper.testConnection()
            binding.dbStatus.text = status
        }
        val spinnerItems = listOf(
            "титул",
            "книжка",
            "вн.лист"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            spinnerItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        Spinner.adapter = adapter

        Spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                selectedCiCode = ciMap[selectedItem] ?: ""


                binding.statusText.text = if (selectedCiCode.isNotEmpty()) {
                    "Выбран: $selectedItem "
                } else {
                    "Ничего не выбрано"
                }


                if (part3Digits.length == 10) {
                    showNumberStatus(part3Digits)
                }
            }


            override fun onNothingSelected(parent: AdapterView<*>) {
                binding.statusText.text = "Ничего не выбрано"
                selectedCiCode = ""
            }
        }

        binding.scanButton.setOnClickListener {
            if (currentWorkerId.isEmpty() || part3Digits.length != 10) return@setOnClickListener

            CoroutineScope(Dispatchers.Main).launch {
                val (statusText, _, colorRes) = databaseHelper.checkNumberStatus(part3Digits, currentWorkerId, selectedCiCode)
                binding.scanButton.text = statusText
                binding.scanButton.setBackgroundColor(ContextCompat.getColor(this@MainActivity, colorRes))
            }
        }

        binding.resetButton.setOnClickListener {
            resetScanning()
        }
        binding.openBaza.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {

                val connectionStatus = databaseHelper.testConnection()

                val allRecords = databaseHelper.getAllRecords()

                val recordsText = if (allRecords.isEmpty()) {
                    "Таблица пуста"
                } else {
                    buildString {
                        append("Подключение: $connectionStatus\n\n")
                        allRecords.forEach { record ->
                            append("Серия: ${record.series1}${record.series2}, " +
                                    "Номер: ${record.number}, CI: ${record.ci}, Табельный номер: ${record.inputTabnom}, " +
                                    "Проверен Табельным номером: ${record.checkedTabnom ?: "-"}, Время проверки: ${record.checkedDatetime ?: "-"}, " +
                                    "Номер Дефекта: ${record.defectId}, Комментарий: ${record.comment ?: "-"}\n___________")
                        }
                    }
                }


                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Статус базы данных и все записи")
                    .setMessage(recordsText)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        binding.resultText.textSize = 35f
        binding.resultText.setTypeface(null, Typeface.BOLD)
        if (allPermissionsGranted()) {
            startCamera()
            scanHandler.postDelayed(scanRunnable, 500)
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun showNumberStatus(number: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {

                val (statusText, isScannable, colorRes) =
                    databaseHelper.checkNumberStatus(number, currentWorkerId, selectedCiCode)

                runOnUiThread {

                    binding.scanButton.text = statusText

                    binding.scanButton.backgroundTintList =
                        ContextCompat.getColorStateList(this@MainActivity, colorRes)

                    binding.scanButton.isEnabled = isScannable
                }

            } catch (e: Exception) {
                runOnUiThread {

                    binding.scanButton.text = "Ошибка проверки статуса"
                    binding.scanButton.backgroundTintList =
                        ContextCompat.getColorStateList(this@MainActivity, android.R.color.holo_red_light)
                    binding.scanButton.isEnabled = false
                }
            }
        }
    }




    private fun lockUI() {
        binding.previewView.isEnabled = false
        binding.scanButton.isEnabled = false
        binding.resetButton.isEnabled = false
        binding.openBaza.isEnabled = false
        binding.previewView.alpha = 0.7f
    }

    private fun unlockUI() {
        binding.previewView.isEnabled = true
        binding.scanButton.isEnabled = true
        binding.resetButton.isEnabled = true
        binding.openBaza.isEnabled = true
        binding.previewView.alpha = 1f
    }

    private fun loadWorkersData(): Map<String, String> {
        return try {
            assets.open("rabotniki.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .associate {
                        val parts = it.split(" - ")
                        parts[0].trim() to parts[1].trim()
                    }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing || isDestroyed) {
            return
        }
        if (!isDialogShown && binding.fioEditText.text.isNullOrEmpty()) {
            lockUI()
            showWorkerIdDialog()
        }
    }

    private fun resetUserState() {
        currentWorkerId = ""
        currentWorkerFio = ""
        binding.fioEditText.setText("")
        binding.fioEditText.isEnabled = true
        isDialogShown = false
    }

    private fun showWorkerIdDialog() {
        if (isDialogShown || isFinishing || isDestroyed) return
        isDialogShown = true
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Введите ваш номер работника"
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Идентификация")
            .setMessage("Введите ваш табельный номер")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Продолжить") { dialog, _ ->
                val workerId = input.text.toString().trim()
                if (validateWorkerId(workerId)) {
                    currentWorkerId = workerId
                    currentWorkerFio = workersMap[workerId] ?: ""
                    binding.fioEditText.setText(currentWorkerFio)
                    binding.fioEditText.isEnabled = false
                    dialog.dismiss()
                    unlockUI()
                    showWelcomeMessage(currentWorkerFio)
                } else {
                    input.error = "Номер не найден"
                    input.requestFocus()
                }
            }
            .setOnDismissListener {
                isDialogShown = false
            }
            .create()
        dialog.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK
        }
        dialog.setCanceledOnTouchOutside(false)
        if (!isFinishing && !isDestroyed) {
            dialog.show()
        }
    }

    private fun validateWorkerId(workerId: String): Boolean {
        return workerId.isNotEmpty() && workersMap.containsKey(workerId)
    }

    private fun showWelcomeMessage(fio: String) {
        Toast.makeText(this, "Здравствуйте, $fio", Toast.LENGTH_SHORT).show()
    }

    private fun autoScanRedRectangle() {
        if (isScanning || isResultShown) return
        isScanning = true
        lastScanTimestamp = System.currentTimeMillis()
        part3Digits = ""
        runOnUiThread {
            binding.scanRect.visibility = View.VISIBLE
        }
        imageAnalysis.clearAnalyzer()
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            synchronized(this) {
                if (isScanning && lastImageProxy == null) {
                    lastImageProxy = imageProxy
                    processFrame(imageProxy, true)
                    imageAnalysis.clearAnalyzer()
                } else {
                    imageProxy.close()
                }
            }
        }
    }

    private fun processFrame(imageProxy: ImageProxy, isAutoScan: Boolean = false) {
        cameraExecutor.execute {
            try {
                val bitmap = imageProxy.toBitmap()?.rotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    ?: run {
                        runOnUiThread {
                            isScanning = false
                            binding.scanRect.visibility = View.INVISIBLE
                        }
                        return@execute
                    }
                scanArea(bitmap, binding.scanRect) { digits ->
                    part3Digits = digits
                    runOnUiThread {
                        isScanning = false
                        if (part3Digits.length == 10) {
                            isResultShown = true
                            binding.resultText.text = formatNumber(part3Digits)
                            showNumberStatus(part3Digits)
                        } else {
                            binding.resultText.text = ""
                            binding.statusText.text = ""
                            binding.resultCard.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
                            updateScanButtonState(false)
                        }
                        binding.scanRect.visibility = View.INVISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isScanning = false
                    binding.resultText.text = ""
                    binding.statusText.text = ""
                    binding.resultCard.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
                    binding.scanRect.visibility = View.INVISIBLE
                    updateScanButtonState(false)
                }
            } finally {
                imageProxy.close()
                synchronized(this) { lastImageProxy = null }
            }
        }
    }

    private fun formatNumber(number: String): String {
        return if (number.length == 10) {
            "${number.substring(0, 2)} ${number.substring(2, 4)} ${number.substring(4)}"
        } else {
            "Неполный номер: $number"
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                synchronized(this) {
                    lastImageProxy?.close()
                    lastImageProxy = imageProxy
                }
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                val zoomRatio = 1.2f
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
                camera?.cameraControl?.setZoomRatio(zoomRatio)
                val meteringPoint = binding.previewView.meteringPointFactory.createPoint(0.5f, 0.5f)
                val focusMeteringAction = FocusMeteringAction.Builder(meteringPoint)
                    .disableAutoCancel()
                    .build()
                camera?.cameraControl?.startFocusAndMetering(focusMeteringAction)
            } catch (exc: Exception) {
                Log.e("Camera", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateScanButtonState(isEnabled: Boolean) {
        runOnUiThread {
            binding.scanButton.isEnabled = isEnabled
            binding.scanButton.backgroundTintList = ContextCompat.getColorStateList(
                this,
                if (isEnabled) android.R.color.holo_red_dark else android.R.color.darker_gray
            )
            binding.scanButton.isClickable = isEnabled
        }
    }

    private fun resetScanning() {
        isScanning = false
        isResultShown = false
        runOnUiThread {
            binding.resultText?.text = ""
            binding.statusText?.text = ""
            binding.scanButton.text = ""
            binding.scanButton.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
            binding.resultCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
            binding.scanRect.visibility = View.INVISIBLE
            updateScanButtonState(false)
        }
    }

    private fun scanArea(
        bitmap: Bitmap,
        rectView: View,
        callback: (String) -> Unit
    ) {
        try {
            val croppedBitmap = getCroppedBitmap(bitmap, rectView) ?: run {
                callback("")
                return
            }
            val processedBitmap = preprocessImage(croppedBitmap)
            saveDebugImage(processedBitmap)
            if (!isImageClear(processedBitmap)) {
                callback("")
                return
            }
            recognizeText(processedBitmap, callback)
        } catch (e: Exception) {
            Log.e("OCR", "Bitmap processing error", e)
            callback("")
        }
    }

    private fun getCroppedBitmap(bitmap: Bitmap, rectView: View): Bitmap? {
        val previewView = binding.previewView
        val rectLocation = IntArray(2).apply { rectView.getLocationOnScreen(this) }
        val previewLocation = IntArray(2).apply { previewView.getLocationOnScreen(this) }
        val relativeY = rectLocation[1] - previewLocation[1]
        if (relativeY < 0) return null
        val scaleX = bitmap.width.toFloat() / previewView.width.toFloat()
        val scaleY = bitmap.height.toFloat() / previewView.height.toFloat()
        val scaledX = (rectLocation[0] * scaleX).toInt().coerceAtLeast(0)
        val scaledY = (relativeY * scaleY).toInt().coerceAtLeast(0)
        val scaledWidth = (rectView.width * scaleX).toInt().coerceAtMost(bitmap.width - scaledX)
        val scaledHeight = (rectView.height * scaleY).toInt().coerceAtMost(bitmap.height - scaledY)
        return try {
            Bitmap.createBitmap(bitmap, scaledX, scaledY, scaledWidth, scaledHeight)
        } catch (e: Exception) {
            Log.e("OCR", "Bitmap cropping failed", e)
            null
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        return createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888).applyCanvas {
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        setSaturation(0f)
                        set(
                            floatArrayOf(
                                2.0f, 0f, 0f, 0f, -100f,
                                0f, 2.0f, 0f, 0f, -100f,
                                0f, 0f, 2.0f, 0f, -100f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    }
                )
            }
            drawBitmap(bitmap, 0f, 0f, paint)
        }
    }

    private fun isImageClear(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var sumGradient = 0f
        for (y in 1 until bitmap.height) {
            for (x in 1 until bitmap.width) {
                val idx = y * bitmap.width + x
                val left = pixels[idx - 1] and 0xFF
                val top = pixels[idx - bitmap.width] and 0xFF
                val current = pixels[idx] and 0xFF
                sumGradient += abs(current - left) + abs(current - top)
            }
        }
        val avgGradient = sumGradient / (bitmap.width * bitmap.height)
        return avgGradient > 0.0
    }

    private fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val numbers = buildString {
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                append(element.text.filter { it.isDigit() })
                            }
                        }
                    }
                }

                Log.d("OCR", "Все найденные числа: $numbers")
                callback(numbers)
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Ошибка OCR", e)
                callback("")
            }
    }



    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val plane = planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            ).apply {
                copyPixelsFromBuffer(buffer)
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Conversion error", e)
            null
        }
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        return if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        } else {
            this
        }
    }

    private fun saveDebugImage(bitmap: Bitmap) {
        try {
            val debugDir = File(getExternalFilesDir(null), "ocr_debug")
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }
            File(debugDir, "debug_${System.currentTimeMillis()}.jpg").outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            Log.e("OCR", "Error saving debug image", e)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        resetUserState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
                scanHandler.postDelayed(scanRunnable, 1000)
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDialogShown = false
        scanHandler.removeCallbacks(scanRunnable)
        synchronized(this) {
            lastImageProxy?.close()
            lastImageProxy = null
        }
        cameraExecutor.shutdown()
    }
}