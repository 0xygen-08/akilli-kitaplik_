package com.kitaplik.app.ui.reader

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitaplik.app.data.local.BookFormat
import com.kitaplik.app.domain.reader.EpubPage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()

    var showControlsOverlay by remember { mutableStateOf(true) }
    var invertPdfColorsForNight by remember { mutableStateOf(false) }
    var isVerticalScrollMode by remember { mutableStateOf(false) }
    var epubFontSizeSp by remember { mutableFloatStateOf(18f) }

    val context = LocalContext.current
    val activity = context as? Activity

    // Okuyucu ekranında alt gezinme çubuğunu her zaman gizli tut;
    // kontroller gizlendiğinde durum çubuğunu da gizleyerek tam ekran okuma deneyimi sun.
    LaunchedEffect(showControlsOverlay, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        if (showControlsOverlay) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
    }

    // Belge kapatıldığında açık olan sayfayı kesin olarak veritabanına kaydet ve durum çubuğunu geri getir
    DisposableEffect(bookId, activity) {
        onDispose {
            viewModel.saveProgressOnClose()
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    when (val state = uiState) {
        is ReaderUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is ReaderUiState.Error -> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Okuyucu",
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }

        is ReaderUiState.Ready -> {
            val pagerState = rememberPagerState(
                initialPage = state.initialPage,
                pageCount = { state.totalPages }
            )
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        viewModel.onPageChanged(page)
                    }
            }

            val goToPreviousPage: () -> Unit = {
                if (pagerState.currentPage > 0) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }
            }

            val goToNextPage: () -> Unit = {
                if (pagerState.currentPage < state.totalPages - 1) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ReaderPagerContent(
                    pagerState = pagerState,
                    isVerticalScrollMode = isVerticalScrollMode,
                    format = state.book.format,
                    epubPages = state.epubPages,
                    invertPdfColorsForNight = invertPdfColorsForNight,
                    epubFontSizeSp = epubFontSizeSp,
                    renderPage = { viewModel.renderPdfPage(it) },
                    onTapCenter = { showControlsOverlay = !showControlsOverlay },
                    onTapLeftEdge = goToPreviousPage,
                    onTapRightEdge = goToNextPage
                )

                // Kontroller gizliyken sayfanın en altında ince ilerleme çizgisi
                if (!showControlsOverlay && state.totalPages > 1) {
                    val progress = ((currentPage + 1).toFloat() / state.totalPages.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            .align(Alignment.BottomStart)
                    )
                }

                // Üst Sakin Araç Çubuğu
                AnimatedVisibility(
                    visible = showControlsOverlay,
                    enter = slideInVertically(tween(180)) { -it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(140)) { -it } + fadeOut(tween(140)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ReaderOverlayButton(
                                    onClick = {
                                        viewModel.saveProgressOnClose()
                                        onNavigateBack()
                                    },
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kitaplığa Dön"
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.book.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Sayfa ${currentPage + 1} / ${state.totalPages} · " +
                                            if (isVerticalScrollMode) "Dikey" else "Yatay",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ReaderOverlayButton(
                                        onClick = { isVerticalScrollMode = !isVerticalScrollMode },
                                        icon = if (isVerticalScrollMode) {
                                            Icons.Default.SwapVert
                                        } else {
                                            Icons.Default.SwapHoriz
                                        },
                                        contentDescription = "Kaydırma Yönünü Değiştir (Yatay / Dikey)",
                                        isActive = isVerticalScrollMode
                                    )

                                    if (state.book.format == BookFormat.PDF) {
                                        ReaderOverlayButton(
                                            onClick = { invertPdfColorsForNight = !invertPdfColorsForNight },
                                            icon = Icons.Default.Contrast,
                                            contentDescription = "PDF Gece Okuma Modu",
                                            isActive = invertPdfColorsForNight
                                        )
                                    } else {
                                        ReaderOverlayButton(
                                            onClick = {
                                                epubFontSizeSp = when {
                                                    epubFontSizeSp < 18f -> 18f
                                                    epubFontSizeSp < 22f -> 22f
                                                    else -> 15f
                                                }
                                            },
                                            icon = Icons.Default.FormatSize,
                                            contentDescription = "Yazı Boyutunu Değiştir",
                                            isActive = epubFontSizeSp != 18f
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )
                        }
                    }
                }

                // Yüzen Alt Kontrol Paneli (Önceki/Sonraki + Slider)
                AnimatedVisibility(
                    visible = showControlsOverlay && state.totalPages > 1,
                    enter = slideInVertically(tween(180)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(140)) { it } + fadeOut(tween(140)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sayfa ${currentPage + 1} / ${state.totalPages}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val percent = ((currentPage + 1) * 100) / state.totalPages
                                Text(
                                    text = "%$percent",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = goToPreviousPage,
                                    enabled = currentPage > 0,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Önceki Sayfa"
                                    )
                                }

                                Slider(
                                    value = currentPage.toFloat(),
                                    onValueChange = { target ->
                                        val pageIdx = target.toInt().coerceIn(0, state.totalPages - 1)
                                        coroutineScope.launch {
                                            pagerState.scrollToPage(pageIdx)
                                        }
                                    },
                                    valueRange = 0f..(state.totalPages - 1).coerceAtLeast(1).toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = goToNextPage,
                                    enabled = currentPage < state.totalPages - 1,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Sonraki Sayfa"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderOverlayButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.background
        },
        contentColor = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderPagerContent(
    pagerState: PagerState,
    isVerticalScrollMode: Boolean,
    format: BookFormat,
    epubPages: List<EpubPage>,
    invertPdfColorsForNight: Boolean,
    epubFontSizeSp: Float,
    renderPage: suspend (Int) -> Bitmap?,
    onTapCenter: () -> Unit,
    onTapLeftEdge: () -> Unit,
    onTapRightEdge: () -> Unit
) {
    val pageContent: @Composable (Int) -> Unit = { pageIndex ->
        when (format) {
            BookFormat.PDF -> {
                PdfPageView(
                    pageIndex = pageIndex,
                    invertColors = invertPdfColorsForNight,
                    renderPage = renderPage,
                    onTapCenter = onTapCenter,
                    onTapLeftEdge = onTapLeftEdge,
                    onTapRightEdge = onTapRightEdge
                )
            }

            BookFormat.EPUB -> {
                val epubPage = epubPages.getOrNull(pageIndex)
                if (epubPage != null) {
                    EpubPageView(
                        epubPage = epubPage,
                        fontSizeSp = epubFontSizeSp,
                        onTapCenter = onTapCenter,
                        onTapLeftEdge = onTapLeftEdge,
                        onTapRightEdge = onTapRightEdge
                    )
                }
            }
        }
    }

    if (isVerticalScrollMode) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            pageContent(pageIndex)
        }
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            pageContent(pageIndex)
        }
    }
}

/**
 * Tek parmakla kaydırma (HorizontalPager / VerticalPager sayfa çevirme) jestlerini
 * ENGELLEMEYEN, çift dokunuşla ve iki parmakla yakınlaştırma (pinch-to-zoom) +
 * sol/sağ kenara dokunarak sayfa çevirme destekli PDF sayfa görünümü.
 */
@Composable
private fun PdfPageView(
    pageIndex: Int,
    invertColors: Boolean,
    renderPage: suspend (Int) -> Bitmap?,
    onTapCenter: () -> Unit,
    onTapLeftEdge: () -> Unit,
    onTapRightEdge: () -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = renderPage(pageIndex)
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }

    val invertColorFilter = remember(invertColors) {
        if (invertColors) {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.2f
                        }
                    },
                    onTap = { tapOffset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        when {
                            scale <= 1.05f && tapOffset.x < width * 0.22f -> onTapLeftEdge()
                            scale <= 1.05f && tapOffset.x > width * 0.78f -> onTapRightEdge()
                            else -> onTapCenter()
                        }
                    }
                )
            }
            .pointerInput(pageIndex) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                            scale = newScale
                            if (newScale > 1.02f) {
                                val maxX = (containerSize.width * (newScale - 1f)) / 2f
                                val maxY = (containerSize.height * (newScale - 1f)) / 2f
                                offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } else if (pointerCount == 1 && scale > 1.02f) {
                            val panChange = event.calculatePan()
                            val maxX = (containerSize.width * (scale - 1f)) / 2f
                            val maxY = (containerSize.height * (scale - 1f)) / 2f
                            val nextX = offsetX + panChange.x
                            val nextY = offsetY + panChange.y

                            val withinHorizontalBounds = nextX in -maxX..maxX || abs(panChange.y) > abs(panChange.x)
                            offsetX = nextX.coerceIn(-maxX, maxX)
                            offsetY = nextY.coerceIn(-maxY, maxY)

                            if (withinHorizontalBounds && (abs(panChange.x) > 0.5f || abs(panChange.y) > 0.5f)) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Sayfa ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                colorFilter = invertColorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EpubPageView(
    epubPage: EpubPage,
    fontSizeSp: Float,
    onTapCenter: () -> Unit,
    onTapLeftEdge: () -> Unit,
    onTapRightEdge: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(epubPage.pageIndex) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        when {
                            tapOffset.x < width * 0.22f -> onTapLeftEdge()
                            tapOffset.x > width * 0.78f -> onTapRightEdge()
                            else -> onTapCenter()
                        }
                    }
                )
            }
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 84.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Text(
                text = epubPage.chapterTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(1.5.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = epubPage.content,
                fontFamily = FontFamily.Serif,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.7f).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
