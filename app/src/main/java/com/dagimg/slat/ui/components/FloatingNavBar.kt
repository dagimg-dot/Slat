package com.dagimg.slat.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dagimg.slat.ui.theme.AccentDanger
import com.dagimg.slat.ui.theme.TextPrimary

private const val TINT_MS = 220
private const val SWAP_MS = 160
private val POP = spring<Float>(dampingRatio = 0.42f, stiffness = 520f)

data class NavDestination(
    val id: String,
    val label: String,
    val icon: NavIcon,
    val badge: Int = 0,
)

@Composable
fun FloatingNavBar(
    destinations: List<NavDestination>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier =
            modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                .padding(5.dp),
    ) {
        destinations.forEach { destination ->
            NavItem(
                destination = destination,
                selected = destination.id == selectedId,
                onClick = { onSelect(destination.id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navItemScale",
    )
    val pop = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (selected && settled) {
            pop.snapTo(0.78f)
            pop.animateTo(1f, POP)
        }
        settled = true
    }
    val tint by animateColorAsState(
        if (selected) scheme.primary else scheme.onSurfaceVariant,
        tween(TINT_MS),
        label = "navItemTint",
    )

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .background(if (selected) scheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = interaction,
                    indication = null,
                ).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Crossfade(targetState = selected, animationSpec = tween(SWAP_MS), label = "navItemIcon") { filled ->
                Icon(
                    imageVector = if (filled) destination.icon.filled else destination.icon.outline,
                    contentDescription = null,
                    tint = tint,
                    modifier =
                        Modifier.size(25.dp).graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        },
                )
            }
            if (destination.badge > 0) {
                Text(
                    text = destination.badge.toString(),
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-6).dp)
                            .clip(CircleShape)
                            .background(AccentDanger)
                            .padding(horizontal = 5.dp),
                )
            }
        }
        Text(
            text = destination.label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
