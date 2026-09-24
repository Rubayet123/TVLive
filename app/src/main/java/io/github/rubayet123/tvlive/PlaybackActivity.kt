package io.github.rubayet123.tvlive

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.TranslateAnimation
import android.view.animation.Animation
import kotlin.math.abs
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.Presenter
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.rubayet123.tvlive.data.network.NetworkClient
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
class PlaybackActivity : FragmentActivity() {

    // ── Gestures ──────────────────────────────────────────────────────────────
    private lateinit var gestureDetector: GestureDetector
    private var touchDownOnOverlay = false
    private var channelSwipeTriggered = false
    private var lastChannelSwitchTime = 0L

    private fun checkTouchHitOverlay(e: MotionEvent): Boolean {
        val rx = e.rawX
        val ry = e.rawY

        val isPortrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        if (isPortrait && !io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) {
            val pLoc = IntArray(2)
            playerView.getLocationOnScreen(pLoc)
            val pLeft = pLoc[0].toFloat()
            val pTop = pLoc[1].toFloat()
            val pRight = pLeft + playerView.width
            val pBottom = pTop + playerView.height
            if (rx < pLeft || rx > pRight || ry < pTop || ry > pBottom) {
                return true
            }
        }

        if (isTrackPanelVisible()) {
            if (::trackPanelContainer.isInitialized && isTrackPanelVisible()) {
                val tLoc = IntArray(2)
                trackPanelContainer.getLocationOnScreen(tLoc)
                if (rx >= tLoc[0]) {
                    return true
                }
            }
        }

        if (isChannelListVisible()) {
            if (::recentChannelsOverlay.isInitialized && recentChannelsOverlay.visibility == View.VISIBLE) {
                val rLoc = IntArray(2)
                recentChannelsOverlay.getLocationOnScreen(rLoc)
                val rLeft = rLoc[0].toFloat()
                val rTop = rLoc[1].toFloat()
                val rRight = rLeft + recentChannelsOverlay.width
                val rBottom = rTop + recentChannelsOverlay.height
                if (rx >= rLeft && rx <= rRight && ry >= rTop && ry <= rBottom) {
                    return true
                }
            }
        }

        if (isControlsVisible()) {
            if (::landscapeTopBar.isInitialized && landscapeTopBar.visibility == View.VISIBLE) {
                val topLoc = IntArray(2)
                landscapeTopBar.getLocationOnScreen(topLoc)
                val topLeft = topLoc[0].toFloat()
                val topTop = topLoc[1].toFloat()
                val topRight = topLeft + landscapeTopBar.width
                val topBottom = topTop + landscapeTopBar.height
                if (rx >= topLeft && rx <= topRight && ry >= topTop && ry <= topBottom) {
                    return true
                }
            }
            if (::playerControls.isInitialized && playerControls.visibility == View.VISIBLE) {
                val ctrlLoc = IntArray(2)
                playerControls.getLocationOnScreen(ctrlLoc)
                val ctrlLeft = ctrlLoc[0].toFloat()
                val ctrlTop = ctrlLoc[1].toFloat()
                val ctrlRight = ctrlLeft + playerControls.width
                val ctrlBottom = ctrlTop + playerControls.height
                if (rx >= ctrlLeft && rx <= ctrlRight && ry >= ctrlTop && ry <= ctrlBottom) {
                    return true
                }
            }
        }

        return false
    }

    // ── ExoPlayer ─────────────────────────────────────────────────────────────
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var bufferLoader: ProgressBar
    private val okHttpClient = NetworkClient.client

    // ── Stream state ──────────────────────────────────────────────────────────
    private var streamUrl: String? = null
    private var channelName: String? = null

    // ── Top-left OSD (brief on-screen display) ────────────────────────────────
    private lateinit var channelNameView: TextView

    // ── Channel list overlay (opens on ↓) ────────────────────────────────────
    private lateinit var recentChannelsOverlay: HorizontalGridView
    private var currentVolumeFloat: Float = -1f

    // ── Player control bar views ──────────────────────────────────────────────
    private lateinit var landscapeTopBar: LinearLayout
    private lateinit var btnLandscapeBack: ImageButton
    private lateinit var tvLandscapeHeaderTitle: TextView
    private lateinit var playerControls: LinearLayout
    private lateinit var tvCtrlChannelName: TextView
    private lateinit var liveBadge: LinearLayout
    private lateinit var tvPosition: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnAudioSubtitle: ImageButton
    private lateinit var btnQuality: ImageButton
    private lateinit var btnCtrlInfo: ImageButton

    // ── Additional controls & gesture views ──────────────────────────────────
    private lateinit var btnAspectRatio: ImageButton
    private lateinit var btnPip: ImageButton
    private lateinit var btnRotate: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var layoutBrightnessGauge: LinearLayout
    private lateinit var progressBrightness: ProgressBar
    private lateinit var tvBrightnessPercent: TextView
    private lateinit var layoutVolumeGauge: LinearLayout
    private lateinit var progressVolume: ProgressBar
    private lateinit var tvVolumePercent: TextView
    private lateinit var layoutSeekLeft: LinearLayout
    private lateinit var tvSeekLeftLabel: TextView
    private lateinit var layoutSeekRight: LinearLayout
    private lateinit var tvSeekRightLabel: TextView
    private lateinit var layoutUnlockOverlay: LinearLayout
    private lateinit var btnUnlock: ImageButton

    // ── Mobile Portrait Section Views ─────────────────────────────────────────
    private lateinit var playerContainer: FrameLayout
    private lateinit var mobilePortraitPanel: LinearLayout
    private lateinit var portraitTopBar: LinearLayout
    private lateinit var btnPortraitBack: ImageButton
    private lateinit var portraitTopLogo: ImageView
    private lateinit var tvPortraitHeaderTitle: TextView

    private lateinit var btnPortraitPlayPause: ImageButton
    private lateinit var btnPortraitInfo: ImageButton
    private lateinit var btnPortraitFavorite: ImageButton
    private lateinit var btnPortraitAspect: ImageButton
    private lateinit var btnPortraitAudio: ImageButton
    private lateinit var btnPortraitSource: ImageButton
    private lateinit var btnPortraitQuality: ImageButton
    private lateinit var btnPortraitFullscreen: ImageButton

    private lateinit var btnSource: ImageButton
    private var currentPlayingChannel: io.github.rubayet123.tvlive.model.Channel? = null
    private var activeSources: List<io.github.rubayet123.tvlive.model.StreamSource> = emptyList()
    private var currentSourceIndex: Int = 0
    private var sourceDialog: android.app.Dialog? = null

    private var activeToast: android.widget.Toast? = null
    private var hudOverlayView: TextView? = null
    private val hudHideHandler = Handler(Looper.getMainLooper())
    private val hudHideRunnable = Runnable {
        hudOverlayView?.animate()?.alpha(0f)?.setDuration(250)?.withEndAction {
            hudOverlayView?.visibility = View.GONE
        }?.start()
    }

    private lateinit var portraitCategoryRecycler: RecyclerView
    private lateinit var portraitChannelsRecycler: RecyclerView

    private var portraitChannelsAdapter: PortraitChannelsAdapter? = null
    private var portraitCategoriesAdapter: PortraitCategoriesAdapter? = null
    private var selectedPortraitCategory: String = "All"

    private var isScreenLocked = false
    private var currentResizeModeIndex = 0
    private val resizeModes = intArrayOf(
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    )
    private val resizeModeNames = arrayOf("Aspect: Fit", "Aspect: Fill (Crop)", "Aspect: Zoom", "Aspect: Fixed Width", "Aspect: Fixed Height")

    private val gaugeHandler = Handler(Looper.getMainLooper())
    private val seekHandler = Handler(Looper.getMainLooper())
    private val bufferingWatchdogHandler = Handler(Looper.getMainLooper())
    private var singleStreamRetryCount = 0

    private val bufferingWatchdogRunnable = Runnable {
        if (player?.playbackState == Player.STATE_BUFFERING) {
            bufferLoader.visibility = View.GONE
            if (!attemptFailoverToNextSource("Stream connection timed out")) {
                handleSingleStreamRecovery("Stream stalled or buffering timed out")
            }
        }
    }

    private fun startBufferingWatchdog(timeoutMs: Long? = null) {
        if (!io.github.rubayet123.tvlive.data.StreamHealthConfig.isWatchdogEnabled(this)) {
            cancelBufferingWatchdog()
            return
        }
        val effectiveTimeout = timeoutMs ?: (io.github.rubayet123.tvlive.data.StreamHealthConfig.getStallTimeoutSec(this) * 1000L)
        bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable)
        bufferingWatchdogHandler.postDelayed(bufferingWatchdogRunnable, effectiveTimeout)
    }

    private fun cancelBufferingWatchdog() {
        bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable)
    }

    private val pipActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                "ACTION_PIP_PLAY_PAUSE" -> {
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                    updatePipParams()
                }
                "ACTION_PIP_PREV" -> {
                    io.github.rubayet123.tvlive.data.LiveTvManager.getPreviousChannel()?.let { playChannel(it) }
                    updatePipParams()
                }
                "ACTION_PIP_NEXT" -> {
                    io.github.rubayet123.tvlive.data.LiveTvManager.getNextChannel()?.let { playChannel(it) }
                    updatePipParams()
                }
            }
        }
    }

    // ── Track panel views ─────────────────────────────────────────────────────
    private lateinit var trackPanelContainer: FrameLayout
    private lateinit var tvPanelTitle: TextView
    private lateinit var trackRecycler: RecyclerView
    private var currentTrackType: TrackType? = null
    private var lastTrackPanelOpenTime: Long = 0L

    // ── Quality selection state (empty = Auto adaptive) ───────────────────────
    private val selectedQualityIndices = mutableSetOf<Int>()

    // ── Control bar auto-hide timer ───────────────────────────────────────────
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val CONTROLS_HIDE_DELAY_MS = 3_500L
    private val controlsHideRunnable = Runnable { hidePlayerControls() }

    // ── Seekbar periodic updater ──────────────────────────────────────────────
    private val seekBarHandler = Handler(Looper.getMainLooper())
    private val seekBarRunnable = object : Runnable {
        override fun run() {
            updateSeekBarProgress()
            seekBarHandler.postDelayed(this, 500)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Track types / data model
    // ─────────────────────────────────────────────────────────────────────────

    enum class TrackType { AUDIO, SUBTITLE, QUALITY, AUDIO_SUBTITLE }

    data class TrackOption(
        val label: String,
        var isSelected: Boolean,
        val group: Tracks.Group? = null,
        val trackIndex: Int = 0,
        /** -1 = Auto/Off sentinel; ≥ 0 = index within the video TrackGroup */
        val qualityTrackIndex: Int = -1,
        val isHeader: Boolean = false,
        val trackType: TrackType? = null
    )

    // ─────────────────────────────────────────────────────────────────────────
    // onCreate
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.rubayet123.tvlive.util.DeviceUtils.setupOrientationForDevice(this)
        setContentView(R.layout.activity_playback)

        val rootPlaybackContainer = findViewById<View>(R.id.root_playback_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootPlaybackContainer) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            insets
        }

        // Core views
        playerView              = findViewById(R.id.player_view)
        bufferLoader            = findViewById(R.id.buffer_loader)
        hudOverlayView          = findViewById<TextView>(R.id.tv_hud_status)?.apply {
            val density = resources.displayMetrics.density
            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#E612131A"))
                setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#33FFFFFF"))
                cornerRadius = 20 * density
            }
            background = shape
        }
        channelNameView         = findViewById(R.id.channel_name)
        recentChannelsOverlay   = findViewById(R.id.recent_channels_overlay)
        recentChannelsOverlay.setGravity(android.view.Gravity.BOTTOM)

        // Configure smooth touch physics on Mobile devices while preserving D-Pad snap on TV
        if (!io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) {
            recentChannelsOverlay.focusScrollStrategy = androidx.leanback.widget.BaseGridView.FOCUS_SCROLL_ITEM
            recentChannelsOverlay.windowAlignment = androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_NO_EDGE
            recentChannelsOverlay.windowAlignmentOffsetPercent = androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            recentChannelsOverlay.itemAlignmentOffsetPercent = androidx.leanback.widget.BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED
            recentChannelsOverlay.setOnTouchListener { _, _ ->
                resetChannelOverlayTimer(5000L)
                false
            }
        } else {
            recentChannelsOverlay.focusScrollStrategy = androidx.leanback.widget.BaseGridView.FOCUS_SCROLL_ALIGNED
        }

        // Control bar views
        landscapeTopBar        = findViewById(R.id.landscape_top_bar)
        btnLandscapeBack       = findViewById(R.id.btn_landscape_back)
        tvLandscapeHeaderTitle = findViewById(R.id.tv_landscape_header_title)
        btnLandscapeBack.setOnClickListener {
            finish()
        }

        playerControls          = findViewById(R.id.player_controls)
        tvCtrlChannelName       = findViewById(R.id.tv_ctrl_channel_name)
        liveBadge               = findViewById(R.id.live_badge)
        tvPosition              = findViewById(R.id.tv_position)
        seekBar                 = findViewById(R.id.seek_bar)
        btnPrev                 = findViewById(R.id.btn_prev)
        btnPlayPause            = findViewById(R.id.btn_play_pause)
        btnNext                 = findViewById(R.id.btn_next)
        btnAudioSubtitle        = findViewById(R.id.btn_audio_subtitle)
        btnSource               = findViewById(R.id.btn_source)
        btnQuality              = findViewById(R.id.btn_quality)
        btnCtrlInfo             = findViewById(R.id.btn_ctrl_info)
        btnAspectRatio          = findViewById(R.id.btn_aspect_ratio)
        btnPip                  = findViewById(R.id.btn_pip)
        btnRotate               = findViewById(R.id.btn_rotate)
        btnLock                 = findViewById(R.id.btn_lock)

        // Gesture and status views
        layoutBrightnessGauge   = findViewById(R.id.layout_brightness_gauge)
        progressBrightness      = findViewById(R.id.progress_brightness)
        tvBrightnessPercent     = findViewById(R.id.tv_brightness_percent)
        layoutVolumeGauge       = findViewById(R.id.layout_volume_gauge)
        progressVolume          = findViewById(R.id.progress_volume)
        tvVolumePercent         = findViewById(R.id.tv_volume_percent)
        layoutSeekLeft          = findViewById(R.id.layout_seek_left)
        tvSeekLeftLabel         = findViewById(R.id.tv_seek_left_label)
        layoutSeekRight         = findViewById(R.id.layout_seek_right)
        tvSeekRightLabel        = findViewById(R.id.tv_seek_right_label)
        layoutUnlockOverlay     = findViewById(R.id.layout_unlock_overlay)
        btnUnlock               = findViewById(R.id.btn_unlock)

        if (io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) {
            btnPip.visibility = View.GONE
            btnRotate.visibility = View.GONE
            btnLock.visibility = View.GONE
            btnAspectRatio.nextFocusRightId = R.id.btn_aspect_ratio
        } else {
            if (savedInstanceState == null) {
                val prefs = getSharedPreferences("tv_live_prefs", MODE_PRIVATE)
                val defaultOrientation = prefs.getString("pref_player_orientation", "portrait") ?: "portrait"
                when (defaultOrientation) {
                    "portrait" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "sensor" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    "landscape" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            btnRotate.visibility = View.VISIBLE
            val density = resources.displayMetrics.density
            val horizPx = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                (32 * density).toInt()
            } else {
                (16 * density).toInt()
            }
            playerControls.setPadding(horizPx, (40 * density).toInt(), horizPx, (16 * density).toInt())
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, android.content.IntentFilter().apply {
                addAction("ACTION_PIP_PLAY_PAUSE")
                addAction("ACTION_PIP_PREV")
                addAction("ACTION_PIP_NEXT")
            }, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipActionReceiver, android.content.IntentFilter().apply {
                addAction("ACTION_PIP_PLAY_PAUSE")
                addAction("ACTION_PIP_PREV")
                addAction("ACTION_PIP_NEXT")
            })
        }

        // Track panel views
        trackPanelContainer     = findViewById(R.id.track_panel_container)
        tvPanelTitle            = findViewById(R.id.tv_panel_title)
        trackRecycler           = findViewById(R.id.track_recycler)
        trackRecycler.layoutManager = LinearLayoutManager(this)
        findViewById<View>(R.id.btn_close_track_panel)?.setOnClickListener {
            hideTrackPanel()
        }

        setupGestureDetector()

        setupControlBarListeners()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    sourceDialog?.isShowing == true -> {
                        sourceDialog?.dismiss()
                        sourceDialog = null
                        playerView.requestFocus()
                    }
                    isTrackPanelVisible() -> {
                        hideTrackPanel()
                    }
                    isChannelListVisible() -> {
                        hideRecentChannels()
                    }
                    isControlsVisible() -> {
                        hidePlayerControls()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        // Resolve stream URL from Intent or LiveTvManager
        val intentUrl = intent.getStringExtra("stream_url")
        if (!intentUrl.isNullOrEmpty()) {
            streamUrl   = intentUrl
            channelName = intent.getStringExtra("title")
        } else {
            val current = io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
            streamUrl   = current?.streamUrl
            channelName = current?.name
        }

        channelNameView.text    = channelName
        tvCtrlChannelName.text  = channelName
        showInfo()

        // Mobile portrait views
        playerContainer          = findViewById(R.id.player_container)
        mobilePortraitPanel      = findViewById(R.id.mobile_portrait_panel)
        portraitTopBar            = findViewById(R.id.portrait_top_bar)
        btnPortraitBack           = findViewById(R.id.btn_portrait_back)
        portraitTopLogo           = findViewById(R.id.portrait_top_logo)
        tvPortraitHeaderTitle     = findViewById(R.id.tv_portrait_header_title)

        btnPortraitPlayPause      = findViewById(R.id.btn_portrait_play_pause)
        btnPortraitInfo           = findViewById(R.id.btn_portrait_info)
        btnPortraitFavorite       = findViewById(R.id.btn_portrait_favorite)
        btnPortraitAspect         = findViewById(R.id.btn_portrait_aspect)
        btnPortraitAudio          = findViewById(R.id.btn_portrait_audio)
        btnPortraitSource         = findViewById(R.id.btn_portrait_source)
        btnPortraitQuality        = findViewById(R.id.btn_portrait_quality)
        btnPortraitFullscreen     = findViewById(R.id.btn_portrait_fullscreen)

        portraitCategoryRecycler = findViewById(R.id.portrait_category_recycler)
        portraitChannelsRecycler = findViewById(R.id.portrait_channels_recycler)

        btnPortraitBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnPortraitPlayPause.setOnClickListener {
            player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
            }
        }
        btnPortraitInfo.setOnClickListener {
            showStreamInfoDialog()
        }
        btnPortraitFavorite.setOnClickListener {
            val favRepo = io.github.rubayet123.tvlive.data.FavoritesRepository(this)
            val currentChannel = io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
            currentChannel?.let { ch ->
                val isFav = favRepo.isFavorite(ch)
                if (isFav) favRepo.removeFavorite(ch) else favRepo.addFavorite(ch)
                btnPortraitFavorite.setImageResource(if (!isFav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
                toast(if (!isFav) "Added to Favorites" else "Removed from Favorites")
            }
        }
        btnPortraitAspect.setOnClickListener {
            currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
            playerView.resizeMode = resizeModes[currentResizeModeIndex]
            toast(resizeModeNames[currentResizeModeIndex])
        }
        btnPortraitAudio.setOnClickListener {
            showTrackPanel(TrackType.AUDIO)
        }
        btnPortraitSource.setOnClickListener {
            showSourceSelectionDialog()
        }
        btnPortraitQuality.setOnClickListener {
            showTrackPanel(TrackType.QUALITY)
        }
        btnPortraitFullscreen.setOnClickListener {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        updateMobilePortraitLayout()
    }

    private fun setupControlBarListeners() {
        btnPrev.setOnClickListener {
            resetControlsTimer()
            io.github.rubayet123.tvlive.data.LiveTvManager.getPreviousChannel()?.let { playChannel(it) }
        }
        btnNext.setOnClickListener {
            resetControlsTimer()
            io.github.rubayet123.tvlive.data.LiveTvManager.getNextChannel()?.let { playChannel(it) }
        }
        btnPlayPause.setOnClickListener {
            resetControlsTimer()
            player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
                updatePlayPauseButton()
            }
        }
        btnAudioSubtitle.setOnClickListener { resetControlsTimer(); showTrackPanel(TrackType.AUDIO_SUBTITLE) }
        btnSource.setOnClickListener { resetControlsTimer(); showSourceSelectionDialog() }
        btnQuality.setOnClickListener  { resetControlsTimer(); showTrackPanel(TrackType.QUALITY) }
        btnCtrlInfo.setOnClickListener { resetControlsTimer(); showStreamInfoDialog() }

        btnAspectRatio.setOnClickListener {
            resetControlsTimer()
            currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
            playerView.resizeMode = resizeModes[currentResizeModeIndex]
            toast(resizeModeNames[currentResizeModeIndex])
        }

        btnPip.setOnClickListener {
            resetControlsTimer()
            enterPipMode()
        }

        btnRotate.setOnClickListener {
            resetControlsTimer()
            val currentOrientation = resources.configuration.orientation
            if (currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                toast("Landscape Mode")
            } else {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                toast("Portrait Mode")
            }
        }

        btnRotate.setOnLongClickListener {
            resetControlsTimer()
            showOrientationSettingsDialog()
            true
        }

        btnLock.setOnClickListener {
            isScreenLocked = true
            hidePlayerControls()
            toast("Controls Locked")
        }

        btnUnlock.setOnClickListener {
            isScreenLocked = false
            layoutUnlockOverlay.visibility = View.GONE
            showPlayerControls()
            toast("Controls Unlocked")
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = player?.duration ?: return
                    if (dur > 0 && dur != C.TIME_UNSET) player?.seekTo(dur * progress / 1000L)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) = resetControlsTimer()
            override fun onStopTrackingTouch(sb: SeekBar)  = resetControlsTimer()
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateMobilePortraitLayout(newConfig)
    }

    override fun onStart() {
        super.onStart()
        if (androidx.media3.common.util.Util.SDK_INT > 23) initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        if (androidx.media3.common.util.Util.SDK_INT <= 23 || player == null) initializePlayer()
    }

    override fun onPause() {
        super.onPause()
        val isPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!isPip && androidx.media3.common.util.Util.SDK_INT <= 23) releasePlayer()
        stopSeekBarUpdater()
    }

    override fun onStop() {
        super.onStop()
        val isPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!isPip && androidx.media3.common.util.Util.SDK_INT > 23) releasePlayer()
        stopSeekBarUpdater()
    }

    override fun onDestroy() {
        activeProbeJob?.cancel()
        super.onDestroy()
        try {
            unregisterReceiver(pipActionReceiver)
        } catch (e: Exception) {}
        controlsHandler.removeCallbacksAndMessages(null)
        seekBarHandler.removeCallbacksAndMessages(null)
        gaugeHandler.removeCallbacksAndMessages(null)
        seekHandler.removeCallbacksAndMessages(null)
        bufferingWatchdogHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Player control bar — show / hide / timer
    // ─────────────────────────────────────────────────────────────────────────

    private fun isControlsVisible()     = playerControls.visibility == View.VISIBLE
    private fun isTrackPanelVisible()   = trackPanelContainer.visibility == View.VISIBLE
    private fun isChannelListVisible()  = ::recentChannelsOverlay.isInitialized && recentChannelsOverlay.visibility == View.VISIBLE

    private fun hideRecentChannels() {
        channelOverlayHandler.removeCallbacks(channelOverlayHideRunnable)
        if (::recentChannelsOverlay.isInitialized && recentChannelsOverlay.visibility == View.VISIBLE) {
            val isTv = io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)
            if (!isTv) {
                recentChannelsOverlay.animate()
                    .translationY(recentChannelsOverlay.height.toFloat().coerceAtLeast(300f))
                    .alpha(0.0f)
                    .setDuration(180)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        recentChannelsOverlay.visibility = View.GONE
                        recentChannelsOverlay.translationY = 0f
                        recentChannelsOverlay.alpha = 1.0f
                    }
                    .start()
            } else {
                recentChannelsOverlay.visibility = View.GONE
            }
        }
        playerView.requestFocus()
    }

    private fun showPlayerControls() {
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            return
        }
        forceShowPlayerControls()
    }

    private fun forceShowPlayerControls() {
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            return
        }
        tvCtrlChannelName.text = channelName
        if (::tvLandscapeHeaderTitle.isInitialized) {
            tvLandscapeHeaderTitle.text = channelName
        }
        if (::landscapeTopBar.isInitialized) {
            landscapeTopBar.visibility = View.VISIBLE
            landscapeTopBar.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 200 })
        }
        playerControls.visibility = View.VISIBLE
        playerControls.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 200 })
        updateSeekBarProgress()
        startSeekBarUpdater()
        // Give focus to the Prev button so D-pad navigates across the bar
        if (io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) {
            btnPrev.post { btnPrev.requestFocus() }
        }
        resetControlsTimer()
    }

    private fun hidePlayerControls(immediate: Boolean = false) {
        if (immediate) {
            if (::landscapeTopBar.isInitialized) {
                landscapeTopBar.clearAnimation()
                landscapeTopBar.visibility = View.GONE
            }
            playerControls.clearAnimation()
            playerControls.visibility = View.GONE
            controlsHandler.removeCallbacks(controlsHideRunnable)
            stopSeekBarUpdater()
            return
        }
        val fade = AlphaAnimation(1f, 0f).apply { duration = 200; fillAfter = true }
        if (::landscapeTopBar.isInitialized && landscapeTopBar.visibility == View.VISIBLE) {
            landscapeTopBar.startAnimation(fade)
            landscapeTopBar.postDelayed({ landscapeTopBar.visibility = View.GONE }, 200)
        }
        playerControls.startAnimation(fade)
        playerControls.postDelayed({ playerControls.visibility = View.GONE }, 200)
        controlsHandler.removeCallbacks(controlsHideRunnable)
        stopSeekBarUpdater()
    }

    private fun resetControlsTimer() {
        controlsHandler.removeCallbacks(controlsHideRunnable)
        controlsHandler.postDelayed(controlsHideRunnable, CONTROLS_HIDE_DELAY_MS)
    }

    private fun updatePlayPauseButton() {
        val playing = player?.isPlaying ?: false
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (::btnPortraitPlayPause.isInitialized) {
            btnPortraitPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    private fun showStreamInfoDialog() {
        if (isFinishing || isDestroyed) return
        try {
            val dialog = android.app.Dialog(this)
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_stream_info)
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            
            val displayWidth = resources.displayMetrics.widthPixels
            val maxWidth = (380 * resources.displayMetrics.density).toInt()
            val dialogWidth = (displayWidth * 0.85).toInt().coerceAtMost(maxWidth)
            dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

            val tvChannelName = dialog.findViewById<TextView>(R.id.tv_info_channel_name)
            val tvCategory = dialog.findViewById<TextView>(R.id.tv_info_category)
            val tvUrl = dialog.findViewById<TextView>(R.id.tv_info_url)
            val tvResolution = dialog.findViewById<TextView>(R.id.tv_info_resolution)
            val tvFps = dialog.findViewById<TextView>(R.id.tv_info_fps)
            val tvVideoCodec = dialog.findViewById<TextView>(R.id.tv_info_video_codec)
            val tvBitrate = dialog.findViewById<TextView>(R.id.tv_info_bitrate)
            val tvAudioCodec = dialog.findViewById<TextView>(R.id.tv_info_audio_codec)
            val tvAudioDetails = dialog.findViewById<TextView>(R.id.tv_info_audio_details)
            val tvDecoder = dialog.findViewById<TextView>(R.id.tv_info_decoder)
            val btnClose = dialog.findViewById<View>(R.id.btn_close_info)

            val channel = io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
            val name = channel?.name ?: channelName ?: "Unknown Channel"
            val group = channel?.group ?: "Live TV"
            val url = channel?.streamUrl ?: streamUrl ?: "Unknown"

            tvChannelName?.text = name
            tvCategory?.text = group
            tvUrl?.text = url

            val vf = player?.videoFormat
            if (vf != null && vf.width > 0 && vf.height > 0) {
                val tag = when {
                    vf.height >= 2160 -> " (4K UHD)"
                    vf.height >= 1080 -> " (Full HD)"
                    vf.height >= 720  -> " (HD 720p)"
                    else -> " (SD)"
                }
                tvResolution?.text = "${vf.width} x ${vf.height}$tag"
            } else {
                tvResolution?.text = "Auto / Stream Default"
            }

            if (vf != null && vf.frameRate > 0) {
                tvFps?.text = "${vf.frameRate.toInt()} FPS"
            } else {
                tvFps?.text = "50/60 FPS (Auto)"
            }

            val vMime = vf?.sampleMimeType?.replace("video/", "")?.uppercase()
            tvVideoCodec?.text = vMime ?: vf?.codecs ?: "H.264 / AVC (Default)"

            if (vf != null && vf.bitrate > 0) {
                tvBitrate?.text = String.format("%.2f Mbps (%d kbps)", vf.bitrate / 1000000f, vf.bitrate / 1000)
            } else {
                tvBitrate?.text = "Adaptive Bitrate (HLS)"
            }

            val af = player?.audioFormat
            val aMime = af?.sampleMimeType?.replace("audio/", "")?.uppercase()
            tvAudioCodec?.text = aMime ?: af?.codecs ?: "AAC / Stereo"

            val sampleRate = if (af != null && af.sampleRate > 0) "${af.sampleRate / 1000} kHz" else "48 kHz"
            val channels = if (af != null && af.channelCount > 0) "${af.channelCount} Channels" else "2 Channels (Stereo)"
            tvAudioDetails?.text = "$sampleRate • $channels"

            val bufferedMs = player?.let { p -> (p.bufferedPosition - p.currentPosition).coerceAtLeast(0) } ?: 0L
            tvDecoder?.text = "ExoPlayer HW Accel (Buffer: ${bufferedMs / 1000}s)"

            btnClose?.setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Seekbar updater (runs every 500 ms while control bar is visible)
    // ─────────────────────────────────────────────────────────────────────────

    private fun startSeekBarUpdater() {
        seekBarHandler.removeCallbacks(seekBarRunnable)
        seekBarHandler.post(seekBarRunnable)
    }

    private fun stopSeekBarUpdater() {
        seekBarHandler.removeCallbacks(seekBarRunnable)
    }

    private fun updateSeekBarProgress() {
        val p        = player ?: return
        val duration = p.duration
        val isDvr    = duration > 0 && duration != C.TIME_UNSET

        if (isDvr) {
            liveBadge.visibility   = View.GONE
            tvPosition.visibility  = View.VISIBLE
            seekBar.isEnabled      = true
            seekBar.progress       = ((p.currentPosition * 1000L) / duration).toInt()
            tvPosition.text        = "${formatMs(p.currentPosition)} / ${formatMs(duration)}"
        } else {
            liveBadge.visibility   = View.VISIBLE
            tvPosition.visibility  = View.GONE
            seekBar.isEnabled      = false
            seekBar.progress       = 1000   // full bar styled as live indicator
        }
    }

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Track selection panel
    // ─────────────────────────────────────────────────────────────────────────

    private fun showTrackPanel(type: TrackType) {
        val isAlreadyOpen = isTrackPanelVisible()
        if (isAlreadyOpen && currentTrackType == type) {
            hideTrackPanel()
            return
        }
        lastTrackPanelOpenTime = android.os.SystemClock.uptimeMillis()
        currentTrackType = type
        val p      = player ?: return
        val tracks = p.currentTracks
        val options = mutableListOf<TrackOption>()

        when (type) {
            TrackType.AUDIO_SUBTITLE -> {
                tvPanelTitle.text = "Audio & Subtitles"
                options += TrackOption(label = "AUDIO", isSelected = false, isHeader = true)
                tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
                    for (i in 0 until group.length) {
                        val fmt  = group.getTrackFormat(i)
                        val lang = fmt.language?.uppercase() ?: "Track ${options.size}"
                        val ch   = if (fmt.channelCount > 0) " · ${fmt.channelCount}ch" else ""
                        val codec = fmt.sampleMimeType?.substringAfter("/") ?: ""
                        options += TrackOption(
                            label     = "$lang$ch${if (codec.isNotEmpty()) " · $codec" else ""}",
                            isSelected = group.isTrackSelected(i),
                            group     = group,
                            trackIndex = i,
                            trackType = TrackType.AUDIO
                        )
                    }
                }
                options += TrackOption(label = "SUBTITLES", isSelected = false, isHeader = true)
                val anySubOn = tracks.groups.any { g ->
                    g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { g.isTrackSelected(it) }
                }
                options += TrackOption("Off", isSelected = !anySubOn, group = null, trackIndex = -1, trackType = TrackType.SUBTITLE)
                tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                    for (i in 0 until group.length) {
                        val lang = group.getTrackFormat(i).language?.uppercase() ?: "Subtitle ${options.size}"
                        options += TrackOption(lang, isSelected = group.isTrackSelected(i), group = group, trackIndex = i, trackType = TrackType.SUBTITLE)
                    }
                }
            }

            TrackType.AUDIO -> {
                tvPanelTitle.text = "Audio Track"
                tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
                    for (i in 0 until group.length) {
                        val fmt  = group.getTrackFormat(i)
                        val lang = fmt.language?.uppercase() ?: "Track ${options.size + 1}"
                        val ch   = if (fmt.channelCount > 0) " · ${fmt.channelCount}ch" else ""
                        val codec = fmt.sampleMimeType?.substringAfter("/") ?: ""
                        options += TrackOption(
                            label     = "$lang$ch${if (codec.isNotEmpty()) " · $codec" else ""}",
                            isSelected = group.isTrackSelected(i),
                            group     = group,
                            trackIndex = i
                        )
                    }
                }
            }

            TrackType.SUBTITLE -> {
                tvPanelTitle.text = "Subtitles"
                val anySubOn = tracks.groups.any { g ->
                    g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { g.isTrackSelected(it) }
                }
                options += TrackOption("Off", isSelected = !anySubOn, group = null, trackIndex = -1)
                tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                    for (i in 0 until group.length) {
                        val lang = group.getTrackFormat(i).language?.uppercase() ?: "Subtitle ${options.size}"
                        options += TrackOption(lang, isSelected = group.isTrackSelected(i), group = group, trackIndex = i)
                    }
                }
            }

            TrackType.QUALITY -> {
                tvPanelTitle.text = "Quality"
                val videoGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                options += TrackOption("Auto (Adaptive)", isSelected = selectedQualityIndices.isEmpty(), qualityTrackIndex = -1)
                videoGroup?.let { vg ->
                    for (i in 0 until vg.length) {
                        val fmt    = vg.getTrackFormat(i)
                        val res    = if (fmt.height > 0) "${fmt.height}p" else "Track ${i + 1}"
                        val kbps   = if (fmt.bitrate > 0) " · ${fmt.bitrate / 1000} kbps" else ""
                        options += TrackOption(
                            label              = "$res$kbps",
                            isSelected         = selectedQualityIndices.contains(i),
                            group              = vg,
                            trackIndex         = i,
                            qualityTrackIndex  = i
                        )
                    }
                }
            }
        }

        trackRecycler.adapter = TrackAdapter(options) { opt, all ->
            onTrackOptionSelected(type, opt, all)
            resetControlsTimer()
        }

        if (!isAlreadyOpen) {
            trackPanelContainer.visibility = View.VISIBLE
            trackPanelContainer.startAnimation(TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 1f, Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f
            ).apply { duration = 220 })
        }

        if (io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) {
            trackRecycler.post {
                (trackRecycler.findViewHolderForAdapterPosition(0)?.itemView ?: trackRecycler).requestFocus()
            }
        }
    }

    private fun hideTrackPanel() {
        if (!isTrackPanelVisible()) return
        trackPanelContainer.startAnimation(TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f
        ).apply { duration = 200; fillAfter = true })
        trackPanelContainer.postDelayed({ trackPanelContainer.visibility = View.GONE }, 200)
        currentTrackType = null
        if (isControlsVisible() && io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) {
            btnPlayPause.post { btnPlayPause.requestFocus() }
        }
    }

    private fun showOrientationSettingsDialog() {
        val prefs = getSharedPreferences("tv_live_prefs", MODE_PRIVATE)
        val currentPref = prefs.getString("pref_player_orientation", "portrait") ?: "portrait"
        val options = arrayOf("Portrait First (Default)", "Auto-Rotate / Sensor", "Landscape First")
        val selectedIndex = when (currentPref) {
            "sensor" -> 1
            "landscape" -> 2
            else -> 0
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Default Player Orientation")
            .setSingleChoiceItems(options, selectedIndex) { dialog: android.content.DialogInterface, which: Int ->
                val newPref = when (which) {
                    1 -> "sensor"
                    2 -> "landscape"
                    else -> "portrait"
                }
                prefs.edit().putString("pref_player_orientation", newPref).apply()
                toast("Default set to: ${options[which]}")

                when (newPref) {
                    "portrait" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "sensor" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    "landscape" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onTrackOptionSelected(type: TrackType, option: TrackOption, all: MutableList<TrackOption>) {
        val p = player ?: return
        val targetType = if (type == TrackType.AUDIO_SUBTITLE) (option.trackType ?: type) else type
        when (targetType) {
            TrackType.AUDIO_SUBTITLE -> {
                // Handled via AUDIO or SUBTITLE options
            }

            TrackType.AUDIO -> {
                val group = option.group ?: return
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
                    .build()
                if (type == TrackType.AUDIO_SUBTITLE) {
                    all.filter { it.trackType == TrackType.AUDIO }.forEach { it.isSelected = (it === option) }
                } else {
                    all.forEach { it.isSelected = (it === option) }
                }
            }

            TrackType.SUBTITLE -> {
                if (option.group == null) {
                    // "Off"
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                } else {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
                        .build()
                }
                if (type == TrackType.AUDIO_SUBTITLE) {
                    all.filter { it.trackType == TrackType.SUBTITLE }.forEach { it.isSelected = (it === option) }
                } else {
                    all.forEach { it.isSelected = (it === option) }
                }
            }

            TrackType.QUALITY -> {
                val videoGroup = p.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                if (option.qualityTrackIndex == -1) {
                    // Auto — clear all video overrides
                    selectedQualityIndices.clear()
                    if (videoGroup != null) {
                        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .build()
                    }
                    all.forEach { it.isSelected = (it.qualityTrackIndex == -1) }
                } else {
                    // Toggle this quality
                    if (selectedQualityIndices.contains(option.qualityTrackIndex))
                        selectedQualityIndices.remove(option.qualityTrackIndex)
                    else
                        selectedQualityIndices.add(option.qualityTrackIndex)

                    // Recompute selection state
                    all.first { it.qualityTrackIndex == -1 }.isSelected = false
                    all.filter { it.qualityTrackIndex >= 0 }
                        .forEach { it.isSelected = selectedQualityIndices.contains(it.qualityTrackIndex) }

                    if (videoGroup != null) {
                        if (selectedQualityIndices.isEmpty()) {
                            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .build()
                            all.first { it.qualityTrackIndex == -1 }.isSelected = true
                        } else {
                            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                                .setOverrideForType(TrackSelectionOverride(
                                    videoGroup.mediaTrackGroup,
                                    selectedQualityIndices.toList()
                                ))
                                .build()
                        }
                    }
                }
            }
        }
    }

    /** Show/hide Audio, Subtitle and Quality buttons based on available tracks. */
    private fun updateTrackButtonVisibility(tracks: Tracks) {
        btnAudioSubtitle.visibility = View.VISIBLE
        btnQuality.visibility  = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView Adapter for track panels
    // ─────────────────────────────────────────────────────────────────────────

    inner class TrackAdapter(
        private val options: MutableList<TrackOption>,
        private val onSelect: (TrackOption, MutableList<TrackOption>) -> Unit
    ) : RecyclerView.Adapter<TrackAdapter.VH>() {

        private val VIEW_TYPE_HEADER = 0
        private val VIEW_TYPE_ITEM = 1

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView  = v.findViewById(R.id.tv_track_label)
            val check: ImageView? = v.findViewById(R.id.iv_track_check)
        }

        override fun getItemViewType(position: Int): Int {
            return if (options[position].isHeader) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
        }

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
            val inflater = LayoutInflater.from(parent.context)
            val layoutId = if (vt == VIEW_TYPE_HEADER) R.layout.item_track_header else R.layout.item_track_option
            return VH(inflater.inflate(layoutId, parent, false))
        }

        override fun getItemCount() = options.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val opt = options[pos]
            h.label.text = opt.label
            if (opt.isHeader) {
                h.check?.visibility = View.GONE
                h.itemView.isFocusable = false
                h.itemView.isClickable = false
                h.itemView.setOnClickListener(null)
                h.itemView.setOnFocusChangeListener(null)
                h.itemView.setOnKeyListener(null)
                return
            }

            h.check?.visibility = if (opt.isSelected) View.VISIBLE else View.INVISIBLE

            h.itemView.isFocusable = true
            h.itemView.isClickable = true
            h.itemView.setOnClickListener {
                onSelect(opt, options)
                notifyDataSetChanged()
            }
            h.itemView.setOnFocusChangeListener { v, focused ->
                v.setBackgroundColor(if (focused) 0x33FFFFFF else 0x00000000)
            }
            // Make items keyboard-clickable (D-pad OK fires onClick)
            h.itemView.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    v.performClick()
                    true
                } else false
            }
        }
    }

    // ── Gestures & Touch Support ──────────────────────────────────────────────

    private fun setupGestureDetector() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val maxVolume = audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 15

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onDown(e: MotionEvent): Boolean {
                currentVolumeFloat = -1f
                channelSwipeTriggered = false
                touchDownOnOverlay = checkTouchHitOverlay(e)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isScreenLocked) {
                    showUnlockOverlay()
                    return true
                }

                if (touchDownOnOverlay || (android.os.SystemClock.uptimeMillis() - lastChannelSwitchTime < 1000)) {
                    return false
                }

                val isPortrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                if (isPortrait) {
                    val pLoc = IntArray(2)
                    playerView.getLocationOnScreen(pLoc)
                    val pLeft = pLoc[0].toFloat()
                    val pTop = pLoc[1].toFloat()
                    val pRight = pLeft + playerView.width
                    val pBottom = pTop + playerView.height
                    if (e.rawX < pLeft || e.rawX > pRight || e.rawY < pTop || e.rawY > pBottom) {
                        return false
                    }
                }

                if (isTrackPanelVisible()) {
                    if (android.os.SystemClock.uptimeMillis() - lastTrackPanelOpenTime < 400) {
                        return true
                    }
                    if (e.rawX < trackPanelContainer.x) {
                        hideTrackPanel()
                        return true
                    }
                } else if (isChannelListVisible()) {
                    hideRecentChannels()
                    return true
                } else if (isControlsVisible()) {
                    hidePlayerControls()
                    return true
                } else {
                    if (!isPortrait) {
                        forceShowPlayerControls()
                        return true
                    }
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked || touchDownOnOverlay) return false
                val screenWidth = resources.displayMetrics.widthPixels
                val clickX = e.x

                if (clickX < screenWidth * 0.35f) {
                    performQuickSeek(isForward = false)
                    return true
                } else if (clickX > screenWidth * 0.65f) {
                    performQuickSeek(isForward = true)
                    return true
                }
                return false
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 == null || isScreenLocked || touchDownOnOverlay) return false
                if (io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this@PlaybackActivity)) return false
                if (isTrackPanelVisible()) return false

                val isPortrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                val isTv = io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this@PlaybackActivity)
                val pLoc = IntArray(2)
                playerView.getLocationOnScreen(pLoc)
                val pLeft = pLoc[0].toFloat()
                val pTop = pLoc[1].toFloat()
                val pRight = pLeft + playerView.width
                val pBottom = pTop + playerView.height

                if (e1.x < pLeft || e1.x > pRight || e1.y < pTop || e1.y > pBottom) {
                    return false
                }

                val totalDiffX = kotlin.math.abs(e2.x - e1.x)
                val totalDiffY = kotlin.math.abs(e2.y - e1.y)
                if (totalDiffX > totalDiffY) {
                    return false
                }
                val density = resources.displayMetrics.density
                if (totalDiffY < 12 * density) {
                    return false
                }

                val playerWidth = if (isPortrait) playerView.width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
                val playerHeight = if (isPortrait) playerView.height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
                val relX = e1.x - (if (isPortrait) pLeft else 0f)
                val relY = e1.y - (if (isPortrait) pTop else 0f)
                val deltaY = e1.y - e2.y // positive when dragging up, negative when dragging down

                // If channel list is currently visible: dragging down dismisses it smoothly
                if (isChannelListVisible()) {
                    if (deltaY < -35 && !channelSwipeTriggered) {
                        channelSwipeTriggered = true
                        hideRecentChannels()
                        return true
                    }
                    return false
                }

                // Swiping up: If gesture starts in the bottom area (bottom 40% of screen) OR in the center zone (20%..80%)
                val isBottomOrCenterSwipeUp = (relY > playerHeight * 0.60f || (relX in (playerWidth * 0.20f)..(playerWidth * 0.80f))) && deltaY > 35
                if (isBottomOrCenterSwipeUp && (!isPortrait || isTv)) {
                    if (!channelSwipeTriggered && !isChannelListVisible()) {
                        channelSwipeTriggered = true
                        openRecentChannels()
                        return true
                    }
                }

                if (relX < playerWidth * 0.30f) {
                    // Left side: Brightness
                    val deltaBrightness = distanceY / (playerHeight * 0.6f)
                    val lp = window.attributes
                    var currentBrightness = lp.screenBrightness
                    if (currentBrightness < 0) {
                        currentBrightness = try {
                            android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
                        } catch (e: Exception) {
                            0.5f
                        }
                    }
                    val newBrightness = (currentBrightness + deltaBrightness).coerceIn(0.01f, 1.0f)
                    lp.screenBrightness = newBrightness
                    window.attributes = lp

                    val bPercent = (newBrightness * 100).toInt()
                    progressBrightness.progress = bPercent
                    tvBrightnessPercent.text = "$bPercent%"
                    layoutBrightnessGauge.visibility = View.VISIBLE

                    gaugeHandler.removeCallbacksAndMessages(null)
                    gaugeHandler.postDelayed({ layoutBrightnessGauge.visibility = View.GONE }, 1500)
                    return true
                } else if (relX > playerWidth * 0.70f) {
                    // Right side: Volume
                    if (audioManager != null) {
                        if (currentVolumeFloat < 0) {
                            currentVolumeFloat = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                        }
                        val deltaVolume = (distanceY / (playerHeight * 0.6f)) * maxVolume
                        currentVolumeFloat = (currentVolumeFloat + deltaVolume).coerceIn(0f, maxVolume.toFloat())
                        val newVol = kotlin.math.round(currentVolumeFloat).toInt().coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                        val vPercent = (currentVolumeFloat * 100f / maxVolume).toInt().coerceIn(0, 100)
                        progressVolume.progress = vPercent
                        tvVolumePercent.text = "$vPercent%"
                        layoutVolumeGauge.visibility = View.VISIBLE

                        gaugeHandler.removeCallbacksAndMessages(null)
                        gaugeHandler.postDelayed({
                            layoutVolumeGauge.visibility = View.GONE
                            currentVolumeFloat = -1f
                        }, 1500)
                    }
                    return true
                } else {
                    return true
                }
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || isScreenLocked || touchDownOnOverlay) return false
                if (isTrackPanelVisible()) return false

                val isPortrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                val isTv = io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this@PlaybackActivity)
                val pLoc = IntArray(2)
                playerView.getLocationOnScreen(pLoc)
                val pLeft = pLoc[0].toFloat()
                val pTop = pLoc[1].toFloat()
                val pRight = pLeft + playerView.width
                val pBottom = pTop + playerView.height

                if (e1.x < pLeft || e1.x > pRight || e1.y < pTop || e1.y > pBottom) {
                    return false
                }

                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x

                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (!isControlsVisible() && !isTrackPanelVisible() && !isChannelListVisible()) {
                            if (diffX > 0) {
                                io.github.rubayet123.tvlive.data.LiveTvManager.getPreviousChannel()?.let { playChannel(it) }
                            } else {
                                io.github.rubayet123.tvlive.data.LiveTvManager.getNextChannel()?.let { playChannel(it) }
                            }
                            return true
                        }
                    }
                } else {
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (!isControlsVisible() && !isTrackPanelVisible()) {
                            if (diffY < 0) { // Fling Up
                                if (!isChannelListVisible() && (!isPortrait || isTv) && !channelSwipeTriggered) {
                                    channelSwipeTriggered = true
                                    openRecentChannels()
                                    return true
                                }
                            } else if (diffY > 0) { // Fling Down
                                if (isChannelListVisible()) {
                                    hideRecentChannels()
                                    return true
                                }
                            }
                        }
                    }
                }
                return false
            }
        })
    }

    private fun performQuickSeek(isForward: Boolean) {
        val p = player ?: return
        val dur = p.duration
        val isSeekable = p.isCurrentMediaItemSeekable && dur > 0 && dur != C.TIME_UNSET

        if (isSeekable) {
            val currentPos = p.currentPosition
            val targetPos = if (isForward) (currentPos + 10_000).coerceAtMost(dur) else (currentPos - 10_000).coerceAtLeast(0)
            p.seekTo(targetPos)

            if (isForward) {
                tvSeekRightLabel.text = "+10s"
                layoutSeekRight.visibility = View.VISIBLE
            } else {
                tvSeekLeftLabel.text = "-10s"
                layoutSeekLeft.visibility = View.VISIBLE
            }
        } else {
            if (isForward) {
                tvSeekRightLabel.text = "Next"
                layoutSeekRight.visibility = View.VISIBLE
                io.github.rubayet123.tvlive.data.LiveTvManager.getNextChannel()?.let { playChannel(it) }
            } else {
                tvSeekLeftLabel.text = "Prev"
                layoutSeekLeft.visibility = View.VISIBLE
                io.github.rubayet123.tvlive.data.LiveTvManager.getPreviousChannel()?.let { playChannel(it) }
            }
        }

        seekHandler.removeCallbacksAndMessages(null)
        seekHandler.postDelayed({
            layoutSeekLeft.visibility = View.GONE
            layoutSeekRight.visibility = View.GONE
        }, 800)
    }

    private fun showUnlockOverlay() {
        layoutUnlockOverlay.visibility = View.VISIBLE
        gaugeHandler.removeCallbacksAndMessages(null)
        gaugeHandler.postDelayed({ layoutUnlockOverlay.visibility = View.GONE }, 3000)
    }

    // ── PiP Mode Support ──────────────────────────────────────────────────────

    private fun enterPipMode() {
        if (io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val params = buildPipParams()
                enterPictureInPictureMode(params)
            } else {
                toast("PiP is not supported on this device")
            }
        } else {
            toast("PiP requires Android 8.0+")
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun buildPipParams(): android.app.PictureInPictureParams {
        val builder = android.app.PictureInPictureParams.Builder()
        builder.setAspectRatio(android.util.Rational(16, 9))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }
        val actions = ArrayList<android.app.RemoteAction>()
        val isPlaying = player?.isPlaying ?: false

        val prevIntent = android.app.PendingIntent.getBroadcast(
            this, 1, android.content.Intent("ACTION_PIP_PREV"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val prevIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_skip_prev)
        actions.add(android.app.RemoteAction(prevIcon, "Previous", "Previous channel", prevIntent))

        val playPauseIntent = android.app.PendingIntent.getBroadcast(
            this, 2, android.content.Intent("ACTION_PIP_PLAY_PAUSE"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = android.graphics.drawable.Icon.createWithResource(
            this, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        actions.add(android.app.RemoteAction(
            playPauseIcon, if (isPlaying) "Pause" else "Play",
            if (isPlaying) "Pause playback" else "Play playback", playPauseIntent
        ))

        val nextIntent = android.app.PendingIntent.getBroadcast(
            this, 3, android.content.Intent("ACTION_PIP_NEXT"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val nextIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_skip_next)
        actions.add(android.app.RemoteAction(nextIcon, "Next", "Next channel", nextIntent))

        builder.setActions(actions)
        return builder.build()
    }

    private fun updatePipParams() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this) &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                setPictureInPictureParams(buildPipParams())
            } catch (e: Exception) {
                // Ignore if PiP is not active or unavailable
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this) &&
            player?.isPlaying == true) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            hidePlayerControls()
            hideTrackPanel()
            recentChannelsOverlay.visibility = View.GONE
            channelNameView.visibility = View.GONE
            bufferLoader.visibility = View.GONE
            layoutBrightnessGauge.visibility = View.GONE
            layoutVolumeGauge.visibility = View.GONE
            layoutSeekLeft.visibility = View.GONE
            layoutSeekRight.visibility = View.GONE
            layoutUnlockOverlay.visibility = View.GONE
        } else {
            if (player?.isPlaying == false) {
                showPlayerControls()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Key event routing
    // ─────────────────────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        if (sourceDialog?.isShowing == true) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    sourceDialog?.dismiss()
                    sourceDialog = null
                    playerView.requestFocus()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // TV Channel Overlay Active Key Handling
        if (isChannelListVisible()) {
            resetChannelOverlayTimer(7000L)
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        hideRecentChannels()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    // Ignore down press when already in overlay to prevent re-triggering / freezing
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    // Let the HorizontalGridView and its child channel cards handle horizontal navigation and selection
                    return super.dispatchKeyEvent(event)
                }
            }
            return super.dispatchKeyEvent(event)
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                openRecentChannels()
            }
            return true
        }

        if (!isControlsVisible() && !isTrackPanelVisible() && !isChannelListVisible()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        showSourceSelectionDialog()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        showPlayerControls()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val prev = io.github.rubayet123.tvlive.data.LiveTvManager.getPreviousChannel()
                        if (prev != null) playChannel(prev)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val next = io.github.rubayet123.tvlive.data.LiveTvManager.getNextChannel()
                        if (next != null) playChannel(next)
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Note: LEFT / RIGHT / DOWN channel-zap handled in dispatchKeyEvent only.
        try {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isChannelListVisible()) {
                        openRecentChannels()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (!isControlsVisible() && !isTrackPanelVisible() && !isChannelListVisible() && sourceDialog?.isShowing != true) {
                        showPlayerControls(); return true
                    }
                    // Controls or panel visible — let focused view handle OK
                    resetControlsTimer()
                }
                KeyEvent.KEYCODE_BACK -> {
                    when {
                        sourceDialog?.isShowing == true -> {
                            sourceDialog?.dismiss()
                            sourceDialog = null
                            playerView.requestFocus()
                            return true
                        }
                        isTrackPanelVisible()   -> { hideTrackPanel();      return true }
                        isControlsVisible()     -> { hidePlayerControls();  return true }
                        isChannelListVisible()  -> {
                            hideRecentChannels()
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaybackActivity", "Key event error", e)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            toggleFavourite(); return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Favourites (long-press OK)
    // ─────────────────────────────────────────────────────────────────────────

    private fun toggleFavourite() {
        val current = io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel() ?: return
        val repo    = io.github.rubayet123.tvlive.data.FavoritesRepository(this)
        val isFav   = repo.isFavorite(current)
        if (isFav) repo.removeFavorite(current) else repo.addFavorite(current)
        val msg = if (isFav) "Removed from Favourites" else "Added to Favourites"
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Channel list overlay (opens on ↓)
    // ─────────────────────────────────────────────────────────────────────────
    private var channelOverlayAdapter: ArrayObjectAdapter? = null
    private val channelOverlayHideRunnable = Runnable { hideRecentChannels() }
    private val channelOverlayHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun resetChannelOverlayTimer(delayMillis: Long = 7000L) {
        channelOverlayHandler.removeCallbacks(channelOverlayHideRunnable)
        if (isChannelListVisible()) {
            channelOverlayHandler.postDelayed(channelOverlayHideRunnable, delayMillis)
        }
    }

    private fun openRecentChannels() {
        val isPortraitMobile = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
                !io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)
        if (isPortraitMobile) return
        if (isChannelListVisible()) return
        if (isControlsVisible()) {
            hidePlayerControls(immediate = true)
        }

        val rawMaster = io.github.rubayet123.tvlive.data.LiveTvManager.getMasterPlaylist()
        if (rawMaster.isEmpty()) {
            hideRecentChannels()
            android.widget.Toast.makeText(this, "No channels available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Apply category overrides and homescreen category order
        val overridden = io.github.rubayet123.tvlive.util.CategoryOverrideManager.applyOverrides(this, rawMaster)
        val ordered = io.github.rubayet123.tvlive.util.CategoryOrderManager.getCategoryOrderedChannels(this, overridden)

        // 2. Filter out hidden channels
        val all = io.github.rubayet123.tvlive.util.HiddenChannelsManager.filterVisibleChannels(this, ordered)
        if (all.isEmpty()) {
            hideRecentChannels()
            android.widget.Toast.makeText(this, "No channels available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val isTv = io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this@PlaybackActivity)
        val needsRebuild = channelOverlayAdapter == null || recentChannelsOverlay.adapter == null || channelOverlayAdapter?.size() != all.size
        if (needsRebuild) {
            val adapter = ArrayObjectAdapter(ChannelCardPresenter())
            all.forEach { adapter.add(it) }
            channelOverlayAdapter = adapter
            recentChannelsOverlay.adapter = ItemBridgeAdapter(adapter)
        }

        val currentPlaying = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
        val targetKeys = if (currentPlaying != null) io.github.rubayet123.tvlive.util.CanonicalKeyHelper.getLookupKeys(currentPlaying).toSet() else emptySet()

        val idx = if (currentPlaying != null) {
            all.indexOfFirst {
                it.id == currentPlaying.id ||
                it.streamUrl == currentPlaying.streamUrl ||
                it.name.equals(currentPlaying.name, ignoreCase = true) ||
                io.github.rubayet123.tvlive.util.CanonicalKeyHelper.getLookupKeys(it).any { k -> targetKeys.contains(k) }
            }.coerceAtLeast(0)
        } else {
            all.indexOfFirst {
                it.streamUrl == streamUrl || (channelName != null && it.name.equals(channelName, ignoreCase = true))
            }.coerceAtLeast(0)
        }

        recentChannelsOverlay.visibility = View.VISIBLE
        recentChannelsOverlay.bringToFront()
        recentChannelsOverlay.setNumRows(1)
        recentChannelsOverlay.selectedPosition = idx

        if (!isTv) {
            recentChannelsOverlay.translationY = 220f
            recentChannelsOverlay.alpha = 0.5f
            recentChannelsOverlay.animate()
                .translationY(0f)
                .alpha(1.0f)
                .setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            resetChannelOverlayTimer(5000L)
            recentChannelsOverlay.post {
                recentChannelsOverlay.selectedPosition = idx
            }
        } else {
            recentChannelsOverlay.translationY = 0f
            recentChannelsOverlay.alpha = 1.0f
            recentChannelsOverlay.requestFocus()
            recentChannelsOverlay.post {
                recentChannelsOverlay.selectedPosition = idx
                val childView = recentChannelsOverlay.layoutManager?.findViewByPosition(idx)
                if (childView != null) {
                    childView.requestFocus()
                } else {
                    recentChannelsOverlay.requestFocus()
                    recentChannelsOverlay.post {
                        recentChannelsOverlay.layoutManager?.findViewByPosition(idx)?.requestFocus()
                    }
                }
                resetChannelOverlayTimer(7000L)
            }
        }
    }

    inner class ChannelCardPresenter : Presenter() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent_channel, parent, false)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
            val ch       = item as? io.github.rubayet123.tvlive.model.Channel ?: return
            val logo     = vh.view.findViewById<android.widget.ImageView>(R.id.img_channel_logo)
            val name     = vh.view.findViewById<TextView>(R.id.tv_channel_name)
            val category = vh.view.findViewById<TextView>(R.id.tv_channel_category)
            name.text    = ch.name
            val groupName = ch.group?.trim()
            if (!groupName.isNullOrEmpty()) {
                category?.visibility = View.VISIBLE
                category?.text = groupName
            } else {
                category?.visibility = View.GONE
            }

            if (!ch.logoUrl.isNullOrEmpty()) {
                Glide.with(this@PlaybackActivity).load(ch.logoUrl)
                    .placeholder(R.drawable.fallback_logo).error(R.drawable.fallback_logo).into(logo)
            } else {
                logo.setImageResource(R.drawable.fallback_logo)
            }
            vh.view.setOnClickListener {
                hideRecentChannels()
                playChannel(ch)
            }
        }
        override fun onUnbindViewHolder(vh: ViewHolder) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top-left OSD
    // ─────────────────────────────────────────────────────────────────────────

    private fun showInfo() {
        val isPortraitMobile = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
                !io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)
        if (isPortraitMobile) {
            channelNameView.visibility = View.GONE
            return
        }
        channelNameView.visibility = View.VISIBLE
        channelNameView.postDelayed({ channelNameView.visibility = View.GONE }, 5000)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Channel switch
    // ─────────────────────────────────────────────────────────────────────────

    private var hasAttemptedFailoverProbing = false
    private val verifiedCandidatesQueue = java.util.ArrayDeque<Int>()

    private fun playChannel(channel: io.github.rubayet123.tvlive.model.Channel, sourceIndex: Int = 0, isFailover: Boolean = false) {
        if (!isFailover) {
            hasAttemptedFailoverProbing = false
            verifiedCandidatesQueue.clear()
            singleStreamRetryCount = 0
        }
        lastChannelSwitchTime = android.os.SystemClock.uptimeMillis()
        currentPlayingChannel = channel
        val rawSources = collectSourcesForChannel(channel)
        // Rank candidates based on StreamHealthManager strategy & penalties
        activeSources = io.github.rubayet123.tvlive.data.StreamHealthManager.rankCandidates(this, rawSources)
        currentSourceIndex = sourceIndex.coerceIn(0, (activeSources.size - 1).coerceAtLeast(0))

        val currentSource = activeSources.getOrNull(currentSourceIndex)
        val streamToPlay = currentSource?.streamUrl ?: channel.streamUrl

        if (streamToPlay.isEmpty()) {
            android.widget.Toast.makeText(this, "Channel has empty URL", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Dismiss all overlays
        hideRecentChannels()
        if (isTrackPanelVisible())   { trackPanelContainer.visibility = View.GONE; currentTrackType = null }
        if (isControlsVisible())     hidePlayerControls()
        sourceDialog?.dismiss()
        sourceDialog = null

        channelName = channel.name
        streamUrl   = streamToPlay
        channelNameView.text   = channelName
        
        val sourceIndicator = if (activeSources.size > 1) " • ${currentSource?.providerName}" else ""
        tvCtrlChannelName.text = channelName + sourceIndicator
        showInfo()
        selectedQualityIndices.clear()

        if (::btnSource.isInitialized) {
            btnSource.visibility = View.VISIBLE
        }
        if (::btnPortraitSource.isInitialized) {
            btnPortraitSource.visibility = View.VISIBLE
        }

        if (::tvPortraitHeaderTitle.isInitialized) {
            tvPortraitHeaderTitle.text = channel.name
            if (!channel.logoUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(channel.logoUrl)
                    .placeholder(R.drawable.ic_tv_fallback)
                    .into(portraitTopLogo)
            } else {
                portraitTopLogo.setImageResource(R.drawable.ic_tv_fallback)
            }
            portraitChannelsAdapter?.updatePlayingStreamUrl(channel.streamUrl)
        }
        if (::btnPortraitFavorite.isInitialized) {
            val isFav = io.github.rubayet123.tvlive.data.FavoritesRepository(this).isFavorite(channel)
            btnPortraitFavorite.setImageResource(if (isFav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        }

        io.github.rubayet123.tvlive.data.LiveTvManager.setCurrentChannelInMaster(channel)

        try {
            activeProbeJob?.cancel()
            bufferLoader.visibility = View.VISIBLE
            startBufferingWatchdog()
            player?.stop()

            val connectTimeoutSec = io.github.rubayet123.tvlive.data.StreamHealthConfig.getConnectTimeoutSec(this)
            val connectTimeoutMs = connectTimeoutSec * 1000

            val activeHeaders = currentSource?.headers ?: channel.headers

            lifecycleScope.launch {
                var url = streamToPlay.trim()
                when {
                    url.startsWith("damitv://") -> {
                        val slug = url.substringAfter("damitv://")
                        val r = io.github.rubayet123.tvlive.scraper.DamitvRepository(okHttpClient, this@PlaybackActivity).resolveStream(slug)
                        if (r != null) url = r else {
                            if (!attemptFailoverToNextSource("Damitv resolution failed")) {
                                if (!hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    withContext(Dispatchers.Main) {
                                        handle403ScrapeRecovery(channel)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        handleSingleStreamRecovery("Stream resolution failed")
                                    }
                                }
                            }
                            return@launch
                        }
                    }
                    url.startsWith("roarzone://") -> {
                        val r = io.github.rubayet123.tvlive.scraper.RoarzoneRepository(okHttpClient)
                            .resolveStream(url.substringAfter("roarzone://"))
                        if (r != null) url = r else {
                            if (!attemptFailoverToNextSource("Roarzone resolution failed")) {
                                if (!hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    withContext(Dispatchers.Main) {
                                        handle403ScrapeRecovery(channel)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        handleSingleStreamRecovery("Stream resolution failed")
                                    }
                                }
                            }
                            return@launch
                        }
                    }
                    url.startsWith("splex://") -> {
                        val r = io.github.rubayet123.tvlive.scraper.SplexRepository(okHttpClient)
                            .resolveStream(url.substringAfter("splex://"))
                        if (r != null) url = r else {
                            if (!attemptFailoverToNextSource("Splex resolution failed")) {
                                if (!hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    withContext(Dispatchers.Main) {
                                        handle403ScrapeRecovery(channel)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        handleSingleStreamRecovery("Stream resolution failed")
                                    }
                                }
                            }
                            return@launch
                        }
                    }
                    url.startsWith("redforce://") -> {
                        val r = io.github.rubayet123.tvlive.scraper.RedforceRepository(okHttpClient)
                            .resolveStream(url.substringAfter("redforce://"))
                        if (r != null) url = r else {
                            if (!attemptFailoverToNextSource("Redforce resolution failed")) {
                                if (!hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    withContext(Dispatchers.Main) {
                                        handle403ScrapeRecovery(channel)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        handleSingleStreamRecovery("Stream resolution failed")
                                    }
                                }
                            }
                            return@launch
                        }
                    }
                    url.startsWith("idealtv://") -> {
                        val r = io.github.rubayet123.tvlive.scraper.IdealTvRepository(okHttpClient)
                            .resolveStream(url.substringAfter("idealtv://"))
                        if (r != null) url = r else {
                            if (!attemptFailoverToNextSource("Ideal TV resolution failed")) {
                                if (!hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    withContext(Dispatchers.Main) {
                                        handle403ScrapeRecovery(channel)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        handleSingleStreamRecovery("Stream resolution failed")
                                    }
                                }
                            }
                            return@launch
                        }
                    }
                }

                val (userAgent, defaultHeaders) = buildHeadersForUrl(url, channel.copy(headers = activeHeaders))
                val httpDsf = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true).setUserAgent(userAgent)
                    .setConnectTimeoutMs(connectTimeoutMs).setReadTimeoutMs(connectTimeoutMs)
                    .setDefaultRequestProperties(defaultHeaders)
                val unwrappingHttpDsf = io.github.rubayet123.tvlive.data.network.TsUnwrappingDataSource.Factory(httpDsf)
                val dsf = androidx.media3.datasource.DefaultDataSource.Factory(this@PlaybackActivity, unwrappingHttpDsf)

                withContext(Dispatchers.Main) {
                    playerView.visibility = View.VISIBLE
                }
                val ms = createMediaSourceFactory(dsf)
                    .createMediaSource(createMediaItem(url, channel))
                player?.playWhenReady = true
                player?.setMediaSource(ms)
                player?.prepare()
                updatePipParams()
                io.github.rubayet123.tvlive.data.LiveTvManager.addToRecent(channel)
            }
        } catch (e: Exception) {
            if (!attemptFailoverToNextSource("Playback initialization error")) {
                handleSingleStreamRecovery("Error playing channel: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private var activeProbeJob: kotlinx.coroutines.Job? = null

    private suspend fun probeStreamSource(
        channel: io.github.rubayet123.tvlive.model.Channel,
        source: io.github.rubayet123.tvlive.model.StreamSource
    ): String? {
        var url = source.streamUrl.trim()
        if (url.isEmpty()) return null
        return try {
            when {
                url.startsWith("damitv://") -> {
                    val slug = url.substringAfter("damitv://")
                    url = io.github.rubayet123.tvlive.scraper.DamitvRepository(okHttpClient, this@PlaybackActivity).resolveStream(slug) ?: return null
                }
                url.startsWith("roarzone://") -> {
                    url = io.github.rubayet123.tvlive.scraper.RoarzoneRepository(okHttpClient).resolveStream(url.substringAfter("roarzone://")) ?: return null
                }
                url.startsWith("splex://") -> {
                    url = io.github.rubayet123.tvlive.scraper.SplexRepository(okHttpClient).resolveStream(url.substringAfter("splex://")) ?: return null
                }
                url.startsWith("redforce://") -> {
                    url = io.github.rubayet123.tvlive.scraper.RedforceRepository(okHttpClient).resolveStream(url.substringAfter("redforce://")) ?: return null
                }
                url.startsWith("idealtv://") -> {
                    url = io.github.rubayet123.tvlive.scraper.IdealTvRepository(okHttpClient).resolveStream(url.substringAfter("idealtv://")) ?: return null
                }
            }

            val activeHeaders = source.headers ?: channel.headers
            val (userAgent, defaultHeaders) = buildHeadersForUrl(url, channel.copy(headers = activeHeaders))

            val reqBuilder = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
            for ((k, v) in defaultHeaders) {
                reqBuilder.header(k, v)
            }

            val fastClient = okHttpClient.newBuilder()
                .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            fastClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful || response.code in 300..308 || response.code == 206 || response.code == 405 || response.code == 403 || response.code == 503) {
                    url
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun attemptFailoverToNextSource(reason: String): Boolean {
        val channel = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel() ?: return false
        val currentStreamUrl = activeSources.getOrNull(currentSourceIndex)?.streamUrl ?: channel.streamUrl
        io.github.rubayet123.tvlive.data.StreamHealthManager.recordFailure(this, currentStreamUrl)

        if (!io.github.rubayet123.tvlive.data.StreamHealthConfig.isAutoSwitchEnabled(this)) {
            return false
        }

        val sources = activeSources.ifEmpty { channel.effectiveSources }
        if (sources.size <= 1) {
            handleSingleStreamRecovery("Channel currently unavailable", skipReconnectRetries = true)
            return true
        }

        val mode = io.github.rubayet123.tvlive.data.StreamHealthConfig.getFailoverMode(this)
        val hudMode = io.github.rubayet123.tvlive.data.StreamHealthConfig.getFailoverHudMode(this)

        if (mode == "SEQUENTIAL") {
            val nextIndex = currentSourceIndex + 1
            if (nextIndex < sources.size) {
                val nextSource = sources[nextIndex]
                showHudNotification("Switching to backup stream: ${nextSource.providerName}")
                playChannel(channel, nextIndex, isFailover = true)
                return true
            } else {
                handleSingleStreamRecovery("Channel currently unavailable", skipReconnectRetries = true)
                return true
            }
        } else {
            // CONCURRENT mode
            if (!verifiedCandidatesQueue.isEmpty()) {
                val nextCandidateIndex = verifiedCandidatesQueue.poll()
                if (nextCandidateIndex != null && nextCandidateIndex < sources.size) {
                    val candidateSource = sources[nextCandidateIndex]
                    showHudNotification("Connected to ${candidateSource.providerName}")
                    playChannel(channel, nextCandidateIndex, isFailover = true)
                    return true
                }
            }

            if (hasAttemptedFailoverProbing) {
                handleSingleStreamRecovery("Channel currently unavailable", skipReconnectRetries = true)
                return true
            }

            val remainingIndices = (0 until sources.size).filter { it != currentSourceIndex }
            if (remainingIndices.isEmpty()) {
                handleSingleStreamRecovery("Channel currently unavailable", skipReconnectRetries = true)
                return true
            }

            hasAttemptedFailoverProbing = true
            activeProbeJob?.cancel()

            showHudNotification("Checking backup streams...")

            activeProbeJob = lifecycleScope.launch(Dispatchers.IO) {
                val successfulIndices = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, Long>>())
                val finishedCount = java.util.concurrent.atomic.AtomicInteger(0)

                val probeJobs = remainingIndices.map { index ->
                    launch(Dispatchers.IO) {
                        val startTime = android.os.SystemClock.elapsedRealtime()
                        val src = sources[index]
                        val workingUrl = probeStreamSource(channel, src)
                        if (workingUrl != null) {
                            val duration = android.os.SystemClock.elapsedRealtime() - startTime
                            successfulIndices.add(Pair(index, duration))
                        }
                        finishedCount.incrementAndGet()
                    }
                }

                probeJobs.forEach { it.join() }

                withContext(Dispatchers.Main) {
                    if (successfulIndices.isNotEmpty()) {
                        // Sort by response time (fastest first)
                        successfulIndices.sortBy { it.second }
                        verifiedCandidatesQueue.clear()
                        for (pair in successfulIndices) {
                            verifiedCandidatesQueue.add(pair.first)
                        }

                        val firstWinningIndex = verifiedCandidatesQueue.poll()
                        if (firstWinningIndex != null) {
                            val winningSource = sources[firstWinningIndex]
                            showHudNotification("Connected to ${winningSource.providerName}")
                            playChannel(channel, firstWinningIndex, isFailover = true)
                        } else {
                            handleSingleStreamRecovery("Channel currently unavailable", skipReconnectRetries = true)
                        }
                    } else {
                        handleSingleStreamRecovery("Channel currently unavailable", skipReconnectRetries = true)
                    }
                }
            }
            return true
        }
    }

    private fun handleSingleStreamRecovery(reason: String, skipReconnectRetries: Boolean = false) {
        runOnUiThread {
            bufferLoader.visibility = View.GONE
            cancelBufferingWatchdog()

            val channel = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
            val sources = activeSources.ifEmpty { channel?.effectiveSources ?: emptyList() }
            val isMultiSource = sources.size > 1 || hasAttemptedFailoverProbing

            val isAutoReconnect = io.github.rubayet123.tvlive.data.StreamHealthConfig.isAutoReconnectEnabled(this)
            val maxAttempts = io.github.rubayet123.tvlive.data.StreamHealthConfig.getMaxReconnectAttempts(this)

            if (!skipReconnectRetries && !isMultiSource && isAutoReconnect && channel != null && singleStreamRetryCount < maxAttempts) {
                singleStreamRetryCount++
                val delayMs = (singleStreamRetryCount * 1500L).coerceAtMost(6000L)
                showHudNotification("Reconnecting stream (Attempt $singleStreamRetryCount/$maxAttempts)...")
                bufferingWatchdogHandler.postDelayed({
                    playChannel(channel, currentSourceIndex)
                }, delayMs)
                return@runOnUiThread
            }

            // Exhausted all retries or auto-reconnect disabled
            singleStreamRetryCount = 0
            val failureAction = io.github.rubayet123.tvlive.data.StreamHealthConfig.getFailureAction(this)
            when (failureAction) {
                "NEXT_CHANNEL" -> {
                    showHudNotification("Stream offline. Skipping to next channel...")
                    val next = io.github.rubayet123.tvlive.data.LiveTvManager.getNextChannel()
                    if (next != null) {
                        playChannel(next)
                    } else {
                        finish()
                    }
                }
                "EXIT" -> {
                    showHudNotification("Stream offline. Returning to home.")
                    finish()
                }
                else -> {
                    showHudNotification("Stream offline: $reason")
                }
            }
        }
    }

    private fun collectSourcesForChannel(channel: io.github.rubayet123.tvlive.model.Channel): List<io.github.rubayet123.tvlive.model.StreamSource> {
        val list = mutableListOf<io.github.rubayet123.tvlive.model.StreamSource>()
        // 1. Channel's own effective sources
        list.addAll(channel.effectiveSources)

        // 2. Discover matching sources from master playlist
        val normTarget = normalizeChannelNameForMatching(channel.name)
        if (normTarget.isNotEmpty()) {
            val allChannels = io.github.rubayet123.tvlive.data.LiveTvManager.getMasterPlaylist()
            for (other in allChannels) {
                if (other.id == channel.id && other.streamUrl == channel.streamUrl) continue
                val normOther = normalizeChannelNameForMatching(other.name)
                if (normOther == normTarget || (normTarget.length >= 3 && normOther == normTarget)) {
                    for (src in other.effectiveSources) {
                        if (list.none { it.streamUrl.equals(src.streamUrl, ignoreCase = true) }) {
                            list.add(src)
                        }
                    }
                }
            }
        }
        return if (list.isNotEmpty()) list else channel.effectiveSources
    }

    private fun normalizeChannelNameForMatching(raw: String): String {
        return raw.lowercase()
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
            .replace("bdix", "")
            .replace("hd", "")
            .replace("fhd", "")
            .replace("4k", "")
            .replace("sd", "")
            .replace("tv", "")
            .replace("live", "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun showSourceSelectionDialog() {
        val channel = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel() ?: return
        val sources = collectSourcesForChannel(channel)
        activeSources = sources

        if (sources.isEmpty()) {
            toast("No stream source available")
            return
        }

        val items = sources.mapIndexed { idx, src ->
            val isCurrent = (idx == currentSourceIndex)
            val tag = if (isCurrent) {
                if (sources.size == 1) "  [Active • Press OK to Reload]" else "  [Active]"
            } else {
                "  [Backup]"
            }
            "${idx + 1}. ${src.providerName}$tag"
        }.toTypedArray()

        val isTv = io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)
        val builder = if (isTv) {
            android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        } else {
            android.app.AlertDialog.Builder(this)
        }

        sourceDialog?.dismiss()

        val subtitle = if (sources.size == 1) "1 Source available" else "${sources.size} Sources available"
        val dialog = builder.setTitle("Stream Sources (${channel.name})\n$subtitle")
            .setSingleChoiceItems(items, currentSourceIndex) { d, which ->
                d.dismiss()
                sourceDialog = null
                if (which != currentSourceIndex) {
                    toast("Switching to ${sources[which].providerName}...")
                    playChannel(channel, which)
                } else {
                    toast("Reloading ${sources[which].providerName}...")
                    playChannel(channel, which)
                }
                playerView.requestFocus()
            }
            .setNegativeButton("Close") { d, _ ->
                d.dismiss()
                sourceDialog = null
                playerView.requestFocus()
            }
            .setOnDismissListener {
                sourceDialog = null
                playerView.requestFocus()
            }
            .create()

        sourceDialog = dialog
        dialog.show()
    }

    private fun toast(msg: String) {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            activeToast?.cancel()
            val t = android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_SHORT)
            activeToast = t
            t.show()
        }
    }

    private fun showHudNotification(message: String) {
        val hudMode = io.github.rubayet123.tvlive.data.StreamHealthConfig.getFailoverHudMode(this)
        if (hudMode == "SILENT") return

        if (hudMode == "TOAST") {
            toast(message)
            return
        }

        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val tv = hudOverlayView ?: findViewById<TextView>(R.id.tv_hud_status) ?: return@runOnUiThread
            tv.text = message
            tv.visibility = View.VISIBLE
            tv.animate().cancel()
            tv.alpha = 1.0f

            hudHideHandler.removeCallbacks(hudHideRunnable)
            hudHideHandler.postDelayed(hudHideRunnable, 2500L)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Player initialisation
    // ─────────────────────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        if (player != null) return

        val url = streamUrl?.trim()
        if (url.isNullOrEmpty()) {
            toast("Error: Empty Stream URL"); return
        }

        val currentChannel = io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
        val (userAgent, defaultHeaders) = buildHeadersForUrl(url, currentChannel)

        val connectTimeoutSec = io.github.rubayet123.tvlive.data.StreamHealthConfig.getConnectTimeoutSec(this)
        val connectTimeoutMs = connectTimeoutSec * 1000

        val httpDsf = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true).setUserAgent(userAgent)
            .setConnectTimeoutMs(connectTimeoutMs).setReadTimeoutMs(connectTimeoutMs)
            .setDefaultRequestProperties(defaultHeaders)
        val unwrappingHttpDsf = io.github.rubayet123.tvlive.data.network.TsUnwrappingDataSource.Factory(httpDsf)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, unwrappingHttpDsf)

        val bufferProfile = io.github.rubayet123.tvlive.data.StreamHealthConfig.getBufferProfile(this)
        val (minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForRebufferMs) = when (bufferProfile) {
            "FAST_ZAPPING" -> listOf(5_000, 20_000, 500, 1_000)
            "HIGH_STABILITY" -> listOf(30_000, 90_000, 3_000, 5_000)
            else -> listOf(15_000, 50_000, 1_500, 2_000)
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForRebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedSampleRateAdaptiveness(true)
                .setAllowAudioNonSeamlessAdaptiveness(true)
        )

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context))
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(true)
                    .build()
            }
        }
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            .setEnableAudioTrackPlaybackParams(true)

        val isFfmpegAvailable = try {
            androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()
        } catch (e: Throwable) {
            false
        }
        android.util.Log.d("TVLive_Playback", "FFmpeg Software Decoder available: $isFfmpegAvailable")

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(
                createMediaSourceFactory(dataSourceFactory)
            )
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()

        // Apply global H.264-first + 1080p/8 Mbps cap for ALL streams
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setMaxVideoSize(1920, 1080)
            .setMaxVideoBitrate(8_000_000)
            .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_VP9)
            .setForceLowestBitrate(false)
            .build()

        playerView.player = player
        playerView.keepScreenOn = true

        player!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                bufferLoader.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_BUFFERING) {
                    startBufferingWatchdog()
                } else {
                    cancelBufferingWatchdog()
                }
                if (state == Player.STATE_READY) {
                    singleStreamRetryCount = 0
                    hasAttempted403ScrapeRecovery = false
                    hudHideHandler.removeCallbacks(hudHideRunnable)
                    hudOverlayView?.visibility = View.GONE
                    io.github.rubayet123.tvlive.data.StreamHealthManager.recordSuccess(url)
                    updatePlayPauseButton()
                    updatePipParams()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton()
                updatePipParams()
            }

            override fun onTracksChanged(tracks: Tracks) = updateTrackButtonVisibility(tracks)

            override fun onPlayerError(error: PlaybackException) {
                cancelBufferingWatchdog()
                bufferLoader.visibility = View.GONE
                android.util.Log.e("TVLive_Playback", "Player Error: ${error.message}", error)

                if (attemptFailoverToNextSource("Player error: ${error.message}")) {
                    return
                }

                // Auto-recover: switch to a supported audio track on codec failure
                if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.message?.contains("MediaCodec") == true) {
                    val other = player?.currentTracks?.groups?.firstOrNull { g ->
                        g.type == C.TRACK_TYPE_AUDIO && !g.isTrackSelected(0) && g.isSupported
                    }
                    if (other != null) {
                        player?.trackSelectionParameters = player!!.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(other.mediaTrackGroup, 0))
                            .build()
                        player?.prepare(); player?.play(); return
                    }
                }

                // BehindLiveWindow & HTTP 403 forbidden recovery
                val isBehind    = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                    isBehindLiveWindow(error) || isBehindLiveWindow(error.cause)
                val isForbidden = isForbiddenError(error) || isForbiddenError(error.cause)
                val isNotFound  = isNotFoundError(error) || isNotFoundError(error.cause)
                val httpStatusCode = getHttpResponseCode(error) ?: getHttpResponseCode(error.cause)

                if (isBehind || isForbidden) {
                    val ch = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
                    if (isBehind) {
                        player?.let { p ->
                            val item = p.currentMediaItem ?: return@let
                            p.setMediaItem(item); p.prepare(); p.playWhenReady = true
                        }; return
                    } else if (isForbidden && ch != null && !hasAttempted403ScrapeRecovery) {
                        hasAttempted403ScrapeRecovery = true
                        handle403ScrapeRecovery(ch)
                        return
                    }
                }

                // Check for connection timeout / unreachable host or gateway server errors (500, 502, 503, 504)
                val isServerError = httpStatusCode in 500..599
                val isTimeoutOrUnreachable = isServerError ||
                    error.cause is java.net.SocketTimeoutException ||
                    error.cause?.cause is java.net.SocketTimeoutException ||
                    error.cause is java.net.ConnectException ||
                    error.cause?.cause is java.net.ConnectException ||
                    error.cause is java.net.UnknownHostException ||
                    error.cause?.cause is java.net.UnknownHostException ||
                    error.cause is java.net.NoRouteToHostException ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

                if (isTimeoutOrUnreachable) {
                    val ch = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
                    if (ch != null && !hasAttempted403ScrapeRecovery && (ch.streamUrl.startsWith("roarzone://") || ch.streamUrl.startsWith("splex://") || ch.streamUrl.startsWith("redforce://") || ch.streamUrl.startsWith("damitv://"))) {
                        hasAttempted403ScrapeRecovery = true
                        handle403ScrapeRecovery(ch)
                        return
                    }
                }

                error.printStackTrace()
                val msg = when {
                    isServerError -> "Server Error (HTTP $httpStatusCode Bad Gateway)"
                    isTimeoutOrUnreachable -> "Stream unreachable or timed out"
                    isNotFound -> "Stream not found (HTTP 404)"
                    isForbidden -> "Provider Blocked: HTTP 403"
                    httpStatusCode != null -> "Provider Error: HTTP $httpStatusCode"
                    error.errorCode == PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM License Failed"
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Provider Error (HTTP status)"
                    else -> "Playback error: ${error.message ?: "Source error"}"
                }
                handleSingleStreamRecovery(msg)
            }
        })

        lifecycleScope.launch {
            var urlToPlay = url
            when {
                urlToPlay.startsWith("damitv://") -> {
                    val slug = urlToPlay.substringAfter("damitv://")
                    val r = io.github.rubayet123.tvlive.scraper.DamitvRepository(okHttpClient, this@PlaybackActivity).resolveStream(slug)
                    if (r != null) urlToPlay = r else {
                        withContext(Dispatchers.Main) {
                            if (!attemptFailoverToNextSource("Damitv resolution failed")) {
                                val ch = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
                                if (ch != null && !hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    handle403ScrapeRecovery(ch)
                                } else {
                                    handleSingleStreamRecovery("Stream resolution failed")
                                }
                            }
                        }
                        return@launch
                    }
                }
                urlToPlay.startsWith("roarzone://") -> {
                    val r = io.github.rubayet123.tvlive.scraper.RoarzoneRepository(okHttpClient)
                        .resolveStream(urlToPlay.substringAfter("roarzone://"))
                    if (r != null) urlToPlay = r else {
                        withContext(Dispatchers.Main) {
                            if (!attemptFailoverToNextSource("Roarzone resolution failed")) {
                                val ch = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
                                if (ch != null && !hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    handle403ScrapeRecovery(ch)
                                } else {
                                    handleSingleStreamRecovery("Stream resolution failed")
                                }
                            }
                        }
                        return@launch
                    }
                }
                urlToPlay.startsWith("splex://") -> {
                    val r = io.github.rubayet123.tvlive.scraper.SplexRepository(okHttpClient)
                        .resolveStream(urlToPlay.substringAfter("splex://"))
                    if (r != null) urlToPlay = r else {
                        withContext(Dispatchers.Main) {
                            if (!attemptFailoverToNextSource("Splex resolution failed")) {
                                val ch = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
                                if (ch != null && !hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    handle403ScrapeRecovery(ch)
                                } else {
                                    handleSingleStreamRecovery("Stream resolution failed")
                                }
                            }
                        }
                        return@launch
                    }
                }
                urlToPlay.startsWith("redforce://") -> {
                    val r = io.github.rubayet123.tvlive.scraper.RedforceRepository(okHttpClient)
                        .resolveStream(urlToPlay.substringAfter("redforce://"))
                    if (r != null) urlToPlay = r else {
                        withContext(Dispatchers.Main) {
                            if (!attemptFailoverToNextSource("Redforce resolution failed")) {
                                val ch = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
                                if (ch != null && !hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    handle403ScrapeRecovery(ch)
                                } else {
                                    handleSingleStreamRecovery("Stream resolution failed")
                                }
                            }
                        }
                        return@launch
                    }
                }
                urlToPlay.startsWith("idealtv://") -> {
                    val r = io.github.rubayet123.tvlive.scraper.IdealTvRepository(okHttpClient)
                        .resolveStream(urlToPlay.substringAfter("idealtv://"))
                    if (r != null) urlToPlay = r else {
                        withContext(Dispatchers.Main) {
                            if (!attemptFailoverToNextSource("Ideal TV resolution failed")) {
                                val ch = currentPlayingChannel ?: io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
                                if (ch != null && !hasAttempted403ScrapeRecovery) {
                                    hasAttempted403ScrapeRecovery = true
                                    handle403ScrapeRecovery(ch)
                                } else {
                                    handleSingleStreamRecovery("Stream resolution failed")
                                }
                            }
                        }
                        return@launch
                    }
                }
            }
            withContext(Dispatchers.Main) {
                playerView.visibility = View.VISIBLE
                bufferLoader.visibility = View.VISIBLE
                startBufferingWatchdog(12_000L)
            }

            val (userAgent, defaultHeaders) = buildHeadersForUrl(urlToPlay, currentChannel)
            val httpDsf = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true).setUserAgent(userAgent)
                .setConnectTimeoutMs(connectTimeoutMs).setReadTimeoutMs(connectTimeoutMs)
                .setDefaultRequestProperties(defaultHeaders)
            val unwrappingHttpDsf = io.github.rubayet123.tvlive.data.network.TsUnwrappingDataSource.Factory(httpDsf)
            val resolvedDsf = androidx.media3.datasource.DefaultDataSource.Factory(this@PlaybackActivity, unwrappingHttpDsf)

            val mediaItem    = createMediaItem(urlToPlay, currentChannel)
            val mediaSource  = createMediaSourceFactory(resolvedDsf)
                .createMediaSource(mediaItem)
            player!!.playWhenReady = true
            player!!.setMediaSource(mediaSource)
            player!!.prepare()
        }
    }

    private fun createMediaSourceFactory(dataSourceFactory: androidx.media3.datasource.DataSource.Factory): androidx.media3.exoplayer.source.DefaultMediaSourceFactory {
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
            )
            .setTsExtractorTimestampSearchBytes(1500 * androidx.media3.extractor.ts.TsExtractor.TS_PACKET_SIZE)
        return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1))
    }

    private var hasAttempted403ScrapeRecovery = false

    private fun handle403ScrapeRecovery(channel: io.github.rubayet123.tvlive.model.Channel) {
        toast("Updating plugin channels in background...")
        bufferLoader.visibility = View.VISIBLE
        startBufferingWatchdog(25_000L)

        lifecycleScope.launch {
            try {
                val context = applicationContext
                val scraped = io.github.rubayet123.tvlive.scraper.PluginScraperManager.forceScrapeForChannel(context, channel)
                if (scraped) {
                    val newMaster = io.github.rubayet123.tvlive.scraper.PluginScraperManager.reloadMasterPlaylist(context)
                    val updatedChannel = newMaster.find { it.name.equals(channel.name, ignoreCase = true) }
                    if (updatedChannel != null) {
                        io.github.rubayet123.tvlive.data.LiveTvManager.setCurrentChannelInMaster(updatedChannel)
                        withContext(Dispatchers.Main) {
                            playChannel(updatedChannel)
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackActivity", "Plugin scrape recovery failed", e)
            }
            withContext(Dispatchers.Main) {
                toast("Could not refresh plugin channels.")
                bufferLoader.visibility = View.GONE
                cancelBufferingWatchdog()
            }
        }
    }

    private fun releasePlayer() {
        player?.release(); player = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MediaItem builder (handles DRM / MIME hints)
    // ─────────────────────────────────────────────────────────────────────────

    private fun createMediaItem(url: String, channel: io.github.rubayet123.tvlive.model.Channel?): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        val sUrl = url.lowercase()
        when {
            sUrl.contains(".mpd")  -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            sUrl.contains(".m3u8") || sUrl.contains("stvp") || sUrl.contains("jmp2.uk")
                                   -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            sUrl.endsWith(".ts") || sUrl.contains("/ts2/") || sUrl.contains("video/mp2t")
                                   -> builder.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        if (channel != null && !channel.licenseKey.isNullOrEmpty()) {
            try {
                val parts = channel.licenseKey.split(":")
                if (parts.size == 2) {
                    val json    = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"${hexToBase64Url(parts[1])}\",\"kid\":\"${hexToBase64Url(parts[0])}\"}]}"
                    val dataUri = "data:application/json,$json"
                    builder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                            .setLicenseUri(android.net.Uri.parse(dataUri)).build()
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        return builder.build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun isForbiddenError(error: Throwable?): Boolean = isHttpErrorCode(error, 403)

    private fun isNotFoundError(error: Throwable?): Boolean = isHttpErrorCode(error, 404)

    private fun isHttpErrorCode(error: Throwable?, targetCode: Int): Boolean {
        var curr: Throwable? = error
        while (curr != null) {
            if (curr is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && curr.responseCode == targetCode) {
                return true
            }
            curr = curr.cause
        }
        return false
    }

    private fun getHttpResponseCode(error: Throwable?): Int? {
        var curr: Throwable? = error
        while (curr != null) {
            if (curr is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                return curr.responseCode
            }
            curr = curr.cause
        }
        return null
    }

    private fun isBehindLiveWindow(error: Throwable?): Boolean {
        var curr: Throwable? = error
        while (curr != null) {
            if (curr is androidx.media3.exoplayer.source.BehindLiveWindowException) {
                return true
            }
            curr = curr.cause
        }
        return false
    }

    private fun hexToBase64Url(hex: String): String {
        val bytes = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2)
            bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        return android.util.Base64.encodeToString(
            bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    /**
     * Centralised provider-header detection.
     * Returns (userAgent, headers) for the given stream URL.
     * Update this ONE place to affect all playback paths.
     */
    private fun buildHeadersForUrl(
        url: String,
        channel: io.github.rubayet123.tvlive.model.Channel?
    ): Pair<String, Map<String, String>> {
        var userAgent = channel?.headers?.get("User-Agent") ?: "VLC/3.0.18 LibVLC/3.0.18"
        val headers   = mutableMapOf("Accept" to "*/*")
        val sUrl      = url.lowercase()
        when {
            sUrl.contains("sil1li1lili") -> {
                userAgent        = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                headers["Referer"] = "https://sil1li1liliil1lii1lil1i1lilil.c3ryzwftlmltb3r2lm5lda.online/"
            }
            sUrl.contains("stvp") || sUrl.contains("jmp2.uk") -> {
                userAgent          = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                headers["Referer"] = "https://www.samsung.com/"
                headers["Origin"]  = "https://www.samsung.com/"
            }
            sUrl.contains("splex.live") || sUrl.startsWith("splex://") -> {
                userAgent          = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"
                val sid            = channel?.id?.filter { it.isDigit() } ?: ""
                headers["Referer"] = if (sid.isNotEmpty()) "https://splex.live/play.php?stream=$sid" else "https://splex.live/"
                headers["Origin"]  = "https://splex.live"
                headers["X-Requested-With"]  = "com.android.chrome"
                headers["Accept-Language"]   = "en-US,en;q=0.9"
                headers["Sec-Fetch-Dest"]    = "video"
                headers["Sec-Fetch-Mode"]    = "cors"
                headers["Sec-Fetch-Site"]    = "cross-site"
            }
            sUrl.contains("roarzone.info") || sUrl.startsWith("roarzone://") -> {
                userAgent          = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"
                headers["Referer"] = "https://tv.roarzone.info/"
                headers["Origin"]  = "https://tv.roarzone.info"
                headers["sec-ch-ua"]          = "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\""
                headers["sec-ch-ua-mobile"]   = "?1"
                headers["sec-ch-ua-platform"] = "\"Android\""
                headers["sec-fetch-dest"]     = "empty"
                headers["sec-fetch-mode"]     = "cors"
                headers["sec-fetch-site"]     = "same-site"
                headers["Accept-Language"]    = "en-US,en;q=0.9,bn-BD;q=0.8,bn;q=0.7"
            }
            sUrl.contains("redforce.live") || sUrl.startsWith("redforce://") -> {
                userAgent          = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"
                headers["Referer"] = "http://redforce.live/"
                headers["Origin"]  = "http://redforce.live"
                headers["Accept"]  = "*/*"
                headers["Accept-Language"] = "en-US,en;q=0.9,bn-BD;q=1.0"
            }
            sUrl.contains("172.16.60.2") || sUrl.startsWith("idealtv://") -> {
                userAgent          = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                val sid            = channel?.id?.filter { it.isDigit() } ?: ""
                headers["Referer"] = if (sid.isNotEmpty()) "http://172.16.60.2/img/play.php?stream=$sid" else "http://172.16.60.2/"
                headers["Origin"]  = "http://172.16.60.2"
                headers["Accept"]  = "*/*"
                headers["Accept-Language"] = "en-US,en;q=0.9,bn-BD;q=0.8,bn;q=0.7"
            }
            sUrl.contains("172.19.17.") || sUrl.startsWith("orbittv://") -> {
                userAgent          = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                headers["Referer"] = "http://172.19.17.3:8090/"
                headers["Origin"]  = "http://172.19.17.3:8090"
                headers["Accept"]  = "*/*"
                headers["Accept-Language"] = "en-US,en;q=0.9,bn-BD;q=0.8,bn;q=0.7"
            }
            sUrl.contains("damitv") || sUrl.contains("ondemand.st") || sUrl.startsWith("damitv://") -> {
                userAgent          = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                headers["Referer"] = "https://damitv.st/"
                headers["Origin"]  = "https://damitv.st"
                headers["Sec-Fetch-Dest"] = "empty"
                headers["Sec-Fetch-Mode"] = "cors"
                headers["Sec-Fetch-Site"] = "cross-site"
                headers["Accept"]  = "*/*"
            }
        }
        // M3U per-channel headers always override auto-detection
        channel?.headers?.forEach { (k, v) ->
            if (k == "User-Agent") userAgent = v else headers[k] = v
        }
        return userAgent to headers
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mobile Portrait Section Helper Logic & Adapters
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateMobilePortraitLayout(config: android.content.res.Configuration = resources.configuration) {
        if (!::mobilePortraitPanel.isInitialized) return

        if (io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            findViewById<View>(R.id.root_playback_container)?.setPadding(0, 0, 0, 0)
            if (::portraitTopBar.isInitialized) portraitTopBar.visibility = View.GONE
            mobilePortraitPanel.visibility = View.GONE
            val lp = playerContainer.layoutParams as LinearLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            playerContainer.layoutParams = lp
            btnAspectRatio.visibility = View.VISIBLE
            btnRotate.visibility = View.GONE
            btnLock.visibility = View.GONE
            btnAspectRatio.nextFocusRightId = R.id.btn_aspect_ratio
            return
        }

        val rootContainer = findViewById<View>(R.id.root_playback_container)
        val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (isLandscape) {
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            rootContainer?.setPadding(0, 0, 0, 0)
        } else {
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            rootContainer?.setPadding(0, 0, 0, 0)
        }

        val density = resources.displayMetrics.density
        val horizPx = if (isLandscape) (32 * density).toInt() else (16 * density).toInt()
        playerControls.setPadding(horizPx, (40 * density).toInt(), horizPx, (16 * density).toInt())

        if (isLandscape) {
            if (::portraitTopBar.isInitialized) portraitTopBar.visibility = View.GONE
            mobilePortraitPanel.visibility = View.GONE
            val lp = playerContainer.layoutParams as LinearLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            playerContainer.layoutParams = lp
            btnAspectRatio.visibility = View.VISIBLE
            btnRotate.visibility = View.VISIBLE
            btnLock.visibility = View.VISIBLE
            btnPip.visibility = if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) View.VISIBLE else View.GONE
        } else {
            if (::portraitTopBar.isInitialized) portraitTopBar.visibility = View.VISIBLE
            mobilePortraitPanel.visibility = View.VISIBLE
            recentChannelsOverlay.visibility = View.GONE
            playerControls.visibility = View.GONE
            val videoHeight = (resources.displayMetrics.widthPixels * 9) / 16
            val lp = playerContainer.layoutParams as LinearLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = videoHeight
            playerContainer.layoutParams = lp

            // In portrait mode: hide overlay controls; start portrait seekbar updates
            btnAspectRatio.visibility = View.VISIBLE
            btnPip.visibility = View.GONE
            btnLock.visibility = View.GONE
            btnRotate.visibility = View.VISIBLE

            updateSeekBarProgress()
            startSeekBarUpdater()

            setupMobilePortraitChannelsSection()
        }
    }

    private fun setupMobilePortraitChannelsSection() {
        val currentChannel = io.github.rubayet123.tvlive.data.LiveTvManager.getCurrentChannel()
        val titleText = currentChannel?.name ?: channelName ?: "Live TV"
        if (::tvPortraitHeaderTitle.isInitialized) {
            tvPortraitHeaderTitle.text = titleText
            if (!currentChannel?.logoUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(currentChannel?.logoUrl)
                    .placeholder(R.drawable.ic_tv_fallback)
                    .into(portraitTopLogo)
            } else {
                portraitTopLogo.setImageResource(R.drawable.ic_tv_fallback)
            }
        }
        if (::btnPortraitFavorite.isInitialized && currentChannel != null) {
            val isFav = io.github.rubayet123.tvlive.data.FavoritesRepository(this).isFavorite(currentChannel)
            btnPortraitFavorite.setImageResource(if (isFav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        }

        val masterChannels = io.github.rubayet123.tvlive.data.LiveTvManager.getMasterPlaylist()
        val categories = mutableListOf("All")
        val groups = masterChannels.mapNotNull { it.group?.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        categories.addAll(groups)

        if (portraitCategoryRecycler.adapter == null) {
            portraitCategoryRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            portraitCategoriesAdapter = PortraitCategoriesAdapter(categories) { selected ->
                selectedPortraitCategory = selected
                filterAndSetChannels()
            }
            portraitCategoryRecycler.adapter = portraitCategoriesAdapter
        }

        if (portraitChannelsRecycler.adapter == null) {
            portraitChannelsRecycler.layoutManager = LinearLayoutManager(this)
            portraitChannelsAdapter = PortraitChannelsAdapter { ch ->
                playChannel(ch)
            }
            portraitChannelsRecycler.adapter = portraitChannelsAdapter
        }

        filterAndSetChannels()
    }

    private fun filterAndSetChannels() {
        val master = io.github.rubayet123.tvlive.data.LiveTvManager.getMasterPlaylist()
        val filtered = if (selectedPortraitCategory == "All") {
            master
        } else {
            master.filter { it.group?.equals(selectedPortraitCategory, ignoreCase = true) == true }
        }
        portraitChannelsAdapter?.submitList(filtered, streamUrl)
    }

    private inner class PortraitCategoriesAdapter(
        private val categories: List<String>,
        private val onSelected: (String) -> Unit
    ) : RecyclerView.Adapter<PortraitCategoriesAdapter.ViewHolder>() {

        private var selectedIndex = 0

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val density = parent.resources.displayMetrics.density
            val tv = TextView(parent.context).apply {
                id = android.R.id.text1
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding((14 * density).toInt(), (6 * density).toInt(), (14 * density).toInt(), (6 * density).toInt())
                val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, (8 * density).toInt(), 0)
                layoutParams = params
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cat = categories[position]
            holder.tvName.text = cat
            val isSelected = position == selectedIndex
            val density = holder.itemView.resources.displayMetrics.density
            if (isSelected) {
                holder.tvName.setTextColor(android.graphics.Color.WHITE)
                holder.tvName.setTypeface(null, android.graphics.Typeface.BOLD)
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#10B981"))
                    cornerRadius = 20 * density
                }
                holder.tvName.background = shape
            } else {
                holder.tvName.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                holder.tvName.setTypeface(null, android.graphics.Typeface.NORMAL)
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#222530"))
                    cornerRadius = 20 * density
                }
                holder.tvName.background = shape
            }

            holder.view.setOnClickListener {
                val old = selectedIndex
                selectedIndex = holder.adapterPosition
                if (old in categories.indices) notifyItemChanged(old)
                if (selectedIndex in categories.indices) notifyItemChanged(selectedIndex)
                onSelected(cat)
            }
        }

        override fun getItemCount(): Int = categories.size
    }

    private inner class PortraitChannelsAdapter(
        private val onClick: (io.github.rubayet123.tvlive.model.Channel) -> Unit
    ) : RecyclerView.Adapter<PortraitChannelsAdapter.ViewHolder>() {

        private var channels = listOf<io.github.rubayet123.tvlive.model.Channel>()
        private var activeStreamUrl: String? = null

        fun submitList(list: List<io.github.rubayet123.tvlive.model.Channel>, currentUrl: String?) {
            channels = list
            activeStreamUrl = currentUrl
            notifyDataSetChanged()
        }

        fun updatePlayingStreamUrl(url: String?) {
            activeStreamUrl = url
            notifyDataSetChanged()
        }

        inner class ViewHolder(
            val container: LinearLayout,
            val imgLogo: ImageView,
            val tvName: TextView,
            val tvGroup: TextView
        ) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val density = parent.resources.displayMetrics.density
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val marginHoriz = (12 * density).toInt()
                val marginVert = (4 * density).toInt()
                val padHoriz = (14 * density).toInt()
                val padVert = (10 * density).toInt()
                val lp = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(marginHoriz, marginVert, marginHoriz, marginVert)
                }
                layoutParams = lp
                setPadding(padHoriz, padVert, padHoriz, padVert)
            }

            val img = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams((42 * density).toInt(), (42 * density).toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(R.drawable.ic_tv_fallback)
            }

            val textLayout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins((12 * density).toInt(), 0, (8 * density).toInt(), 0)
                }
            }

            val tvName = TextView(parent.context).apply {
                setTextColor(android.graphics.Color.WHITE)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val tvGroup = TextView(parent.context).apply {
                setTextColor(android.graphics.Color.parseColor("#888888"))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            textLayout.addView(tvName)
            textLayout.addView(tvGroup)

            container.addView(img)
            container.addView(textLayout)

            return ViewHolder(container, img, tvName, tvGroup)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ch = channels[position]
            holder.tvName.text = ch.name
            holder.tvGroup.text = ch.group ?: "Live Stream"

            if (!ch.logoUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(ch.logoUrl)
                    .placeholder(R.drawable.ic_tv_fallback)
                    .into(holder.imgLogo)
            } else {
                holder.imgLogo.setImageResource(R.drawable.ic_tv_fallback)
            }

            val isPlaying = ch.streamUrl == activeStreamUrl
            val density = holder.itemView.resources.displayMetrics.density

            if (isPlaying) {
                holder.tvName.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.tvGroup.setTextColor(android.graphics.Color.parseColor("#A7F3D0"))
                val activeBg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#1F10B981"))
                    setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#4410B981"))
                    cornerRadius = 12 * density
                }
                holder.container.background = activeBg
            } else {
                holder.tvName.setTextColor(android.graphics.Color.WHITE)
                holder.tvGroup.setTextColor(android.graphics.Color.parseColor("#888888"))
                val normalBg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#181A20"))
                    cornerRadius = 12 * density
                }
                holder.container.background = normalBg
            }

            holder.container.setOnClickListener {
                onClick(ch)
            }
        }

        override fun getItemCount(): Int = channels.size
    }
}