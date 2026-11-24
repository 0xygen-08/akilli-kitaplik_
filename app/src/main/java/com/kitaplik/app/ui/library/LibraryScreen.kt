package com.kitaplik.app.ui.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitaplik.app.data.local.BookEntity
import com.kitaplik.app.data.local.BookFormat
import com.kitaplik.app.data.local.BookListEntity
import com.kitaplik.app.data.local.ThemeMode
import com.kitaplik.app.ui.theme.MonoMetaStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PressEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

private fun Modifier.tactileClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.985f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(durationMillis = 90, easing = PressEasing),
        label = "tactileScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (BookEntity) -> Unit
) {
    val bookLists by viewModel.bookLists.collectAsStateWithLifecycle()
    val selectedList by viewModel.selectedList.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val listBookCounts by viewModel.listBookCounts.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isOrganizing by viewModel.isOrganizing.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasStoragePermission by remember {
        mutableStateOf(checkStorageAccessPermission())
    }

    var showCreateListDialog by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<BookListEntity?>(null) }

    val manualAddLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addBookManually(uri)
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasStoragePermission = checkStorageAccessPermission()
        if (hasStoragePermission) {
            viewModel.triggerManualScan()
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg.text)
            viewModel.clearMessage()
        }
    }

    val featuredBook = remember(books) {
        books.filter { it.lastReadPage > 0 }
            .maxByOrNull { it.lastReadTimestamp }
    }

    val runManualScan: () -> Unit = {
        hasStoragePermission = checkStorageAccessPermission()
        if (hasStoragePermission) {
            viewModel.triggerManualScan()
        } else {
            requestStoragePermission(
                context = context,
                legacyLauncher = { perms -> legacyPermissionLauncher.launch(perms) }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            EditorialTopMasthead(
                listName = selectedList.name,
                bookCount = books.size,
                totalListsCount = bookLists.size,
                weeklyAutoScanEnabled = preferences.weeklyAutoScanEnabled,
                isScanning = isScanning,
                themeMode = preferences.themeMode,
                onEditListClick = { editingList = selectedList },
                onScanClick = runManualScan,
                onToggleThemeClick = { viewModel.toggleThemeMode() }
            )
        },
        floatingActionButton = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .tactileClickable {
                        manualAddLauncher.launch(
                            arrayOf("application/pdf", "application/epub+zip")
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Elle Ekle",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Elle Ekle",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ListsSelectorBar(
                lists = bookLists,
                selectedListId = selectedList.id,
                listBookCounts = listBookCounts,
                onSelectList = { viewModel.selectList(it) },
                onEditSelectedList = { editingList = selectedList },
                onCreateNewList = { showCreateListDialog = true }
            )

            ControlBar(
                activeListName = selectedList.name,
                weeklyScanEnabled = preferences.weeklyAutoScanEnabled,
                lastScanTimestamp = preferences.lastScanTimestamp,
                bookCount = books.size,
                isOrganizing = isOrganizing,
                onToggleWeeklyScan = { viewModel.setWeeklyAutoScanEnabled(it) },
                onGatherBooksClick = { viewModel.gatherBooksInSingleFolderWithoutCopy() }
            )

            AnimatedVisibility(
                visible = !hasStoragePermission,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                PermissionBanner(
                    onGrantClick = {
                        requestStoragePermission(
                            context = context,
                            legacyLauncher = { perms -> legacyPermissionLauncher.launch(perms) }
                        )
                    },
                    onRefreshPermissionState = {
                        hasStoragePermission = checkStorageAccessPermission()
                    }
                )
            }

            if (books.isEmpty()) {
                EmptyLibraryView(
                    listName = selectedList.name,
                    isScanning = isScanning,
                    onManualScanClick = runManualScan,
                    onEditCriteriaClick = { editingList = selectedList }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (featuredBook != null) {
                        item(key = "featured_continue_${featuredBook.id}") {
                            FeaturedContinueCard(
                                book = featuredBook,
                                onClick = { onBookClick(featuredBook) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    item(key = "shelf_section_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RAF · ${books.size} BELGE",
                                style = MonoMetaStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = selectedList.name.uppercase(Locale.forLanguageTag("tr-TR")),
                                style = MonoMetaStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    items(
                        items = books,
                        key = { it.id }
                    ) { book ->
                        BookListItem(
                            book = book,
                            onClick = { onBookClick(book) },
                            onSingleClickRemove = { viewModel.removeIrrelevantBookSingleClick(book) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateListDialog) {
        ListConfigDialog(
            initialList = null,
            canDelete = false,
            onDismiss = { showCreateListDialog = false },
            onSave = { name, minPages, maxPages, whitelist, blacklist, firstPages, useDefaultHeuristics, matchBoth, scanNow ->
                showCreateListDialog = false
                viewModel.createList(
                    name = name,
                    minPages = minPages,
                    maxPages = maxPages,
                    filenameWhitelist = whitelist,
                    filenameBlacklist = blacklist,
                    firstPagesKeywords = firstPages,
                    useDefaultBookHeuristics = useDefaultHeuristics,
                    matchBothFilenameAndFirstPages = matchBoth,
                    scanImmediately = scanNow
                )
            },
            onDelete = {}
        )
    }

    editingList?.let { target ->
        ListConfigDialog(
            initialList = target,
            canDelete = bookLists.size > 1,
            onDismiss = { editingList = null },
            onSave = { name, minPages, maxPages, whitelist, blacklist, firstPages, useDefaultHeuristics, matchBoth, scanNow ->
                editingList = null
                viewModel.updateList(
                    updatedList = target.copy(
                        name = name,
                        minPages = minPages,
                        maxPages = maxPages,
                        filenameWhitelist = whitelist,
                        filenameBlacklist = blacklist,
                        firstPagesKeywords = firstPages,
                        useDefaultBookHeuristics = useDefaultHeuristics,
                        matchBothFilenameAndFirstPages = matchBoth
                    ),
                    scanImmediately = scanNow
                )
            },
            onDelete = {
                editingList = null
                viewModel.deleteList(target)
            }
        )
    }
}

@Composable
private fun EditorialTopMasthead(
    listName: String,
    bookCount: Int,
    totalListsCount: Int,
    weeklyAutoScanEnabled: Boolean,
    isScanning: Boolean,
    themeMode: ThemeMode,
    onEditListClick: () -> Unit,
    onScanClick: () -> Unit,
    onToggleThemeClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (weeklyAutoScanEnabled) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }
                        Text(
                            text = "$bookCount belge · $totalListsCount liste",
                            style = MonoMetaStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MastheadIconButton(
                        onClick = onScanClick,
                        enabled = !isScanning,
                        contentDescription = "Bu Listenin Kriterleriyle Şimdi Tara"
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Bu Listenin Kriterleriyle Şimdi Tara",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    MastheadIconButton(
                        onClick = onEditListClick,
                        contentDescription = "Liste Kriterlerini ve Adını Düzenle",
                        icon = Icons.Default.Tune
                    )

                    val themeIcon = when (themeMode) {
                        ThemeMode.DARK -> Icons.Default.DarkMode
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                    }
                    MastheadIconButton(
                        onClick = onToggleThemeClick,
                        contentDescription = "Tema Değiştir (Karanlık / Aydınlık)",
                        icon = themeIcon
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        }
    }
}

@Composable
private fun MastheadIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    content: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .tactileClickable(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun ListsSelectorBar(
    lists: List<BookListEntity>,
    selectedListId: Long,
    listBookCounts: Map<Long, Int>,
    onSelectList: (Long) -> Unit,
    onEditSelectedList: () -> Unit,
    onCreateNewList: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (list in lists) {
            val isSelected = list.id == selectedListId
            val count = listBookCounts[list.id] ?: 0
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.surface
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val borderColor = if (isSelected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

            Surface(
                shape = RoundedCornerShape(9.dp),
                color = containerColor,
                contentColor = contentColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .tactileClickable { onSelectList(list.id) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = list.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = "$count",
                        style = MonoMetaStyle,
                        color = if (isSelected) {
                            contentColor.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable { onEditSelectedList() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Listeyi İsimlendir veya Kriterlerini Düzenle",
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .tactileClickable { onCreateNewList() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Yeni Liste",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ControlBar(
    activeListName: String,
    weeklyScanEnabled: Boolean,
    lastScanTimestamp: Long,
    bookCount: Int,
    isOrganizing: Boolean,
    onToggleWeeklyScan: (Boolean) -> Unit,
    onGatherBooksClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (weeklyScanEnabled) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (weeklyScanEnabled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (weeklyScanEnabled) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .tactileClickable { onToggleWeeklyScan(!weeklyScanEnabled) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (weeklyScanEnabled) "Haftalık Tarama: Açık" else "Haftalık Tarama: Kapalı",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (bookCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .tactileClickable(enabled = !isOrganizing, onClick = onGatherBooksClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isOrganizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = "'$activeListName' Klasöründe Toparla",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (lastScanTimestamp > 0L) {
            val formatted = remember(lastScanTimestamp) {
                SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("tr-TR"))
                    .format(Date(lastScanTimestamp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Son tarama: $formatted · Belgeler listeler arası çakışmaz",
                style = MonoMetaStyle.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

@Composable
private fun FeaturedContinueCard(
    book: BookEntity,
    onClick: () -> Unit
) {
    val currentPageDisplay = (book.lastReadPage + 1).coerceAtMost(book.totalPages.coerceAtLeast(1))
    val progress = if (book.totalPages > 0) {
        (currentPageDisplay.toFloat() / book.totalPages.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .tactileClickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OKUMAYA DEVAM ET",
                    style = MonoMetaStyle,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = "%${book.progressPercent} · ${book.format.name}",
                    style = MonoMetaStyle,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val meta = listOfNotNull(book.author, book.publisher).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.16f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sayfa $currentPageDisplay / ${book.totalPages}",
                    style = MonoMetaStyle,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Kaldığın Yerden Aç",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookListItem(
    book: BookEntity,
    onClick: () -> Unit,
    onSingleClickRemove: () -> Unit
) {
    val isPdf = book.format == BookFormat.PDF
    val coverBg = if (isPdf) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val coverInk = if (isPdf) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val spineColor = if (isPdf) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    val currentPageDisplay = (book.lastReadPage + 1).coerceAtMost(book.totalPages.coerceAtLeast(1))
    val hasStarted = book.lastReadPage > 0 && book.totalPages > 0
    val progress = if (hasStarted) {
        (currentPageDisplay.toFloat() / book.totalPages.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tactileClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cilt sırtlı minyatür kitap kapağı
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(coverBg)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(spineColor)
                        .align(Alignment.CenterStart)
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isPdf) {
                            Icons.AutoMirrored.Filled.MenuBook
                        } else {
                            Icons.Default.AutoStories
                        },
                        contentDescription = null,
                        tint = coverInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = book.format.name,
                        style = MonoMetaStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = coverInk
                    )
                }
                if (hasStarted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.5.dp)
                            .background(spineColor)
                            .align(Alignment.BottomStart)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitleParts = buildList {
                    book.author?.let { add(it) }
                    book.publisher?.let { add(it) }
                    if (isEmpty() && book.detectionReason.isNotBlank()) {
                        add(book.detectionReason)
                    }
                }
                if (subtitleParts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (hasStarted) {
                            "Sayfa $currentPageDisplay / ${book.totalPages}"
                        } else {
                            "${book.totalPages} sayfa"
                        },
                        style = MonoMetaStyle,
                        color = if (hasStarted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (hasStarted) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onSingleClickRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tek Tıkla Listeden Kaldır",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ListConfigDialog(
    initialList: BookListEntity?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        minPages: Int,
        maxPages: Int,
        whitelist: String,
        blacklist: String,
        firstPages: String,
        useDefaultHeuristics: Boolean,
        matchBoth: Boolean,
        scanNow: Boolean
    ) -> Unit,
    onDelete: () -> Unit
) {
    val isEditing = initialList != null

    var name by remember { mutableStateOf(initialList?.name ?: "") }
    var minPagesText by remember { mutableStateOf((initialList?.minPages ?: 1).toString()) }
    var maxPagesText by remember {
        mutableStateOf(
            if ((initialList?.maxPages ?: 0) > 0) initialList!!.maxPages.toString() else ""
        )
    }
    var filenameWhitelist by remember { mutableStateOf(initialList?.filenameWhitelist ?: "") }
    var filenameBlacklist by remember { mutableStateOf(initialList?.filenameBlacklist ?: "") }
    var firstPagesKeywords by remember { mutableStateOf(initialList?.firstPagesKeywords ?: "") }
    var useDefaultHeuristics by remember {
        mutableStateOf(initialList?.useDefaultBookHeuristics ?: false)
    }
    var matchBoth by remember {
        mutableStateOf(initialList?.matchBothFilenameAndFirstPages ?: false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = if (isEditing) "Listeyi ve Kriterleri Düzenle" else "Yeni Liste ve Tarama Kriterleri",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "HIZLI ŞABLON DOLDUR",
                    style = MonoMetaStyle,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TemplateChip(
                        label = "Ders Notları Şablonu",
                        onClick = {
                            if (name.isBlank()) name = "Ders Notlarım"
                            minPagesText = "1"
                            maxPagesText = ""
                            filenameWhitelist = "not, notu, notlar, vize, final, ders, ödev, hw, lab, sunum, slayt, çıkmış, quiz, özet"
                            filenameBlacklist = "fatura, dekont, makbuz"
                            firstPagesKeywords = "ders, hafta, soru, örnek, çözüm, vize, final, dönem"
                            useDefaultHeuristics = false
                            matchBoth = false
                        }
                    )
                    TemplateChip(
                        label = "Kitaplar Şablonu",
                        onClick = {
                            if (name.isBlank()) name = "Kitaplar"
                            minPagesText = "45"
                            maxPagesText = ""
                            filenameWhitelist = ""
                            filenameBlacklist = "not, notu, notlar, vize, final, hw, ödev, rapor, fatura, dekont, lab, sunum, slayt, quiz"
                            firstPagesKeywords = "içindekiler, contents, yayınevi, yayınları, chapter, bölüm, isbn, önsöz"
                            useDefaultHeuristics = true
                            matchBoth = false
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Liste İsmi (Klasör adı olarak da kullanılır)") },
                    placeholder = { Text("Örn: Ders Notlarım") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minPagesText,
                        onValueChange = { minPagesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Min. Sayfa") },
                        placeholder = { Text("1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxPagesText,
                        onValueChange = { maxPagesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Maks. Sayfa (Boş=Sınırsız)") },
                        placeholder = { Text("Sınırsız") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = filenameWhitelist,
                    onValueChange = { filenameWhitelist = it },
                    label = { Text("Belgenin İsminde Aranan Kelimeler (Virgülle)") },
                    placeholder = { Text("Örn: not, vize, final, ders, hw, lab, sunum") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = firstPagesKeywords,
                    onValueChange = { firstPagesKeywords = it },
                    label = { Text("İlk Sayfalarda Aranan Kelimeler (Virgülle)") },
                    placeholder = { Text("Örn: içindekiler, bölüm, hafta, ders, soru") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = filenameBlacklist,
                    onValueChange = { filenameBlacklist = it },
                    label = { Text("Kelime Kara Listesi - Dışlananlar (Virgülle)") },
                    placeholder = { Text("Örn: fatura, dekont, rapor") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { matchBoth = !matchBoth }
                ) {
                    Checkbox(
                        checked = matchBoth,
                        onCheckedChange = { matchBoth = it }
                    )
                    Text(
                        text = "Hem belge ismi hem ilk sayfa kelimeleri aynı anda zorunlu olsun (işaretli değilse birinin eşleşmesi yeterlidir)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Bu Listeyi Sil",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                onClick = {
                    val minP = minPagesText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val maxP = maxPagesText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    onSave(
                        name.ifBlank { "Yeni Liste" },
                        minP,
                        maxP,
                        filenameWhitelist,
                        filenameBlacklist,
                        firstPagesKeywords,
                        useDefaultHeuristics,
                        matchBoth,
                        true
                    )
                }
            ) {
                Text("Kaydet ve Şimdi Tara")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = {
                        val minP = minPagesText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val maxP = maxPagesText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        onSave(
                            name.ifBlank { "Yeni Liste" },
                            minP,
                            maxP,
                            filenameWhitelist,
                            filenameBlacklist,
                            firstPagesKeywords,
                            useDefaultHeuristics,
                            matchBoth,
                            false
                        )
                    }
                ) {
                    Text("Sadece Kaydet")
                }
            }
        }
    )
}

@Composable
private fun TemplateChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .tactileClickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun PermissionBanner(
    onGrantClick: () -> Unit,
    onRefreshPermissionState: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Dosya Erişimi İzni Gerekli",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cihazdaki tüm klasörlerde bulunan PDF/EPUB belgelerini özel kriterlerinize göre tarayabilmek ve her listeyi kendi klasöründe toparlayabilmek için dosya erişim izni verin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("İzin Ver")
                }
                OutlinedButton(
                    onClick = onRefreshPermissionState,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "İzni Kontrol Et",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    listName: String,
    isScanning: Boolean,
    onManualScanClick: () -> Unit,
    onEditCriteriaClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = if (isScanning) {
                        "\"$listName\" İçin Cihaz Taranıyor..."
                    } else {
                        "\"$listName\" Rafı Henüz Boş"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Bu listenin kendine özel kriterlerine göre cihazdaki tüm klasörleri tarayabilir veya kriterleri düzenleyebilirsiniz. Başka bir listede yer alan belgeler bu listeye tekrar eklenmez.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onManualScanClick,
                    enabled = !isScanning,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("\"$listName\" İçin Dosyaları Tara")
                }

                OutlinedButton(
                    onClick = onEditCriteriaClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Liste Kriterlerini Düzenle", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun checkStorageAccessPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

private fun requestStoragePermission(
    context: Context,
    legacyLauncher: (Array<String>) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }.onFailure {
            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(fallback)
        }
    } else {
        legacyLauncher(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }
}
