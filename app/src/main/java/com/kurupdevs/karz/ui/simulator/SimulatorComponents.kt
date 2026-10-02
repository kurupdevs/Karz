package com.kurupdevs.karz.ui.simulator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurupdevs.karz.math.PrepayStrategy

/**
 * Local design tokens, copied from SPEC section 2. the app theme is the
 * long-term home for these; this file stays self-contained so the simulator
 * compiles and looks right regardless of scaffold naming.
 */
object SimColors {
    val Navy = Color(0xFF1C1C30)
    val NearBlack = Color(0xFF14141F)
    val Deep = Color(0xFF23233F)
    val PurpleStart = Color(0xFF7C6CF5)
    val PurpleEnd = Color(0xFF4A3FD1)
    val PurpleSolid = Color(0xFF6C5CE7)
    val AppBg = Color(0xFFF5F5FA)
    val Headline = Color(0xFF14141F)
    val Secondary = Color(0xFF8A8A9E)
    val CardWhite = Color(0xFFFFFFFF)
    val TrackBg = Color(0xFFE9EAF2)
    val Good = Color(0xFF1E9E6A)
    val Warn = Color(0xFFC77B1A)
    val Bad = Color(0xFFD64545)
    val PinkTile = Color(0xFFFBE3EC)
    val GreenTile = Color(0xFFDFF3E4)
    val BlueTile = Color(0xFFDCE9FD)
    val LavenderTile = Color(0xFFE6E2FB)
}

/** Motion specs mirroring SPEC section 5. */
object SimMotion {
    /** Bouncy entrance for hero numbers and success pops. */
    val bouncy = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)
    val snappy = spring<Float>(dampingRatio = 0.85f, stiffness = 1200f)
    val gentle = spring<Float>(dampingRatio = 0.9f, stiffness = 200f)
}

/** Tabular numerals for every money figure. */
val TnumStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = SimColors.Headline,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SimColors.Secondary,
            )
        }
    }
}

/**
 * Radical transparency primitive: every number in the simulator expands to
 * the exact inputs and formula behind it. [rows] are label to value pairs,
 * [formula] is the one-line math shown at the bottom.
 */
@Composable
fun WorkingExpandable(
    rows: List<Pair<String, String>>,
    formula: String? = null,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = SimMotion.snappy,
        label = "chevron",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SimColors.LavenderTile),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = { open = !open },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Show the working",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = SimColors.Headline,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "›",
                    modifier = Modifier.rotate(chevron),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SimColors.PurpleSolid,
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(animationSpec = SimMotion.gentle) + fadeIn(),
                exit = shrinkVertically(animationSpec = SimMotion.gentle) + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    rows.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = SimColors.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall.merge(TnumStyle)
                                    .copy(fontWeight = FontWeight.SemiBold),
                                color = SimColors.Headline,
                            )
                        }
                    }
                    if (formula != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = formula,
                            style = MaterialTheme.typography.bodySmall,
                            color = SimColors.Secondary,
                        )
                    }
                }
            }
        }
    }
}

/** Reduce tenure | Reduce EMI segmented toggle. */
@Composable
fun StrategyToggle(
    selected: PrepayStrategy,
    onSelect: (PrepayStrategy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SimColors.TrackBg)
            .padding(4.dp),
    ) {
        StrategyOption(
            label = "Reduce tenure",
            sub = "Same EMI, finish early",
            isSelected = selected == PrepayStrategy.REDUCE_TENURE,
            onClick = { onSelect(PrepayStrategy.REDUCE_TENURE) },
            modifier = Modifier.weight(1f),
        )
        StrategyOption(
            label = "Reduce EMI",
            sub = "Same tenure, pay less",
            isSelected = selected == PrepayStrategy.REDUCE_EMI,
            onClick = { onSelect(PrepayStrategy.REDUCE_EMI) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StrategyOption(
    label: String,
    sub: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) SimColors.CardWhite else Color.Transparent,
        label = "toggleBg",
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (isSelected) SimColors.Headline else SimColors.Secondary,
        )
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = SimColors.Secondary,
        )
    }
}

/** Big screen-recordable number with a small label above it. */
@Composable
fun HeroNumber(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = SimColors.PurpleSolid,
) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            ),
            color = SimColors.Secondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall.merge(TnumStyle)
                .copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
            color = accent,
        )
    }
}
