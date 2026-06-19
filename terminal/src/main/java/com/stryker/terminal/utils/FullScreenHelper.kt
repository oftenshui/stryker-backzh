package com.stryker.terminal.utils

import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class FullScreenHelper private constructor(
  activity: AppCompatActivity,
  var fullScreen: Boolean,
  private var shouldSkipFirst: Boolean
) {

  interface KeyBoardListener {
    fun onKeyboardChange(isShow: Boolean, keyboardHeight: Int)
  }

  private val mChildOfContent: View
  private var usableHeightPrevious: Int = 0
  private val frameLayoutParams: FrameLayout.LayoutParams

  private var mOriginHeight: Int = 0
  private var mPreHeight: Int = 0
  private var mKeyBoardListener: KeyBoardListener? = null

  fun setKeyBoardListener(mKeyBoardListener: KeyBoardListener) {
    this.mKeyBoardListener = mKeyBoardListener
  }

  init {
    val content = activity.findViewById<FrameLayout>(android.R.id.content)
    mChildOfContent = content.getChildAt(0)
    mChildOfContent.viewTreeObserver.addOnGlobalLayoutListener {
      if (this@FullScreenHelper.fullScreen) {
        possiblyResizeChildOfContent()
      }
      monitorImeStatus()
    }
    frameLayoutParams = mChildOfContent.layoutParams as FrameLayout.LayoutParams
  }

  private fun monitorImeStatus() {
    val currHeight = mChildOfContent.height
    if (currHeight == 0 && shouldSkipFirst) {
      return
    }

    shouldSkipFirst = false
    var hasChange = false
    if (mPreHeight == 0) {
      mPreHeight = currHeight
      mOriginHeight = currHeight
    } else {
      if (mPreHeight != currHeight) {
        hasChange = true
        mPreHeight = currHeight
      } else {
        hasChange = false
      }
    }
    if (hasChange) {
      var keyboardHeight = 0
      val keyBoardIsShowing: Boolean
      if (Math.abs(mOriginHeight - currHeight) < 100) {
        keyBoardIsShowing = false
      } else {
        keyboardHeight = mOriginHeight - currHeight
        keyBoardIsShowing = true
      }

      if (mKeyBoardListener != null) {
        mKeyBoardListener!!.onKeyboardChange(keyBoardIsShowing, keyboardHeight)
      }
    }
  }

  private fun possiblyResizeChildOfContent() {
    val usableHeightNow = computeUsableHeight()
    val currentHeightLayoutHeight: Int

    if (usableHeightNow != usableHeightPrevious) {
      val usableHeightSansKeyboard = mChildOfContent.rootView.height
      val heightDifference = usableHeightSansKeyboard - usableHeightNow
      if (heightDifference > usableHeightSansKeyboard / 4) {
        currentHeightLayoutHeight = usableHeightSansKeyboard - heightDifference
      } else {
        currentHeightLayoutHeight = usableHeightSansKeyboard
      }
      frameLayoutParams.height = currentHeightLayoutHeight
      mChildOfContent.requestLayout()
      usableHeightPrevious = usableHeightNow
    }
  }

  private fun computeUsableHeight(): Int {
    val r = Rect()
    mChildOfContent.getWindowVisibleDisplayFrame(r)
    return r.bottom - r.top
  }

  companion object {
    fun injectActivity(activity: AppCompatActivity, fullScreen: Boolean, recreate: Boolean): FullScreenHelper {
      return FullScreenHelper(activity, fullScreen, recreate)
    }

  }
}
