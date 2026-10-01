package com.example.dimanow.lms

/**
 * Plain-language LMS messages shown to people (D-094(14)).
 *
 * Raw transport or WebView errors (HTTP codes, `net::ERR_*`, exception text) are never shown;
 * these explain what happened and what to do next, in the app's 해요체 voice.
 */
internal object LmsUserMessages {
    const val REFRESH_FAILED = "수업 정보를 받지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요"
    const val NETWORK = "학교 서버에 연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요"
    const val SIGN_IN_REQUIRED = "로그인이 필요해요. 다시 로그인해 주세요"
    const val COURSE_CATALOG_FAILED = "수업 목록을 확인하지 못했어요. 다시 시도해 주세요"
    const val DETAIL_FAILED = "글을 불러오지 못했어요. 잠시 후 다시 시도해 주세요"
    const val ATTACHMENT_FAILED = "첨부파일을 받지 못했어요. 잠시 후 다시 시도해 주세요"
    const val ATTACHMENT_SAVE_FAILED = "첨부파일을 저장하지 못했어요. 저장 위치를 확인하고 다시 시도해 주세요"
    const val UNSAFE_PAGE_BLOCKED = "안전하지 않은 페이지라서 열지 않았어요"
}
