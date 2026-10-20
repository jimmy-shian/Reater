package com.reater.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.ui.AvatarIcons

/** Resolve every category through its saved icon key and the existing vector library. */
@Composable
fun CategoryIcon(category: CategoryEntity?, modifier: Modifier = Modifier.size(20.dp)) {
    Icon(
        painter = painterResource(AvatarIcons.getDrawableRes(category?.avatarIcon)),
        contentDescription = null,
        modifier = modifier,
        tint = Color.Unspecified
    )
}
