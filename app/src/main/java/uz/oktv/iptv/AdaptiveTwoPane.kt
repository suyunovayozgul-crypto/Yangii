package uz.oktv.iptv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration

@Composable
fun AdaptiveTwoPane(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isTablet = configuration.screenWidthDp >= 600
    val isWide = !isPortrait || isTablet

    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        if (measurables.size < 2) {
            val placeable = measurables.firstOrNull()?.measure(constraints)
            return@Layout layout(width, height) { placeable?.placeRelative(0, 0) }
        }

        if (isWide) {
            val firstWidth = (width * 0.58f).toInt()
            val secondWidth = width - firstWidth
            val firstPlaceable = measurables[0].measure(
                constraints.copy(minWidth = firstWidth, maxWidth = firstWidth, minHeight = height, maxHeight = height)
            )
            val secondPlaceable = measurables[1].measure(
                constraints.copy(minWidth = secondWidth, maxWidth = secondWidth, minHeight = height, maxHeight = height)
            )
            layout(width, height) {
                firstPlaceable.placeRelative(0, 0)
                secondPlaceable.placeRelative(firstWidth, 0)
            }
        } else {
            val firstHeight = (height * 0.45f).toInt()
            val secondHeight = height - firstHeight
            val firstPlaceable = measurables[0].measure(
                constraints.copy(minWidth = width, maxWidth = width, minHeight = firstHeight, maxHeight = firstHeight)
            )
            val secondPlaceable = measurables[1].measure(
                constraints.copy(minWidth = width, maxWidth = width, minHeight = secondHeight, maxHeight = secondHeight)
            )
            layout(width, height) {
                firstPlaceable.placeRelative(0, 0)
                secondPlaceable.placeRelative(0, firstHeight)
            }
        }
    }
}
