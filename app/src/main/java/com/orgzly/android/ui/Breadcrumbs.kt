package com.orgzly.android.ui

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View

class Breadcrumbs {
    private val ssb = SpannableStringBuilder()

    // Track clickable span to remove the last one
    private var lastClickableSpan: ClickableSpan? = null

    fun add(name: String, onClick: () -> Any) {
        if (!ssb.isEmpty()) {
            ssb.append(" • ")
        }

        // val isLast = index == path.size - 1

        val start = ssb.length

        ssb.append(truncateName(name))

        ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        lastClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }
        }

        ssb.setSpan(lastClickableSpan, start, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun truncateName(name: String): String {
        return if (name.length - 3 > MAX_NAME_LENGTH) {
            name.substring(0, MAX_NAME_LENGTH) + '…'
        } else {
            name
        }
    }

    fun toCharSequence(): CharSequence {
        ssb.removeSpan(lastClickableSpan)

        return ssb
    }

    companion object {
        private const val MAX_NAME_LENGTH = 20
    }
}
