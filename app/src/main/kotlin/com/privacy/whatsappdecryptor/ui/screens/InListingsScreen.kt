package com.privacy.whatsappdecryptor.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privacy.whatsappdecryptor.core.inventory.ProjectInventorySummary
import com.privacy.whatsappdecryptor.core.inventory.ProjectRegistry
import com.privacy.whatsappdecryptor.ui.fold.FoldPosture
import com.privacy.whatsappdecryptor.ui.fold.foldPosture
import com.privacy.whatsappdecryptor.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ListingDateFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InListingsScreen(
    inProjects: List<ProjectInventorySummary>,
    isLoading: Boolean,
    isScanning: Boolean = false,
    scanProgress: String = "",
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onScanProjects: () -> Unit = {},
    onAddSociety: (String, List<String>) -> Unit = { _, _ -> },
    onRemoveCustomSociety: (String) -> Unit = {},
    onExportSingleProject: (ProjectInventorySummary) -> Unit,
    onExportMasterCsv: () -> Unit,
    onExportAllZip: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabFilter by remember { mutableStateOf("ALL") } // "ALL", "ACTIVE", "EMPTY"
    var showAddDialog by remember { mutableStateOf(false) }
    var newSocietyName by remember { mutableStateOf("") }
    var newSocietyAliases by remember { mutableStateOf("") }

    val filteredProjects = remember(inProjects, searchQuery, selectedTabFilter) {
        inProjects.filter { proj ->
            val matchesQuery = searchQuery.isBlank() ||
                    proj.society.contains(searchQuery.trim(), ignoreCase = true)
            val matchesTab = when (selectedTabFilter) {
                "ACTIVE" -> proj.totalListings > 0
                "EMPTY" -> proj.totalListings == 0
                else -> true
            }
            matchesQuery && matchesTab
        }
    }

    val totalActiveListings = remember(inProjects) {
        inProjects.sumOf { it.totalListings }
    }
    val projectsWithInventory = remember(inProjects) {
        inProjects.count { it.totalListings > 0 }
    }
    val cover = foldPosture() == FoldPosture.Cover

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                newSocietyName = ""
                newSocietyAliases = ""
            },
            title = {
                Text(
                    text = "Add Target Society",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Add a society to your curated IN portfolio. Messages mentioning this society will be tracked and included in Sub-Excel exports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newSocietyName,
                        onValueChange = { newSocietyName = it },
                        label = { Text("Society Name") },
                        placeholder = { Text("e.g. Godrej Aristocrat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSocietyAliases,
                        onValueChange = { newSocietyAliases = it },
                        label = { Text("Aliases / Keywords (optional)") },
                        placeholder = { Text("e.g. Aristocrat, Godrej 49") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = newSocietyName.trim().uppercase()
                        if (cleanName.isNotBlank()) {
                            val aliasesList = newSocietyAliases.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            onAddSociety(cleanName, aliasesList)
                            showAddDialog = false
                            newSocietyName = ""
                            newSocietyAliases = ""
                        }
                    },
                    enabled = newSocietyName.isNotBlank()
                ) {
                    Text("Add to IN")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddDialog = false
                        newSocietyName = ""
                        newSocietyAliases = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Target (IN) Properties",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${inProjects.size} Curated Societies • $totalActiveListings Active Listings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (cover) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add society")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = Spacing.sm),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh IN List")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings & Privacy")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scanning Status / Progress Banner
            if (isScanning) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(Spacing.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Extracting Listings from WhatsApp Messages…",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Spacing.xs)
                                .clip(RoundedCornerShape(Spacing.xxs)),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = scanProgress.ifEmpty { "Scanning and deduplicating property listings…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (totalActiveListings == 0) {
                // Not yet scanned banner
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(Spacing.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Messages Not Scanned Yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "All ${inProjects.size} curated societies are loaded. Tap below to scan your decrypted WhatsApp chats and extract active property listings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onScanProjects,
                            shape = RoundedCornerShape(Spacing.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Scan Messages Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Global Export & Overview Hero Card (60/30/10 structure)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(Spacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Header Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Curated Master Catalog",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$projectsWithInventory of ${inProjects.size} societies have listings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(Spacing.xs),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "IN LIST",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
                            )
                        }
                    }

                    if (cover) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            MasterExcelButton(onExportMasterCsv, Modifier.fillMaxWidth())
                            AllSubExcelsButton(onExportAllZip, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            MasterExcelButton(onExportMasterCsv, Modifier.weight(1f))
                            AllSubExcelsButton(onExportAllZip, Modifier.weight(1f))
                        }
                    }
                }
            }

            // Search Bar with 8-pt spacing
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                placeholder = { Text("Search curated properties (e.g. M3M, DXP, Sobha)…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(Spacing.sm)
            )

            // Filter Chips (All, With Inventory, Pending)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                FilterChip(
                    selected = selectedTabFilter == "ALL",
                    onClick = { selectedTabFilter = "ALL" },
                    label = { Text("All (${inProjects.size})") },
                    shape = RoundedCornerShape(Spacing.xs)
                )
                FilterChip(
                    selected = selectedTabFilter == "ACTIVE",
                    onClick = { selectedTabFilter = "ACTIVE" },
                    label = { Text("With Listings ($projectsWithInventory)") },
                    shape = RoundedCornerShape(Spacing.xs),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = selectedTabFilter == "EMPTY",
                    onClick = { selectedTabFilter = "EMPTY" },
                    label = { Text("Pending (${inProjects.size - projectsWithInventory})") },
                    shape = RoundedCornerShape(Spacing.xs)
                )
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Curated Properties List
            if (filteredProjects.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(Spacing.huge)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Apartment,
                                    contentDescription = null,
                                    modifier = Modifier.size(Spacing.xl),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "No Properties Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                searchQuery.isNotEmpty() -> "No curated property matches \"$searchQuery\"."
                                selectedTabFilter == "ACTIVE" && projectsWithInventory == 0 ->
                                    "No societies have extracted listings yet. Tap 'Scan Messages Now' above to extract listings from your chats."
                                else -> "No properties match the selected filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (searchQuery.isNotEmpty()) {
                            Button(
                                onClick = { onSearchQueryChanged("") },
                                shape = RoundedCornerShape(Spacing.sm),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Clear Search")
                            }
                        } else if (selectedTabFilter != "ALL") {
                            OutlinedButton(
                                onClick = { selectedTabFilter = "ALL" },
                                shape = RoundedCornerShape(Spacing.sm),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Show All Societies")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(filteredProjects, key = { it.society }) { project ->
                        CuratedPropertyCard(
                            project = project,
                            onExportSubExcel = { onExportSingleProject(project) },
                            onRemoveCustom = { onRemoveCustomSociety(project.society) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterExcelButton(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(Spacing.sm),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text("Master Excel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AllSubExcelsButton(onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(Spacing.sm)
    ) {
        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text("All Sub-Excels", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CuratedPropertyCard(
    project: ProjectInventorySummary,
    onExportSubExcel: () -> Unit,
    onRemoveCustom: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasListings = project.totalListings > 0
    val isCustom = remember(project.society) { ProjectRegistry.isCustomProject(project.society) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.md),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Society Information
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(Spacing.sm),
                    color = if (hasListings) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(Spacing.xxl)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Apartment,
                            contentDescription = null,
                            tint = if (hasListings) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(Spacing.lg)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = project.society,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isCustom) {
                            Spacer(Modifier.width(Spacing.xs))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(Spacing.xxs)
                            ) {
                                Text(
                                    text = "Custom",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = Spacing.xxs, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.xxs))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasListings) {
                            Text(
                                text = "${project.totalListings} listings",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${project.uniqueDealers} dealers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (project.latestTimestamp > 0) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = runCatching {
                                        ListingDateFormatter.format(Instant.ofEpochMilli(project.latestTimestamp))
                                    }.getOrDefault(""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "0 extracted listings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(Spacing.xs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Export Sub-Excel Action Button
                FilledTonalButton(
                    onClick = onExportSubExcel,
                    shape = RoundedCornerShape(Spacing.sm),
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Spacing.xxs))
                    Text(
                        text = "Sub-Excel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isCustom) {
                    Spacer(Modifier.width(Spacing.xxs))
                    IconButton(
                        onClick = onRemoveCustom,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove Custom Society",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

