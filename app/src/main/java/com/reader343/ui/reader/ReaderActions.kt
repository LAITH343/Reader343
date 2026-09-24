package com.reader343.ui.reader

interface ReaderActions {
    fun onChromeHold(held: Boolean)
    fun onToggleBookmark()
    fun onRemoveBookmark(page: Int)
    fun onAddPageNote()
    fun onToggleHighlightMode()
    fun onShowContents()
    fun onHideContents()
    fun onJumpToPage(page: Int)

    companion object {
        val None = object : ReaderActions {
            override fun onChromeHold(held: Boolean) = Unit
            override fun onToggleBookmark() = Unit
            override fun onRemoveBookmark(page: Int) = Unit
            override fun onAddPageNote() = Unit
            override fun onToggleHighlightMode() = Unit
            override fun onShowContents() = Unit
            override fun onHideContents() = Unit
            override fun onJumpToPage(page: Int) = Unit
        }
    }
}
