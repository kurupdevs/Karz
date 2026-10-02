package com.kurupdevs.karz.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurupdevs.karz.ui.motion.HapticEvent
import com.kurupdevs.karz.ui.motion.Motion
import com.kurupdevs.karz.ui.motion.rememberHapticTick
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.NavBar
import com.kurupdevs.karz.ui.theme.NavInactive
import com.kurupdevs.karz.ui.theme.NavyPrimary

enum class MainTab(val label: String, val icon: ImageVector) {
    Main("Main", Icons.Filled.Home),
    Manage("Manage", Icons.Filled.AccountBalanceWallet),
    Simulate("Simulate", Icons.Filled.Calculate)
}

/**
 * Black pill bottom nav with a sliding white pill behind the active tab.
 * Pill offset and width animate with springSnappy; icon tint crossfades
 * over 200ms. Manual and cheap: no shared-transition needed here.
 */
@Composable
fun BottomPillNav(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tick = rememberHapticTick()
    var rowWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val tabWidth = remember(rowWidthPx, density) {
        with(density) { (rowWidthPx / 3f).toDp() }
    }
    val pillWidth by animateDpAsState(
        targetValue = tabWidth,
        animationSpec = Motion.springSnappy(),
        label = "pillWidth"
    )
    val pillOffset by animateDpAsState(
        targetValue = tabWidth * selected.ordinal.toFloat(),
        animationSpec = Motion.springSnappy(),
        label = "pillOffset"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(NavBar)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .offset { IntOffset(pillOffset.roundToPx(), 0) }
                .width(pillWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Color.White)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowWidthPx = it.size.width }
        ) {
            MainTab.entries.forEach { tab ->
                val active = tab == selected
                val tint by animateColorAsState(
                    targetValue = if (active) NavyPrimary else NavInactive,
                    animationSpec = Motion.easeInOut(200),
                    label = "tabTint"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(onClick = {
                            if (!active) {
                                tick(HapticEvent.TabSwitch)
                                onSelect(tab)
                            }
                        })
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        Text(
                            text = tab.label,
                            style = MortgageTypography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = tint
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomPillNavPreview() {
    KarzTheme {
        BottomPillNav(
            selected = MainTab.Main,
            onSelect = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
