package com.reader343.domain

private val IsbnPattern = Regex(
    """(?i)(ISBN(?:[-\s]*1[03])?\s*[:：]?\s*)?(?<![\d\-‐‑‒–—])(\d[\d\s\-‐‑‒–—]{8,20}[\dX])(?![\d\-‐‑‒–—])""",
)
private val IsbnSeparators = Regex("""[\s\-‐‑‒–—]""")

fun findIsbn(text: String): String? {
    var unlabeled: String? = null
    for (match in IsbnPattern.findAll(text)) {
        val labeled = match.groups[1] != null
        val digits = match.groupValues[2].replace(IsbnSeparators, "").uppercase()
        val isbn13 = digits.take(ISBN13_LENGTH)
        val isbn10 = digits.take(ISBN10_LENGTH)
        val isbn = when {
            isValidIsbn13(isbn13) -> isbn13
            labeled && isValidIsbn10(isbn10) -> isbn10To13(isbn10)
            else -> null
        } ?: continue
        if (labeled) return isbn
        if (unlabeled == null) unlabeled = isbn
    }
    return unlabeled
}

fun isValidIsbn13(value: String): Boolean {
    if (value.length != ISBN13_LENGTH || !value.all { it.isDigit() }) return false
    if (!value.startsWith("978") && !value.startsWith("979")) return false
    val sum = value.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
    return sum % 10 == 0
}

fun isValidIsbn10(value: String): Boolean {
    if (value.length != ISBN10_LENGTH) return false
    if (!value.take(ISBN10_LENGTH - 1).all { it.isDigit() }) return false
    val last = value.last()
    if (!last.isDigit() && last != 'X') return false
    val sum = value.mapIndexed { i, c ->
        val digit = if (c == 'X') 10 else c - '0'
        digit * (ISBN10_LENGTH - i)
    }.sum()
    return sum % 11 == 0
}

fun isbn10To13(value: String): String {
    val core = "978" + value.take(ISBN10_LENGTH - 1)
    val sum = core.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
    return core + ((10 - sum % 10) % 10)
}

private val Brackets = Regex("""[\[(（{【][^\])）}】]*[\])）}】]""")
private val Extension = Regex("""(?i)\.(pdf|epub|djvu)$""")
private val SiteTags = Regex("""(?i)\b(z-?lib(rary)?(\.\w+)?|libgen(\.\w+)?|b-ok(\.\w+)?|ebook|e-book|epub|pdf|retail|scan(ned)?|www\.\S+|\S+\.(com|org|net))\b""")
private val EditionNoise = Regex("""(?i)\b(\d+(st|nd|rd|th)\s+)?(ed\.?|edition|edn)\b|\b(19|20)\d{2}\b|\bv\d+(\.\d+)*\b""")
private val Separators = Regex("""[_+.]+""")
private val Spaces = Regex("""\s+""")

fun cleanTitleQuery(raw: String): String {
    var s = raw.trim().replace(Extension, "")
    s = s.replace(Brackets, " ")
    s = s.replace(SiteTags, " ")
    s = s.replace(Separators, " ")
    val trimmed = s.trim()
    if (trimmed.count { it == '-' } > trimmed.count { it == ' ' }) s = s.replace('-', ' ')
    s = s.replace(Regex("""\s+[-–—]\s+"""), " ")
    s = s.replace(SiteTags, " ").replace(EditionNoise, " ")
    return s.replace(Spaces, " ").trim(' ', '-', '–', '—', ',', ':', ';')
}

private fun titleTokens(value: String): Set<String> =
    value.lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .toSet()

fun titleSimilarity(query: String, candidate: String): Float {
    val a = titleTokens(query)
    val b = titleTokens(candidate)
    if (a.isEmpty() || b.isEmpty()) return 0f
    val shared = a.intersect(b).size
    return 2f * shared / (a.size + b.size)
}

fun rankCandidates(query: MetadataQuery, candidates: List<BookMetadata>): List<RankedCandidate> {
    val ranked = candidates.mapIndexed { index, metadata ->
        val score = when (query) {
            is MetadataQuery.Isbn -> if (metadata.isbn == query.isbn) 1f else 0.9f
            is MetadataQuery.Title -> titleSimilarity(query.title, metadata.title)
        }
        Triple(index, metadata, score)
    }.sortedWith(compareByDescending<Triple<Int, BookMetadata, Float>> { it.third }.thenBy { it.first })
    return ranked.mapIndexed { position, (_, metadata, score) ->
        RankedCandidate(metadata, score, closest = position == 0 && score >= CLOSEST_THRESHOLD)
    }
}

private const val ISBN13_LENGTH = 13
private const val ISBN10_LENGTH = 10
private const val CLOSEST_THRESHOLD = 0.6f
