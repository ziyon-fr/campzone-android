package fr.ziyon.campzone.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Program
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ProgramDetailScreen(
    viewModel: ScheduleViewModel,
    campingId: String,
    programId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId) }

    val program = viewModel.program(programId)
    ProgramDetailContent(
        program = program,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramDetailContent(
    program: Program?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = program?.title ?: "Program",
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                ),
                windowInsets = WindowInsets()
            )
        },
    ) { innerPadding ->
        if (program == null) {
            CzEmptyState(
                title = "Program not found",
                message = "The selected schedule program could not be loaded.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CzSpacing.xl),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = CzSpacing.base,
                    end = CzSpacing.base,
                    top = innerPadding.calculateTopPadding() + CzSpacing.md,
                    bottom = CzSpacing.base,
                ),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
            ) {
                item { ProgramHeader(program = program) }
                item { DetailsSection(program = program) }
                if (program.description.isNotBlank()) {
                    item { AboutSection(description = program.description) }
                }
            }
        }
    }
}

@Composable
private fun ProgramHeader(program: Program) {
    val accent = program.type.accentColor
    Row(
        modifier = Modifier.padding(top = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(accent, accent.copy(alpha = 0.6f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = program.type.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Surface(
            color = accent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(CzRadius.full),
        ) {
            Text(
                text = program.type.displayName,
                style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 3.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailsSection(program: Program) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        ProgramSectionHeader(title = "Details", icon = Icons.Rounded.Schedule)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(CzRadius.xl),
        ) {
            Column(modifier = Modifier.padding(vertical = CzSpacing.sm)) {
                ProgramInfoRow(
                    label = "Start",
                    value = fullDateTimeText(program.startDate),
                    icon = Icons.Rounded.Schedule,
                )
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = CzSpacing.base))
                ProgramInfoRow(
                    label = "End",
                    value = fullDateTimeText(program.endDate),
                    icon = Icons.Rounded.Schedule,
                )
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = CzSpacing.base))
                ProgramInfoRow(
                    label = "Duration",
                    value = durationText(program.startDate, program.endDate),
                    icon = Icons.Rounded.Timer,
                )
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = CzSpacing.base))
                ProgramInfoRow(
                    label = "Location",
                    value = program.location,
                    icon = Icons.Rounded.LocationOn,
                )
            }
        }
    }
}

@Composable
private fun ProgramSectionHeader(title: String, icon: ImageVector) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(14.dp))
        Text(text = title, style = CzTypeScale.callout, color = colors.textSecondary)
    }
}

@Composable
private fun ProgramInfoRow(label: String, value: String, icon: ImageVector) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.base, vertical = CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(18.dp))
        Text(text = label, style = CzTypeScale.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(text = value, style = CzTypeScale.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
    }
}

@Composable
private fun AboutSection(description: String) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        ProgramSectionHeader(title = "About", icon = Icons.Rounded.Schedule)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(CzRadius.xl),
        ) {
            Text(
                text = description,
                style = CzTypeScale.body,
                color = colors.textSecondary,
                lineHeight = CzTypeScale.body.lineHeight,
                modifier = Modifier.padding(CzSpacing.base),
            )
        }
    }
}

private val fullDateTimeFormatter = SimpleDateFormat("EEEE, MMMM d · HH:mm", Locale.getDefault())

private fun fullDateTimeText(date: Date): String = fullDateTimeFormatter.format(date)

private fun durationText(start: Date, end: Date): String {
    val minutes = abs(end.time - start.time).toInt() / 60_000
    if (minutes <= 0) return "-"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgramDetailNotFoundPreview() {
    CampzoneTheme {
        ProgramDetailContent(program = null, onBack = {})
    }
}
