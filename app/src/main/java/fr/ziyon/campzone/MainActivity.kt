package fr.ziyon.campzone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.navigation.DeepLinkInbox
import fr.ziyon.campzone.core.navigation.toCampzoneDeepLink
import fr.ziyon.campzone.ui.auth.AuthGate

@AndroidEntryPoint
class MainActivity : ComponentActivity() {  
    private val deepLinkInbox = DeepLinkInbox()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkInbox.offer(intent.toCampzoneDeepLink())
        enableEdgeToEdge()
        setContent {
            CampzoneTheme {
                AuthGate(
                    deepLinkInbox = deepLinkInbox,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkInbox.offer(intent.toCampzoneDeepLink())
    }
}
