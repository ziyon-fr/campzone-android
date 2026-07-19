package fr.ziyon.campzone.ui.checkin

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzSpacing
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * CameraX preview wired to an ML Kit QR analyzer. Emits the raw string of the
 * first QR code found in a frame via [onQrScanned]; the caller debounces and
 * acts on the value. Camera binding follows the current lifecycle and is torn
 * down with the composable.
 */
@Composable
fun QrCameraPreview(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCameraError: (Throwable) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnScan by rememberUpdatedState(onQrScanned)
    val latestOnCameraError by rememberUpdatedState(onCameraError)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>() }
    val disposed = remember { AtomicBoolean(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    var cameraError by remember { mutableStateOf(false) }
    var restartKey by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        disposed.set(false)
        onDispose {
            disposed.set(true)
            cameraProviderRef.get()?.unbindAll()
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    if (cameraError) {
        CameraUnavailablePrompt(
            modifier = modifier,
            onRetry = {
                cameraProviderRef.get()?.unbindAll()
                cameraError = false
                restartKey += 1
            },
        )
    } else {
        key(restartKey) {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    val previewView = PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        runCatching {
                            val cameraProvider = providerFuture.get()
                            cameraProviderRef.set(cameraProvider)
                            if (disposed.get()) {
                                cameraProvider.unbindAll()
                                return@addListener
                            }
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(
                                        analysisExecutor,
                                        QrCodeAnalyzer(scanner) { value -> latestOnScan(value) },
                                    )
                                }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }.onFailure { error ->
                            Log.e("QrCameraPreview", "Unable to start camera preview", error)
                            latestOnCameraError(error)
                            cameraError = true
                        }
                    }, ContextCompat.getMainExecutor(context))
                    previewView
                },
            )
        }
    }
}

@Composable
private fun CameraUnavailablePrompt(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier.padding(CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(42.dp),
        )
        Text(
            text = stringResource(R.string.checkin_camera_unavailable_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = CzSpacing.md),
        )
        Text(
            text = stringResource(R.string.checkin_camera_unavailable_message),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = CzSpacing.sm),
        )
        CzButton(
            text = stringResource(R.string.common_retry),
            onClick = onRetry,
            variant = CzButtonVariant.Secondary,
            modifier = Modifier.padding(top = CzSpacing.md),
        )
    }
}

private class QrCodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onQrScanned: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onQrScanned)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
