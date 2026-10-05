package com.privacy.whatsappdecryptor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacy.whatsappdecryptor.core.database.AndroidWhatsAppDatabaseReader
import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import com.privacy.whatsappdecryptor.core.export.JsonChatExporter
import com.privacy.whatsappdecryptor.core.export.TxtChatExporter
import android.content.Context
import com.privacy.whatsappdecryptor.core.inventory.InventoryCsvWriter
import com.privacy.whatsappdecryptor.core.inventory.InventoryWindow
import com.privacy.whatsappdecryptor.core.inventory.AndroidInventorySql
import com.privacy.whatsappdecryptor.core.inventory.StreamingInventoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.privacy.whatsappdecryptor.core.inventory.ProjectInventorySummary
import com.privacy.whatsappdecryptor.core.inventory.ProjectRegistry
import com.privacy.whatsappdecryptor.core.inventory.ManagedListing
import com.privacy.whatsappdecryptor.core.inventory.ImportantDealerRow
import com.privacy.whatsappdecryptor.core.inventory.CustomProjectRepository

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    TXT("Plain Text (.txt)", "txt", "text/plain"),
    JSON("Structured JSON (.json)", "json", "application/json")
}

sealed class InventoryExportState {
    object Idle : InventoryExportState()
    data class Processing(val processedMessages: Int, val detail: String = "Preparing export…") : InventoryExportState()
    data class Complete(val file: File, val totalRows: Int, val targetInCount: Int) : InventoryExportState()
    data class Error(val message: String) : InventoryExportState()
}

sealed class DatabaseState {
    object Closed : DatabaseState()
    object Loading : DatabaseState()
    data class Ready(val file: File) : DatabaseState()
    data class Error(val message: String) : DatabaseState()
}

class ChatViewModel : ViewModel() {

    private var databaseSource: AndroidWhatsAppDatabaseReader? = null
    private var inventoryJob: Job? = null

    private val _dbState = MutableStateFlow<DatabaseState>(DatabaseState.Closed)
    val dbState: StateFlow<DatabaseState> = _dbState.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlyGroups = MutableStateFlow(false)
    val onlyGroups: StateFlow<Boolean> = _onlyGroups.asStateFlow()

    private val _selectedChat = MutableStateFlow<ChatSummary?>(null)
    val selectedChat: StateFlow<ChatSummary?> = _selectedChat.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoadingMessages = MutableStateFlow(false)
    val isLoadingMessages: StateFlow<Boolean> = _isLoadingMessages.asStateFlow()

    private val _selectedChatIdsForExport = MutableStateFlow<Set<Long>>(emptySet())
    val selectedChatIdsForExport: StateFlow<Set<Long>> = _selectedChatIdsForExport.asStateFlow()

    private val _inventoryExportState = MutableStateFlow<InventoryExportState>(InventoryExportState.Idle)
    val inventoryExportState: StateFlow<InventoryExportState> = _inventoryExportState.asStateFlow()

    private val _projectSummaries = MutableStateFlow<List<ProjectInventorySummary>>(emptyList())
    val projectSummaries: StateFlow<List<ProjectInventorySummary>> = _projectSummaries.asStateFlow()

    private val _projectSearchQuery = MutableStateFlow("")
    val projectSearchQuery: StateFlow<String> = _projectSearchQuery.asStateFlow()

    private val _projectStatusFilter = MutableStateFlow("ALL")
    val projectStatusFilter: StateFlow<String> = _projectStatusFilter.asStateFlow()

    private val _isProjectScanning = MutableStateFlow(false)
    val isProjectScanning: StateFlow<Boolean> = _isProjectScanning.asStateFlow()

    private val _projectScanProgress = MutableStateFlow("")
    val projectScanProgress: StateFlow<String> = _projectScanProgress.asStateFlow()

    private val _projectMonths = MutableStateFlow(1L)
    val projectMonths: StateFlow<Long> = _projectMonths.asStateFlow()

    private val _inListings = MutableStateFlow<List<ManagedListing>>(emptyList())
    val inListings: StateFlow<List<ManagedListing>> = _inListings.asStateFlow()

    private val _inProjectSummaries = MutableStateFlow<List<ProjectInventorySummary>>(
        ProjectRegistry.getAllTargetProjectNames().map {
            ProjectInventorySummary(
                society = it,
                status = "IN",
                totalListings = 0,
                uniqueDealers = 0,
                latestTimestamp = 0L
            )
        }
    )
    val inProjectSummaries: StateFlow<List<ProjectInventorySummary>> = _inProjectSummaries.asStateFlow()

    private val _inListingsSearchQuery = MutableStateFlow("")
    val inListingsSearchQuery: StateFlow<String> = _inListingsSearchQuery.asStateFlow()

    private val _inListingsSocietyFilter = MutableStateFlow<String?>(null)
    val inListingsSocietyFilter: StateFlow<String?> = _inListingsSocietyFilter.asStateFlow()

    private val _isLoadingInListings = MutableStateFlow(false)
    val isLoadingInListings: StateFlow<Boolean> = _isLoadingInListings.asStateFlow()

    private val _outProjectSummaries = MutableStateFlow<List<ProjectInventorySummary>>(emptyList())
    val outProjectSummaries: StateFlow<List<ProjectInventorySummary>> = _outProjectSummaries.asStateFlow()

    private val _outListingsSearchQuery = MutableStateFlow("")
    val outListingsSearchQuery: StateFlow<String> = _outListingsSearchQuery.asStateFlow()

    private val _isLoadingOutListings = MutableStateFlow(false)
    val isLoadingOutListings: StateFlow<Boolean> = _isLoadingOutListings.asStateFlow()

    fun loadDatabase(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _dbState.value = DatabaseState.Loading
            try {
                databaseSource?.close()
                val reader = AndroidWhatsAppDatabaseReader.open(file)
                databaseSource = reader
                _dbState.value = DatabaseState.Ready(file)
                refreshChats()
                loadCachedProjectSummaries(file)
            } catch (e: Exception) {
                _dbState.value = DatabaseState.Error(e.localizedMessage ?: "Failed to open SQLite database")
            }
        }
    }

    private fun loadCachedProjectSummaries(databaseFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            CustomProjectRepository.loadAndSync(databaseFile.parentFile)
            val listingsDb = activeListingsDb(databaseFile.parentFile, _projectMonths.value)
            val cacheDb = File(databaseFile.parentFile, "inventory_cache/parsed-text.db")
            if (listingsDb.exists() && cacheDb.exists()) {
                runCatching {
                    AndroidInventorySql(listingsDb).use { work ->
                        AndroidInventorySql(cacheDb).use { cache ->
                            StreamingInventoryStore(work, cache).use { store ->
                                _projectSummaries.value = store.getProjectSummaries()
                                _inProjectSummaries.value = store.getCuratedInProjectSummaries()
                                _outProjectSummaries.value = store.getOutProjectSummaries()
                                _inListings.value = store.getInListings(
                                    searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                    societyFilter = _inListingsSocietyFilter.value
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onProjectSearchQueryChanged(query: String) {
        _projectSearchQuery.value = query
    }

    fun onProjectStatusFilterChanged(filter: String) {
        _projectStatusFilter.value = filter
    }

    fun onProjectMonthsChanged(months: Long, context: Context? = null) {
        if (_projectMonths.value == months) return
        _projectMonths.value = months
        context?.let { ctx ->
            val databaseFile = (_dbState.value as? DatabaseState.Ready)?.file
            val listingsDb = databaseFile?.let { activeListingsDb(it.parentFile, months) }
            if (listingsDb?.exists() == true) {
                // If this timeframe was already scanned and cached, load it immediately into UI
                viewModelScope.launch(Dispatchers.IO) {
                    val cacheDb = File(databaseFile.parentFile, "inventory_cache/parsed-text.db")
                    runCatching {
                        AndroidInventorySql(listingsDb).use { work ->
                            AndroidInventorySql(cacheDb).use { cache ->
                                StreamingInventoryStore(work, cache).use { store ->
                                    _projectSummaries.value = store.getProjectSummaries()
                                    _inProjectSummaries.value = store.getCuratedInProjectSummaries()
                                    _outProjectSummaries.value = store.getOutProjectSummaries()
                                    _inListings.value = store.getInListings(
                                        searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                        societyFilter = _inListingsSocietyFilter.value
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Not yet scanned: do NOT auto-scan, clear list so user can tap 'Scan Projects' when ready
                _projectSummaries.value = emptyList()
                _inProjectSummaries.value = ProjectRegistry.getAllTargetProjectNames().map {
                    ProjectInventorySummary(it, "IN", 0, 0, 0L)
                }
                _outProjectSummaries.value = emptyList()
                _inListings.value = emptyList()
            }
        }
    }

    fun onInListingsSearchQueryChanged(query: String, context: Context? = null) {
        _inListingsSearchQuery.value = query
        context?.let { loadInListings(it) }
    }

    fun onInListingsSocietyFilterChanged(society: String?, context: Context? = null) {
        _inListingsSocietyFilter.value = society
        context?.let { loadInListings(it) }
    }

    fun onOutListingsSearchQueryChanged(query: String) {
        _outListingsSearchQuery.value = query
    }

    fun loadInListings(context: Context, months: Long = _projectMonths.value) {
        val appContext = context.applicationContext
        val databaseFile = (_dbState.value as? DatabaseState.Ready)?.file
        val listingsDb = databaseFile?.let { activeListingsDb(it.parentFile, months) }
        if (listingsDb == null || !listingsDb.exists()) {
            if (databaseFile != null && databaseFile.exists() && inventoryJob?.isActive != true) {
                scanProjectInventory(appContext, months, forceRefresh = false)
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingInListings.value = true
            try {
                CustomProjectRepository.loadAndSync(appContext)
                val cacheDb = File(databaseFile.parentFile, "inventory_cache/parsed-text.db")
                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(cacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            val currentIn = store.getInListings(
                                searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                societyFilter = _inListingsSocietyFilter.value
                            )
                            _inListings.value = currentIn
                            _inProjectSummaries.value = store.getCuratedInProjectSummaries()
                            _outProjectSummaries.value = store.getOutProjectSummaries()
                            _projectSummaries.value = store.getProjectSummaries()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load IN listings", e)
            } finally {
                _isLoadingInListings.value = false
            }
        }
    }

    fun loadOutListings(context: Context, months: Long = _projectMonths.value) {
        val appContext = context.applicationContext
        val databaseFile = (_dbState.value as? DatabaseState.Ready)?.file
        val listingsDb = databaseFile?.let { activeListingsDb(it.parentFile, months) }
        if (listingsDb == null || !listingsDb.exists()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingOutListings.value = true
            try {
                CustomProjectRepository.loadAndSync(appContext)
                val cacheDb = File(databaseFile.parentFile, "inventory_cache/parsed-text.db")
                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(cacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            _outProjectSummaries.value = store.getOutProjectSummaries()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load OUT listings", e)
            } finally {
                _isLoadingOutListings.value = false
            }
        }
    }

    fun promoteOutSocietyToIn(society: String, context: Context, onComplete: ((Int) -> Unit)? = null) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val clean = society.trim().uppercase()
            CustomProjectRepository.addCustomProject(appContext, clean)
            val months = _projectMonths.value
            val databaseFile = (_dbState.value as? DatabaseState.Ready)?.file
            val listingsDb = databaseFile?.let { activeListingsDb(it.parentFile, months) }
            val cacheDb = databaseFile?.let { File(it.parentFile, "inventory_cache/parsed-text.db") }
            var count = 0
            if (listingsDb?.exists() == true && cacheDb?.exists() == true) {
                runCatching {
                    AndroidInventorySql(listingsDb).use { work ->
                        AndroidInventorySql(cacheDb).use { cache ->
                            StreamingInventoryStore(work, cache).use { store ->
                                count = store.promoteSocietyToIn(clean)
                                _inProjectSummaries.value = store.getCuratedInProjectSummaries()
                                _outProjectSummaries.value = store.getOutProjectSummaries()
                                _projectSummaries.value = store.getProjectSummaries()
                                _inListings.value = store.getInListings(
                                    searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                    societyFilter = _inListingsSocietyFilter.value
                                )
                            }
                        }
                    }
                }
            } else {
                _inProjectSummaries.value = ProjectRegistry.getAllTargetProjectNames().map {
                    ProjectInventorySummary(society = it, status = "IN", totalListings = 0, uniqueDealers = 0, latestTimestamp = 0L)
                }
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(count)
            }
        }
    }

    fun addManualInSociety(society: String, aliases: List<String>, context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val clean = society.trim().uppercase()
            CustomProjectRepository.addCustomProject(appContext, clean, aliases)
            val months = _projectMonths.value
            val databaseFile = (_dbState.value as? DatabaseState.Ready)?.file
            val listingsDb = databaseFile?.let { activeListingsDb(it.parentFile, months) }
            val cacheDb = databaseFile?.let { File(it.parentFile, "inventory_cache/parsed-text.db") }
            if (listingsDb?.exists() == true && cacheDb?.exists() == true) {
                runCatching {
                    AndroidInventorySql(listingsDb).use { work ->
                        AndroidInventorySql(cacheDb).use { cache ->
                            StreamingInventoryStore(work, cache).use { store ->
                                store.promoteSocietyToIn(clean)
                                _inProjectSummaries.value = store.getCuratedInProjectSummaries()
                                _outProjectSummaries.value = store.getOutProjectSummaries()
                                _projectSummaries.value = store.getProjectSummaries()
                                _inListings.value = store.getInListings(
                                    searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                    societyFilter = _inListingsSocietyFilter.value
                                )
                            }
                        }
                    }
                }
            } else {
                _inProjectSummaries.value = ProjectRegistry.getAllTargetProjectNames().map {
                    ProjectInventorySummary(society = it, status = "IN", totalListings = 0, uniqueDealers = 0, latestTimestamp = 0L)
                }
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    fun removeCustomInSociety(society: String, context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val clean = society.trim().uppercase()
            CustomProjectRepository.removeCustomProject(appContext, clean)
            val months = _projectMonths.value
            val databaseFile = (_dbState.value as? DatabaseState.Ready)?.file
            val listingsDb = databaseFile?.let { activeListingsDb(it.parentFile, months) }
            val cacheDb = databaseFile?.let { File(it.parentFile, "inventory_cache/parsed-text.db") }
            if (listingsDb?.exists() == true && cacheDb?.exists() == true) {
                runCatching {
                    AndroidInventorySql(listingsDb).use { work ->
                        AndroidInventorySql(cacheDb).use { cache ->
                            StreamingInventoryStore(work, cache).use { store ->
                                store.demoteSocietyToOut(clean)
                                _inProjectSummaries.value = store.getCuratedInProjectSummaries()
                                _outProjectSummaries.value = store.getOutProjectSummaries()
                                _projectSummaries.value = store.getProjectSummaries()
                                _inListings.value = store.getInListings(
                                    searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                    societyFilter = _inListingsSocietyFilter.value
                                )
                            }
                        }
                    }
                }
            } else {
                _inProjectSummaries.value = ProjectRegistry.getAllTargetProjectNames().map {
                    ProjectInventorySummary(society = it, status = "IN", totalListings = 0, uniqueDealers = 0, latestTimestamp = 0L)
                }
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    fun updateListing(context: Context, id: String, updatedRow: ImportantDealerRow, months: Long = _projectMonths.value) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingInListings.value = true
            try {
                val (listingsDb, parsedCacheDb) = getOrCreateActiveStore(appContext, months, false)
                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(parsedCacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            store.updateListing(id, updatedRow)
                            _projectSummaries.value = store.getProjectSummaries()
                            _inListings.value = store.getInListings(
                                searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                societyFilter = _inListingsSocietyFilter.value
                            )
                        }
                    }
                }
            } finally {
                _isLoadingInListings.value = false
            }
        }
    }

    fun deleteListing(context: Context, id: String, months: Long = _projectMonths.value) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingInListings.value = true
            try {
                val (listingsDb, parsedCacheDb) = getOrCreateActiveStore(appContext, months, false)
                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(parsedCacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            store.deleteListing(id)
                            _projectSummaries.value = store.getProjectSummaries()
                            _inListings.value = store.getInListings(
                                searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                societyFilter = _inListingsSocietyFilter.value
                            )
                        }
                    }
                }
            } finally {
                _isLoadingInListings.value = false
            }
        }
    }

    fun addManualListing(context: Context, row: ImportantDealerRow, months: Long = _projectMonths.value) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingInListings.value = true
            try {
                val (listingsDb, parsedCacheDb) = getOrCreateActiveStore(appContext, months, false)
                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(parsedCacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            store.addListing(row)
                            _projectSummaries.value = store.getProjectSummaries()
                            _inListings.value = store.getInListings(
                                searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                societyFilter = _inListingsSocietyFilter.value
                            )
                        }
                    }
                }
            } finally {
                _isLoadingInListings.value = false
            }
        }
    }

    fun refreshChats() {
        val reader = databaseSource ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val list = reader.listChats(
                query = _searchQuery.value.takeIf { it.isNotBlank() },
                onlyGroups = _onlyGroups.value,
                limit = 500
            )
            _chats.value = list
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        refreshChats()
    }

    fun onFilterGroupsChanged(onlyGroups: Boolean) {
        _onlyGroups.value = onlyGroups
        refreshChats()
    }

    fun selectChat(chat: ChatSummary) {
        _selectedChat.value = chat
        val reader = databaseSource ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMessages.value = true
            try {
                // Fetch up to 1,000 messages for fast rendering
                val msgs = reader.getChatMessages(chat.id, limit = 1000, offset = 0)
                _messages.value = msgs
            } finally {
                _isLoadingMessages.value = false
            }
        }
    }

    fun clearSelectedChat() {
        _selectedChat.value = null
        _messages.value = emptyList()
    }

    fun toggleChatSelection(chatId: Long) {
        val current = _selectedChatIdsForExport.value.toMutableSet()
        if (current.contains(chatId)) {
            current.remove(chatId)
        } else {
            current.add(chatId)
        }
        _selectedChatIdsForExport.value = current
    }

    fun selectAllChats() {
        _selectedChatIdsForExport.value = _chats.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedChatIdsForExport.value = emptySet()
    }

    suspend fun exportChat(
        chat: ChatSummary,
        format: ExportFormat,
        outputStream: OutputStream
    ) = withContext(Dispatchers.IO) {
        val reader = databaseSource ?: throw IllegalStateException("Database not open")
        val messagesSeq = reader.getAllChatMessages(chat.id)

        when (format) {
            ExportFormat.TXT -> {
                TxtChatExporter().exportChat(chat, messagesSeq, outputStream)
            }
            ExportFormat.JSON -> {
                JsonChatExporter().exportChat(chat, messagesSeq, outputStream)
            }
        }
    }

    private fun activeListingsDb(parent: File, window: Long): File {
        return File(parent, "active_inventory/${InventoryWindow.databaseName(window)}")
    }

    private suspend fun getOrCreateActiveStore(
        appContext: Context,
        months: Long,
        forceRefresh: Boolean = false,
        onProgress: ((Int, String) -> Unit)? = null
    ): Pair<File, File> = withContext(Dispatchers.IO) {
        val sourceFile = (_dbState.value as? DatabaseState.Ready)?.file ?: error("Database not loaded")
        val activeDir = File(appContext.noBackupFilesDir, "active_inventory").apply { mkdirs() }
        val listingsDb = File(activeDir, InventoryWindow.databaseName(months))
        val cacheDir = File(appContext.noBackupFilesDir, "inventory_cache").apply { mkdirs() }
        val parsedCacheDb = File(cacheDir, "parsed-text.db")

        if (listingsDb.exists() && !forceRefresh) {
            return@withContext Pair(listingsDb, parsedCacheDb)
        }

        listingsDb.delete()
        File(listingsDb.parentFile, "${listingsDb.name}-wal").delete()
        File(listingsDb.parentFile, "${listingsDb.name}-shm").delete()
        File(listingsDb.parentFile, "${listingsDb.name}-journal").delete()
        AndroidWhatsAppDatabaseReader.open(sourceFile).use { reader ->
            val latest = reader.latestBackupTimestamp() ?: error("No messages found in this backup")
            val latestDate = Instant.ofEpochMilli(latest).atZone(ZoneId.systemDefault()).toLocalDate()
            val cutoff = InventoryWindow.cutoff(latestDate, months)
            val cutoffMs = cutoff.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            AndroidInventorySql(listingsDb).use { work ->
                AndroidInventorySql(parsedCacheDb).use { cache ->
                    StreamingInventoryStore(work, cache).use { store ->
                        var groupDetail = ""
                        reader.scanInventoryMessages(cutoffMs, { done, total, name ->
                            coroutineContext.ensureActive()
                            groupDetail = "Groups: $done / $total • Since $cutoff\n$name"
                            onProgress?.invoke(store.processed, groupDetail)
                        }) { message ->
                            coroutineContext.ensureActive()
                            store.add(message)
                            if (store.processed % 500 == 0) {
                                onProgress?.invoke(
                                    store.processed,
                                    "$groupDetail\nListings: ${store.extracted} • Reused texts: ${store.reused}"
                                )
                            }
                        }
                        onProgress?.invoke(store.processed, "Preparing deduplicated inventory…")
                        store.prepare()
                        val summaries = store.getProjectSummaries()
                        _projectSummaries.value = summaries
                        _inProjectSummaries.value = store.getCuratedInProjectSummaries()
                        _outProjectSummaries.value = store.getOutProjectSummaries()
                        _inListings.value = store.getInListings(
                            searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                            societyFilter = _inListingsSocietyFilter.value
                        )
                    }
                }
            }
        }
        Pair(listingsDb, parsedCacheDb)
    }

    fun scanProjectInventory(context: Context, months: Long = _projectMonths.value, forceRefresh: Boolean = false) {
        if (inventoryJob?.isActive == true) return
        val appContext = context.applicationContext
        _isProjectScanning.value = true
        _projectScanProgress.value = "Starting project scan…"
        inventoryJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                CustomProjectRepository.loadAndSync(appContext)
                val (listingsDb, parsedCacheDb) = getOrCreateActiveStore(appContext, months, forceRefresh) { processed, detail ->
                    _projectScanProgress.value = "$detail (Messages: $processed)"
                }
                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(parsedCacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            _projectSummaries.value = store.getProjectSummaries()
                            _inProjectSummaries.value = store.getCuratedInProjectSummaries()
                            _outProjectSummaries.value = store.getOutProjectSummaries()
                            _inListings.value = store.getInListings(
                                searchQuery = _inListingsSearchQuery.value.takeIf { it.isNotBlank() },
                                societyFilter = _inListingsSocietyFilter.value
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _inventoryExportState.value = InventoryExportState.Error(e.localizedMessage ?: "Project scan failed")
            } finally {
                _isProjectScanning.value = false
                _projectScanProgress.value = ""
            }
        }
    }

    fun exportSingleProjectSubExcel(
        context: Context,
        project: ProjectInventorySummary,
        months: Long = _projectMonths.value,
        onShareReady: (File) -> Unit
    ) {
        if (inventoryJob?.isActive == true) return
        val appContext = context.applicationContext
        inventoryJob = viewModelScope.launch(Dispatchers.IO) {
            _inventoryExportState.value = InventoryExportState.Processing(0, "Exporting sub-excel for ${project.society}…")
            try {
                val (listingsDb, parsedCacheDb) = getOrCreateActiveStore(appContext, months, false)
                val exportDir = File(appContext.cacheDir, "shared_exports").apply { mkdirs() }
                val fileName = InventoryCsvWriter.subExcelFileName(project.society, months)
                val exportFile = File(exportDir, fileName)

                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(parsedCacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            FileOutputStream(exportFile).use { fos ->
                                InventoryCsvWriter.writeCsv(fos) { emit ->
                                    store.forEachRowForProject(project.society) { row ->
                                        coroutineContext.ensureActive()
                                        emit(row)
                                    }
                                }
                            }
                        }
                    }
                }
                _inventoryExportState.value = InventoryExportState.Complete(
                    exportFile,
                    project.totalListings,
                    if (project.status == "IN") project.totalListings else 0
                )
                withContext(Dispatchers.Main) { onShareReady(exportFile) }
            } catch (e: CancellationException) {
                _inventoryExportState.value = InventoryExportState.Idle
                throw e
            } catch (e: Exception) {
                _inventoryExportState.value = InventoryExportState.Error(e.localizedMessage ?: "Failed to export project sub-excel")
            }
        }
    }

    fun exportAllProjectsZip(
        context: Context,
        months: Long = _projectMonths.value,
        onShareReady: (File) -> Unit
    ) {
        if (inventoryJob?.isActive == true) return
        val appContext = context.applicationContext
        inventoryJob = viewModelScope.launch(Dispatchers.IO) {
            _inventoryExportState.value = InventoryExportState.Processing(0, "Packaging all project sub-excels into ZIP…")
            try {
                val (listingsDb, parsedCacheDb) = getOrCreateActiveStore(appContext, months, false)
                val exportDir = File(appContext.cacheDir, "shared_exports").apply { mkdirs() }
                val zipFile = File(exportDir, "All_Projects_SubExcels_${LocalDate.now()}_${InventoryWindow.suffix(months)}.zip")

                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(parsedCacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            val summaries = store.getProjectSummaries()
                            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                                summaries.forEachIndexed { index, proj ->
                                    coroutineContext.ensureActive()
                                    _inventoryExportState.value = InventoryExportState.Processing(
                                        index, "Adding ${proj.society} (${index + 1}/${summaries.size}) to ZIP…"
                                    )
                                    val entry = ZipEntry(InventoryCsvWriter.subExcelFileName(proj.society, months))
                                    zos.putNextEntry(entry)
                                    InventoryCsvWriter.writeCsv(zos) { emit ->
                                        store.forEachRowForProject(proj.society) { row ->
                                            emit(row)
                                        }
                                    }
                                    zos.closeEntry()
                                }
                            }
                            _inventoryExportState.value = InventoryExportState.Complete(
                                zipFile,
                                summaries.size,
                                summaries.count { it.status == "IN" }
                            )
                        }
                    }
                }
                withContext(Dispatchers.Main) { onShareReady(zipFile) }
            } catch (e: CancellationException) {
                _inventoryExportState.value = InventoryExportState.Idle
                throw e
            } catch (e: Exception) {
                _inventoryExportState.value = InventoryExportState.Error(e.localizedMessage ?: "Failed to export ZIP package")
            }
        }
    }

    fun exportMasterPropertyInventory(context: Context, months: Long = 1, onShareReady: (File) -> Unit) {
        if (inventoryJob?.isActive == true) return
        require(months > 0)
        val appContext = context.applicationContext
        inventoryJob = viewModelScope.launch(Dispatchers.IO) {
            _inventoryExportState.value = InventoryExportState.Processing(0)
            try {
                val (listingsDb, parsedCacheDb) = getOrCreateActiveStore(appContext, months, false) { processed, detail ->
                    _inventoryExportState.value = InventoryExportState.Processing(processed, detail)
                }
                val exportDir = File(appContext.cacheDir, "shared_exports").apply { mkdirs() }
                val exportFile = File(exportDir, "Master Important Dealer Inventory ${LocalDate.now()} ${InventoryWindow.suffix(months)}.csv")
                val staged = File(appContext.cacheDir, "master_inventory_staged.csv")

                AndroidInventorySql(listingsDb).use { work ->
                    AndroidInventorySql(parsedCacheDb).use { cache ->
                        StreamingInventoryStore(work, cache).use { store ->
                            val summaries = store.getProjectSummaries()
                            _projectSummaries.value = summaries
                            var written = 0
                            FileOutputStream(staged).use { output ->
                                InventoryCsvWriter.writeCsv(output) { emit ->
                                    store.forEachRow { row ->
                                        coroutineContext.ensureActive()
                                        emit(row)
                                        written++
                                        if (written % 500 == 0) {
                                            _inventoryExportState.value = InventoryExportState.Processing(
                                                written, "Writing Master CSV: $written rows"
                                            )
                                        }
                                    }
                                }
                            }
                            java.nio.file.Files.move(staged.toPath(), exportFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            val targetIn = summaries.filter { it.status == "IN" }.sumOf { it.totalListings }
                            _inventoryExportState.value = InventoryExportState.Complete(exportFile, written, targetIn)
                        }
                    }
                }
                withContext(Dispatchers.Main) { onShareReady(exportFile) }
            } catch (e: CancellationException) {
                _inventoryExportState.value = InventoryExportState.Idle
                throw e
            } catch (e: Exception) {
                _inventoryExportState.value = InventoryExportState.Error(e.localizedMessage ?: "Failed to export property inventory")
            }
        }
    }

    fun dismissInventoryExportDialog() {
        _inventoryExportState.value = InventoryExportState.Idle
    }

    fun purgeDecryptedData(databaseFile: File): Boolean {
        if (inventoryJob?.isActive == true) return false
        File(databaseFile.parentFile, "inventory_cache").deleteRecursively()
        File(databaseFile.parentFile, "active_inventory").deleteRecursively()
        databaseSource?.close()
        databaseSource = null
        _dbState.value = DatabaseState.Closed
        _chats.value = emptyList()
        _messages.value = emptyList()
        _selectedChat.value = null
        _selectedChatIdsForExport.value = emptySet()
        _projectSummaries.value = emptyList()
        _inProjectSummaries.value = ProjectRegistry.getAllTargetProjectNames().map {
            ProjectInventorySummary(it, "IN", 0, 0, 0L)
        }
        _outProjectSummaries.value = emptyList()

        return try {
            if (databaseFile.exists()) {
                databaseFile.delete()
            } else true
        } catch (_: Exception) {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        databaseSource?.close()
        databaseSource = null
    }
}
