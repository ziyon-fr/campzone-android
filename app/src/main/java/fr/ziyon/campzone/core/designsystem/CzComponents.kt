package fr.ziyon.campzone.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale

enum class CzButtonVariant {
    Primary,
    Secondary,
    Outline,
    Ghost,
    Destructive,
}

@Composable
fun CzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: CzButtonVariant = CzButtonVariant.Primary,
    loading: Boolean = false,
    contentDescription: String? = text,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.czColors
    val buttonModifier = modifier
        .defaultMinSize(minHeight = CzSpacing.minTouchTarget)
        .czContentDescription(contentDescription, Role.Button)
    val shape = RoundedCornerShape(CzRadius.full)
    val contentPadding = PaddingValues(
        horizontal = CzSpacing.lg,
        vertical = CzSpacing.md,
    )
    val isEnabled = enabled && !loading
    val content: @Composable RowScope.() -> Unit = {
        CzButtonContent(
            text = text,
            loading = loading,
            leadingIcon = leadingIcon,
        )
    }

    when (variant) {
        CzButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.surface,
                disabledContentColor = colors.textSecondary,
            ),
            contentPadding = contentPadding,
            content = content,
        )

        CzButtonVariant.Secondary -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.secondary,
                contentColor = colors.onSecondary,
                disabledContainerColor = colors.surface,
                disabledContentColor = colors.textSecondary,
            ),
            contentPadding = contentPadding,
            content = content,
        )

        CzButtonVariant.Outline -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colors.primary,
                disabledContentColor = colors.textSecondary,
            ),
            border = BorderStroke(1.dp, colors.divider),
            contentPadding = contentPadding,
            content = content,
        )

        CzButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = colors.primary,
                disabledContentColor = colors.textSecondary,
            ),
            contentPadding = contentPadding,
            content = content,
        )

        CzButtonVariant.Destructive -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.error,
                contentColor = colors.onError,
                disabledContainerColor = colors.surface,
                disabledContentColor = colors.textSecondary,
            ),
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
fun CzCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(CzSpacing.base),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.czColors
    val cardModifier = modifier
        .then(if (onClick != null) Modifier.defaultMinSize(minHeight = CzSpacing.minTouchTarget) else Modifier)
        .czContentDescription(contentDescription, if (onClick != null) Role.Button else null)
    val shape = RoundedCornerShape(CzRadius.lg)
    val cardColors = CardDefaults.cardColors(
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    )
    val border = BorderStroke(1.dp, colors.divider)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = cardColors,
            border = border,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = cardColors,
            border = border,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}

@Composable
fun CzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    contentDescription: String? = label,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.czColors

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .defaultMinSize(minHeight = CzSpacing.minTouchTarget)
            .czContentDescription(contentDescription),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        isError = isError,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(CzRadius.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            disabledTextColor = colors.textSecondary,
            errorTextColor = colors.textPrimary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = colors.surface,
            errorContainerColor = Color.Transparent,
            cursorColor = colors.primary,
            errorCursorColor = colors.error,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.divider,
            disabledBorderColor = colors.divider,
            errorBorderColor = colors.error,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.textSecondary,
            disabledLabelColor = colors.textSecondary,
            errorLabelColor = colors.error,
            focusedPlaceholderColor = colors.textSecondary,
            unfocusedPlaceholderColor = colors.textSecondary,
            disabledPlaceholderColor = colors.textSecondary,
            errorPlaceholderColor = colors.textSecondary,
            focusedSupportingTextColor = colors.textSecondary,
            unfocusedSupportingTextColor = colors.textSecondary,
            disabledSupportingTextColor = colors.textSecondary,
            errorSupportingTextColor = colors.error,
        ),
    )
}

enum class CzBadgeTone {
    Neutral,
    Primary,
    Secondary,
    Success,
    Warning,
    Error,
}

@Composable
fun CzBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: CzBadgeTone = CzBadgeTone.Neutral,
) {
    val colors = MaterialTheme.czColors
    val (containerColor, contentColor) = when (tone) {
        CzBadgeTone.Neutral -> colors.surface to colors.textSecondary
        CzBadgeTone.Primary -> colors.primary.copy(alpha = 0.14f) to colors.primary
        CzBadgeTone.Secondary -> colors.secondary.copy(alpha = 0.14f) to colors.secondary
        CzBadgeTone.Success -> colors.success.copy(alpha = 0.14f) to colors.success
        CzBadgeTone.Warning -> colors.warning.copy(alpha = 0.14f) to colors.warning
        CzBadgeTone.Error -> colors.error.copy(alpha = 0.14f) to colors.error
    }

    Surface(
        modifier = modifier.heightIn(min = CzSpacing.xl),
        shape = RoundedCornerShape(CzRadius.full),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun CzEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    CzStateBlock(
        title = title,
        modifier = modifier,
        message = message,
        icon = icon,
        action = action,
    )
}

@Composable
fun CzErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Retry",
) {
    CzStateBlock(
        title = title,
        modifier = modifier,
        message = message,
        icon = {
            CzBadge(
                text = "!",
                tone = CzBadgeTone.Error,
            )
        },
        action = onRetry?.let {
            {
                CzButton(
                    text = retryLabel,
                    onClick = it,
                    variant = CzButtonVariant.Outline,
                    contentDescription = retryLabel,
                )
            }
        },
    )
}

@Composable
fun CzLoadingView(
    modifier: Modifier = Modifier,
    message: String? = null,
    contentDescription: String = "Loading",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CzSpacing.xl)
            .czContentDescription(contentDescription),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(CzSpacing.xl),
            color = MaterialTheme.czColors.primary,
            strokeWidth = 2.dp,
        )
        if (message != null) {
            Text(
                text = message,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

enum class CzAvatarSize(val value: Dp) {
    Small(32.dp),
    Medium(48.dp),
    Large(64.dp),
}

@Composable
fun CzAvatar(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    initials: String? = null,
    size: CzAvatarSize = CzAvatarSize.Medium,
) {
    val colors = MaterialTheme.czColors
    val safeInitials = initials
        ?.trim()
        ?.take(2)
        ?.uppercase(Locale.getDefault())
        .takeUnless { it.isNullOrBlank() }
        ?: "?"

    Box(
        modifier = modifier
            .size(size.value)
            .clip(CircleShape)
            .background(colors.secondary.copy(alpha = 0.18f))
            .czContentDescription(contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.size(size.value),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = safeInitials,
                color = colors.secondary,
                style = when (size) {
                    CzAvatarSize.Small -> MaterialTheme.typography.labelLarge
                    CzAvatarSize.Medium -> MaterialTheme.typography.titleSmall
                    CzAvatarSize.Large -> MaterialTheme.typography.titleMedium
                },
            )
        }
    }
}

@Composable
fun CzSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    actionContentDescription: String? = actionLabel,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Text(
                text = title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (onActionClick != null && actionLabel != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier
                    .defaultMinSize(minHeight = CzSpacing.minTouchTarget)
                    .czContentDescription(actionContentDescription, Role.Button),
                shape = RoundedCornerShape(CzRadius.full),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.czColors.primary,
                    disabledContentColor = MaterialTheme.czColors.textSecondary,
                ),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun CzButtonContent(
    text: String,
    loading: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(CzSpacing.sm))
    } else if (leadingIcon != null) {
        leadingIcon()
        Spacer(modifier = Modifier.width(CzSpacing.sm))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun CzStateBlock(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        if (icon != null) {
            icon()
        }
        Text(
            text = title,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            action()
        }
    }
}

private fun Modifier.czContentDescription(
    description: String?,
    semanticRole: Role? = null,
): Modifier = if (description == null && semanticRole == null) {
    this
} else {
    semantics {
        if (description != null) {
            contentDescription = description
        }
        if (semanticRole != null) {
            role = semanticRole
        }
    }
}
