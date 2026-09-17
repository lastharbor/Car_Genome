package com.cargenome.app.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import com.cargenome.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fullscreen interactive PDF viewer with smooth pinch-to-zoom (scaling),
 * panning, double-tap zoom, page navigation, and sharing.
 * Designed for presenting official e-OSAGO / CASCO policy documents to traffic police or inspectors.
 */
@Composable
fun PdfViewerDialog(
    pdfUriString: String,
    onDismiss: () -> Unit,
    title: String = "Страховой полис (PDF)",
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Transformation / Scaling state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Initialize renderer
    DisposableEffect(pdfUriString) {
        val uri = pdfUriString.toUri()
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                fileDescriptor = pfd
                val pdfRenderer = PdfRenderer(pfd)
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
                currentPageIndex = 0
            } else {
                loadError = "Не удалось открыть файл"
            }
        } catch (e: Exception) {
            loadError = e.localizedMessage ?: "Ошибка чтения PDF"
        }

        onDispose {
            try {
                renderer?.close()
                fileDescriptor?.close()
                currentBitmap?.recycle()
                currentBitmap = null
            } catch (_: Exception) {}
        }
    }

    // Render page when currentPageIndex or renderer changes
    LaunchedEffect(renderer, currentPageIndex) {
        val currentRenderer = renderer ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect

        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val page = currentRenderer.openPage(currentPageIndex)
                val bitmap = try {
                    // 2.5x density factor for ultra-sharp text when zooming
                    val renderScale = 2.5f
                    val width = (page.width * renderScale).toInt().coerceAtLeast(1)
                    val height = (page.height * renderScale).toInt().coerceAtLeast(1)

                    val bmp = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    page.close()
                }

                withContext(Dispatchers.Main) {
                    val old = currentBitmap
                    currentBitmap = bitmap
                    old?.recycle()
                    isLoading = false
                    // Reset zoom when switching pages
                    scale = 1f
                    offset = Offset.Zero
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    loadError = "Ошибка отрисовки страницы: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F11)),
        ) {
            // Document Viewport with gestures
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale > 1f) {
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y,
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    offset = Offset.Zero
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "Страница ${currentPageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }

                if (loadError != null) {
                    Text(
                        text = loadError ?: "Ошибка загрузки",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            // Top Header Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                color = Color.Black.copy(alpha = 0.75f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "Страница ${currentPageIndex + 1} из $pageCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Open external app / Share
                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(pdfUriString.toUri(), "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(sendIntent) }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share),
                                tint = Color.White,
                            )
                        }

                        // Close button
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            // Bottom Navigation and Zoom Controls
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Page navigation
                    if (pageCount > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (currentPageIndex > 0) currentPageIndex--
                                },
                                enabled = currentPageIndex > 0,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = if (currentPageIndex > 0) Color.White else Color.Gray,
                                )
                            }

                            Text(
                                text = "${currentPageIndex + 1} / $pageCount",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )

                            IconButton(
                                onClick = {
                                    if (currentPageIndex < pageCount - 1) currentPageIndex++
                                },
                                enabled = currentPageIndex < pageCount - 1,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = if (currentPageIndex < pageCount - 1) Color.White else Color.Gray,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Масштабирование щипком или 2× тапом",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }

                    // Zoom Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = {
                                scale = (scale - 0.5f).coerceAtLeast(1f)
                                if (scale == 1f) offset = Offset.Zero
                            },
                            enabled = scale > 1f,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (scale > 1f) Color.White else Color.Gray,
                            )
                        }

                        TextButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                        ) {
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        IconButton(
                            onClick = {
                                scale = (scale + 0.5f).coerceAtMost(6f)
                            },
                            enabled = scale < 6f,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = if (scale < 6f) Color.White else Color.Gray,
                            )
                        }
                    }
                }
            }
        }
    }
}
