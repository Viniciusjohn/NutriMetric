package br.com.nutrimetric.app.ui.screens

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerScreen(
    onBarcodeDetected: (String) -> Unit,
    onBack: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    if (!cameraPermissionState.status.isGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Precisamos de acesso à câmera para escanear o código de barras.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Permitir")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) {
                Text("Voltar")
            }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasDetected by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanear Código de Barras", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val executor = Executors.newSingleThreadExecutor()
                        val scanner = BarcodeScanning.getClient()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            if (hasDetected) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            processImageProxy(scanner, imageProxy) { barcode ->
                                if (!hasDetected) {
                                    hasDetected = true
                                    onBarcodeDetected(barcode)
                                }
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            // error
                        }

                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )
            
            // Frame overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(250.dp, 150.dp)
                        .background(Color.Transparent)
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            val cornerLength = 30.dp.toPx()
                            val cornerRadius = 8.dp.toPx()
                            
                            // Top left
                            moveTo(0f, cornerLength)
                            lineTo(0f, cornerRadius)
                            quadraticBezierTo(0f, 0f, cornerRadius, 0f)
                            lineTo(cornerLength, 0f)
                            
                            // Top right
                            moveTo(size.width - cornerLength, 0f)
                            lineTo(size.width - cornerRadius, 0f)
                            quadraticBezierTo(size.width, 0f, size.width, cornerRadius)
                            lineTo(size.width, cornerLength)
                            
                            // Bottom right
                            moveTo(size.width, size.height - cornerLength)
                            lineTo(size.width, size.height - cornerRadius)
                            quadraticBezierTo(size.width, size.height, size.width - cornerRadius, size.height)
                            lineTo(size.width - cornerLength, size.height)
                            
                            // Bottom left
                            moveTo(cornerLength, size.height)
                            lineTo(cornerRadius, size.height)
                            quadraticBezierTo(0f, size.height, 0f, size.height - cornerRadius)
                            lineTo(0f, size.height - cornerLength)
                        }
                        drawPath(
                            path = path,
                            color = Color.Green,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 4.dp.toPx()
                            )
                        )
                    }
                }
            }

            Text(
                text = "Aponte a câmera para o código de barras do produto",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onSuccess: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let {
                        onSuccess(it)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener {
                // error
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
