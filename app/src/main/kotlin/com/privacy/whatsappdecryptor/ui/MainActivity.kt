package com.privacy.whatsappdecryptor.ui

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacy.whatsappdecryptor.core.inventory.InventoryWindow
import com.privacy.whatsappdecryptor.core.service.DecryptionForegroundService
import com.privacy.whatsappdecryptor.ui.fold.FoldPosture
import com.privacy.whatsappdecryptor.ui.fold.foldPosture
import com.privacy.whatsappdecryptor.ui.screens.*
import com.privacy.whatsappdecryptor.ui.theme.WhatsAppDecryptorTheme
import com.privacy.whatsappdecryptor.ui.viewmodel.ChatViewModel
import com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState
import java.io.File

enum class Screen {
    SETUP,
    PROGRESS,
    PROJECTS,
    IN_LISTINGS,
    OUT_LISTINGS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Privacy Mode (FLAG_SECURE) default enabled
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val privacyMode = prefs.getBoolean("privacy_mode_enabled", true)
        updatePrivacyFlags(privacyMode)

        // Check if a decrypted database already exists from a previous session
        val decryptedFile = File(noBackupFilesDir, "msgstore_decrypted.db")
        val initialScreen = if (decryptedFile.exists()) {
            viewModel.loadDatabase(decryptedFile)
            Screen.PROJECTS
        } else {
            Screen.SETUP
        }

        setContent {
            WhatsAppDecryptorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(initialScreen) }
                    var privacyModeEnabled by remember { mutableStateOf(privacyMode) }

                    val progressState by DecryptionForegroundService.progressState.collectAsStateWithLifecycle()
                    val inventoryExportState by viewModel.inventoryExportState.collectAsStateWithLifecycle()

                    // Project Inventory States
                    val projectSummaries by viewModel.projectSummaries.collectAsStateWithLifecycle()
                    val isProjectScanning by viewModel.isProjectScanning.collectAsStateWithLifecycle()
                    val projectScanProgress by viewModel.projectScanProgress.collectAsStateWithLifecycle()
                    val projectSearchQuery by viewModel.projectSearchQuery.collectAsStateWithLifecycle()
                    val projectStatusFilter by viewModel.projectStatusFilter.collectAsStateWithLifecycle()

                    // IN Listings States
                    val inProjectSummaries by viewModel.inProjectSummaries.collectAsStateWithLifecycle()
                    val inListingsSearchQuery by viewModel.inListingsSearchQuery.collectAsStateWithLifecycle()
                    val isLoadingInListings by viewModel.isLoadingInListings.collectAsStateWithLifecycle()

                    // OUT Listings States
                    val outProjectSummaries by viewModel.outProjectSummaries.collectAsStateWithLifecycle()
                    val outListingsSearchQuery by viewModel.outListingsSearchQuery.collectAsStateWithLifecycle()
                    val isLoadingOutListings by viewModel.isLoadingOutListings.collectAsStateWithLifecycle()

                    // Helper to share files
                    fun shareFile(file: File, mimeType: String, subject: String, title: String) {
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            file
                        )
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(android.content.Intent.createChooser(shareIntent, title))
                    }

                    // Back Handler Navigation
                    BackHandler {
                        when (currentScreen) {
                            Screen.SETTINGS -> currentScreen = Screen.PROJECTS
                            Screen.OUT_LISTINGS -> currentScreen = Screen.PROJECTS
                            Screen.IN_LISTINGS -> currentScreen = Screen.PROJECTS
                            Screen.PROJECTS -> finish()
                            Screen.PROGRESS -> {
                                DecryptionForegroundService.cancel(this@MainActivity)
                                currentScreen = Screen.SETUP
                            }
                            Screen.SETUP -> finish()
                        }
                    }

                    // Centralized Inventory Export Dialogs (Processing, Complete, Error)
                    when (val state = inventoryExportState) {
                        is InventoryExportState.Processing -> {
                            AlertDialog(
                                onDismissRequest = {},
                                icon = { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) },
                                title = { Text("Processing Inventory", fontWeight = FontWeight.Bold) },
                                text = { Text("${state.detail}\n\nProcessed: ${state.processedMessages}") },
                                confirmButton = {}
                            )
                        }
                        is InventoryExportState.Complete -> {
                            AlertDialog(
                                onDismissRequest = viewModel::dismissInventoryExportDialog,
                                icon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                },
                                title = { Text("Export Ready!", fontWeight = FontWeight.Bold) },
                                text = {
                                    Text("Successfully generated:\n${state.file.name}\n\nListings included: ${state.totalRows}\nOpening share menu...")
                                },
                                confirmButton = {
                                    Button(onClick = viewModel::dismissInventoryExportDialog) {
                                        Text("OK")
                                    }
                                }
                            )
                        }
                        is InventoryExportState.Error -> {
                            AlertDialog(
                                onDismissRequest = viewModel::dismissInventoryExportDialog,
                                icon = {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                title = { Text("Export Failed", fontWeight = FontWeight.Bold) },
                                text = { Text(state.message) },
                                confirmButton = {
                                    Button(onClick = viewModel::dismissInventoryExportDialog) {
                                        Text("Dismiss")
                                    }
                                }
                            )
                        }
                        InventoryExportState.Idle -> {}
                    }

                    val isDashboard = currentScreen == Screen.PROJECTS || currentScreen == Screen.IN_LISTINGS || currentScreen == Screen.OUT_LISTINGS
                    val cover = foldPosture() == FoldPosture.Cover

                    Scaffold(
                        bottomBar = {
                            if (isDashboard) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 4.dp
                                ) {
                                    val navColors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    NavigationBarItem(
                                        selected = currentScreen == Screen.PROJECTS,
                                        onClick = { currentScreen = Screen.PROJECTS },
                                        icon = {
                                             Icon(
                                                 Icons.Default.Apartment,
                                                 contentDescription = "Projects"
                                             )
                                        },
                                        label = {
                                             Text(
                                                 "Projects",
                                                 fontWeight = if (currentScreen == Screen.PROJECTS) FontWeight.Bold else FontWeight.Normal
                                             )
                                        },
                                        colors = navColors
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen == Screen.IN_LISTINGS,
                                        onClick = {
                                             currentScreen = Screen.IN_LISTINGS
                                             viewModel.loadInListings(this@MainActivity)
                                        },
                                        icon = {
                                             Icon(
                                                 Icons.Default.FormatListBulleted,
                                                 contentDescription = "IN Listings"
                                             )
                                        },
                                        label = {
                                             Text(
                                                 if (cover) "IN" else "IN Listings",
                                                 fontWeight = if (currentScreen == Screen.IN_LISTINGS) FontWeight.Bold else FontWeight.Normal
                                             )
                                        },
                                        colors = navColors
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen == Screen.OUT_LISTINGS,
                                        onClick = {
                                             currentScreen = Screen.OUT_LISTINGS
                                             viewModel.loadOutListings(this@MainActivity)
                                        },
                                        icon = {
                                             Icon(
                                                 Icons.Default.TravelExplore,
                                                 contentDescription = "OUT Listings"
                                             )
                                        },
                                        label = {
                                             Text(
                                                 if (cover) "OUT" else "OUT Listings",
                                                 fontWeight = if (currentScreen == Screen.OUT_LISTINGS) FontWeight.Bold else FontWeight.Normal
                                             )
                                        },
                                        colors = navColors
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            when (currentScreen) {
                                Screen.SETUP -> {
                                    SetupScreen(
                                        onStartDecryption = { uri, key, _ ->
                                            DecryptionForegroundService.start(this@MainActivity, uri, key)
                                            currentScreen = Screen.PROGRESS
                                        }
                                    )
                                }

                                Screen.PROGRESS -> {
                                    ProgressScreen(
                                        progressState = progressState,
                                        onDecryptionComplete = {
                                            val targetFile = File(noBackupFilesDir, "msgstore_decrypted.db")
                                            viewModel.loadDatabase(targetFile)
                                            currentScreen = Screen.PROJECTS
                                        },
                                        onCancel = {
                                            currentScreen = Screen.SETUP
                                        }
                                    )
                                }

                                Screen.PROJECTS -> {
                                    ProjectInventoryScreen(
                                        projectSummaries = projectSummaries,
                                        isScanning = isProjectScanning,
                                        scanProgress = projectScanProgress,
                                        searchQuery = projectSearchQuery,
                                        onSearchQueryChanged = viewModel::onProjectSearchQueryChanged,
                                        statusFilter = projectStatusFilter,
                                        onStatusFilterChanged = viewModel::onProjectStatusFilterChanged,
                                        onScanProjects = {
                                            viewModel.scanProjectInventory(
                                                this@MainActivity,
                                                InventoryWindow.LAST_WEEK,
                                                forceRefresh = true
                                            )
                                        },
                                        onExportSingleProject = { proj ->
                                            viewModel.exportSingleProjectSubExcel(this@MainActivity, proj, InventoryWindow.LAST_WEEK) { shareFile ->
                                                shareFile(shareFile, "text/csv", "Sub-Excel: ${proj.society}", "Share ${proj.society} Sub-Excel")
                                            }
                                        },
                                        onExportAllZip = {
                                            viewModel.exportAllProjectsZip(this@MainActivity, InventoryWindow.LAST_WEEK) { zipFile ->
                                                shareFile(zipFile, "application/zip", "All Project Sub-Excels", "Share All Project Sub-Excels (ZIP)")
                                            }
                                        },
                                        onExportMasterCsv = {
                                            viewModel.exportMasterPropertyInventory(this@MainActivity, InventoryWindow.LAST_WEEK) { csvFile ->
                                                shareFile(csvFile, "text/csv", "Master Property Inventory", "Share Master Inventory CSV")
                                            }
                                        },
                                        onNavigateToSettings = {
                                            currentScreen = Screen.SETTINGS
                                        }
                                    )
                                }

                                Screen.IN_LISTINGS -> {
                                    InListingsScreen(
                                        inProjects = inProjectSummaries,
                                        isLoading = isLoadingInListings,
                                        isScanning = isProjectScanning,
                                        scanProgress = projectScanProgress,
                                        searchQuery = inListingsSearchQuery,
                                        onSearchQueryChanged = { viewModel.onInListingsSearchQueryChanged(it, this@MainActivity) },
                                        onScanProjects = {
                                            viewModel.scanProjectInventory(
                                                this@MainActivity,
                                                InventoryWindow.LAST_WEEK,
                                                forceRefresh = true
                                            )
                                        },
                                        onAddSociety = { name, aliases ->
                                            viewModel.addManualInSociety(name, aliases, this@MainActivity)
                                        },
                                        onRemoveCustomSociety = { name ->
                                            viewModel.removeCustomInSociety(name, this@MainActivity)
                                        },
                                        onExportSingleProject = { proj ->
                                            viewModel.exportSingleProjectSubExcel(this@MainActivity, proj, InventoryWindow.LAST_WEEK) { shareFile ->
                                                shareFile(shareFile, "text/csv", "Sub-Excel: ${proj.society}", "Share ${proj.society} Sub-Excel")
                                            }
                                        },
                                        onExportMasterCsv = {
                                            viewModel.exportMasterPropertyInventory(this@MainActivity, InventoryWindow.LAST_WEEK) { csvFile ->
                                                shareFile(csvFile, "text/csv", "Master Property Inventory", "Share Master Inventory CSV")
                                            }
                                        },
                                        onExportAllZip = {
                                            viewModel.exportAllProjectsZip(this@MainActivity, InventoryWindow.LAST_WEEK) { zipFile ->
                                                shareFile(zipFile, "application/zip", "All Project Sub-Excels", "Share All Project Sub-Excels (ZIP)")
                                            }
                                        },
                                        onRefresh = {
                                            viewModel.loadInListings(this@MainActivity)
                                        },
                                        onNavigateToSettings = {
                                            currentScreen = Screen.SETTINGS
                                        }
                                    )
                                }

                                Screen.OUT_LISTINGS -> {
                                    OutListingsScreen(
                                        outProjects = outProjectSummaries,
                                        isLoading = isLoadingOutListings,
                                        isScanning = isProjectScanning,
                                        scanProgress = projectScanProgress,
                                        searchQuery = outListingsSearchQuery,
                                        onSearchQueryChanged = viewModel::onOutListingsSearchQueryChanged,
                                        onPromoteSociety = { society ->
                                            viewModel.promoteOutSocietyToIn(society, this@MainActivity)
                                        },
                                        onRefresh = {
                                            viewModel.loadOutListings(this@MainActivity)
                                        },
                                        onNavigateToSettings = {
                                            currentScreen = Screen.SETTINGS
                                        }
                                    )
                                }

                                Screen.SETTINGS -> {
                                    SettingsScreen(
                                        decryptedFile = File(noBackupFilesDir, "msgstore_decrypted.db"),
                                        privacyModeEnabled = privacyModeEnabled,
                                        onTogglePrivacyMode = { enabled ->
                                            privacyModeEnabled = enabled
                                            prefs.edit().putBoolean("privacy_mode_enabled", enabled).apply()
                                            updatePrivacyFlags(enabled)
                                        },
                                        onPurgeDecryptedData = {
                                            viewModel.purgeDecryptedData(File(noBackupFilesDir, "msgstore_decrypted.db"))
                                            currentScreen = Screen.SETUP
                                        },
                                        onBack = {
                                            currentScreen = Screen.PROJECTS
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updatePrivacyFlags(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
