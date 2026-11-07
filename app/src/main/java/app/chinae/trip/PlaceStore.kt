package app.chinae.trip

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import app.chinae.vehicle.VehicleStateHub

/** 집과 회사 위치. 직접 지정한 값(설정)이 내비 즐겨찾기(HOME, OFFICE)보다 우선 (masterplan 3.3) */
class PlaceStore(private val manual: () -> Places = { Places(null, null) }) {
    @Volatile private var bookmarks = Places(null, null)

    val places: Places
        get() = manual().let { m -> Places(m.home ?: bookmarks.home, m.work ?: bookmarks.work) }

    fun updateFromBookmarks(list: List<BookmarkInfo>) {
        bookmarks = fromBookmarks(list)
    }

    companion object {
        fun fromBookmarks(list: List<BookmarkInfo>): Places {
            fun find(type: BookmarkType) =
                list.firstOrNull { it.category == type }?.let { VehicleStateHub.latLng(it.latitude, it.longitude) }
            return Places(home = find(BookmarkType.HOME), work = find(BookmarkType.OFFICE))
        }
    }
}
