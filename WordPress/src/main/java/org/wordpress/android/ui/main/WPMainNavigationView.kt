package org.wordpress.android.ui.main

import android.annotation.SuppressLint
import android.app.Fragment
import android.app.FragmentManager
import android.content.Context
import android.support.annotation.DrawableRes
import android.support.annotation.IdRes
import android.support.annotation.StringRes
import android.support.design.internal.BottomNavigationItemView
import android.support.design.internal.BottomNavigationMenuView
import android.support.design.widget.BottomNavigationView
import android.support.design.widget.BottomNavigationView.OnNavigationItemReselectedListener
import android.support.design.widget.BottomNavigationView.OnNavigationItemSelectedListener
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView

import org.wordpress.android.R
import org.wordpress.android.ui.main.WPMainActivity.OnScrollToTopListener
import org.wordpress.android.ui.notifications.NotificationsListFragment
import org.wordpress.android.ui.prefs.AppPrefs
import org.wordpress.android.ui.reader.ReaderPostListFragment
import org.wordpress.android.util.AniUtils
import org.wordpress.android.util.AniUtils.Duration
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T

import java.lang.reflect.Field

import android.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE

/*
 * Bottom navigation view and related adapter used by the main activity for the
 * four primary views - note that we ignore the built-in icons and labels and
 * insert our own custom views so we have more control over their appearance
 */
class WPMainNavigationView : BottomNavigationView, OnNavigationItemSelectedListener, OnNavigationItemReselectedListener {
    private var mNavAdapter: NavAdapter? = null
    private var mFragmentManager: FragmentManager? = null
    private var mListener: OnPageListener? = null
    private var mPrevPosition = -1

    internal val activeFragment: Fragment?
        get() = mNavAdapter!!.getFragment(currentPosition)

    internal var currentPosition: Int
        get() = getPositionForItemId(selectedItemId)
        set(position) = setCurrentPosition(position, true)

    internal interface OnPageListener {
        fun onPageChanged(position: Int)
        fun onNewPostButtonClicked()
    }

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    internal fun init(fm: FragmentManager, listener: OnPageListener) {
        mFragmentManager = fm
        mListener = listener

        mNavAdapter = NavAdapter()
        assignNavigationListeners(true)
        disableShiftMode()

        // overlay each item with our custom view
        val menuView = getChildAt(0) as BottomNavigationMenuView
        val inflater = LayoutInflater.from(context)
        for (i in 0 until menu.size()) {
            val itemView = menuView.getChildAt(i) as BottomNavigationItemView
            val customView: View
            // remove the background ripple and use a different layout for the post button
            if (i == PAGE_NEW_POST) {
                itemView.background = null
                customView = inflater.inflate(R.layout.navbar_post_item, menuView, false)
            } else {
                customView = inflater.inflate(R.layout.navbar_item, menuView, false)
                val txtLabel = customView.findViewById<TextView>(R.id.nav_label)
                val imgIcon = customView.findViewById<ImageView>(R.id.nav_icon)
                txtLabel.text = getTitleForPosition(i)
                txtLabel.contentDescription = getContentDescriptionForPosition(i)
                imgIcon.setImageResource(getDrawableResForPosition(i))
            }

            itemView.addView(customView)
        }

        val position = AppPrefs.getMainPageIndex()
        currentPosition = position
    }

    /*
     * uses reflection to disable "shift mode" so the item are equal width
     */
    @SuppressLint("RestrictedApi")
    private fun disableShiftMode() {
        val menuView = getChildAt(0) as BottomNavigationMenuView
        try {
            val shiftingMode = menuView.javaClass.getDeclaredField("mShiftingMode")
            shiftingMode.isAccessible = true
            shiftingMode.setBoolean(menuView, false)
            shiftingMode.isAccessible = false
            for (i in 0 until menuView.childCount) {
                val item = menuView.getChildAt(i) as BottomNavigationItemView
                item.setShiftingMode(false)
                // force the view to update
                item.setChecked(item.itemData.isChecked)
            }
        } catch (e: NoSuchFieldException) {
            AppLog.e(T.MAIN, "Unable to disable shift mode", e)
        } catch (e: IllegalAccessException) {
            AppLog.e(T.MAIN, "Unable to disable shift mode", e)
        }
    }

    private fun assignNavigationListeners(assign: Boolean) {
        setOnNavigationItemSelectedListener(if (assign) this else null)
        setOnNavigationItemReselectedListener(if (assign) this else null)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val position = getPositionForItemId(item.itemId)
        if (position == PAGE_NEW_POST) {
            handlePostButtonClicked()
            return false
        } else {
            setCurrentPosition(position, false)
            mListener!!.onPageChanged(position)
            return true
        }
    }

    private fun handlePostButtonClicked() {
        val postView = getItemView(PAGE_NEW_POST)

        // animate the button icon before telling the listener the post button was clicked - this way
        // the user sees the animation before the editor appears
        AniUtils.startAnimation(postView, R.anim.navbar_button_scale, object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                // noop
            }

            override fun onAnimationEnd(animation: Animation) {
                mListener!!.onNewPostButtonClicked()
            }

            override fun onAnimationRepeat(animation: Animation) {
                // noop
            }
        })
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        // scroll the active fragment's contents to the top when user re-taps the current item
        val position = getPositionForItemId(item.itemId)
        if (position != PAGE_NEW_POST) {
            val fragment = mNavAdapter!!.getFragment(position)
            if (fragment is OnScrollToTopListener) {
                (fragment as OnScrollToTopListener).onScrollToTop()
            }
        }
    }

    private fun getPositionForItemId(@IdRes itemId: Int): Int {
        when (itemId) {
            R.id.nav_sites -> return PAGE_MY_SITE
            R.id.nav_reader -> return PAGE_READER
            R.id.nav_write -> return PAGE_NEW_POST
            R.id.nav_me -> return PAGE_ME
            else -> return PAGE_NOTIFS
        }
    }

    @IdRes
    private fun getItemIdForPosition(position: Int): Int {
        when (position) {
            PAGE_MY_SITE -> return R.id.nav_sites
            PAGE_READER -> return R.id.nav_reader
            PAGE_NEW_POST -> return R.id.nav_write
            PAGE_ME -> return R.id.nav_me
            else -> return R.id.nav_notifications
        }
    }

    private fun setCurrentPosition(position: Int, ensureSelected: Boolean) {
        // new post page can't be selected, only tapped
        if (position == PAGE_NEW_POST) {
            return
        }

        // remove the title and selected state from the previously selected item
        if (mPrevPosition > -1) {
            showTitleForPosition(mPrevPosition, false)
            setImageViewSelected(mPrevPosition, false)
        }

        // set the title and selected state from the newly selected item
        showTitleForPosition(position, true)
        setImageViewSelected(position, true)

        AppPrefs.setMainPageIndex(position)
        mPrevPosition = position

        if (ensureSelected) {
            // temporarily disable the nav listeners so they don't fire when we change the selected page
            assignNavigationListeners(false)
            try {
                selectedItemId = getItemIdForPosition(position)
            } finally {
                assignNavigationListeners(true)
            }
        }

        val fragment = mNavAdapter!!.getFragment(position)
        if (fragment != null) {
            mFragmentManager!!
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .setTransition(TRANSIT_FRAGMENT_FADE)
                    .commit()
        }
    }

    /*
     * ideally we'd use a color selector to tint the icon based on its selected state, but prior to
     * API 21 setting a color selector via XML will crash the app, and setting it programmatically
     * will have no effect
     */
    private fun setImageViewSelected(position: Int, isSelected: Boolean) {
        val color = resources.getColor(if (isSelected) R.color.blue_medium else R.color.grey_lighten_10)
        getImageViewForPosition(position).setColorFilter(color, android.graphics.PorterDuff.Mode.MULTIPLY)
    }

    @DrawableRes
    private fun getDrawableResForPosition(position: Int): Int {
        when (position) {
            PAGE_MY_SITE -> return R.drawable.ic_my_sites_white_32dp
            PAGE_READER -> return R.drawable.ic_reader_white_32dp
            PAGE_NEW_POST -> return R.drawable.ic_create_white_24dp
            PAGE_ME -> return R.drawable.ic_user_circle_white_32dp
            else -> return R.drawable.ic_bell_white_32dp
        }
    }

    internal fun getTitleForPosition(position: Int): CharSequence {
        @StringRes val idRes: Int
        when (position) {
            PAGE_MY_SITE -> idRes = R.string.my_site_section_screen_title
            PAGE_READER -> idRes = R.string.reader_screen_title
            PAGE_NEW_POST -> idRes = R.string.write_post
            PAGE_ME -> idRes = R.string.me_section_screen_title
            else -> idRes = R.string.notifications_screen_title
        }
        return context.getString(idRes)
    }

    internal fun getContentDescriptionForPosition(position: Int): CharSequence {
        @StringRes val idRes: Int
        when (position) {
            PAGE_MY_SITE -> idRes = R.string.tabbar_accessibility_label_my_site
            PAGE_READER -> idRes = R.string.tabbar_accessibility_label_reader
            PAGE_NEW_POST -> idRes = R.string.tabbar_accessibility_label_write
            PAGE_ME -> idRes = R.string.tabbar_accessibility_label_me
            else -> idRes = R.string.tabbar_accessibility_label_notifications
        }
        return context.getString(idRes)
    }

    private fun getTitleViewForPosition(position: Int): TextView? {
        if (position == PAGE_NEW_POST) {
            return null
        }
        val itemView = getItemView(position)
        return itemView!!.findViewById(R.id.nav_label)
    }

    private fun getImageViewForPosition(position: Int): ImageView {
        val itemView = getItemView(position)
        return itemView!!.findViewById(R.id.nav_icon)
    }

    private fun showTitleForPosition(position: Int, show: Boolean) {
        val txtTitle = getTitleViewForPosition(position)
        if (txtTitle != null) {
            txtTitle.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    internal fun getFragment(position: Int): Fragment? {
        return mNavAdapter!!.getFragment(position)
    }

    private fun getItemView(position: Int): BottomNavigationItemView? {
        if (isValidPosition(position)) {
            val menuView = getChildAt(0) as BottomNavigationMenuView
            return menuView.getChildAt(position) as BottomNavigationItemView
        }
        return null
    }

    /*
     * show or hide the badge on the notification page
     */
    internal fun showNoteBadge(showBadge: Boolean) {
        val notifView = getItemView(PAGE_NOTIFS)
        val badgeView = notifView!!.findViewById<View>(R.id.badge)

        val currentVisibility = badgeView.visibility
        val newVisibility = if (showBadge) View.VISIBLE else View.GONE
        if (currentVisibility == newVisibility) {
            return
        }

        if (showBadge) {
            AniUtils.fadeIn(badgeView, Duration.MEDIUM)
        } else {
            AniUtils.fadeOut(badgeView, Duration.MEDIUM)
        }
    }

    private fun isValidPosition(position: Int): Boolean {
        return position >= 0 && position < NUM_PAGES
    }

    private inner class NavAdapter {
        private val mFragments = SparseArray<Fragment>(NUM_PAGES)

        private fun createFragment(position: Int): Fragment? {
            val fragment: Fragment
            when (position) {
                PAGE_MY_SITE -> fragment = MySiteFragment.newInstance()
                PAGE_READER -> fragment = ReaderPostListFragment.newInstance()
                PAGE_ME -> fragment = MeFragment.newInstance()
                PAGE_NOTIFS -> fragment = NotificationsListFragment.newInstance()
                else -> return null
            }

            mFragments.put(position, fragment)
            return fragment
        }

        internal fun getFragment(position: Int): Fragment? {
            return if (isValidPosition(position) && mFragments.get(position) != null) {
                mFragments.get(position)
            } else {
                createFragment(position)
            }
        }
    }

    companion object {
        private val NUM_PAGES = 5

        const val PAGE_MY_SITE = 0
        const val PAGE_READER = 1
        const val PAGE_NEW_POST = 2
        const val PAGE_ME = 3
        const val PAGE_NOTIFS = 4
    }
}

