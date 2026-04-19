package com.srini.wheresthatphoto.ml.tokenizer

/**
 * WordPiece tokenizer for MiniLM / BERT ONNX (all-MiniLM-L6-v2 style vocab.txt).
 */
internal class MiniLmWordpieceTokenizer(
    private val vocab: Map<String, Int>,
    private val unkId: Int = 100,
    private val clsId: Int = 101,
    private val sepId: Int = 102,
    private val padId: Int = 0,
    private val maxLen: Int = 128,
    private val maxInputCharsPerWord: Int = 30
) {
    fun encode(text: String): Pair<LongArray, LongArray> {
        val words = tokenizeWords(text.lowercase())
        val output = ArrayList<Int>(maxLen)
        output.add(clsId)
        for (word in words) {
            if (output.size >= maxLen - 1) break
            if (word.isEmpty()) continue
            wordpieceToIds(word, output)
        }
        output.add(sepId)
        val inputIds = LongArray(maxLen) { padId.toLong() }
        val mask = LongArray(maxLen) { 0L }
        for (i in output.indices) {
            if (i >= maxLen) break
            inputIds[i] = output[i].toLong()
            mask[i] = 1L
        }
        return inputIds to mask
    }

    private fun tokenizeWords(text: String): List<String> =
        text.split(Regex("[\\s\\p{Punct}]+")).filter { it.isNotEmpty() }

    private fun wordpieceToIds(token: String, out: ArrayList<Int>) {
        if (token.length > maxInputCharsPerWord) {
            out.add(unkId)
            return
        }
        var start = 0
        while (start < token.length) {
            var end = token.length
            var found: String? = null
            while (start < end) {
                var sub = token.substring(start, end)
                if (start > 0) sub = "##$sub"
                if (vocab.containsKey(sub)) {
                    found = sub
                    break
                }
                end--
            }
            if (found == null) {
                out.add(unkId)
                return
            }
            out.add(vocab.getValue(found))
            start = end
        }
    }

    companion object {
        fun fromVocabLines(lines: List<String>): MiniLmWordpieceTokenizer {
            val vocab = lines.mapIndexed { index, token -> token to index }.toMap()
            return MiniLmWordpieceTokenizer(vocab)
        }
    }
}
