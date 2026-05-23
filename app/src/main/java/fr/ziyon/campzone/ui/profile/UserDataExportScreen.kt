package fr.ziyon.campzone.ui.profile

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.profile.UserDataExportResult
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun UserDataExportScreen(
    authenticatedUser: AuthenticatedUser,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserDataExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = MaterialTheme.czColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(CzSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onNavigateBack) {
                Text(stringResource(R.string.common_back))
            }
            Text(
                text = stringResource(R.string.profile_export_data_title),
                modifier = Modifier.weight(1f),
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineLarge,
            )
        }

        UserDataExportSection(
            title = stringResource(R.string.profile_export_intro_title),
            icon = "D",
            footer = stringResource(R.string.profile_export_intro_footer),
        ) {
            Text(
                text = stringResource(R.string.profile_export_intro_message),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        UserDataExportSection(
            title = stringResource(R.string.profile_export_action_title),
            icon = "D",
        ) {
            CzButton(
                text = if (uiState.exportResult == null) {
                    stringResource(R.string.profile_create_export)
                } else {
                    stringResource(R.string.profile_create_new_export)
                },
                onClick = { viewModel.exportData(authenticatedUser) },
                enabled = !uiState.isExporting,
                loading = uiState.isExporting,
                variant = CzButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        uiState.exportResult?.let { result ->
            UserDataExportResultSection(
                result = result,
                onShare = { shareExportFile(context, result.file) },
            )
        }

        uiState.exportError?.let { error ->
            UserDataExportSection(
                title = stringResource(R.string.profile_export_error_title),
                icon = "!",
            ) {
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun UserDataExportResultSection(
    result: UserDataExportResult,
    onShare: () -> Unit,
) {
    val recordText = stringResource(
        R.string.profile_export_ready_count,
        result.recordCount,
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(result.generatedAt),
    )
    UserDataExportSection(
        title = stringResource(R.string.profile_export_ready),
        icon = "Y",
    ) {
        Text(
            text = recordText,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (result.failureCount > 0) {
            Text(
                text = stringResource(R.string.profile_export_failure_count, result.failureCount),
                color = MaterialTheme.czColors.warning,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        CzButton(
            text = stringResource(R.string.profile_share_export_file),
            onClick = onShare,
            variant = CzButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UserDataExportSection(
    title: String,
    icon: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = icon,
                color = colors.primary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = title,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CzRadius.lg),
            color = colors.surface,
            contentColor = colors.textPrimary,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Column(
                modifier = Modifier.padding(CzSpacing.base),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                content = content,
            )
        }
        if (footer != null) {
            Text(
                text = footer,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun shareExportFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(
            intent,
            context.getString(R.string.profile_share_export_file),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun UserDataExportScreenPreview() {
    CampzoneTheme {
        UserDataExportSection(
            title = "Export Data",
            icon = "D",
        ) {
            Text(
                text = "Preview generated on ${Date()}",
                color = MaterialTheme.czColors.textSecondary,
            )
        }
    }
}
