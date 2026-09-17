package com.cargenome.app.ui.common

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.cargenome.app.R
import com.cargenome.app.data.attachment.AttachmentManager
import com.cargenome.app.data.ocr.ReceiptOcrScanner
import com.cargenome.app.data.ocr.ReceiptScanResult
import kotlinx.coroutines.launch

private class RobustTakePictureContract : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            clipData = ClipData.newUri(context.contentResolver, "receipt_photo", input)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val resolved = context.packageManager.queryIntentActivities(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            context.grantUriPermission(
                pkg,
                input,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}

@Composable
fun ReceiptAttachmentSection(
    attachmentUris: List<String>,
    onAddAttachment: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    attachmentManager: AttachmentManager,
    ocrScanner: ReceiptOcrScanner,
    modifier: Modifier = Modifier,
    onApplyOcr: ((ReceiptScanResult) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var detectedResult by remember { mutableStateOf<ReceiptScanResult?>(null) }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        RobustTakePictureContract(),
    ) { success ->
        if (success) {
            tempCameraUri?.let { uri ->
                val uriStr = uri.toString()
                onAddAttachment(uriStr)
                isScanning = true
                coroutineScope.launch {
                    val res = ocrScanner.scanReceipt(uri)
                    isScanning = false
                    if (res.hasData) {
                        detectedResult = res
                    }
                }
            }
        } else {
            attachmentManager.cleanTempCameraFile(tempCameraUri)
            tempCameraUri = null
        }
    }

    fun launchCameraInternal() {
        runCatching {
            val uri = attachmentManager.createTempCameraUri()
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            launchCameraInternal()
        }
    }

    fun onTakePhotoClicked() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchCameraInternal()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val uriStr = uri.toString()
            onAddAttachment(uriStr)
            isScanning = true
            coroutineScope.launch {
                val res = ocrScanner.scanReceipt(uri)
                isScanning = false
                if (res.hasData) {
                    detectedResult = res
                }
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.receipt_section_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onTakePhotoClicked() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.receipt_take_photo))
                }

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.receipt_pick_gallery))
                }
            }

            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.receipt_ocr_processing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            AnimatedVisibility(visible = detectedResult != null && onApplyOcr != null) {
                val res = detectedResult ?: return@AnimatedVisibility
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "🧾 " + stringResource(R.string.receipt_ocr_detected),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (res.totalCost != null) {
                            Text(
                                text = "• Сумма: ${res.totalCost} ₽",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (res.volumeLitres != null) {
                            Text(
                                text = "• Объём: ${res.volumeLitres} л",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (res.station != null) {
                            Text(
                                text = "• АЗС / Сервис: ${res.station}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (res.date != null) {
                            Text(
                                text = "• Дата: ${res.date}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { detectedResult = null }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                onApplyOcr?.invoke(res)
                                detectedResult = null
                            }) {
                                Text(stringResource(R.string.receipt_ocr_apply))
                            }
                        }
                    }
                }
            }

            if (attachmentUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(attachmentUris, key = { it }) { uriStr ->
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { fullScreenImageUri = uriStr },
                        ) {
                            AsyncImage(
                                model = uriStr,
                                contentDescription = "Чек",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )

                            IconButton(
                                onClick = { onRemoveAttachment(uriStr) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                            ) {
                                Text(
                                    text = "✕",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (fullScreenImageUri != null) {
        Dialog(onDismissRequest = { fullScreenImageUri = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { fullScreenImageUri = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = fullScreenImageUri,
                    contentDescription = "Чек полноэкранный",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
        }
    }
}

