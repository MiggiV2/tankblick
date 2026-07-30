package de.mymiggi.tankblick.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.ui.AppViewModelProvider
import de.mymiggi.tankblick.ui.theme.TankblickTheme

private const val ONBOARDING_URL = "https://onboarding.tankerkoenig.de/"

/**
 * Asked for once, before anything else can work: Tankerkönig issues keys per
 * person, and a key baked into the APK would be extractable anyway.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    OnboardingContent(
        input = uiState.input,
        showInvalidKeyError = uiState.showInvalidKeyError,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::onSubmit,
        onUseDemoKey = viewModel::onUseDemoKey,
        onOpenRegistration = {
            context.startActivity(Intent(Intent.ACTION_VIEW, ONBOARDING_URL.toUri()))
        },
        modifier = modifier,
    )
}

@Composable
private fun OnboardingContent(
    input: String,
    showInvalidKeyError: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onUseDemoKey: () -> Unit,
    onOpenRegistration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_why),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_steps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onOpenRegistration,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_open_registration))
        }

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(stringResource(R.string.onboarding_key_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_key_placeholder)) },
            supportingText = {
                Text(
                    if (showInvalidKeyError) {
                        stringResource(R.string.onboarding_key_invalid)
                    } else {
                        stringResource(R.string.onboarding_key_hint)
                    },
                )
            },
            isError = showInvalidKeyError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                onSubmit()
            }),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                keyboard?.hide()
                onSubmit()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_save))
        }

        TextButton(
            onClick = onUseDemoKey,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_use_demo_key))
        }

        Text(
            text = stringResource(R.string.onboarding_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.attribution_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingContentPreview() {
    TankblickTheme(dynamicColor = false) {
        OnboardingContent(
            input = "",
            showInvalidKeyError = false,
            onInputChange = {},
            onSubmit = {},
            onUseDemoKey = {},
            onOpenRegistration = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingContentErrorPreview() {
    TankblickTheme(dynamicColor = false) {
        OnboardingContent(
            input = "kein-key",
            showInvalidKeyError = true,
            onInputChange = {},
            onSubmit = {},
            onUseDemoKey = {},
            onOpenRegistration = {},
        )
    }
}
