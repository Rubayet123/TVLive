package io.github.rubayet123.tvlive

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import io.github.rubayet123.tvlive.model.Channel
import kotlin.properties.Delegates

/**
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an ImageCardView with dynamic TV Reorder arrows and indicators.
 */
class CardPresenter : Presenter() {
    private var mDefaultCardImage: Drawable? = null
    private var sSelectedBackgroundColor: Int by Delegates.notNull()
    private var sDefaultBackgroundColor: Int by Delegates.notNull()

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        Log.d(TAG, "onCreateViewHolder")

        sDefaultBackgroundColor = ContextCompat.getColor(parent.context, R.color.frosted_card)
        sSelectedBackgroundColor =
            ContextCompat.getColor(parent.context, R.color.selected_background)
        mDefaultCardImage = ContextCompat.getDrawable(parent.context, R.drawable.fallback_logo)

        val isTv = io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(parent.context)

        val cardView = object : ImageCardView(parent.context) {
            override fun setSelected(selected: Boolean) {
                updateCardBackgroundColor(this, selected)
                super.setSelected(selected)
            }
        }

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = isTv
        cardView.isClickable = true
        cardView.foreground = ContextCompat.getDrawable(parent.context, R.drawable.bg_card_foreground_selector)
        updateCardBackgroundColor(cardView, false)

        // Focus scaling 1.1x
        cardView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }

        return Presenter.ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        Log.d(TAG, "onBindViewHolder")

        if (item is Channel) {
            val isReordering = activeReorderChannelKey != null && 
                activeReorderChannelKey == io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(item)

            if (isReordering) {
                val activeBg = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(cardView.context, R.color.selected_background))
                    setStroke(6, Color.parseColor("#38BDF8"))
                    cornerRadius = 16f
                }
                cardView.background = activeBg
                cardView.setInfoAreaBackgroundColor(Color.parseColor("#1E293B"))
            } else {
                updateCardBackgroundColor(cardView, cardView.isSelected)
            }

            if (item.name.isNotEmpty()) {
                cardView.titleText = item.name
                val categoryText = item.group ?: ""
                val subtitleText = if (!item.subtitle.isNullOrBlank()) {
                    item.subtitle
                } else if (item.sources.size > 1) {
                    if (categoryText.isNotEmpty()) "$categoryText • ${item.sources.size} Sources" else "${item.sources.size} Sources"
                } else {
                    categoryText
                }
                cardView.contentText = subtitleText

                // Enhance typography for 10-foot TV viewing
                val titleTv = cardView.findViewById<android.widget.TextView>(androidx.leanback.R.id.title_text)
                val contentTv = cardView.findViewById<android.widget.TextView>(androidx.leanback.R.id.content_text)
                titleTv?.textSize = 15f
                titleTv?.typeface = android.graphics.Typeface.DEFAULT_BOLD
                contentTv?.textSize = 12.5f
                contentTv?.alpha = 0.95f

                cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
                cardView.mainImageView?.let { imageView ->
                    Glide.with(viewHolder.view.context)
                        .load(item.logoUrl)
                        .centerCrop()
                        .error(mDefaultCardImage)
                        .into(imageView)
                }

                cardView.setOnLongClickListener {
                    val activity = findActivity(cardView.context)
                    val dialogContext = activity ?: cardView.context
                    val mainFragment = (activity as? MainActivity)?.supportFragmentManager?.findFragmentById(R.id.main_browse_fragment) as? MainFragment

                    io.github.rubayet123.tvlive.ui.dialogs.ChannelQuickActionTvDialog.show(
                        context = dialogContext,
                        channel = item,
                        onOpenChangeCategory = {
                            io.github.rubayet123.tvlive.ui.dialogs.ChangeChannelCategoryDialog.show(dialogContext, item) {
                                (activity as? MainActivity)?.refreshActiveFragment()
                            }
                        },
                        onStartMoveMode = {
                            mainFragment?.startReorderMode(item)
                        },
                        onChannelUpdated = {
                            (activity as? MainActivity)?.refreshActiveFragment()
                        }
                    )
                    true
                }
            }
        } else if (item is Movie) {
            updateCardBackgroundColor(cardView, cardView.isSelected)

            cardView.titleText = item.title
            cardView.contentText = item.description ?: ""
            cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            cardView.mainImageView?.let { imageView ->
                Glide.with(viewHolder.view.context)
                    .load(item.cardImageUrl)
                    .centerCrop()
                    .error(mDefaultCardImage)
                    .into(imageView)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        val cardView = viewHolder.view as ImageCardView
        // Remove references to images so that the garbage collector can free up memory
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    private fun updateCardBackgroundColor(view: ImageCardView, selected: Boolean) {
        val color = if (selected) sSelectedBackgroundColor else sDefaultBackgroundColor
        // Both background colors should be set because the view's background is temporarily visible
        // during animations.
        view.setBackgroundColor(color)
        view.setInfoAreaBackgroundColor(color)
        val contentTv = view.findViewById<android.widget.TextView>(androidx.leanback.R.id.content_text)
        if (selected) {
            contentTv?.setTextColor(Color.WHITE)
        } else {
            contentTv?.setTextColor(Color.parseColor("#CBD5E1"))
        }
    }

    private fun findActivity(context: android.content.Context): androidx.fragment.app.FragmentActivity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is androidx.fragment.app.FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    companion object {
        private val TAG = "CardPresenter"
        var activeReorderChannelKey: String? = null

        private val CARD_WIDTH = 313
        private val CARD_HEIGHT = 176
    }
}
