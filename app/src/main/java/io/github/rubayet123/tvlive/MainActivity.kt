package io.github.rubayet123.tvlive
 
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity

/**
 * Loads [MainFragment].
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.rubayet123.tvlive.scraper.PluginScraperManager.ensureDefaultPluginsInitialized(this)
        io.github.rubayet123.tvlive.util.DeviceUtils.setupOrientationForDevice(this)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            val fragment = if (io.github.rubayet123.tvlive.util.DeviceUtils.isTvDevice(this)) {
                MainFragment()
            } else {
                MobileHomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, fragment)
                .commitNow()
        }
        
        // Trigger App Start Refreshes for M3U
        io.github.rubayet123.tvlive.data.SourceRepository(this).triggerAppStartRefreshes()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val currentFrag = supportFragmentManager.findFragmentById(R.id.main_browse_fragment)
            if (currentFrag is MainFragment && currentFrag.isReorderModeActive) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (currentFrag.moveReorderingChannelLeft()) return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (currentFrag.moveReorderingChannelRight()) return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (currentFrag.confirmReorder()) return true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (currentFrag.exitReorderMode()) return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Keep focus on active row during reordering
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val currentFrag = supportFragmentManager.findFragmentById(R.id.main_browse_fragment)
            if (currentFrag is MainFragment && currentFrag.showQuickActionForSelected()) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            val currentFrag = supportFragmentManager.findFragmentById(R.id.main_browse_fragment)
            if (currentFrag is MainFragment && currentFrag.showQuickActionForSelected()) {
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    fun refreshActiveFragment() {
        val currentFrag = supportFragmentManager.findFragmentById(R.id.main_browse_fragment)
        if (currentFrag is MainFragment) {
            currentFrag.loadRows()
        } else if (currentFrag is MobileHomeFragment) {
            currentFrag.reloadChannels()
        }
    }
}