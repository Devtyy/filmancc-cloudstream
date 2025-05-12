package xyz.filman.filmancc

import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.Headers
import org.jsoup.Jsoup
import rx.Observable

class FilmanCcProvider : HttpSource() {

    override val name = "Filman.cc"
    override val baseUrl = "https://filman.cc "
    override val lang = "pl"
    override val supportsLatest = true
    override val client = network.cloudflareClient

    private val json = Json { ignoreUnknownKeys = true }

    // 🔍 Wyszukiwanie filmów
    override fun fetchSearchAnime(page: Int, query: String): Observable<List<SearchResponse>> {
        return client.newCall(GET("$baseUrl/item?phrase=$query")).asObservableSuccess()
            .flatMap { searchAnimeParse(it) }
    }

    override fun searchAnimeParse(response: Response): List<SearchResponse> {
        val doc = response.asJsoup()

        return doc.select(".poster a").mapNotNull { element ->
            val title = element.attr("title") ?: element.select("img").attr("alt")
            val href = element.attr("href")

            if (title.isBlank()) return@mapNotNull null

            newMovieSearchResponse(title, href, this.name)
                .copy(
                    posterUrl = fixUrl(element.select("img").attr("src")),
                    type = TvType.Movie
                )
        }
    }

    // 🏠 Strona główna
    override val mainPage = mainPageOf(
        Pair("1", "Filmy na czasie"),
        Pair("2", "Filmy na topie")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(baseUrl).document
        val items = when (request.data) {
            "1" -> document.select("#item-list .col-xs-6.col-sm-2")
            "2" -> document.select("#item-list .col-xs-6.col-sm-2")
            else -> emptyElements()
        }

        val list = items.mapNotNull { element ->
            val linkElement = element.selectFirst("a") ?: return@mapNotNull null
            val title = linkElement.attr("title") ?: linkElement.select("img").attr("alt")
            val href = linkElement.attr("href")
            val poster = fixUrl(linkElement.select("img").attr("src"))

            newMovieSearchResponse(title, href, name)
                .copy(posterUrl = poster)
        }

        return newHomePageResponse(request.name, list)
    }

    // 🎬 Szczegóły filmu
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.select("h1[itemprop='name']").text().trim()
        val year = document.select(".info > ul > li:eq(0)").text().replace("Rok:", "").trim().toIntOrNull()
        val description = document.select(".description").text().trim()
        val tags = document.select(".categories a").eachText()
        val poster = fixUrl(document.select("img[itemprop='image']").attr("src"))

        return newMovieLoadResponse(title, url, TvType.Movie, { Jsoup.parse(description) }) {
            this.year = year
            this.plot = description
            this.posterUrl = poster
            this.tags = tags
        }
    }

    // 🎥 Linki do filmów
    override suspend fun loadLinks(data: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(data).document
        val rows = doc.select("#links tbody tr")

        for (row in rows) {
            val anchor = row.selectFirst("a") ?: continue
            val hosting = anchor.select("img").attr("alt")
            val quality = row.select("td:eq(2)").text().replace("p", "").toIntOrNull() ?: 0
            val encodedIframe = anchor.attr("data-iframe")

            if (encodedIframe.isNotBlank()) {
                try {
                    val decoded = Base64.decode(encodedIframe, Base64.DEFAULT)
                    val iframeJson = String(decoded)
                    val src = json.parseToJsonElement(iframeJson).jsonObject["src"]?.jsonPrimitive?.content.orEmpty()

                    if (src.isNotBlank()) {
                        callback.invoke(
                            ExtractorLink(
                                source = "$name ($hosting)",
                                name = hosting,
                                url = src,
                                headers = Headers.of(mapOf("Referer" to baseUrl))
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignoruj błędne dane
                }
            }
        }

        return true
    }

    // Pomocnicze
    private fun fixUrl(url: String): String {
        return if (url.startsWith("/")) "$baseUrl$url" else url
    }

    private fun emptyElements(): List<Element> {
        return listOf()
    }
}