package com.zunelo.pdfreader.cleanup

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.zunelo.pdfreader.R

object LargeFileVisuals {
    @DrawableRes
    fun icon(category: LargeFileCategory?): Int = when (category) {
        null -> R.drawable.ic_file_category_all
        LargeFileCategory.DOCUMENT -> R.drawable.ic_file_category_document
        LargeFileCategory.IMAGE -> R.drawable.ic_file_category_image
        LargeFileCategory.VIDEO -> R.drawable.ic_file_category_video
        LargeFileCategory.AUDIO -> R.drawable.ic_file_category_audio
        LargeFileCategory.ARCHIVE -> R.drawable.ic_file_category_archive
        LargeFileCategory.OTHER -> R.drawable.ic_file_category_other
    }

    @ColorInt
    fun color(category: LargeFileCategory?): Int = when (category) {
        null -> 0xFFE91922.toInt()
        LargeFileCategory.DOCUMENT -> 0xFFE91922.toInt()
        LargeFileCategory.IMAGE -> 0xFF7657D6.toInt()
        LargeFileCategory.VIDEO -> 0xFFE05F83.toInt()
        LargeFileCategory.AUDIO -> 0xFF258E70.toInt()
        LargeFileCategory.ARCHIVE -> 0xFFC77B20.toInt()
        LargeFileCategory.OTHER -> 0xFF697080.toInt()
    }
}
