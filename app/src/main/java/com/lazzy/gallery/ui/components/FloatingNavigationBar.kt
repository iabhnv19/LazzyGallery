package com.lazzy.gallery.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun Modifier.softDropShadow(
    color: Color = Color.Black.copy(alpha = 0.08f),
    borderRadius: Dp = 28.dp,
    blurRadius: Dp = 8.dp,
    offsetY: Dp = 1.dp
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = color.toArgb()
        if (blurRadius > 0.dp) {
            frameworkPaint.maskFilter = BlurMaskFilter(
                blurRadius.toPx(),
                BlurMaskFilter.Blur.NORMAL
            )
        }
        val radius = borderRadius.toPx()
        canvas.drawRoundRect(
            left = 0f,
            top = offsetY.toPx(),
            right = size.width,
            bottom = size.height + offsetY.toPx(),
            radiusX = radius,
            radiusY = radius,
            paint = paint
        )
    }
}

@Composable
fun FloatingNavigationBar(
    currentRoute: String,
    rightButtonAction: String = "favorites",
    onNavigate: (String) -> Unit,
    onRightButtonClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSearchSelected = currentRoute == "search"
    val isFavoritesSelected = currentRoute == "favorites" && rightButtonAction == "favorites"

    val colorAnimationSpec = spring<Color>(
        dampingRatio = 0.75f,
        stiffness = 320f
    )

    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

    val searchBackgroundColor by animateColorAsState(
        targetValue = if (isSearchSelected) MaterialTheme.colorScheme.primary else surfaceColor,
        animationSpec = colorAnimationSpec,
        label = "searchBackgroundColor"
    )
    val searchContentColor by animateColorAsState(
        targetValue = if (isSearchSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = colorAnimationSpec,
        label = "searchContentColor"
    )

    val favoritesBackgroundColor by animateColorAsState(
        targetValue = if (isFavoritesSelected) MaterialTheme.colorScheme.primary else surfaceColor,
        animationSpec = colorAnimationSpec,
        label = "favoritesBackgroundColor"
    )
    val favoritesContentColor by animateColorAsState(
        targetValue = if (isFavoritesSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = colorAnimationSpec,
        label = "favoritesContentColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .graphicsLayer { clip = false }
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer { clip = false }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.75f,
                        stiffness = 320f
                    )
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .softDropShadow(
                        color = Color.Black.copy(alpha = 0.08f),
                        borderRadius = 28.dp,
                        blurRadius = 8.dp,
                        offsetY = 1.dp
                    )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = searchBackgroundColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSearchSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigate("search") }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(24.dp),
                            tint = searchContentColor
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .height(56.dp)
                    .wrapContentWidth()
                    .softDropShadow(
                        color = Color.Black.copy(alpha = 0.08f),
                        borderRadius = 28.dp,
                        blurRadius = 8.dp,
                        offsetY = 1.dp
                    )
            ) {
                Surface(
                    modifier = Modifier.height(56.dp),
                    shape = CircleShape,
                    color = surfaceColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.75f,
                                    stiffness = 320f
                                )
                            ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavBarItem(
                            label = "Gallery",
                            icon = Icons.Outlined.Collections,
                            isSelected = currentRoute == "recent",
                            onClick = { onNavigate("recent") }
                        )
                        NavBarItem(
                            label = "Albums",
                            icon = Icons.Outlined.PhotoLibrary,
                            isSelected = currentRoute == "albums",
                            onClick = { onNavigate("albums") }
                        )
                        NavBarItem(
                            label = "Trash",
                            icon = Icons.Outlined.Delete,
                            isSelected = currentRoute == "bin",
                            onClick = { onNavigate("bin") }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .softDropShadow(
                        color = Color.Black.copy(alpha = 0.08f),
                        borderRadius = 28.dp,
                        blurRadius = 8.dp,
                        offsetY = 1.dp
                    )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = favoritesBackgroundColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isFavoritesSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (rightButtonAction == "favorites") {
                                        onNavigate("favorites")
                                    } else {
                                        onRightButtonClick()
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (rightButtonAction) {
                            "camera" -> Icons.Default.CameraAlt
                            "file_manager" -> Icons.Default.Folder
                            else -> if (isFavoritesSelected) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder
                        }
                        val desc = when (rightButtonAction) {
                            "camera" -> "Camera"
                            "file_manager" -> "File Manager"
                            else -> "Favorites"
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = desc,
                            modifier = Modifier.size(24.dp),
                            tint = favoritesContentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBarItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorAnimationSpec = spring<Color>(
        dampingRatio = 0.75f,
        stiffness = 320f
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = colorAnimationSpec,
        label = "backgroundColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )

    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = 320f
                )
            )
            .padding(horizontal = if (isSelected) 18.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isSelected,
            transitionSpec = {
                (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(100)) + scaleOut(targetScale = 0.8f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f))
                    )
            },
            label = "tabContent"
        ) { selected ->
            if (selected) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor
                )
            }
        }
    }
}
