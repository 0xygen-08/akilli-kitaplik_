package com.kitaplik.app.ui.library

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // Elle belge ekleme için sistem dosya seçicisi (PDF ve EPUB)
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

    // Android 10 ve altı için depolama izni başlatıcısı
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = selectedList.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${books.size} belge • ${bookLists.size} ayrı liste",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    // Seçili Listenin Kriterlerini / Adını Düzenle
                    IconButton(
                        onClick = { editingList = selectedList }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Liste Kriterlerini ve Adını Düzenle"
                        )
                    }

                    // Seçili Liste İçin Manuel Tarama Butonu
                    IconButton(
                        onClick = {
                            hasStoragePermission = checkStorageAccessPermission()
                            if (hasStoragePermission) {
                                viewModel.triggerManualScan()
                            } else {
                                requestStoragePermission(
                                    context = context,
                                    legacyLauncher = { perms -> legacyPermissionLauncher.launch(perms) }
                                )
                            }
                        },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Bu Listenin Kriterleriyle Şimdi Tara"
                            )
                        }
                    }

                    // Bu Listeye Özel Klasörde Kopyalamadan Toparla Butonu
                    IconButton(
                        onClick = { viewModel.gatherBooksInSingleFolderWithoutCopy() },
                        enabled = !isOrganizing && books.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = "Bu Listeyi Kendi Klasöründe Toparla"
                        )
                    }

                    // Karanlık / Aydınlık / Sistem Tema Seçimi
                    IconButton(onClick = { viewModel.toggleThemeMode() }) {
                        val icon = when (preferences.themeMode) {
                            ThemeMode.DARK -> Icons.Default.DarkMode
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Tema Değiştir (Karanlık / Aydınlık)"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    manualAddLauncher.launch(
                        arrayOf("application/pdf", "application/epub+zip")
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Elle Ekle"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Elle Ekle", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Listeler Arası Geçiş ve Yeni Liste Oluşturma Şeridi
            ListsSelectorBar(
                lists = bookLists,
                selectedListId = selectedList.id,
                listBookCounts = listBookCounts,
                onSelectList = { viewModel.selectList(it) },
                onEditSelectedList = { editingList = selectedList },
                onCreateNewList = { showCreateListDialog = true }
            )

            // 2. Üst Kontrol Şeridi: Haftalık Tarama + Bu Listeye Özel Klasörde Toparla
            ControlBar(
                activeListName = selectedList.name,
                weeklyScanEnabled = preferences.weeklyAutoScanEnabled,
                lastScanTimestamp = preferences.lastScanTimestamp,
                bookCount = books.size,
                isOrganizing = isOrganizing,
                onToggleWeeklyScan = { viewModel.setWeeklyAutoScanEnabled(it) },
                onGatherBooksClick = { viewModel.gatherBooksInSingleFolderWithoutCopy() }
            )

            // İzin Uyarısı (Gerekliyse)
            AnimatedVisibility(visible = !hasStoragePermission) {
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
                    onManualScanClick = {
                        hasStoragePermission = checkStorageAccessPermission()
                        if (hasStoragePermission) {
                            viewModel.triggerManualScan()
                        } else {
                            requestStoragePermission(
                                context = context,
                                legacyLauncher = { perms -> legacyPermissionLauncher.launch(perms) }
                            )
                        }
                    },
                    onEditCriteriaClick = { editingList = selectedList }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

    // Yeni Liste Oluşturma Diyaloğu
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

    // Mevcut Listeyi Düzenleme / Yeniden İsimlendirme Diyaloğu
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
private fun ListsSelectorBar(
    lists: List<BookListEntity>,
    selectedListId: Long,
    listBookCounts: Map<Long, Int>,
    onSelectList: (Long) -> Unit,
    onEditSelectedList: () -> Unit,
    onCreateNewList: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (list in lists) {
                val isSelected = list.id == selectedListId
                val count = listBookCounts[list.id] ?: 0
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectList(list.id) },
                    label = {
                        Text(
                            text = "${list.name} ($count)",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Listeyi İsimlendir veya Kriterlerini Düzenle",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onEditSelectedList() }
                            )
                        }
                    } else null
                )
            }

            AssistChip(
                onClick = onCreateNewList,
                label = {
                    Text(
                        text = "+ Yeni Liste",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = weeklyScanEnabled,
                    onClick = { onToggleWeeklyScan(!weeklyScanEnabled) },
                    label = {
                        Text(
                            text = if (weeklyScanEnabled) "Haftalık Tarama: Açık" else "Haftalık Tarama: Kapalı",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                if (bookCount > 0) {
                    AssistChip(
                        onClick = onGatherBooksClick,
                        enabled = !isOrganizing,
                        label = {
                            Text(
                                text = "'$activeListName' Klasöründe Toparla",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            if (lastScanTimestamp > 0L) {
                val formatted = remember(lastScanTimestamp) {
                    SimpleDateFormat("dd MMM yyyy HH:mm", Locale.forLanguageTag("tr-TR"))
                        .format(Date(lastScanTimestamp))
                }
                Text(
                    text = "Son tarama: $formatted • Aynı belge iki farklı listede bulunamaz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
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
        title = {
            Text(
                text = if (isEditing) "Listeyi ve Kriterleri Düzenle" else "Yeni Liste ve Tarama Kriterleri",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hızlı Şablon Butonları
                Text(
                    text = "Hızlı Şablon Doldur:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            if (name.isBlank()) name = "Ders Notlarım"
                            minPagesText = "1"
                            maxPagesText = ""
                            filenameWhitelist = "not, notu, notlar, vize, final, ders, ödev, hw, lab, sunum, slayt, çıkmış, quiz, özet"
                            filenameBlacklist = "fatura, dekont, makbuz"
                            firstPagesKeywords = "ders, hafta, soru, örnek, çözüm, vize, final, dönem"
                            useDefaultHeuristics = false
                            matchBoth = false
                        },
                        label = { Text("Ders Notları Şablonu") }
                    )
                    AssistChip(
                        onClick = {
                            if (name.isBlank()) name = "Kitaplar"
                            minPagesText = "45"
                            maxPagesText = ""
                            filenameWhitelist = ""
                            filenameBlacklist = "not, notu, notlar, vize, final, hw, ödev, rapor, fatura, dekont, lab, sunum, slayt, quiz"
                            firstPagesKeywords = "içindekiler, contents, yayınevi, yayınları, chapter, bölüm, isbn, önsöz"
                            useDefaultHeuristics = true
                            matchBoth = false
                        },
                        label = { Text("Kitaplar Şablonu") }
                    )
                }

                HorizontalDivider()

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
                        .clickable { matchBoth = !matchBoth }
                ) {
                    Checkbox(
                        checked = matchBoth,
                        onCheckedChange = { matchBoth = it }
                    )
                    Text(
                        text = "Hem belge ismi hem ilk sayfa kelimeleri aynı anda zorunlu olsun (işaretli değilse birinin eşleşmesi yeterlidir)",
                        style = MaterialTheme.typography.bodySmall
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
                    Text("İptal")
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
private fun BookListItem(
    book: BookEntity,
    onClick: () -> Unit,
    onSingleClickRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (book.format == BookFormat.PDF) {
                            Icons.AutoMirrored.Filled.MenuBook
                        } else {
                            Icons.Default.AutoStories
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = book.format.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                    Text(
                        text = subtitleParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentPageDisplay = (book.lastReadPage + 1).coerceAtMost(book.totalPages)
                    Text(
                        text = if (book.lastReadPage > 0) {
                            "Kaldığı sayfa: $currentPageDisplay / ${book.totalPages}"
                        } else {
                            "${book.totalPages} sayfa"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    if (book.lastReadPage > 0 && book.totalPages > 0) {
                        LinearProgressIndicator(
                            progress = { currentPageDisplay.toFloat() / book.totalPages.toFloat() },
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onSingleClickRemove
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tek Tıkla Listeden Kaldır",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    onGrantClick: () -> Unit,
    onRefreshPermissionState: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Dosya Erişimi İzni",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cihazdaki tüm klasörlerde bulunan PDF/EPUB belgelerini özel kriterlerinize göre tarayabilmek ve her listeyi kendi klasöründe toparlayabilmek için dosya erişim izni verin.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrantClick) {
                    Text("İzin Ver")
                }
                OutlinedButton(onClick = onRefreshPermissionState) {
                    Text("İzni Kontrol Et")
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
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            )
            Text(
                text = if (isScanning) {
                    "\"$listName\" İçin Cihaz Taranıyor..."
                } else {
                    "\"$listName\" Listesi Henüz Boş"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Bu listenin kendine özel kriterlerine göre cihazdaki tüm klasörleri tarayabilir veya kriterleri düzenleyebilirsiniz. Başka bir listede yer alan belgeler bu listeye tekrar eklenmez.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onManualScanClick,
                enabled = !isScanning
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("\"$listName\" İçin Dosyaları Tara")
            }
            OutlinedButton(
                onClick = onEditCriteriaClick
            ) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Liste Kriterlerini Düzenle")
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
    context: android.content.Context,
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
