package io.github.rubayet123.tvlive.model

import androidx.annotation.DrawableRes

data class SettingsCardItem(
    val id: Long,
    val title: String,
    val description: String,
    val badgeText: String,
    @DrawableRes val iconResId: Int,
    val targetActivityClass: Class<*>? = null,
    val actionType: String? = null
)
