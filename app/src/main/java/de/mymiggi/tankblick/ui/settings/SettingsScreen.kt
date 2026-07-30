package de.mymiggi.tankblick.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.mymiggi.tankblick.BuildConfig
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.data.prefs.Settings
import de.mymiggi.tankblick.navapp.NavApp
import de.mymiggi.tankblick.ui.theme.TankblickTheme
import kotlinx.coroutines.launch

private const val TANKERKOENIG_URL = "https://creativecommons.tankerkoenig.de/"
private const val LICENCE_URL = "https://creativecommons.org/licenses/by/4.0/deed.de"

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onRadiusChange: (Int) -> Unit,
    onNavAppChange: (String?) -> Unit,
    onReplaceApiKey: suspend (String) -> Boolean,
    onForgetApiKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadiusSection(radiusKm = uiState.settings.radiusKm, onRadiusChange = onRadiusChange)
        HorizontalDivider()
        NavAppSection(
            navApps = uiState.navApps,
            selected = uiState.settings.navAppPackage,
            onNavAppChange = onNavAppChange,
        )
        HorizontalDivider()
        ApiKeySection(
            maskedApiKey = uiState.maskedApiKey,
            isDemoKey = uiState.isDemoKey,
            onReplaceApiKey = onReplaceApiKey,
            onForgetApiKey = onForgetApiKey,
        )
        HorizontalDivider()
        AboutSection(
            onOpenTankerkoenig = {
                context.startActivity(Intent(Intent.ACTION_VIEW, TANKERKOENIG_URL.toUri()))
            },
            onOpenLicence = {
                context.startActivity(Intent(Intent.ACTION_VIEW, LICENCE_URL.toUri()))
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RadiusSection(radiusKm: Int, onRadiusChange: (Int) -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.settings_radius))
        Text(
            text = pluralStringResource(R.plurals.settings_radius_value, radiusKm, radiusKm),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Slider(
            value = radiusKm.toFloat(),
            onValueChange = { onRadiusChange(it.toInt()) },
            valueRange = Settings.MIN_RADIUS_KM.toFloat()..Settings.MAX_RADIUS_KM.toFloat(),
            steps = Settings.MAX_RADIUS_KM - Settings.MIN_RADIUS_KM - 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = stringResource(R.string.settings_radius_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun NavAppSection(
    navApps: List<NavApp>,
    selected: String?,
    onNavAppChange: (String?) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.settings_nav_app))

        NavAppOption(
            label = stringResource(R.string.settings_nav_app_ask),
            selected = selected == null,
            onSelect = { onNavAppChange(null) },
        )
        navApps.forEach { app ->
            NavAppOption(
                label = app.label,
                selected = selected == app.packageName,
                onSelect = { onNavAppChange(app.packageName) },
            )
        }

        if (navApps.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_nav_app_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun NavAppOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // On the row rather than the button, so the whole line is a target
            // and a screen reader announces it once, not twice.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun ApiKeySection(
    maskedApiKey: String?,
    isDemoKey: Boolean,
    onReplaceApiKey: suspend (String) -> Boolean,
    onForgetApiKey: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Column {
        SectionTitle(stringResource(R.string.settings_api_key))
        Text(
            text = maskedApiKey ?: stringResource(R.string.settings_api_key_none),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (isDemoKey) {
            Text(
                text = stringResource(R.string.settings_api_key_demo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { showDialog = true }) {
                Text(stringResource(R.string.settings_api_key_replace))
            }
            TextButton(onClick = onForgetApiKey) {
                Text(stringResource(R.string.settings_api_key_forget))
            }
        }
    }

    if (showDialog) {
        ReplaceKeyDialog(
            onReplaceApiKey = onReplaceApiKey,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun ReplaceKeyDialog(
    onReplaceApiKey: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var isInvalid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_api_key_replace)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    isInvalid = false
                },
                label = { Text(stringResource(R.string.onboarding_key_label)) },
                supportingText = {
                    if (isInvalid) Text(stringResource(R.string.onboarding_key_invalid))
                },
                isError = isInvalid,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (onReplaceApiKey(input)) onDismiss() else isInvalid = true
                }
            }) {
                Text(stringResource(R.string.onboarding_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AboutSection(onOpenTankerkoenig: () -> Unit, onOpenLicence: () -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.settings_about))

        Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_licence),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onOpenTankerkoenig) {
                Text(stringResource(R.string.settings_open_tankerkoenig))
            }
            TextButton(onClick = onOpenLicence) {
                Text(stringResource(R.string.settings_open_licence))
            }
        }
        Text(
            text = stringResource(R.string.attribution_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    TankblickTheme(dynamicColor = false) {
        SettingsScreen(
            uiState = SettingsUiState(
                settings = Settings(radiusKm = 8),
                maskedApiKey = "d4f1a2b3-…-abcdefabcdef",
                navApps = listOf(
                    NavApp("app.organicmaps", "Organic Maps"),
                    NavApp("net.osmand", "OsmAnd"),
                ),
            ),
            onRadiusChange = {},
            onNavAppChange = {},
            onReplaceApiKey = { true },
            onForgetApiKey = {},
        )
    }
}
