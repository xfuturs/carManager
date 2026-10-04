@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.carmanager.app.features.documents

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import com.carmanager.app.core.ui.components.LocalDataContent
import com.carmanager.app.core.ui.components.CompactEmptyState
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.carmanager.app.R
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import com.carmanager.app.core.ui.theme.*
import com.carmanager.app.core.util.DateFormatter
import com.carmanager.app.core.util.UiEvent
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val reportActions = reportUiActions(snackbarHostState)
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCategory by remember { mutableStateOf<DocumentCategory?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            showAddDialog = true
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.Success -> {}
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    BackHandler(enabled = selectedCategory != null || isSearchActive) {
        if (isSearchActive) {
            isSearchActive = false
            viewModel.onSearchQueryChange("")
        } else {
            selectedCategory = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSearchActive) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClose = {
                        isSearchActive = false
                        viewModel.onSearchQueryChange("")
                    }
                )
            } else {
                CarManagerBackAppBar(
                    title = if (selectedCategory == null) stringResource(R.string.docs_title) else getCategoryName(selectedCategory!!),
                    onNavigateBack = { if (selectedCategory == null) onNavigateBack() else selectedCategory = null },
                    backDescription = stringResource(R.string.cancel),
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Rechercher")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selectedCategory != DocumentCategory.REPORTS) FloatingActionButton(
                onClick = { pickerLauncher.launch("*/*") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Importer un document")
            }
        }
    ) { padding ->
        LocalDataContent(state, viewModel::retryLoading, Modifier.padding(padding)) { documents ->
            if (searchQuery.isNotEmpty()) {
                // Vue recherche globale
                DocumentGridView(
                    documents = documents,
                    isSearchResult = true,
                    onDelete = viewModel::requestDeletion,
                    onOpen = { if (it.category == DocumentCategory.REPORTS) reportActions.open(it) else if (viewModel.canOpen(it)) openFile(context, it) },
                    onSave = reportActions.save,
                    onShare = reportActions.share,
                    onConvertToPdf = viewModel::convertToPdf,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (selectedCategory == null) {
                FolderGridView(
                    documents = documents,
                    onCategoryClick = { selectedCategory = it },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val filteredDocs = documents.filter { it.category == selectedCategory }
                DocumentGridView(
                    documents = filteredDocs,
                    onDelete = viewModel::requestDeletion,
                    onOpen = { if (it.category == DocumentCategory.REPORTS) reportActions.open(it) else if (viewModel.canOpen(it)) openFile(context, it) },
                    onSave = reportActions.save,
                    onShare = reportActions.share,
                    isReportsFolder = selectedCategory == DocumentCategory.REPORTS,
                    onConvertToPdf = viewModel::convertToPdf,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    viewModel.pendingDeletion?.let { document ->
        val report = document.category == DocumentCategory.REPORTS
        AlertDialog(onDismissRequest = viewModel::cancelDeletion,
            title = { Text(if (report) "Supprimer ce rapport ?" else "Supprimer ce document ?") },
            text = { Text(if (report)
                "Cette action supprimera définitivement le rapport enregistré dans Car Manager. Les copies que vous avez enregistrées ailleurs sur votre appareil ne seront pas supprimées."
                else "Cette action supprimera définitivement la copie enregistrée dans Car Manager. Les originaux et copies enregistrés ailleurs ne seront pas supprimés.",
                modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = viewModel::confirmDeletion, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Supprimer définitivement") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDeletion, modifier = Modifier.heightIn(min = 48.dp)) { Text("Annuler") } })
    }
    if (showAddDialog && selectedUri != null) {
        AddDocumentDialog(
            initialCategory = selectedCategory?.takeUnless { it == DocumentCategory.REPORTS } ?: DocumentCategory.PHOTOS,
            onDismiss = { 
                showAddDialog = false
                selectedUri = null
            },
            onConfirm = { title, category ->
                viewModel.addDocument(selectedUri!!, title, category)
                showAddDialog = false
                selectedUri = null
                selectedCategory = category
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    CarManagerBackAppBar(
        title = "Rechercher un document",
        onNavigateBack = onClose,
        backDescription = "Fermer la recherche",
        titleContent = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Rechercher un document...") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Effacer la recherche")
                }
            }
        }
    )
}

@Composable
private fun FolderGridView(
    documents: List<Document>,
    onCategoryClick: (DocumentCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = ordinaryDocumentCategories
    val summary = ReportSummary.from(documents)
    
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 320.dp * fontScale && fontScale <= 1.3f) 2 else 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "car_manager_reports", span = { GridItemSpan(maxLineSpan) }) {
                Card(onClick = { onCategoryClick(DocumentCategory.REPORTS) }, modifier = Modifier.fillMaxWidth(),
                    shape = CarManagerShapes.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.docs_category_reports), fontWeight = FontWeight.SemiBold)
                            Text(if (summary.count == 0) "Aucun rapport enregistré." else "${summary.count} rapport(s) enregistré(s).",
                                style = MaterialTheme.typography.bodySmall)
                            summary.latest?.let { Text("Dernier rapport : ${reportDate(it)}", style = MaterialTheme.typography.labelSmall) }
                            Text(if (summary.count == 0) "Les rapports générés pour ce véhicule apparaîtront ici." else "Rapports PDF générés par l’application",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            items(categories, key = { it.name }) { category ->
                FolderItem(category, documents.count { it.category == category }) { onCategoryClick(category) }
            }
        }
    }
}

@Composable
private fun FolderItem(category: DocumentCategory, count: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = CarManagerShapes.card,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(getCategoryIcon(category), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Text(getCategoryName(category), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text("$count " + if (count == 1) "document" else "documents",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DocumentGridView(
    documents: List<Document>,
    onDelete: (Document) -> Unit,
    onOpen: (Document) -> Unit,
    onConvertToPdf: (Document) -> Unit,
    onSave: (Document) -> Unit,
    onShare: (Document) -> Unit,
    modifier: Modifier = Modifier,
    isSearchResult: Boolean = false,
    isReportsFolder: Boolean = false
) {
    if (documents.isEmpty()) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            CompactEmptyState(Icons.Default.FolderOpen,
                if (isSearchResult) "Aucun résultat" else if (isReportsFolder) "Aucun rapport enregistré." else "Aucun document dans ce dossier",
                if (isSearchResult) "Essayez un autre titre de document." else if (isReportsFolder) "Les rapports générés pour ce véhicule apparaîtront ici." else "Utilisez + pour importer un document ou une photo.")
        }
    } else {
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(modifier.fillMaxSize()) {
            val columns = if (!isReportsFolder && maxWidth >= 320.dp * fontScale && fontScale <= 1.3f) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents, key = { it.id }, span = { doc ->
                    GridItemSpan(if (doc.category == DocumentCategory.REPORTS) maxLineSpan else 1)
                }) { doc ->
                    if (doc.category == DocumentCategory.REPORTS)
                        ReportCard(doc, { onDelete(doc) }, { onOpen(doc) }, { onSave(doc) }, { onShare(doc) })
                    else DocumentItem(doc, { onDelete(doc) }, { onOpen(doc) }, { onConvertToPdf(doc) }, { onSave(doc) }, { onShare(doc) })
                }
            }
        }
    }
}

@Composable
private fun getCategoryName(category: DocumentCategory): String {
    return when(category) {
        DocumentCategory.ADMINISTRATIVE -> stringResource(R.string.docs_category_admin)
        DocumentCategory.INSURANCE -> stringResource(R.string.docs_category_insurance)
        DocumentCategory.TECHNICAL_INSPECTION -> stringResource(R.string.docs_category_ct)
        DocumentCategory.MAINTENANCE -> stringResource(R.string.docs_category_maintenance)
        DocumentCategory.FUEL -> stringResource(R.string.docs_category_fuel)
        DocumentCategory.PHOTOS -> stringResource(R.string.docs_category_photos)
        DocumentCategory.CLAIMS -> stringResource(R.string.docs_category_claims)
        DocumentCategory.OTHER -> stringResource(R.string.docs_category_other)
        DocumentCategory.REPORTS -> stringResource(R.string.docs_category_reports)
    }
}

private fun getCategoryIcon(category: DocumentCategory): ImageVector {
    return when(category) {
        DocumentCategory.ADMINISTRATIVE -> Icons.AutoMirrored.Filled.Assignment
        DocumentCategory.INSURANCE -> Icons.Default.Shield
        DocumentCategory.TECHNICAL_INSPECTION -> Icons.AutoMirrored.Filled.FactCheck
        DocumentCategory.MAINTENANCE -> Icons.Default.Build
        DocumentCategory.FUEL -> Icons.Default.LocalGasStation
        DocumentCategory.PHOTOS -> Icons.Default.CameraAlt
        DocumentCategory.CLAIMS -> Icons.Default.ReportProblem
        DocumentCategory.OTHER -> Icons.Default.Category
        DocumentCategory.REPORTS -> Icons.Default.PictureAsPdf
    }
}

private fun openFile(context: Context, document: Document) {
    try {
        val file = File(document.filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: getMimeType(document.filePath))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Ouvrir avec"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getMimeType(filePath: String): String {
    return when {
        filePath.endsWith(".pdf", true) -> "application/pdf"
        filePath.endsWith(".jpg", true) || filePath.endsWith(".jpeg", true) -> "image/jpeg"
        filePath.endsWith(".png", true) -> "image/png"
        else -> "*/*"
    }
}

@Composable
private fun DocumentItem(
    document: Document,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onConvertToPdf: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val isPdf = document.filePath.endsWith(".pdf", true)

    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        shape = CarManagerShapes.card,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        if (isPdf) {
            Column(Modifier.fillMaxWidth().heightIn(min = 96.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Description, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("PDF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AsyncImage(model = File(document.filePath), contentDescription = document.title,
                modifier = Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(document.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(DateFormatter.formatShort(document.date), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (document.category == DocumentCategory.REPORTS) {
                    TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ouvrir") }
                    TextButton(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp)) { Text("Enregistrer une copie") }
                    TextButton(onClick = onShare, modifier = Modifier.heightIn(min = 48.dp)) { Text("Partager") }
                }
                if (!isPdf) {
                    IconButton(onClick = onConvertToPdf, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Default.PictureAsPdf, stringResource(R.string.docs_convert_pdf),
                            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.docs_delete),
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AddDocumentDialog(
    initialCategory: DocumentCategory,
    onDismiss: () -> Unit,
    onConfirm: (String, DocumentCategory) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.docs_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.docs_label_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CarManagerShapes.control,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                
                Text(stringResource(R.string.docs_label_category), style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    DocumentCategory.entries.filterNot { it == DocumentCategory.REPORTS }.forEach { category ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = selectedCategory == category, role = Role.RadioButton,
                                onClick = { selectedCategory = category })
                        ) {
                            RadioButton(
                                selected = selectedCategory == category,
                                onClick = null
                            )
                            Text(getCategoryName(category))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title, selectedCategory) },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
