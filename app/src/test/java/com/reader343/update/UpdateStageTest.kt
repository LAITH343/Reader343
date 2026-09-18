package com.reader343.update

import com.reader343.data.repo.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class UpdateStageTest {

    private val installed = InstalledApp(versionName = "1.0", versionCode = 104, minSdk = 26, updatedAt = null)

    private fun release(code: Int) = Release(
        tag = "v1.$code",
        apk = ApkAsset("reader343-1.$code-$code.apk", "1.$code", code, "", 1_000L),
        publishedAt = null,
        htmlUrl = null,
        changelog = null,
    )

    private fun status(
        release: Release? = null,
        checkedAt: Instant? = Instant.EPOCH,
        checking: Boolean = false,
        failed: Boolean = false,
        download: DownloadState = DownloadState.Idle,
    ) = UpdateStatus(installed, release, checkedAt, checking, failed, download)

    @Test
    fun `available only when release is newer`() {
        assertTrue(status(release(118)).available)
        assertFalse(status(release(104)).available)
        assertFalse(status(release(90)).available)
        assertFalse(status(null).available)
    }

    @Test
    fun `stage follows download when update available`() {
        val newer = release(118)
        assertEquals(UpdateStage.Available, status(newer).stage())
        assertEquals(UpdateStage.Downloading, status(newer, download = DownloadState.Downloading(1, 2, 3)).stage())
        assertEquals(UpdateStage.Paused, status(newer, download = DownloadState.Paused(1, 2)).stage())
        assertEquals(UpdateStage.Verifying, status(newer, download = DownloadState.Verifying).stage())
        assertEquals(UpdateStage.Ready, status(newer, download = DownloadState.Ready(File("a.apk"))).stage())
        assertEquals(UpdateStage.Failed, status(newer, download = DownloadState.Failed(DownloadError.Network)).stage())
    }

    @Test
    fun `stage without update`() {
        assertEquals(UpdateStage.UpToDate, status(release(104)).stage())
        assertEquals(UpdateStage.Checking, status(checking = true).stage())
        assertEquals(UpdateStage.CheckFailed, status(failed = true).stage())
        assertEquals(UpdateStage.Checking, status(checkedAt = null).stage())
    }

    @Test
    fun `summary reflects availability`() {
        assertEquals(UpdateSummary(true, "1.118", 118, 1_000L), status(release(118)).summary())
        assertEquals(UpdateSummary(false, "1.0", 104, 0L), status(release(104)).summary())
        assertNull(status(checkedAt = null).summary())
    }

    @Test
    fun `eta rounds up remaining seconds`() {
        assertEquals(10L, etaSeconds(0L, 1_000L, 100L))
        assertEquals(1L, etaSeconds(990L, 1_000L, 100L))
        assertEquals(1L, etaSeconds(1_000L, 1_000L, 100L))
        assertNull(etaSeconds(0L, 1_000L, 0L))
        assertNull(etaSeconds(0L, 0L, 100L))
    }
}
