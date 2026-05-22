package fr.ziyon.campzone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampzoneTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    CampzonePlaceholder(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(CzSpacing.base),
                    )
                }
            }
        }
    }
}

@Composable
private fun CampzonePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Campzone",
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.displayLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CampzoneTheme {
        CampzonePlaceholder()
    }
}
