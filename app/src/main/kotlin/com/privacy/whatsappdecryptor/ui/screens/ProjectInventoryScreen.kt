package com.privacy.whatsappdecryptor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privacy.whatsappdecryptor.core.inventory.ProjectInventorySummary
import com.privacy.whatsappdecryptor.ui.fold.FoldPosture
import com.privacy.whatsappdecryptor.ui.fold.foldPosture
import com.privacy.whatsappdecryptor.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectInventoryScreen(
    projectSummaries: List<ProjectInventorySummary>,
    isScanning: Boolean,
    scanProgress: String,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChanged: (String) -> Unit,
    onScanProjects: () -> Unit,
    onExportSingleProject: (ProjectInventorySummary) -> Unit,
    onExportAllZip: () -> Unit,
    onExportMasterCsv: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredProjects = remember(projectSummaries, searchQuery, statusFilter) {
        projectSummaries.filter { proj ->
            val matchesQuery = searchQuery.isBlank() ||
                    proj.society.contains(searchQuery.trim(), ignoreCase = true)
            val matchesStatus = when (statusFilter) {
                "IN" -> proj.status == "IN"
                "OUT" -> proj.status != "IN"
                else -> true
            }
            matchesQuery && matchesStatus
        }
    }

    val totalTargetProjects = remember(projectSummaries) {
        projectSummaries.count { it.status == "IN" }
    }
    val totalListingsCount = remember(projectSummaries) {
        projectSummaries.sumOf { it.totalListings }
    }
    val cover = foldPosture() == FoldPosture.Cover

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Search projects (e.g. M3M, DXP)...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Project Sub-Excels",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (projectSummaries.isNotEmpty()) {
                                    "${projectSummaries.size} projects • $totalListingsCount listings"
                                } else {
                                    "Last 7 days"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Projects")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings & Privacy")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Control Header: timeframe selector and global export actions
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
                    Text(
                        text = "Last 7 days",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (cover) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            ProjectScanButton(
                                empty = projectSummaries.isEmpty(),
                                enabled = !isScanning,
                                onClick = onScanProjects,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (projectSummaries.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onExportAllZip,
                                    enabled = !isScanning,
                                    shape = RoundedCornerShape(Spacing.sm),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("ZIP All", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = onExportMasterCsv,
                                    enabled = !isScanning,
                                    shape = RoundedCornerShape(Spacing.sm),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Master CSV", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            ProjectScanButton(
                                empty = projectSummaries.isEmpty(),
                                enabled = !isScanning,
                                onClick = onScanProjects,
                                modifier = Modifier.weight(1f)
                            )
                            if (projectSummaries.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onExportAllZip,
                                    enabled = !isScanning,
                                    shape = RoundedCornerShape(Spacing.sm),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("ZIP All", fontWeight = FontWeight.SemiBold)
                                }
                                FilledTonalIconButton(
                                    onClick = onExportMasterCsv,
                                    enabled = !isScanning,
                                    shape = RoundedCornerShape(Spacing.sm),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Export Master CSV")
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = isScanning) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xxs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Spacing.xs)
                                    .clip(RoundedCornerShape(Spacing.xxs)),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = scanProgress.ifEmpty { "Scanning and deduplicating project inventory…" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Filter Chips with horizontal scrolling and sleek styling
            if (projectSummaries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    FilterChip(
                        selected = statusFilter == "ALL",
                        onClick = { onStatusFilterChanged("ALL") },
                        label = { Text("All (${projectSummaries.size})") },
                        shape = RoundedCornerShape(Spacing.xs)
                    )
                    FilterChip(
                        selected = statusFilter == "IN",
                        onClick = { onStatusFilterChanged("IN") },
                        label = { Text("Target IN (${totalTargetProjects})") },
                        shape = RoundedCornerShape(Spacing.xs),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = statusFilter == "OUT",
                        onClick = { onStatusFilterChanged("OUT") },
                        label = { Text("Other (${projectSummaries.size - totalTargetProjects})") },
                        shape = RoundedCornerShape(Spacing.xs)
                    )
                }
            }

            // Content Area: Scanning vs Empty State vs Projects List
            if (isScanning && projectSummaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Spacing.xxl)
                        )
                        Text(
                            text = "Analyzing WhatsApp Messages…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = scanProgress.ifEmpty { "Extracting and grouping property listings by project…" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (projectSummaries.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Domain,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Text(
                            text = "Individual Project Sub-Excels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Extract and browse separated Excel spreadsheets for each project (e.g. Smart World DXP, M3M Mansion, Sobha City) with one-tap WhatsApp sharing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Button(
                            onClick = onScanProjects,
                            shape = RoundedCornerShape(Spacing.sm),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Scan & Generate Sub-Excels", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (filteredProjects.isEmpty() && projectSummaries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No projects matching \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (cover) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(filteredProjects, key = { it.society }) { project ->
                            ProjectCard(
                                project = project,
                                onExportSubExcel = { onExportSingleProject(project) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(filteredProjects, key = { it.society }) { project ->
                            ProjectCard(
                                project = project,
                                onExportSubExcel = { onExportSingleProject(project) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectScanButton(
    empty: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Spacing.sm),
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Icon(
            imageVector = if (empty) Icons.Default.PlayArrow else Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            if (empty) "Scan Projects" else "Rescan",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectInventorySummary,
    onExportSubExcel: () -> Unit
) {
    val isTarget = project.status == "IN"
    val formattedDate = remember(project.latestTimestamp) {
        if (project.latestTimestamp > 0) {
            DateFormatter.format(Instant.ofEpochMilli(project.latestTimestamp))
        } else ""
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget)
                MaterialTheme.colorScheme.surfaceContainerHighest
            else
                MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(Spacing.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header Row: Society name & Target Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.society,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (formattedDate.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = "Latest: $formattedDate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.xs))

                Surface(
                    shape = RoundedCornerShape(Spacing.xs),
                    color = if (isTarget)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                    ) {
                        if (isTarget) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = if (isTarget) "Target (IN)" else "Discovered",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTarget)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Metrics: Listings count & Unique Dealers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Icon(
                        Icons.Default.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${project.totalListings} listings",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${project.uniqueDealers} dealers",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Export Sub-Excel Button with 44dp height for thumb zone
            FilledTonalButton(
                onClick = onExportSubExcel,
                shape = RoundedCornerShape(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Export CSV", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

