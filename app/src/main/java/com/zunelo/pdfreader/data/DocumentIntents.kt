package com.zunelo.pdfreader.data

import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.*
import com.zunelo.pdfreader.common.*
import com.zunelo.pdfreader.data.*
import com.zunelo.pdfreader.dialog.*
import com.zunelo.pdfreader.home.*
import com.zunelo.pdfreader.onboarding.*
import com.zunelo.pdfreader.pdf.*
import com.zunelo.pdfreader.permission.*
import com.zunelo.pdfreader.startup.*
import com.zunelo.pdfreader.tools.*
import com.zunelo.pdfreader.viewer.*

import android.content.Intent
import android.net.Uri
import android.os.Bundle

const val EXTRA_URI = "document_uri"
const val EXTRA_NAME = "document_name"
const val EXTRA_SIZE = "document_size"
const val EXTRA_MODIFIED = "document_modified"
const val EXTRA_MIME = "document_mime"

fun Intent.putDocument(item: DocumentItem): Intent = apply {
    putExtra(EXTRA_URI, item.uri.toString())
    putExtra(EXTRA_NAME, item.name)
    putExtra(EXTRA_SIZE, item.size)
    putExtra(EXTRA_MODIFIED, item.modified)
    putExtra(EXTRA_MIME, item.mime)
}

fun Intent.document(): DocumentItem? {
    val value = getStringExtra(EXTRA_URI) ?: return null
    return DocumentItem(
        Uri.parse(value),
        getStringExtra(EXTRA_NAME) ?: "Document",
        getLongExtra(EXTRA_SIZE, 0L),
        getLongExtra(EXTRA_MODIFIED, 0L),
        getStringExtra(EXTRA_MIME) ?: "application/octet-stream"
    )
}

fun Bundle.putDocument(item: DocumentItem): Bundle = apply {
    putString(EXTRA_URI, item.uri.toString()); putString(EXTRA_NAME, item.name); putLong(EXTRA_SIZE, item.size)
    putLong(EXTRA_MODIFIED, item.modified); putString(EXTRA_MIME, item.mime)
}

fun Bundle.document(): DocumentItem? {
    val value = getString(EXTRA_URI) ?: return null
    return DocumentItem(Uri.parse(value), getString(EXTRA_NAME) ?: "Document", getLong(EXTRA_SIZE), getLong(EXTRA_MODIFIED), getString(EXTRA_MIME) ?: "application/octet-stream")
}
