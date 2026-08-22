package tr.com.tti.hakedis.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.designsystem.ErrorBanner
import tr.com.tti.hakedis.designsystem.Spacing

/**
 * Giriş ekranı. Stateless: [uiState] okur, [onEvent] yayar.
 *
 * Ekranda **iki alan var**: kullanıcı adı ve parola. ~~Sunucu adresi alanı, "göster/gizle" düğmesinin
 * arkasında~~ kaldırıldı (19 Ağustos 2026) — adres derleme zamanında sabit.
 */
@Composable
fun LoginScreen(uiState: LoginUiState, onEvent: (LoginEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = FORM_MAX_WIDTH),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Marka logosu kartın üstünde, ortada — web giriş ekranıyla aynı düzen. Görsel saydam
            // zeminli, `ContentScale.Fit` ile oranı korunuyor.
            Image(
                painter = painterResource(R.drawable.tti_logo),
                contentDescription = stringResource(R.string.login_logo_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = LOGO_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
            )
            Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            uiState.error?.let { ErrorBanner(message = it) }

            OutlinedTextField(
                value = uiState.userName,
                onValueChange = { onEvent(LoginEvent.UserNameChanged(it)) },
                label = { Text(text = stringResource(R.string.login_user_name)) },
                singleLine = true,
                enabled = !uiState.busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.fieldHeight),
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
                label = { Text(text = stringResource(R.string.login_password)) },
                singleLine = true,
                enabled = !uiState.busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.fieldHeight),
            )

            // Sunucu adresi alanı **kaldırıldı** (19 Ağustos 2026): adres derleme zamanında sabit
            // (`res/values/config.xml`). Giriş yalnızca kullanıcı adı + parola.

            Button(
                onClick = { onEvent(LoginEvent.Submitted) },
                enabled = uiState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Spacing.minTouchTarget),
            ) {
                if (uiState.busy) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = PROGRESS_SIZE), strokeWidth = 2.dp)
                } else {
                    Text(text = stringResource(R.string.login_submit), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private val FORM_MAX_WIDTH = 480.dp

/** Logo formun tamamını kaplamasın: tablette 480 dp'lik forma göre orantılı bir baş görseldir. */
private val LOGO_MAX_WIDTH = 300.dp
private val PROGRESS_SIZE = 24.dp
