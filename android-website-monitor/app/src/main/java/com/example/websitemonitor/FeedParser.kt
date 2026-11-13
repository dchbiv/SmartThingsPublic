package com.example.websitemonitor

/** Minimal RSS/Atom reader: pulls out the title of each item/entry. */
object FeedParser {
    private val itemRegex = Regex("<(item|entry)\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val titleRegex = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cdataRegex = Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)
    private val tagRegex = Regex("<[^>]+>")

    /** Returns item titles, or null if [text] doesn't look like a feed. */
    fun itemTitles(text: String): List<String>? {
        val head = text.take(2000)
        if (!head.contains("<rss", true) && !head.contains("<feed", true) &&
            !head.contains("<rdf:RDF", true)
        ) return null
        return itemRegex.findAll(text).mapNotNull { m ->
            titleRegex.find(m.groupValues[2])?.groupValues?.get(1)?.let(::clean)
        }.filter { it.isNotEmpty() }.toList()
    }

    private fun clean(raw: String): String {
        val s = cdataRegex.replace(raw) { it.groupValues[1] }
        return tagRegex.replace(s, "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
            .replace(Regex("\\s+"), " ").trim()
    }
}
