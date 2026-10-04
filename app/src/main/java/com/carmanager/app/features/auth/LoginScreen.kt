package com.carmanager.app.features.auth

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import com.carmanager.app.core.ui.components.SecondarySectionTitle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.util.UiEvent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private const val WEB_CLIENT_ID = "1013731819268-gaeksvqn9oacfvtgfe02uo26nnbuk45p.apps.googleusercontent.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    // Configuration Google Sign-In
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val token = account?.idToken
            if (token.isNullOrBlank()) viewModel.onGoogleSignInError("Connexion Google impossible. Réessayez.")
            else viewModel.onGoogleSignIn(token)
        } catch (e: ApiException) {
            viewModel.onGoogleSignInError("Connexion Google annulée ou impossible. Réessayez.")
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.Success -> onNavigateBack()
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(loginErrorMessage(event.message))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CarManagerBackAppBar(
                title = stringResource(R.string.login_title),
                onNavigateBack = onNavigateBack,
                backDescription = stringResource(R.string.cancel)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_car_manager_mark), contentDescription = null,
                    modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                SecondarySectionTitle("Car Manager", Modifier.weight(1f))
            }
            Text(
                text = "Suivez vos véhicules, vos dépenses et vos documents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    if (viewModel.isLoading) {
                        stateDescription = "Connexion en cours"
                        liveRegion = LiveRegionMode.Polite
                    }
                },
                enabled = !viewModel.isLoading,
                shape = CarManagerShapes.control,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp).clearAndSetSemantics {},
                        color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connexion en cours…", Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text(stringResource(R.string.login_google), Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            Text(stringResource(R.string.login_desc), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onNavigateToPrivacy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_privacy_policy), Modifier.weight(1f), textAlign = TextAlign.Start)
            }
        }
    }
}
