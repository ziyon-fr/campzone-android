package fr.ziyon.campzone.data.songbook

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface CantusService {
    suspend fun songs(query: CantusSongQuery, forceRefresh: Boolean = false): CantusPage<CantusSong>
    suspend fun songDetail(slug: String): CantusSong
    suspend fun artists(forceRefresh: Boolean = false): List<CantusArtist>
    suspend fun songbooks(forceRefresh: Boolean = false): List<CantusSongbook>
}

@Singleton
class BackendCantusService @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationContext context: Context,
) : CantusService {
    private val cache = CantusDiskCache(File(context.cacheDir, "CantusCatalog"))

    override suspend fun songs(query: CantusSongQuery, forceRefresh: Boolean): CantusPage<CantusSong> =
        cached(
            key = query.cacheKey,
            forceRefresh = forceRefresh,
            parse = ::parseSongPage,
        ) {
            requestData("songs", query.queryItems)
        }

    override suspend fun songDetail(slug: String): CantusSong =
        cached(
            key = "song-detail&slug=$slug",
            forceRefresh = false,
            parse = ::parseSongDetail,
        ) {
            requestData("songs/${slug.urlEncoded()}", emptyList())
        }

    override suspend fun artists(forceRefresh: Boolean): List<CantusArtist> =
        cached(
            key = "artists",
            forceRefresh = forceRefresh,
            parse = { raw -> parseArtistPage(raw).data },
        ) {
            aggregatePages("artists")
        }

    override suspend fun songbooks(forceRefresh: Boolean): List<CantusSongbook> =
        cached(
            key = "songbooks",
            forceRefresh = forceRefresh,
            parse = { raw -> parseSongbookPage(raw).data },
        ) {
            aggregatePages("songbooks")
        }

    private suspend fun aggregatePages(path: String): String {
        val all = JSONArray()
        var pagination = CantusPagination(page = 1)
        var page = 1
        while (page <= MaxAggregatedPages) {
            val raw = requestData(
                path = path,
                queryItems = listOf("limit" to PageLimit.toString(), "page" to page.toString()),
            )
            val pageObject = JSONObject(raw)
            pageObject.optJSONArray("data")?.copyInto(all)
            pagination = parsePagination(pageObject.optJSONObject("pagination"))
            page = pagination.nextPage ?: break
        }
        return JSONObject()
            .put("data", all)
            .put("pagination", pagination.toJson())
            .toString()
    }

    private suspend fun <T> cached(
        key: String,
        forceRefresh: Boolean,
        parse: (String) -> T,
        fetch: suspend () -> String,
    ): T {
        if (!forceRefresh) {
            cache.entry(key)?.takeIf { it.ageMs < CacheLifetimeMs }?.let { return parse(it.payload) }
        }

        return try {
            val fresh = fetch()
            cache.store(key, fresh)
            parse(fresh)
        } catch (error: Exception) {
            cache.entry(key)?.let { parse(it.payload) } ?: throw error
        }
    }

    private suspend fun requestData(path: String, queryItems: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val token = auth.currentUser?.getIdToken(false)?.await()?.token
                ?: error("Sign in to browse the song catalog.")
            val query = queryItems
                .filter { it.second.isNotBlank() }
                .joinToString("&") { (name, value) -> "${name.urlEncoded()}=${value.urlEncoded()}" }
            val url = buildString {
                append(BuildConfig.BACKEND_BASE_URL)
                append("/cantus/")
                append(path)
                if (query.isNotBlank()) append("?").append(query)
            }
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val response = connection.readResponse()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(catalogErrorMessage(connection.responseCode))
            }
            val root = JSONObject(response)
            if (!root.has("success")) return@withContext response
            root.opt("data")?.toString()
                ?: throw IllegalStateException("The song catalog response could not be read.")
        }

    private fun HttpURLConnection.readResponse(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun catalogErrorMessage(status: Int): String = when (status) {
        401 -> "Sign in again to browse the song catalog."
        404 -> "This song is no longer in the catalog."
        429 -> "The song catalog is busy. Try again in a minute."
        else -> "The song catalog is unavailable right now. Try again later."
    }

    private companion object {
        const val CacheLifetimeMs = 24L * 60L * 60L * 1000L
        const val PageLimit = 100
        const val MaxAggregatedPages = 20
    }
}

private class CantusDiskCache(private val directory: File) {
    data class Entry(val payload: String, val savedAtMs: Long) {
        val ageMs: Long get() = System.currentTimeMillis() - savedAtMs
    }

    init {
        directory.mkdirs()
    }

    fun entry(key: String): Entry? {
        val file = fileFor(key)
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText())
            Entry(
                payload = root.getString("payload"),
                savedAtMs = root.optLong("savedAtMs", 0L),
            )
        }.getOrNull()
    }

    fun store(key: String, payload: String) {
        runCatching {
            fileFor(key).writeText(
                JSONObject()
                    .put("savedAtMs", System.currentTimeMillis())
                    .put("payload", payload)
                    .toString(),
            )
        }
    }

    private fun fileFor(key: String): File {
        val safe = key.map { char ->
            if (char.isLetterOrDigit() || char in "-_=&") char else '-'
        }.joinToString("")
        return File(directory, "$safe.json")
    }
}

private fun parseSongPage(raw: String): CantusPage<CantusSong> {
    val root = JSONObject(raw)
    return CantusPage(
        data = root.optJSONArray("data").orEmpty().mapObjects(::parseSong),
        pagination = parsePagination(root.optJSONObject("pagination")),
    )
}

private fun parseSongDetail(raw: String): CantusSong {
    val root = JSONObject(raw)
    val song = root.optJSONObject("data") ?: root
    return parseSong(song)
}

private fun parseArtistPage(raw: String): CantusPage<CantusArtist> {
    val root = JSONObject(raw)
    return CantusPage(
        data = root.optJSONArray("data").orEmpty().mapObjects(::parseArtist),
        pagination = parsePagination(root.optJSONObject("pagination")),
    )
}

private fun parseSongbookPage(raw: String): CantusPage<CantusSongbook> {
    val root = JSONObject(raw)
    return CantusPage(
        data = root.optJSONArray("data").orEmpty().mapObjects(::parseSongbook),
        pagination = parsePagination(root.optJSONObject("pagination")),
    )
}

private fun parseSong(json: JSONObject): CantusSong =
    CantusSong(
        id = json.optStringOrNull("id") ?: json.optStringOrNull("slug").orEmpty(),
        slug = json.optStringOrNull("slug") ?: json.optStringOrNull("id").orEmpty(),
        title = json.optStringOrNull("title").orEmpty(),
        artist = json.optJSONObject("artist")?.let(::parseArtistRef),
        key = json.optStringOrNull("key"),
        bpm = json.optDoubleOrNull("bpm"),
        timeSignature = json.optStringOrNull("timeSignature"),
        language = json.optStringOrNull("language"),
        themes = json.optJSONArray("themes").orEmpty().mapStrings(),
        songbooks = json.optJSONArray("songbooks").orEmpty().mapObjects(::parseSongbookRef),
        media = json.optJSONObject("media")?.let(::parseMedia),
        chordedLyrics = json.optStringOrNull("chordedLyrics"),
        author = json.optStringOrNull("author"),
        copyright = json.optStringOrNull("copyright"),
    )

private fun parseArtistRef(json: JSONObject): CantusArtistRef =
    CantusArtistRef(
        id = json.optStringOrNull("id"),
        slug = json.optStringOrNull("slug"),
        name = json.optStringOrNull("name"),
    )

private fun parseSongbookRef(json: JSONObject): CantusSongbookRef =
    CantusSongbookRef(
        id = json.optStringOrNull("id"),
        slug = json.optStringOrNull("slug"),
        title = json.optStringOrNull("title"),
        number = json.optIntOrNull("number"),
    )

private fun parseMedia(json: JSONObject): CantusMedia =
    CantusMedia(
        sheetPdfUrl = json.optStringOrNull("sheetPdfUrl"),
        powerpointUrl = json.optStringOrNull("powerpointUrl"),
        audioUrl = json.optStringOrNull("audioUrl"),
        audioFiles = json.optJSONArray("audioFiles").orEmpty().mapObjects(::parseAudioFile),
        musicXmlUrl = json.optStringOrNull("musicXmlUrl"),
    )

private fun parseAudioFile(json: JSONObject): CantusAudioFile =
    CantusAudioFile(
        id = json.optStringOrNull("id"),
        fileName = json.optStringOrNull("fileName"),
        contentType = json.optStringOrNull("contentType"),
        storagePath = json.optStringOrNull("storagePath"),
        downloadUrl = json.optStringOrNull("downloadURL")
            ?: json.optStringOrNull("downloadUrl")
            ?: json.optStringOrNull("url")
            ?: json.optStringOrNull("audioUrl"),
        kind = json.optStringOrNull("kind"),
        duration = json.optDoubleOrNull("duration"),
        fileSize = json.optLongOrNull("fileSize")
            ?: json.optLongOrNull("audioBytes")
            ?: json.optLongOrNull("bytes"),
        voiceType = json.optStringOrNull("voiceType"),
        type = json.optStringOrNull("type"),
        displayName = json.optStringOrNull("displayName"),
    )

private fun parseArtist(json: JSONObject): CantusArtist =
    CantusArtist(
        id = json.optStringOrNull("id") ?: json.optStringOrNull("slug").orEmpty(),
        slug = json.optStringOrNull("slug") ?: json.optStringOrNull("id").orEmpty(),
        name = json.optStringOrNull("name").orEmpty(),
        languages = json.optJSONArray("languages").orEmpty().mapStrings(),
        songCount = json.optIntOrNull("songCount"),
    )

private fun parseSongbook(json: JSONObject): CantusSongbook =
    CantusSongbook(
        id = json.optStringOrNull("id") ?: json.optStringOrNull("slug").orEmpty(),
        slug = json.optStringOrNull("slug") ?: json.optStringOrNull("id").orEmpty(),
        title = json.optStringOrNull("title").orEmpty(),
        language = json.optStringOrNull("language"),
        publisher = json.optStringOrNull("publisher"),
        songCount = json.optIntOrNull("songCount"),
    )

private fun parsePagination(json: JSONObject?): CantusPagination =
    CantusPagination(
        total = json?.optInt("total") ?: 0,
        limit = json?.optInt("limit") ?: 0,
        offset = json?.optInt("offset") ?: 0,
        page = json?.optInt("page") ?: 1,
        nextPage = json?.optIntOrNull("nextPage"),
    )

private fun CantusPagination.toJson(): JSONObject =
    JSONObject()
        .put("total", total)
        .put("limit", limit)
        .put("offset", offset)
        .put("page", page)
        .apply { nextPage?.let { put("nextPage", it) } }

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private fun JSONArray.copyInto(destination: JSONArray) {
    for (index in 0 until length()) destination.put(get(index))
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }

private fun JSONArray.mapStrings(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeUnless { it.isBlank() } }

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeUnless { it.isBlank() } else null

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeUnless { it.isNaN() } else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

@Module
@InstallIn(SingletonComponent::class)
abstract class CantusBindings {
    @Binds
    @Singleton
    abstract fun bindCantusService(impl: BackendCantusService): CantusService
}
