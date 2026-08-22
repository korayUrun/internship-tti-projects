package tr.com.tti.hakedis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import tr.com.tti.hakedis.designsystem.HakedisTheme
import tr.com.tti.hakedis.navigation.HakedisNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HakedisApp).container

        setContent {
            HakedisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HakedisNavHost(container = container)
                }
            }
        }
    }
}
