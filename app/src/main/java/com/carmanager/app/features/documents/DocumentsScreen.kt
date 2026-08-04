package com.carmanager.app.features.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.carmanager.app.core.ui.theme.AdminColor
import com.carmanager.app.core.ui.theme.VehicleColor
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
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
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.docs_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pickerLauncher.launch("*/*") },
                containerColor = VehicleColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null)
            }
        }
    ) { padding ->
        if (documents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.docs_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                var selectedTab by remember { mutableIntStateOf(0) }
                val categories = listOf(
                    DocumentCategory.ADMINISTRATIVE to stringResource(R.string.docs_category_admin),
                    DocumentCategory.INSURANCE to stringResource(R.string.docs_category_insurance),
                    DocumentCategory.TECHNICAL_INSPECTION to stringResource(R.string.docs_category_ct),
                    DocumentCategory.MAINTENANCE to stringResource(R.string.docs_category_maintenance),
                    DocumentCategory.FUEL to stringResource(R.string.docs_category_fuel),
                    DocumentCategory.PHOTOS to stringResource(R.string.docs_category_photos),
                    DocumentCategory.OTHER to stringResource(R.string.docs_category_other)
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    categories.forEachIndexed { index, pair ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(pair.second) }
                        )
                    }
                }

                val filteredDocs = documents.filter { it.category == categories[selectedTab].first }

                if (filteredDocs.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.dashboard_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DocumentItem(
                                document = doc,
                                onDelete = { viewModel.deleteDocument(doc) },
                                onOpen = { openFile(context, doc) },
                                onConvertToPdf = { viewModel.convertToPdf(doc) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && selectedUri != null) {
        AddDocumentDialog(
            onDismiss = { 
                showAddDialog = false
                selectedUri = null
            },
            onConfirm = { title, category ->
                viewModel.addDocument(selectedUri!!, title, category)
                showAddDialog = false
                selectedUri = null
            }
        )
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
    onDismiss: () -> Unit,
    onConfirm: (String, DocumentCategory) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DocumentCategory.PHOTOS) }

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
                            Text(
                                when(category) {
                                    DocumentCategory.ADMINISTRATIVE -> stringResource(R.string.docs_category_admin)
                                    DocumentCategory.INSURANCE -> stringResource(R.string.docs_category_insurance)
                                    DocumentCategory.TECHNICAL_INSPECTION -> stringResource(R.string.docs_category_ct)
                                    DocumentCategory.MAINTENANCE -> stringResource(R.string.docs_category_maintenance)
                                    DocumentCategory.FUEL -> stringResource(R.string.docs_category_fuel)
                                    DocumentCategory.PHOTOS -> stringResource(R.string.docs_category_photos)
                                    DocumentCategory.OTHER -> stringResource(R.string.docs_category_other)
                                }
                            )
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
                Text(stringResource(R.string.vehicle_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
