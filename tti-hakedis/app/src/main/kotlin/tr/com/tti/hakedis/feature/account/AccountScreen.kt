package tr.com.tti.hakedis.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.data.api.UserRole
import tr.com.tti.hakedis.designsystem.FieldColumns
import tr.com.tti.hakedis.designsystem.FormContainer
import tr.com.tti.hakedis.designsystem.LabelValue
import tr.com.tti.hakedis.designsystem.SectionCard
import tr.com.tti.hakedis.designsystem.Spacing
import tr.com.tti.hakedis.designsystem.appTopBarColors

/**
 * Hesap sekmesi — kim, hangi sunucu, çıkış.
 *
 * Bu üçü eskiden iş listesinin üst şeridine sıkışmıştı (ad soyad düz metin + "Çıkış" metin düğmesi).
 * Sunucu adresi burada yalnızca **okunur**: 19 Ağustos 2026'da adres derleme zamanında sabitlendi,
 * düzenleme alanı ve "Kaydet" düğmesi kalktı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(uiState: AccountUiState, onEvent: (AccountEvent) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.account_title)) }, colors = appTopBarColors()) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        ) {
            FormContainer {
                uiState.user?.let { user ->
                    SectionCard(title = stringResource(R.string.account_user_section)) {
                        // Kullanıcı bilgisi tabletde iki sütun; dar ekranda alt alta akar (§8).
                        FieldColumns(
                            left = {
                                LabelValue(label = stringResource(R.string.account_name), value = user.adSoyad)
                                LabelValue(label = stringResource(R.string.account_user_name), value = user.kullaniciAdi)
                            },
                            right = {
                                LabelValue(
                                    label = stringResource(R.string.account_role),
                                    value = stringResource(
                                        if (user.rol == UserRole.YONETICI) R.string.account_role_manager else R.string.account_role_officer,
                                    ),
                                )
                                LabelValue(
                                    label = stringResource(R.string.account_region),
                                    // Ad varsa ad yazılır (web ile aynı); eski bir sunucu `bolgeAdi`
                                    // göndermiyorsa koda düşülür. Yöneticide ikisi de `null` ve bu
                                    // "tüm bölgeler" demektir, "bölgesiz" değil.
                                    value = user.bolgeAdi
                                        ?: user.bolgeKodu
                                        ?: stringResource(R.string.account_region_all),
                                )
                            },
                        )
                    }
                }

                // Adres **salt okunur**: derleme zamanında sabit (19 Ağustos 2026). Gösterilmeye
                // devam ediyor çünkü "hangi sunucuya bağlısınız" bir destek çağrısında ilk sorulan
                // şey; değiştirme alanı ve "Kaydet" düğmesi kalktı.
                SectionCard(title = stringResource(R.string.account_server_section)) {
                    LabelValue(
                        label = stringResource(R.string.login_server_address),
                        value = uiState.serverAddress,
                    )
                }

                Button(
                    onClick = { onEvent(AccountEvent.LogoutAsked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Spacing.minTouchTarget),
                ) {
                    Text(text = stringResource(R.string.action_logout))
                }
            }
        }
    }

    if (uiState.logoutAsked) {
        AlertDialog(
            onDismissRequest = { onEvent(AccountEvent.LogoutDismissed) },
            title = { Text(text = stringResource(R.string.account_logout_title)) },
            text = { Text(text = stringResource(R.string.account_logout_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(AccountEvent.LogoutConfirmed) }) {
                    Text(text = stringResource(R.string.account_logout_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(AccountEvent.LogoutDismissed) }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
