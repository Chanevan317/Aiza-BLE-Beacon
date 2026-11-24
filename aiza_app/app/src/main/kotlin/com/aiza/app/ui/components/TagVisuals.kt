package com.aiza.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiza.data.tags.TagIcon

val TagIcon.vector: ImageVector
    get() = when (this) {
        TagIcon.KEYS -> Icons.Rounded.Key
        TagIcon.WALLET -> Icons.Rounded.AccountBalanceWallet
        TagIcon.BAG -> Icons.Rounded.ShoppingBag
        TagIcon.BACKPACK -> Icons.Rounded.Backpack
        TagIcon.LUGGAGE -> Icons.Rounded.Luggage
        TagIcon.CAR -> Icons.Rounded.DirectionsCar
        TagIcon.PET -> Icons.Rounded.Pets
        TagIcon.BIKE -> Icons.Rounded.PedalBike
        TagIcon.HEADPHONES -> Icons.Rounded.Headphones
        TagIcon.LAPTOP -> Icons.Rounded.Laptop
        TagIcon.REMOTE -> Icons.Rounded.SettingsRemote
        TagIcon.TAG -> Icons.Rounded.Sell
    }

val TagIcon.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

/** Each item type gets its own accent pair so the list is easy to scan. */
val TagIcon.accent: Pair<Color, Color>
    get() = when (this) {
        TagIcon.KEYS -> Color(0xFFF59E0B) to Color(0xFFF97316)
        TagIcon.WALLET -> Color(0xFF10B981) to Color(0xFF0D9488)
        TagIcon.BAG, TagIcon.BACKPACK -> Color(0xFF6366F1) to Color(0xFF8B5CF6)
        TagIcon.LUGGAGE -> Color(0xFF0EA5E9) to Color(0xFF6366F1)
        TagIcon.CAR, TagIcon.BIKE -> Color(0xFFEF4444) to Color(0xFFF97316)
        TagIcon.PET -> Color(0xFFEC4899) to Color(0xFFF43F5E)
        TagIcon.HEADPHONES, TagIcon.LAPTOP, TagIcon.REMOTE -> Color(0xFF14B8A6) to Color(0xFF0EA5E9)
        TagIcon.TAG -> Color(0xFF64748B) to Color(0xFF475569)
    }

@Composable
fun TagAvatar(icon: TagIcon, modifier: Modifier = Modifier, size: Dp = 52.dp, dimmed: Boolean = false) {
    val (a, b) = icon.accent
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(a, b)), alpha = if (dimmed) 0.45f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon.vector, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
