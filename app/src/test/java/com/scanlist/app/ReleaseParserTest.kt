package com.scanlist.app

import com.scanlist.app.domain.update.AppVersion
import com.scanlist.app.domain.update.ReleaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    private val prefix = "https://github.com/JRRocks/SuivieMAT/releases/download/"

    private val digest = "a".repeat(64)

    /** Reponse de `releases/latest`, reduite aux champs lus et a quelques autres autour. */
    private fun release(
        tag: String = "v1.12",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = asset(),
    ) = """
        {
          "url": "https://api.github.com/repos/JRRocks/SuivieMAT/releases/1",
          "html_url": "https://github.com/JRRocks/SuivieMAT/releases/tag/$tag",
          "id": 1,
          "author": {"login": "github-actions[bot]", "id": 41898282},
          "tag_name": "$tag",
          "target_commitish": "0123456789abcdef0123456789abcdef01234567",
          "name": "SuivieMAT 1.12",
          "draft": $draft,
          "prerelease": $prerelease,
          "created_at": "2026-11-09T22:00:00Z",
          "published_at": "2026-11-09T22:05:00Z",
          "assets": [$assets],
          "body": "APK de test."
        }
    """.trimIndent()

    private fun asset(
        name: String = "SuivieMAT-1.12-arm64-v8a.apk",
        url: String = "$prefix" + "v1.12/SuivieMAT-1.12-arm64-v8a.apk",
        size: String = "21331154",
        digestField: String = "\"sha256:$digest\"",
    ) = """
        {
          "url": "https://api.github.com/repos/JRRocks/SuivieMAT/releases/assets/2",
          "id": 2,
          "name": "$name",
          "label": null,
          "uploader": {"login": "github-actions[bot]"},
          "content_type": "application/vnd.android.package-archive",
          "state": "uploaded",
          "size": $size,
          "digest": $digestField,
          "download_count": 0,
          "browser_download_url": "$url"
        }
    """.trimIndent()

    @Test
    fun `une release publiee rend sa version et son APK`() {
        val info = ReleaseParser.parse(release(), prefix)!!

        assertEquals(AppVersion.parseOrNull("1.12"), info.version)
        val apk = info.assets.single()
        assertEquals("SuivieMAT-1.12-arm64-v8a.apk", apk.name)
        assertEquals(prefix + "v1.12/SuivieMAT-1.12-arm64-v8a.apk", apk.downloadUrl)
        assertEquals(21331154L, apk.sizeBytes)
        assertEquals(digest, apk.sha256)
    }

    @Test
    fun `un brouillon ou une pre-version ne sont pas proposes`() {
        assertNull(ReleaseParser.parse(release(draft = true), prefix))
        assertNull(ReleaseParser.parse(release(prerelease = true), prefix))
    }

    @Test
    fun `une etiquette qui n est pas un numero de version est ignoree`() {
        assertNull(ReleaseParser.parse(release(tag = "banc-ocr"), prefix))
    }

    @Test
    fun `une reponse illisible ne leve pas d exception`() {
        assertNull(ReleaseParser.parse("", prefix))
        assertNull(ReleaseParser.parse("<html>502 Bad Gateway</html>", prefix))
        assertNull(ReleaseParser.parse("{\"message\": \"Not Found\"}", prefix))
        assertNull(ReleaseParser.parse("[]", prefix))
    }

    @Test
    fun `un APK heberge hors du depot est ecarte`() {
        val foreign = asset(url = "https://example.org/SuivieMAT-1.12-arm64-v8a.apk")

        assertTrue(ReleaseParser.parse(release(assets = foreign), prefix)!!.assets.isEmpty())
    }

    @Test
    fun `une piece jointe qui n est pas un APK est ecartee`() {
        val notes = asset(name = "notes.txt", url = prefix + "v1.12/notes.txt")

        assertTrue(ReleaseParser.parse(release(assets = notes), prefix)!!.assets.isEmpty())
    }

    @Test
    fun `une release sans piece jointe garde sa version`() {
        val info = ReleaseParser.parse(release(assets = ""), prefix)!!

        assertEquals(AppVersion.parseOrNull("1.12"), info.version)
        assertTrue(info.assets.isEmpty())
    }

    @Test
    fun `l empreinte absente ou d un autre format est ignoree sans ecarter l APK`() {
        val without = ReleaseParser.parse(release(assets = asset(digestField = "null")), prefix)!!
        val other = ReleaseParser.parse(release(assets = asset(digestField = "\"md5:abcdef\"")), prefix)!!
        val bare = ReleaseParser.parse(release(assets = asset(digestField = "\"${"A".repeat(64)}\"")), prefix)!!

        assertNull(without.assets.single().sha256)
        assertNull(other.assets.single().sha256)
        assertNull(bare.assets.single().sha256)
    }

    @Test
    fun `l empreinte est rendue en minuscules`() {
        val upper = asset(digestField = "\"sha256:${"AB".repeat(32)}\"")

        assertEquals("ab".repeat(32), ReleaseParser.parse(release(assets = upper), prefix)!!.assets.single().sha256)
    }

    @Test
    fun `la casse du nom du depot ne fait pas ecarter l APK`() {
        val lower = asset(url = "https://github.com/jrrocks/suiviemat/releases/download/v1.12/SuivieMAT-1.12-arm64-v8a.apk")

        assertEquals(1, ReleaseParser.parse(release(assets = lower), prefix)!!.assets.size)
    }
}
