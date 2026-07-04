package fr.ziyon.campzone.ui.venuemap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.VenueCategory
import fr.ziyon.campzone.data.model.VenueIconCatalog
import fr.ziyon.campzone.data.model.VenueMap
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.pointsOnIllustration

/** Material glyph for a pin category (mirrors the iOS SF Symbols mapping). */
val VenueCategory.icon: ImageVector
    get() = when (this) {
        VenueCategory.Tent -> Icons.Filled.Festival
        VenueCategory.Stage -> Icons.Filled.Mic
        VenueCategory.Dining -> Icons.Filled.Restaurant
        VenueCategory.FirstAid -> Icons.Filled.MedicalServices
        VenueCategory.Restroom -> Icons.Filled.Wc
        VenueCategory.Parking -> Icons.Filled.LocalParking
        VenueCategory.Water -> Icons.Filled.WaterDrop
        VenueCategory.Program -> Icons.Filled.CalendarMonth
        VenueCategory.Info -> Icons.Filled.Info
        VenueCategory.Other -> Icons.Filled.Place
        VenueCategory.Custom -> Icons.Filled.Place
    }

val sfSymbolToMaterialIcon: Map<String, ImageVector> = VenueIconCatalog.allIconNames
    .associateWith { icon ->
        when (icon) {
            "tent.fill" -> Icons.Filled.Festival
            "music.mic", "guitars.fill", "theatermasks.fill" -> Icons.Filled.Mic
            "fork.knife", "cup.and.saucer.fill", "cart.fill" -> Icons.Filled.Restaurant
            "cross.case.fill", "staroflife.fill" -> Icons.Filled.MedicalServices
            "toilet.fill", "shower.fill" -> Icons.Filled.Wc
            "parkingsign", "car.fill", "bus.fill" -> Icons.Filled.LocalParking
            "drop.fill", "spigot.fill" -> Icons.Filled.WaterDrop
            "calendar", "book.fill" -> Icons.Filled.CalendarMonth
            "info.circle.fill", "bell.fill", "exclamationmark.triangle.fill" -> Icons.Filled.Info
            else -> Icons.Filled.Place
        }
    } + mapOf(
    "mappin" to Icons.Filled.Place,
    VenueIconCatalog.defaultIconName to Icons.Filled.Place,
)

fun materialIconForSfSymbol(iconName: String?): ImageVector =
    sfSymbolToMaterialIcon[iconName] ?: Icons.Filled.Place

/** Semantic tint (first-aid is the safety red; the rest use the warm palette). */
val VenueCategory.tint: Color
    @Composable get() = when (this) {
        VenueCategory.FirstAid -> MaterialTheme.czColors.error
        VenueCategory.Water -> MaterialTheme.czColors.pine
        VenueCategory.Dining -> MaterialTheme.czColors.amber
        VenueCategory.Stage -> MaterialTheme.czColors.flame
        else -> MaterialTheme.czColors.ember
    }

val VenueCategory.labelRes: Int
    get() = when (this) {
        VenueCategory.Tent -> R.string.venue_category_tent
        VenueCategory.Stage -> R.string.venue_category_stage
        VenueCategory.Dining -> R.string.venue_category_dining
        VenueCategory.FirstAid -> R.string.venue_category_first_aid
        VenueCategory.Restroom -> R.string.venue_category_restroom
        VenueCategory.Parking -> R.string.venue_category_parking
        VenueCategory.Water -> R.string.venue_category_water
        VenueCategory.Program -> R.string.venue_category_program
        VenueCategory.Info -> R.string.venue_category_info
        VenueCategory.Other -> R.string.venue_category_other
        VenueCategory.Custom -> R.string.venue_category_custom
    }

/** The category-tinted circular marker used everywhere a pin renders. */
@Composable
fun VenuePinGlyph(
    category: VenueCategory,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    diameter: Dp = 30.dp,
    iconName: String? = null,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .background(category.tint, CircleShape)
            .border(if (selected) 3.dp else 2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconName?.let(::materialIconForSfSymbol) ?: category.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(diameter * 0.55f),
        )
    }
}

/** Pin glyph plus an optional name capsule below it. */
@Composable
fun VenuePinView(
    category: VenueCategory,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    label: String? = null,
    iconName: String? = null,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        VenuePinGlyph(category = category, selected = selected, iconName = iconName)
        if (!label.isNullOrEmpty()) {
            Surface(
                color = MaterialTheme.czColors.surface.copy(alpha = 0.92f),
                shape = CircleShape,
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = CzSpacing.xs, vertical = 1.dp),
                )
            }
        }
    }
}

/** Coil-loaded site illustration with a graceful placeholder. */
@Composable
fun VenueSiteImage(url: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.czColors.surface), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            VenueImagePlaceholder(stringResource(R.string.venue_site_image_placeholder))
        } else {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.venue_site_image_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun VenueImagePlaceholder(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.czColors.textSecondary,
            modifier = Modifier.size(32.dp),
        )
        Text(text, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The 4:3 site illustration with pins overlaid at their relative positions.
 * Used read-only by the viewer (`onPlaceAt == null`) and interactively by the
 * editor (tap to place when [isPlacing], or tap a pin to edit).
 */
@Composable
fun VenueImageCanvas(
    map: VenueMap,
    selectedPointId: String?,
    modifier: Modifier = Modifier,
    isPlacing: Boolean = false,
    onTapPin: (VenuePoint) -> Unit = {},
    onPlaceAt: ((Double, Double) -> Unit)? = null,
) {
    val borderColor = if (isPlacing) MaterialTheme.czColors.ember else MaterialTheme.czColors.divider
    var scale by remember(map.imageUrl, isPlacing) { mutableFloatStateOf(1f) }
    var offset by remember(map.imageUrl, isPlacing) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(CzRadius.xl))
            .border(if (isPlacing) 2.dp else 1.dp, borderColor, RoundedCornerShape(CzRadius.xl)),
    ) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight
        val resetZoom = {
            scale = 1f
            offset = Offset.Zero
        }
        fun clampOffset(proposed: Offset, targetScale: Float): Offset {
            val maxX = constraints.maxWidth * (targetScale - 1f) / 2f
            val maxY = constraints.maxHeight * (targetScale - 1f) / 2f
            return Offset(
                x = proposed.x.coerceIn(-maxX, maxX),
                y = proposed.y.coerceIn(-maxY, maxY),
            )
        }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
            scale = nextScale
            offset = if (nextScale <= 1.01f) {
                Offset.Zero
            } else {
                clampOffset(offset + panChange, nextScale)
            }
        }

        val tapModifier = if (isPlacing && onPlaceAt != null) {
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0 && size.height > 0) {
                        onPlaceAt(
                            (offset.x / size.width).toDouble().coerceIn(0.0, 1.0),
                            (offset.y / size.height).toDouble().coerceIn(0.0, 1.0),
                        )
                    }
                }
            }
        } else {
            Modifier
        }
        val zoomModifier = if (isPlacing) Modifier else Modifier.transformable(transformState)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .then(zoomModifier)
                .then(tapModifier),
        ) {
            VenueSiteImage(url = map.imageUrl, modifier = Modifier.fillMaxSize())

            val glyph = 30.dp
            for (point in map.pointsOnIllustration) {
                val x = (point.imageX ?: 0.5)
                val y = (point.imageY ?: 0.5)
                val selected = point.id == selectedPointId
                VenuePinView(
                    category = point.category,
                    contentDescription = point.name,
                    selected = selected,
                    label = if (selected) point.name else null,
                    iconName = point.resolvedIconName,
                    modifier = Modifier
                        .offset(
                            x = canvasWidth * x.toFloat() - glyph / 2,
                            y = canvasHeight * y.toFloat() - glyph / 2,
                        )
                        .graphicsLayer {
                            val inverse = 1f / scale
                            scaleX = inverse
                            scaleY = inverse
                        }
                        .then(
                            if (!isPlacing) Modifier.clickable { onTapPin(point) } else Modifier,
                        ),
                )
            }
        }

        if (!isPlacing && scale > 1.01f) {
            IconButton(
                onClick = resetZoom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(CzSpacing.md)
                    .clip(CircleShape)
                    .background(MaterialTheme.czColors.surface.compositeOver(MaterialTheme.czColors.background)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.venue_reset_zoom),
                    tint = MaterialTheme.czColors.textPrimary,
                )
            }
        }
    }
}

/** Bottom detail card for the tapped pin. */
@Composable
fun VenuePointDetailCard(
    point: VenuePoint,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    // `czColors.surface` is a translucent overlay; composite it over the opaque
    // background so this floating card never shows the map content through it.
    val cardColor = MaterialTheme.czColors.surface.compositeOver(MaterialTheme.czColors.background)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(CzRadius.xl),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VenuePinGlyph(category = point.category, diameter = 36.dp, iconName = point.resolvedIconName)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = point.name,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = point.displayCategoryLabel(),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.czColors.textSecondary,
                    )
                }
            }
            if (point.note.isNotBlank()) {
                Text(
                    text = point.note,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            footer?.invoke()
        }
    }
}

/** Tappable row in the "Locations" legend list. */
@Composable
fun VenueLegendRow(
    point: VenuePoint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VenuePinGlyph(category = point.category, diameter = 28.dp, iconName = point.resolvedIconName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.name,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = point.displayCategoryLabel(),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun VenuePoint.displayCategoryLabel(): String =
    if (category == VenueCategory.Custom) {
        customCategoryName?.trim()?.takeUnless { it.isBlank() } ?: stringResource(category.labelRes)
    } else {
        stringResource(category.labelRes)
    }
