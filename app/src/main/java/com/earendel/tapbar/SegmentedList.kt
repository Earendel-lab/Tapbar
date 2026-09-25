package com.earendel.tapbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val GroupLargeCorner = 18.dp
val GroupSmallCorner = 4.dp

fun segmentedShape(index: Int, count: Int): Shape =
    when {
        count <= 1 -> RoundedCornerShape(GroupLargeCorner)
        index == 0 -> RoundedCornerShape(
            topStart = GroupLargeCorner,
            topEnd = GroupLargeCorner,
            bottomEnd = GroupSmallCorner,
            bottomStart = GroupSmallCorner,
        )
        index == (count - 1) -> RoundedCornerShape(
            topStart = GroupSmallCorner,
            topEnd = GroupSmallCorner,
            bottomEnd = GroupLargeCorner,
            bottomStart = GroupLargeCorner,
        )
        else -> RoundedCornerShape(GroupSmallCorner)
    }

@Composable
fun <T> SegmentedConnectList(
    items: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable (item: T, index: Int) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items.forEachIndexed { index, item ->
            SegmentedCard(
                index = index,
                count = items.size,
            ) {
                content(item, index)
            }
        }
    }
}

@Composable
fun SegmentedCard(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = segmentedShape(index, count)
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    Card(
        shape = shape,
        colors = colors,
        elevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}
