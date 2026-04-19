package com.srini.wheresthatphoto.ml.tokenizer

import java.util.regex.Pattern

/**
 * CLIP byte-pair tokenizer (OpenAI-style) for MobileCLIP text ONNX [batch, 77] int64 input.
 */
internal class ClipBytePairTokenizer(
    private val encoder: Map<String, Int>,
    bpeMerges: List<Pair<String, String>>
) {
    private val bpeRanks: Map<Pair<String, String>, Int> =
        bpeMerges.withIndex().associate { it.value to it.index }

    private val byteEncoder: Map<Int, Char> = bytesToUnicode()
    private val bpeCache = HashMap<String, String>(2048)

    fun encodeToIds(text: String, contextLength: Int = 77): LongArray {
        val cleaned = whitespaceClean(text).lowercase()
        val matcher = PAT.matcher(cleaned)
        val bpeTokens = ArrayList<Int>(48)
        while (matcher.find()) {
            var token = matcher.group()
            val mapped = buildString {
                for (b in token.toByteArray(Charsets.UTF_8)) {
                    append(byteEncoder[b.toInt() and 0xFF] ?: error("byte out of table"))
                }
            }
            for (piece in bpe(mapped).split(' ')) {
                if (piece.isEmpty()) continue
                val id = encoder[piece] ?: encoder["<|endoftext|>"] ?: 49407
                bpeTokens.add(id)
            }
        }
        val bos = encoder["<|startoftext|>"] ?: 49406
        val eos = encoder["<|endoftext|>"] ?: 49407
        val pad = 0
        val out = LongArray(contextLength) { pad.toLong() }
        out[0] = bos.toLong()
        var write = 1
        for (id in bpeTokens) {
            if (write >= contextLength - 1) break
            out[write++] = id.toLong()
        }
        if (write < contextLength) {
            out[write] = eos.toLong()
        }
        return out
    }

    private fun bpe(token: String): String {
        if (token.isEmpty()) return token
        bpeCache[token]?.let { return it }
        val last = token.last().toString()
        val prefix = token.dropLast(1)
        var word = ArrayList<String>(prefix.length + 1).apply {
            prefix.forEach { add(it.toString()) }
            add("$last ")
        }
        var pairs = pairsOf(word)
        if (pairs.isEmpty()) {
            val r = "$token "
            bpeCache[token] = r
            return r
        }
        while (true) {
            val bigram = pairs.minByOrNull { pair -> bpeRanks[pair] ?: Int.MAX_VALUE } ?: break
            if (!bpeRanks.containsKey(bigram)) break
            val (first, second) = bigram
            val newWord = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                val j = indexOfSymbol(word, first, i)
                if (j == -1) {
                    while (i < word.size) newWord.add(word[i++])
                    break
                }
                while (i < j) newWord.add(word[i++])
                if (i < word.size && word[i] == first && i + 1 < word.size && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i++])
                }
            }
            word = newWord
            if (word.size == 1) break
            pairs = pairsOf(word)
        }
        val result = word.joinToString(" ")
        bpeCache[token] = result
        return result
    }

    private fun indexOfSymbol(word: List<String>, symbol: String, start: Int): Int {
        for (idx in start until word.size) {
            if (word[idx] == symbol) return idx
        }
        return -1
    }

    private fun pairsOf(symbols: List<String>): Set<Pair<String, String>> {
        if (symbols.size < 2) return emptySet()
        val pairs = LinkedHashSet<Pair<String, String>>()
        var prev = symbols[0]
        for (i in 1 until symbols.size) {
            val cur = symbols[i]
            pairs.add(prev to cur)
            prev = cur
        }
        return pairs
    }

    private fun whitespaceClean(text: String): String =
        text.trim().replace(Regex("\\s+"), " ")

    companion object {
        // NOTE: Android's java.util.regex.Pattern does NOT support
        // Pattern.UNICODE_CHARACTER_CLASS — passing it throws at class load
        // time (ExceptionInInitializerError). It's only needed on the JVM to
        // make \w/\d/\s match Unicode; \p{L} and \p{N} already reference
        // Unicode general categories directly, so dropping the flag doesn't
        // change tokenization for English (the only language CLIP really
        // supports anyway). Revisit if we ever need Unicode-aware \w/\d.
        private val PAT: Pattern = Pattern.compile(
            """<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""",
            Pattern.CASE_INSENSITIVE
        )

        fun fromMergesAndVocab(
            vocab: Map<String, Int>,
            mergesLines: List<String>
        ): ClipBytePairTokenizer {
            val merges = mergesLines.mapNotNull { line ->
                val p = line.split(' ')
                if (p.size == 2) p[0] to p[1] else null
            }
            return ClipBytePairTokenizer(vocab, merges)
        }

        /** OpenAI CLIP `bytes_to_unicode` table. */
        fun bytesToUnicode(): Map<Int, Char> {
            val bs = mutableListOf<Int>()
            for (c in '!'.code..'~'.code) bs.add(c)
            for (c in '¡'.code..'¬'.code) bs.add(c)
            for (c in '®'.code..'ÿ'.code) bs.add(c)
            val cs = bs.toMutableList()
            var n = 0
            for (b in 0 until 256) {
                if (b !in bs) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }
            val chars = cs.map { code -> Char(code) }
            return bs.mapIndexed { idx, b -> b to chars[idx] }.toMap()
        }
    }
}
