package com.carmanager.app.features.documents

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    val documents by viewModel.documents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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
            FloatingActionButton(
                onClick = { pickerLauncher.launch("*/*") },
                containerColor = VehicleColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (searchQuery.isNotEmpty()) {
            // Vue recherche globale
            DocumentGridView(
                documents = documents,
                onDelete = viewModel::deleteDocument,
                onOpen = { if (viewModel.canOpen(it)) openFile(context, it) },
                onConvertToPdf = viewModel::convertToPdf,
                modifier = Modifier.padding(padding)
            )
        } else if (selectedCategory == null) {
            FolderGridView(
                documents = documents,
                onCategoryClick = { selectedCategory = it },
                modifier = Modifier.padding(padding)
            )
        } else {
            val filteredDocs = documents.filter { it.category == selectedCategory }
            DocumentGridView(
                documents = filteredDocs,
                onDelete = viewModel::deleteDocument,
                onOpen = { if (viewModel.canOpen(it)) openFile(context, it) },
                onConvertToPdf = viewModel::convertToPdf,
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showAddDialog && selectedUri != null) {
        AddDocumentDialog(
            initialCategory = selectedCategory ?: DocumentCategory.PHOTOS,
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
    val categories = DocumentCategory.entries
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(categories) { category ->
            val count = documents.count { it.category == category }
            FolderItem(
                category = category,
                count = count,
                onClick = { onCategoryClick(category) }
            )
        }
    }
}

@Composable
private fun FolderItem(
    category: DocumentCategory,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val icon = getCategoryIcon(category)
            val color = getCategoryColor(category)
            
            Surface(
                color = color.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = getCategoryName(category),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "$count document(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DocumentGridView(
    documents: List<Document>,
    onDelete: (Document) -> Unit,
    onOpen: (Document) -> Unit,
    onConvertToPdf: (Document) -> Unit,
    modifier: Modifier = Modifier
) {
    if (documents.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("Dossier vide", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(documents, key = { it.id }) { doc ->
                DocumentItem(
                    document = doc,
                    onDelete = { onDelete(doc) },
                    onOpen = { onOpen(doc) },
                    onConvertToPdf = { onConvertToPdf(doc) }
                )
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
    }
}

@Composable
private fun getCategoryColor(category: DocumentCategory): Color {
    val isDark = isSystemInDarkTheme()
    return when(category) {
        DocumentCategory.ADMINISTRATIVE -> if (isDark) AdminColorDark else AdminColor
        DocumentCategory.INSURANCE -> SuccessGreen
        DocumentCategory.TECHNICAL_INSPECTION -> if (isDark) MaintenanceColorDark else MaintenanceColor
        DocumentCategory.MAINTENANCE -> if (isDark) MaintenanceColorDark else MaintenanceColor
        DocumentCategory.FUEL -> if (isDark) FuelColorDark else FuelColor
        DocumentCategory.PHOTOS -> if (isDark) VehicleColorDark else VehicleColor
        DocumentCategory.CLAIMS -> ErrorRed
        DocumentCategory.OTHER -> Color.Gray
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
    onConvertToPdf: () -> Unit
) {
    val isPdf = document.filePath.endsWith(".pdf", true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clickable { onOpen() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(modifier = Modifier.weight(1f)) {
                if (isPdf) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = AdminColor
                        )
                        Text(
                            text = "PDF",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            fontWeight = FontWeight.Bold,
                            color = AdminColor
                        )
                    }
                } else {
                    AsyncImage(
                        model = File(document.filePath),
                        contentDescription = document.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            MaterialTheme.shapes.small
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isPdf) {
                        IconButton(onClick = onConvertToPdf) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = stringResource(R.string.docs_convert_pdf),
                                tint = AdminColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.docs_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(document.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
                Text(
                    DateFormatter.formatShort(document.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.docs_label_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(stringResource(R.string.docs_label_category), style = MaterialTheme.typography.labelLarge)
                Column {
                    DocumentCategory.entries.forEach { category ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selectedCategory = category }
                        ) {
                            RadioButton(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category }
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
