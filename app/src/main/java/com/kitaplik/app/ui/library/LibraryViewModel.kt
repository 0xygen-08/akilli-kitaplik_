package com.kitaplik.app.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitaplik.app.data.local.AppDatabase
import com.kitaplik.app.data.local.BookEntity
import com.kitaplik.app.data.local.BookListEntity
import com.kitaplik.app.data.local.ThemeMode
import com.kitaplik.app.data.local.UserPreferences
import com.kitaplik.app.data.local.UserPreferencesRepository
import com.kitaplik.app.domain.organizer.BookOrganizer
import com.kitaplik.app.domain.scanner.DocumentScanner
import com.kitaplik.app.domain.scanner.ManualAddResult
import com.kitaplik.app.worker.ScanScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiMessage(
    val text: String,
    val id: Long = System.currentTimeMillis()
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val bookDao = database.bookDao()
    private val preferencesRepository = UserPreferencesRepository(application)
    private val documentScanner = DocumentScanner(application, bookDao)
    private val bookOrganizer = BookOrganizer(bookDao)

    private val allBooksFlow = bookDao.observeAllBooks()

    val bookLists: StateFlow<List<BookListEntity>> = bookDao.observeAllLists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = listOf(BookListEntity.createDefaultBooksList())
        )

    private val _selectedListId = MutableStateFlow(BookListEntity.DEFAULT_BOOKS_LIST_ID)
    val selectedListId: StateFlow<Long> = _selectedListId.asStateFlow()

    val selectedList: StateFlow<BookListEntity> = combine(
        bookLists,
        _selectedListId
    ) { lists, activeId ->
        lists.find { it.id == activeId }
            ?: lists.firstOrNull()
            ?: BookListEntity.createDefaultBooksList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookListEntity.createDefaultBooksList()
    )

    /**
     * Seçili listeye ait belgeleri gecikmesiz olarak sunar; listeler birbirine karışmaz.
     */
    val books: StateFlow<List<BookEntity>> = combine(
        allBooksFlow,
        selectedList
    ) { allBooks, activeList ->
        allBooks.filter { it.listId == activeList.id }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /**
     * Her listede kaç belge bulunduğunu sekme başlıklarında göstermek için hesaplar.
     */
    val listBookCounts: StateFlow<Map<Long, Int>> = allBooksFlow.combine(bookLists) { allBooks, _ ->
        allBooks.groupingBy { it.listId }.eachCount()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isOrganizing = MutableStateFlow(false)
    val isOrganizing: StateFlow<Boolean> = _isOrganizing.asStateFlow()

    private val _uiMessage = MutableStateFlow<LibraryUiMessage?>(null)
    val uiMessage: StateFlow<LibraryUiMessage?> = _uiMessage.asStateFlow()

    fun selectList(listId: Long) {
        _selectedListId.value = listId
    }

    /**
     * Yeni bir özel liste oluşturur, o listeye geçiş yapar ve isteğe bağlı olarak
     * hemen o listenin kriterlerine göre cihazı tarar.
     */
    fun createList(
        name: String,
        minPages: Int,
        maxPages: Int,
        filenameWhitelist: String,
        filenameBlacklist: String,
        firstPagesKeywords: String,
        useDefaultBookHeuristics: Boolean,
        matchBothFilenameAndFirstPages: Boolean,
        scanImmediately: Boolean
    ) {
        viewModelScope.launch {
            val cleanName = name.trim().ifEmpty { "Yeni Liste" }
            val newList = BookListEntity(
                name = cleanName,
                minPages = minPages.coerceAtLeast(1),
                maxPages = maxPages.coerceAtLeast(0),
                filenameWhitelist = filenameWhitelist.trim(),
                filenameBlacklist = filenameBlacklist.trim(),
                firstPagesKeywords = firstPagesKeywords.trim(),
                useDefaultBookHeuristics = useDefaultBookHeuristics,
                matchBothFilenameAndFirstPages = matchBothFilenameAndFirstPages
            )
            val newId = bookDao.insertBookList(newList)
            if (newId > 0) {
                _selectedListId.value = newId
                if (scanImmediately) {
                    val created = newList.copy(id = newId)
                    scanForSpecificList(created)
                } else {
                    _uiMessage.value = LibraryUiMessage("\"$cleanName\" listesi oluşturuldu.")
                }
            }
        }
    }

    /**
     * Mevcut bir listenin adını ve tarama kriterlerini günceller.
     */
    fun updateList(
        updatedList: BookListEntity,
        scanImmediately: Boolean
    ) {
        viewModelScope.launch {
            val cleanList = updatedList.copy(
                name = updatedList.name.trim().ifEmpty { "Liste" },
                minPages = updatedList.minPages.coerceAtLeast(1),
                maxPages = updatedList.maxPages.coerceAtLeast(0)
            )
            bookDao.updateBookList(cleanList)
            if (scanImmediately) {
                scanForSpecificList(cleanList)
            } else {
                _uiMessage.value = LibraryUiMessage("\"${cleanList.name}\" listesi güncellendi.")
            }
        }
    }

    /**
     * Bir listeyi ve içindeki liste kayıtlarını siler (cihazdaki fiziksel dosyalar silinmez).
     */
    fun deleteList(list: BookListEntity) {
        viewModelScope.launch {
            val currentLists = bookLists.value
            if (currentLists.size <= 1) {
                _uiMessage.value = LibraryUiMessage("En az bir liste bulunmalıdır.")
                return@launch
            }
            bookDao.deleteBookList(list.id)
            val remaining = bookDao.getAllListsSnapshot()
            _selectedListId.value = remaining.firstOrNull()?.id ?: BookListEntity.DEFAULT_BOOKS_LIST_ID
            _uiMessage.value = LibraryUiMessage("\"${list.name}\" listesi silindi.")
        }
    }

    /**
     * Aktif (seçili) listenin kendi kriterlerine göre cihazdaki tüm dosyaları tarar.
     * Başka bir listede bulunan belgeler bu listeye kesinlikle eklenmez.
     */
    fun triggerManualScan() {
        val activeList = selectedList.value
        scanForSpecificList(activeList)
    }

    private fun scanForSpecificList(targetList: BookListEntity) {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val summary = documentScanner.scanDocumentsForList(targetList)
                preferencesRepository.updateLastScanTimestamp()
                val msg = if (summary.newlyAddedBooksCount > 0) {
                    "\"${summary.listName}\" listesine ${summary.newlyAddedBooksCount} yeni belge eklendi."
                } else {
                    "\"${summary.listName}\" için tarama tamamlandı. Yeni eşleşen belge bulunamadı (${summary.scannedFilesCount} dosya incelendi)."
                }
                _uiMessage.value = LibraryUiMessage(msg)
            } catch (e: Exception) {
                _uiMessage.value = LibraryUiMessage("Tarama sırasında hata: ${e.localizedMessage}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Filtrelerden kaçan alakasız belgeleri tek tıkla listeden kaldırır.
     */
    fun removeIrrelevantBookSingleClick(book: BookEntity) {
        viewModelScope.launch {
            bookDao.removeAndIgnoreBook(book)
            _uiMessage.value = LibraryUiMessage("\"${book.title}\" listeden kaldırıldı ve hariç tutuldu.")
        }
    }

    /**
     * Kullanıcının elle seçtiği belgeyi aktif listeye ekler.
     * Eğer belge zaten başka bir listede varsa "aynı belge iki farklı listede bulunamaz" uyarısı verir.
     */
    fun addBookManually(uri: Uri) {
        val activeList = selectedList.value
        viewModelScope.launch {
            when (val res = documentScanner.addBookManually(uri, activeList.id)) {
                is ManualAddResult.Added -> {
                    _uiMessage.value = LibraryUiMessage(
                        "\"${res.book.title}\" -> \"${activeList.name}\" listesine eklendi."
                    )
                }
                is ManualAddResult.AlreadyInCurrentList -> {
                    _uiMessage.value = LibraryUiMessage(
                        "\"${res.book.title}\" zaten \"${activeList.name}\" listesinde bulunuyor."
                    )
                }
                is ManualAddResult.AlreadyInAnotherList -> {
                    _uiMessage.value = LibraryUiMessage(
                        "Aynı belge iki listede bulunamaz: \"${res.book.title}\" zaten \"${res.otherListName}\" listesinde kayıtlı!"
                    )
                }
                ManualAddResult.Failed -> {
                    _uiMessage.value = LibraryUiMessage("Seçilen dosya eklenemedi.")
                }
            }
        }
    }

    /**
     * Aktif listedeki belgeleri kopyalamadan o listeye özel bir klasörde
     * (Documents/<Liste İsmi>) toparlar.
     */
    fun gatherBooksInSingleFolderWithoutCopy() {
        if (_isOrganizing.value) return
        val activeList = selectedList.value
        viewModelScope.launch {
            _isOrganizing.value = true
            try {
                val result = bookOrganizer.gatherListBooksWithoutCopying(activeList)
                val msg = when {
                    result.movedBooksCount > 0 ->
                        "\"${result.listName}\" listesindeki ${result.movedBooksCount} belge kopyalanmadan '${result.targetDirectoryPath}' klasöründe toparlandı."
                    result.alreadyInFolderCount > 0 ->
                        "\"${result.listName}\" listesindeki tüm belgeler (${result.alreadyInFolderCount}) zaten '${result.targetDirectoryPath}' klasöründe."
                    else ->
                        "Taşınacak belge bulunamadı."
                }
                _uiMessage.value = LibraryUiMessage(msg)
            } catch (e: Exception) {
                _uiMessage.value = LibraryUiMessage("Toparlama hatası: ${e.localizedMessage}")
            } finally {
                _isOrganizing.value = false
            }
        }
    }

    fun toggleThemeMode() {
        viewModelScope.launch {
            val nextMode = when (preferences.value.themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.SYSTEM
            }
            preferencesRepository.setThemeMode(nextMode)
        }
    }

    fun setWeeklyAutoScanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWeeklyAutoScan(enabled)
            val appContext = getApplication<Application>().applicationContext
            if (enabled) {
                ScanScheduler.scheduleWeeklyScan(appContext)
                _uiMessage.value = LibraryUiMessage("Haftalık arka plan taraması etkinleştirildi.")
            } else {
                ScanScheduler.cancelWeeklyScan(appContext)
                _uiMessage.value = LibraryUiMessage("Haftalık arka plan taraması kapatıldı (yalnızca manuel tarama).")
            }
        }
    }

    fun clearMessage() {
        _uiMessage.value = null
    }
}
