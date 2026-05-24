package fr.ziyon.campzone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplaneTicket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.navigation.ScreenColumn

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.czColors.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically // Aligns icon and text vertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AirplaneTicket,
                contentDescription = null
            )
            // Top Slogan text
            Text(
                text = stringResource(id = R.string.home_slogan),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        ScreenColumn(modifier = Modifier) {
            Text(
                text = stringResource(id = R.string.home_slogan),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.displayLarge
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    HomeScreen()
}