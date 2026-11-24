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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitaplik.app.data.local.BookEntity
import com.kitaplik.app.data.local.BookFormat
import com.kitaplik.app.data.local.BookListEntity
import com.kitaplik.app.data.local.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val continueReadingBook = remember(books) {
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
            LibraryHeader(
                listName = selectedList.name,
                bookCount = books.size,
                isScanning = isScanning,
                themeMode = preferences.themeMode,
                onScanClick = runManualScan,
                onEditListClick = { editingList = selectedList },
                onToggleThemeClick = { viewModel.toggleThemeMode() }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    manualAddLauncher.launch(
                        arrayOf("application/pdf", "application/epub+zip")
                    )
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = "Elle Ekle",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
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
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (continueReadingBook != null) {
                        item(key = "continue_reading_${continueReadingBook.id}") {
                            ContinueReadingCard(
                                book = continueReadingBook,
                                onClick = { onBookClick(continueReadingBook) }
                            )
                        }
                    }

                    item(key = "shelf_label") {
                        Text(
                            text = "Tüm belgeler (${books.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
private fun LibraryHeader(
    listName: String,
    bookCount: Int,
    isScanning: Boolean,
    themeMode: ThemeMode,
    onScanClick: () -> Unit,
    onEditListClick: () -> Unit,
    onToggleThemeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$bookCount belge",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onScanClick,
                    enabled = !isScanning,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Bu Listenin Kriterleriyle Şimdi Tara",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = onEditListClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Liste Kriterlerini ve Adını Düzenle",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                val themeIcon = when (themeMode) {
                    ThemeMode.DARK -> Icons.Default.DarkMode
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                }
                IconButton(
                    onClick = onToggleThemeClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = themeIcon,
                        contentDescription = "Tema Değiştir (Karanlık / Aydınlık)",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (list in lists) {
            val isSelected = list.id == selectedListId
            val count = listBookCounts[list.id] ?: 0
            Surface(
                onClick = { onSelectList(list.id) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${list.name} · $count",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Listeyi İsimlendir veya Kriterlerini Düzenle",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onEditSelectedList() }
                        )
                    }
                }
            }
        }

        Surface(
            onClick = onCreateNewList,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Yeni Liste",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ControlBar(
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
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { onToggleWeeklyScan(!weeklyScanEnabled) },
                shape = RoundedCornerShape(10.dp),
                color = if (weeklyScanEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (weeklyScanEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (weeklyScanEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (weeklyScanEnabled) "Haftalık tarama: Açık" else "Haftalık tarama: Kapalı",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            if (bookCount > 0) {
                Surface(
                    onClick = onGatherBooksClick,
                    enabled = !isOrganizing,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isOrganizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Klasörde toparla",
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
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("tr-TR"))
                    .format(Date(lastScanTimestamp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Son tarama: $formatted",
                style = MaterialTheme.typography.bodySmall,
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
private fun ContinueReadingCard(
    book: BookEntity,
    onClick: () -> Unit
) {
    val currentPageDisplay = (book.lastReadPage + 1).coerceAtMost(book.totalPages.coerceAtLeast(1))
    val progress = if (book.totalPages > 0) {
        (currentPageDisplay.toFloat() / book.totalPages.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Okumaya devam et",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "%${book.progressPercent}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val meta = listOfNotNull(book.author, book.publisher).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sayfa $currentPageDisplay / ${book.totalPages}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Devam et",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
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
    val badgeBg = if (isPdf) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeInk = if (isPdf) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val currentPageDisplay = (book.lastReadPage + 1).coerceAtMost(book.totalPages.coerceAtLeast(1))
    val hasStarted = book.lastReadPage > 0 && book.totalPages > 0
    val progress = if (hasStarted) {
        (currentPageDisplay.toFloat() / book.totalPages.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeBg,
                contentColor = badgeInk,
                modifier = Modifier
                    .width(44.dp)
                    .height(56.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = book.format.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

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
                        style = MaterialTheme.typography.labelMedium,
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
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onSingleClickRemove,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tek Tıkla Listeden Kaldır",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Hızlı şablon doldur",
                    style = MaterialTheme.typography.labelLarge,
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
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { matchBoth = !matchBoth }
                        .padding(vertical = 4.dp)
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
                        modifier = Modifier
                            .align(Alignment.Start)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
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
                modifier = Modifier.heightIn(min = 48.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
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
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.heightIn(min = 44.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PermissionBanner(
    onGrantClick: () -> Unit,
    onRefreshPermissionState: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Dosya Erişimi İzni Gerekli",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Cihazdaki tüm klasörlerde bulunan PDF ve EPUB belgelerini özel kriterlerinize göre tarayabilmek için dosya erişim izni verin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("İzin Ver")
                }
                OutlinedButton(
                    onClick = onRefreshPermissionState,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)),
                    modifier = Modifier.heightIn(min = 48.dp)
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
            .padding(horizontal = 24.dp, vertical = 24.dp),
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
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = if (isScanning) {
                        "\"$listName\" için cihaz taranıyor..."
                    } else {
                        "\"$listName\" listesi henüz boş"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Bu listenin kriterlerine göre cihazdaki klasörleri tarayabilir veya kriterleri düzenleyebilirsiniz. Başka bir listedeki belgeler bu listeye tekrar eklenmez.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onManualScanClick,
                    enabled = !isScanning,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("\"$listName\" İçin Dosyaları Tara")
                }

                OutlinedButton(
                    onClick = onEditCriteriaClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
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
