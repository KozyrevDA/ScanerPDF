package ru.aiscanner.docs.presentation.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import ru.aiscanner.docs.R
import ru.aiscanner.docs.presentation.common.EmptyState
import ru.aiscanner.docs.presentation.common.toMessage
import ru.aiscanner.docs.presentation.navigation.Routes
import java.io.File

@Composable
fun CameraScreen(navController: NavHostController, viewModel: CameraViewModel = koinViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        viewModel.effects.collect { effect ->
            when (effect) {
                is CameraUiEffect.OpenCrop -> navController.navigate(Routes.crop(effect.pageId))
                is CameraUiEffect.OpenDocument ->
                    navController.navigate(Routes.document(effect.documentId)) {
                        popUpTo(Routes.HOME)
                    }
            }
        }
    }

    if (!hasPermission) {
        PermissionExplanation(
            showSettingsButton = permissionDenied,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
        )
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(state.torchEnabled) {
        camera?.cameraControl?.enableTorch(state.torchEnabled)
    }

    state.error?.let { error ->
        val message = error.toMessage()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    } catch (e: Exception) {
                        viewModel.onCameraError()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = viewModel::toggleTorch) {
                Icon(
                    if (state.torchEnabled) Icons.Default.FlashOff else Icons.Default.FlashOn,
                    contentDescription = stringResource(
                        if (state.torchEnabled) R.string.camera_flash_off else R.string.camera_flash_on,
                    ),
                    tint = Color.White,
                )
            }
            if (state.pageCount > 0) {
                Badge { Text(stringResource(R.string.camera_pages_badge, state.pageCount)) }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = {
                    scope.launch {
                        val path = viewModel.prepareCaptureFilePath()
                        val options = ImageCapture.OutputFileOptions.Builder(File(path)).build()
                        imageCapture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    viewModel.onPhotoSaved(path)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    runCatching { File(path).delete() }
                                    viewModel.onCaptureError()
                                }
                            },
                        )
                    }
                },
                enabled = !state.isSaving,
                modifier = Modifier
                    .size(80.dp)
                    .semantics { contentDescription = "Сделать снимок" },
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                } else {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, Modifier.size(36.dp))
                }
            }
            if (state.pageCount > 0) {
                IconButton(onClick = viewModel::onOpenDocument, modifier = Modifier.padding(start = 24.dp)) {
                    Icon(
                        Icons.Default.Collections,
                        contentDescription = stringResource(R.string.camera_to_pages),
                        tint = Color.White,
                    )
                }
            }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PermissionExplanation(
    showSettingsButton: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            title = stringResource(R.string.camera_permission_title),
            subtitle = stringResource(R.string.camera_permission_message),
            modifier = Modifier.weight(1f, fill = false),
        )
        Button(onClick = onRequest) { Text(stringResource(R.string.camera_permission_grant)) }
        if (showSettingsButton) {
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.camera_permission_settings))
            }
        }
    }
}
