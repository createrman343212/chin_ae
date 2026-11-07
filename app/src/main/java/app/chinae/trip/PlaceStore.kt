package app.chinae.trip

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import app.chinae.vehicle.VehicleStateHub

/** 집과 회사 위치. 지금은 내비 즐겨찾기(HOME, OFFICE)만 씀 (masterplan 3.3 1단계) */
class PlaceStore {
    // shortcut: 저장하지 않고 즐겨찾기만 읽음. 첫 정차 시 등록과 설정 화면(3.3 2·3단계)은 데이터 계층(11.09) 때 추가
    @Volatile var places = Places(null, null)
        private set

    fun updateFromBookmarks(list: List<BookmarkInfo>) {
        places = fromBookmarks(list)
    }

    companion object {
        fun fromBookmarks(list: List<BookmarkInfo>): Places {
            fun find(type: BookmarkType) =
                list.firstOrNull { it.category == type }?.let { VehicleStateHub.latLng(it.latitude, it.longitude) }
            return Places(home = find(BookmarkType.HOME), work = find(BookmarkType.OFFICE))
        }
    }
}
