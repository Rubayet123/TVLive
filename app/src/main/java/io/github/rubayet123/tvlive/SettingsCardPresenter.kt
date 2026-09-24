package io.github.rubayet123.tvlive

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import io.github.rubayet123.tvlive.model.SettingsCardItem

class SettingsCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings_card, parent, false)
        val isTv = io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(parent.context)
        view.isFocusable = true
        view.isFocusableInTouchMode = isTv
        view.isClickable = true

        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }

        return Presenter.ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val cardItem = item as? SettingsCardItem ?: return
        val view = viewHolder.view

        val ivIcon = view.findViewById<ImageView>(R.id.iv_card_icon)
        val tvBadge = view.findViewById<TextView?>(R.id.tv_card_badge)
        val tvTitle = view.findViewById<TextView>(R.id.tv_card_title)
        val tvSubtitle = view.findViewById<TextView?>(R.id.tv_card_subtitle)

        ivIcon.setImageResource(cardItem.iconResId)
        tvTitle.text = cardItem.title

        tvBadge?.apply {
            if (cardItem.badgeText.isNotBlank()) {
                text = cardItem.badgeText
                visibility = android.view.View.VISIBLE
            } else {
                visibility = android.view.View.GONE
            }
        }

        tvSubtitle?.apply {
            if (cardItem.description.isNotBlank()) {
                text = cardItem.description
                visibility = android.view.View.VISIBLE
            } else {
                visibility = android.view.View.GONE
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}
}
