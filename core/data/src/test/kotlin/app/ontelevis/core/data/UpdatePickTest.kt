package app.ontelevis.core.data

import app.ontelevis.core.model.AppVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UpdatePickTest {
    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        asset: String = "ontelevis.apk",
    ) = """
        {"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,"body":"Notes $tag",
         "assets":[{"name":"$asset","size":1234,"browser_download_url":"https://example.com/$tag/$asset"}]}
        """

    private val json =
        "[" +
            listOf(
                release("v1.1.0-beta.1", prerelease = true),
                release("v1.0.1"),
                release("v1.2.0", draft = true),
                release("v1.0.2", asset = "other.zip"),
                release("v1.0.0"),
            ).joinToString(",") + "]"

    private val installed = AppVersion.parse("1.0.0")!!

    @Test
    fun `stable channel takes the newest stable release with the apk`() {
        val update = AppUpdater.pickUpdate(json, installed, betas = false)!!
        assertEquals("1.0.1", update.version.toString())
        assertEquals("https://example.com/v1.0.1/ontelevis.apk", update.downloadUrl)
        assertEquals(1234L, update.size)
    }

    @Test
    fun `betas switch also offers pre-releases`() {
        assertEquals("1.1.0-beta.1", AppUpdater.pickUpdate(json, installed, betas = true)!!.version.toString())
    }

    @Test
    fun `nothing newer means no update`() {
        assertNull(AppUpdater.pickUpdate(json, AppVersion.parse("1.0.1")!!, betas = false))
        assertNull(AppUpdater.pickUpdate("not json", installed, betas = true))
    }

    @Test
    fun `a beta install sees the stable release of the same version`() {
        val beta = AppVersion.parse("1.0.1-beta.3")!!
        assertEquals("1.0.1", AppUpdater.pickUpdate(json, beta, betas = false)!!.version.toString())
    }
}
