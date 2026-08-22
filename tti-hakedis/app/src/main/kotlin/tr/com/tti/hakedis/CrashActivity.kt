package tr.com.tti.hakedis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tr.com.tti.hakedis.designsystem.HakedisTheme
import tr.com.tti.hakedis.designsystem.Spacing

/**
 * Yakalanmamış bir hata sonrası gösterilen kullanıcı dostu ekran (Faz 9).
 * Teknik ayrıntı `Log.e` ile logcat'e yazılır; kullanıcıya sadece ne olduğu ve
 * verisinin kaybolmadığı söylenir (taslak otomatik kaydediliyor).
 *
 * Ayrı bir process'te çalışır (`android:process=":crash"`); asıl process çöktüğü için gereklidir.
 */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HakedisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
                    ) {
                        Text(
                            text = stringResource(R.string.crash_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.crash_message),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = { finishAndRemoveTask() },
                            modifier = Modifier.defaultMinSize(minHeight = Spacing.fieldHeight),
                        ) {
                            Text(text = stringResource(R.string.crash_close))
                        }
                    }
                }
            }
        }
    }
}
