package com.binay.vscroll.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.binay.vscroll.domain.model.ReactionType

/**
 * Social rail action button supporting single-tap liking, long-press emoji reaction selector,
 * and immediate visual feedback with smooth Compose scale animations.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReactionActionItem(
    currentReaction: ReactionType?,
    isLiked: Boolean,
    onReactionSelected: (ReactionType?) -> Unit,
    isSelectorOpen: Boolean,
    onSelectorOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val popScale = remember { Animatable(1f) }

    // Trigger subtle pop animation when reaction changes
    LaunchedEffect(currentReaction, isLiked) {
        popScale.animateTo(1.25f, animationSpec = tween(120, easing = FastOutSlowInEasing))
        popScale.animateTo(1f, animationSpec = tween(120, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        // Floating Horizontal Reaction Selector Bar (shown on long-press)
        AnimatedVisibility(
            visible = isSelectorOpen,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.6f,
                transformOrigin = TransformOrigin(1f, 0.5f),
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(150)) + scaleOut(
                targetScale = 0.6f,
                transformOrigin = TransformOrigin(1f, 0.5f),
                animationSpec = tween(180)
            ),
            modifier = Modifier.padding(end = 56.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReactionType.entries.forEach { reaction ->
                        val isSelected = (currentReaction == reaction)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Tapping same reaction removes it, otherwise sets it
                                    if (isSelected) {
                                        onReactionSelected(null)
                                    } else {
                                        onReactionSelected(reaction)
                                    }
                                    onSelectorOpenChange(false)
                                }
                        ) {
                            Text(
                                text = reaction.emoji,
                                fontSize = if (isSelected) 21.sp else 18.sp
                            )
                        }
                    }
                }
            }
        }

        // Like / Reaction Action Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(48.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isSelectorOpen) {
                            onSelectorOpenChange(false)
                        } else {
                            if (currentReaction != null || isLiked) {
                                onReactionSelected(null)
                            } else {
                                onReactionSelected(ReactionType.LOVE)
                            }
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectorOpenChange(!isSelectorOpen)
                    }
                )
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(
                    width = if (currentReaction != null || isLiked) 1.5.dp else 1.dp,
                    color = if (currentReaction != null || isLiked) Color(0xFFFF2A55).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .size(44.dp)
                    .scale(popScale.value)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (currentReaction != null) {
                        Text(
                            text = currentReaction.emoji,
                            fontSize = 22.sp
                        )
                    } else if (isLiked) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Liked",
                            tint = Color(0xFFFF2A55),
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val labelText = currentReaction?.label ?: if (isLiked) "Liked" else "Like"
            val labelColor = if (currentReaction != null || isLiked) Color(0xFFFF2A55) else Color.White

            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = if (currentReaction != null || isLiked) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Convenience overload preserving backward compatibility for callers not managing selector state.
 */
@Composable
fun ReactionActionItem(
    currentReaction: ReactionType?,
    isLiked: Boolean,
    onReactionSelected: (ReactionType?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSelectorOpen by remember { mutableStateOf(false) }
    ReactionActionItem(
        currentReaction = currentReaction,
        isLiked = isLiked,
        onReactionSelected = onReactionSelected,
        isSelectorOpen = isSelectorOpen,
        onSelectorOpenChange = { isSelectorOpen = it },
        modifier = modifier
    )
}
