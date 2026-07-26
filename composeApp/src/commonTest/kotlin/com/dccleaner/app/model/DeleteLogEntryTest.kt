package com.dccleaner.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteLogEntryTest {
    @Test
    fun progressMessageOmitsExcludedCountWhenNothingIsSkipped() {
        assertEquals(
            "🗑️ 3/10 (30%)",
            deleteGalleryProgressMessage(
                deleted = 3,
                skipped = 0,
                total = 10
            )
        )
    }

    @Test
    fun progressMessageShowsExcludedCountOnlyWhenPresent() {
        assertEquals(
            "🗑️ 7/10 (70% · 5개 제외)",
            deleteGalleryProgressMessage(
                deleted = 2,
                skipped = 5,
                total = 10
            )
        )
    }

    @Test
    fun galleryBoundaryLogsAreRecognizedWithoutMatchingOtherLogs() {
        val start = "[12:00:00] 🚀 국내야구갤러리 글 삭제 시작"
        val completion = "[12:01:00] ✅ 국내야구갤러리 글 삭제 완료 (총 3개)"
        val overallCompletion = "[12:02:00] 🎉 모든 작업 완료! 총 3개 글/댓글 삭제"

        assertTrue(start.isDeleteGalleryStartLog())
        assertTrue(completion.isDeleteGalleryCompletionLog())
        assertFalse(overallCompletion.isDeleteGalleryCompletionLog())
    }

    @Test
    fun guestbookRunLogBoundariesAreRecognized() {
        val start = "[12:00:00] 📝 방명록 가동 기록 작성 중"
        val completion = "[12:01:00] ✅ 방명록 가동 기록 작성 완료"
        val unrelated = "[12:02:00] ✅ 방명록 등록 확인 완료"

        assertTrue(start.isGuestbookRunLogStart())
        assertTrue(completion.isGuestbookRunLogCompletion())
        assertFalse(unrelated.isGuestbookRunLogCompletion())
    }
}
