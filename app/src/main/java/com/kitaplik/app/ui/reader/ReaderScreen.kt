package com.kitaplik.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
    }

    // Belge kapatıldığında açık olan sayfayı kesin olarak veritabanına kaydet
    DisposableEffect(bookId) {
        onDispose {
            viewModel.saveProgressOnClose()
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
                CircularProgressIndicator()
            }
        }

        is ReaderUiState.Error -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Okuyucu") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri"
                                )
                            }
                        }
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
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        is ReaderUiState.Ready -> {
            val pagerState = rememberPagerState(
                initialPage = state.initialPage,
                pageCount = { state.totalPages }
            )
            val coroutineScope = rememberCoroutineScope()

            // Sayfa değiştikçe kaldığı sayfayı otomatik hatırla
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
                // Hem yatay (sağa/sola) hem dikey (aşağı/yukarı) kaydırma desteği
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

                // Üst Bilgi ve Araç Çubuğu
                AnimatedVisibility(
                    visible = showControlsOverlay,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = state.book.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Sayfa ${currentPage + 1} / ${state.totalPages} • " +
                                        if (isVerticalScrollMode) "Dikey Kaydırma" else "Yatay Kaydırma",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.saveProgressOnClose()
                                    onNavigateBack()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kitaplığa Dön"
                                )
                            }
                        },
                        actions = {
                            // Yatay (Sağa/Sola) <-> Dikey (Aşağı/Yukarı) Sayfa Çevirme Modu Seçimi
                            IconButton(
                                onClick = { isVerticalScrollMode = !isVerticalScrollMode }
                            ) {
                                Icon(
                                    imageVector = if (isVerticalScrollMode) {
                                        Icons.Default.SwapVert
                                    } else {
                                        Icons.Default.SwapHoriz
                                    },
                                    contentDescription = "Kaydırma Yönünü Değiştir (Yatay / Dikey)"
                                )
                            }

                            if (state.book.format == BookFormat.PDF) {
                                IconButton(
                                    onClick = { invertPdfColorsForNight = !invertPdfColorsForNight }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Contrast,
                                        contentDescription = "PDF Gece Okuma Modu"
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        epubFontSizeSp = when {
                                            epubFontSizeSp < 18f -> 18f
                                            epubFontSizeSp < 22f -> 22f
                                            else -> 15f
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FormatSize,
                                        contentDescription = "Yazı Boyutunu Değiştir"
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                        )
                    )
                }

                // Alt Sayfa Geçiş Çubuğu (Önceki/Sonraki Butonları + Slider)
                AnimatedVisibility(
                    visible = showControlsOverlay && state.totalPages > 1,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sayfa ${currentPage + 1} / ${state.totalPages}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val percent = ((currentPage + 1) * 100) / state.totalPages
                                Text(
                                    text = "%$percent tamamlandı",
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
                                    enabled = currentPage > 0
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
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = goToNextPage,
                                    enabled = currentPage < state.totalPages - 1
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
            // 1. Tek dokunuş (kenarlardan sayfa çevirme, ortadan bar aç/kapa) ve Çift dokunuş (Zoom)
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
            // 2. İki parmakla yakınlaştırma (Pinch-to-Zoom) ve yalnızca yakınlaştırılmışken pan:
            // scale == 1f ve tek parmak varken olayları TÜKETMEZ, böylece Pager sayfaları rahatça çevirir.
            .pointerInput(pageIndex) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (pointerCount >= 2) {
                            // İki parmakla pinch-to-zoom
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
                            // Sayfa yakınlaştırılmış durumda: tek parmakla sayfa içinde gezin
                            val panChange = event.calculatePan()
                            val maxX = (containerSize.width * (scale - 1f)) / 2f
                            val maxY = (containerSize.height * (scale - 1f)) / 2f
                            val nextX = offsetX + panChange.x
                            val nextY = offsetY + panChange.y

                            val withinHorizontalBounds = nextX in -maxX..maxX || abs(panChange.y) > abs(panChange.x)
                            offsetX = nextX.coerceIn(-maxX, maxX)
                            offsetY = nextY.coerceIn(-maxY, maxY)

                            // Sadece yakınlaştırılmış sayfa sınırları içindeyken olayı tüket;
                            // kenara dayanıldığında Pager'ın sonraki sayfaya geçmesine izin ver.
                            if (withinHorizontalBounds && (abs(panChange.x) > 0.5f || abs(panChange.y) > 0.5f)) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                        // Not: pointerCount == 1 ve scale == 1f iken HİÇBİR olayı consume etmiyoruz!
                        // Böylece HorizontalPager ve VerticalPager kaydırma hareketini doğrudan alır.
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
            CircularProgressIndicator()
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

    Column(
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
            .padding(horizontal = 24.dp, vertical = 72.dp)
    ) {
        Text(
            text = epubPage.chapterTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = epubPage.content,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.55f).sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
