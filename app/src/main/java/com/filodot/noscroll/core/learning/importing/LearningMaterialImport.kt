package com.filodot.noscroll.core.learning.importing

import com.filodot.noscroll.core.learning.model.LearningSource
import com.filodot.noscroll.core.learning.model.LearningSourceChunk
import com.filodot.noscroll.core.learning.model.LearningSourceType
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class MaterialSection(
    val text: String,
    val pageNumber: Int? = null,
    val title: String? = null,
)

data class LearningMaterialDocument(
    val title: String,
    val type: LearningSourceType,
    val sections: List<MaterialSection>,
)

data class PreparedLearningMaterial(
    val source: LearningSource,
    val chunks: List<LearningSourceChunk>,
    val characterCount: Int,
)

/**
 * Platform boundary for SAF/ContentResolver. Keeping Uri out of the domain makes import testable.
 */
fun interface LearningMaterialGateway {
    suspend fun read(reference: String): LearningMaterialDocument
}

class LearningMaterialImportException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Reason {
        UNSUPPORTED_FORMAT,
        FILE_TOO_LARGE,
        EMPTY_MATERIAL,
        PASSWORD_PROTECTED,
        CORRUPTED_FILE,
        READ_FAILED,
    }
}

class LearningMaterialProcessor(
    private val targetChunkCharacters: Int = 2_200,
    private val maximumChunkCharacters: Int = 3_200,
    private val maximumMaterialCharacters: Int = 2_000_000,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    init {
        require(targetChunkCharacters in 200..maximumChunkCharacters)
        require(maximumMaterialCharacters >= maximumChunkCharacters)
    }

    fun prepare(
        courseId: String,
        sourceId: String,
        document: LearningMaterialDocument,
        importedAt: Instant,
    ): PreparedLearningMaterial {
        val normalizedSections = document.sections.mapNotNull { section ->
            normalize(section.text).takeIf(String::isNotBlank)?.let { section.copy(text = it) }
        }
        val fullText = normalizedSections.joinToString("\n\n") { it.text }
        if (fullText.isBlank()) {
            throw LearningMaterialImportException(
                LearningMaterialImportException.Reason.EMPTY_MATERIAL,
                "В выбранном файле не найден текст",
            )
        }
        if (fullText.length > maximumMaterialCharacters) {
            throw LearningMaterialImportException(
                LearningMaterialImportException.Reason.FILE_TOO_LARGE,
                "После извлечения материал превышает допустимый объём",
            )
        }

        val chunks = buildList {
            var globalOffset = 0
            normalizedSections.forEach { section ->
                chunk(section.text).forEach { part ->
                    val start = fullText.indexOf(part, startIndex = globalOffset).coerceAtLeast(globalOffset)
                    val end = start + part.length
                    add(
                        LearningSourceChunk(
                            id = idGenerator(),
                            sourceId = sourceId,
                            courseId = courseId,
                            position = size,
                            text = part,
                            pageNumber = section.pageNumber,
                            sectionTitle = section.title,
                            characterStart = start,
                            characterEnd = end,
                            estimatedTokens = ((part.length + 3) / 4).coerceAtLeast(1),
                        ),
                    )
                    globalOffset = end
                }
                globalOffset = (globalOffset + 2).coerceAtMost(fullText.length)
            }
        }
        val source = LearningSource(
            id = sourceId,
            courseId = courseId,
            title = document.title.trim().ifBlank { "Учебный материал" },
            type = document.type,
            contentHash = sha256(fullText),
            importedAt = importedAt,
        )
        return PreparedLearningMaterial(source, chunks, fullText.length)
    }

    private fun chunk(text: String): List<String> {
        val paragraphs = text.split(Regex("\\n{2,}")).flatMap(::splitOversized)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        paragraphs.forEach { paragraph ->
            val separatorSize = if (current.isEmpty()) 0 else 2
            if (current.length + separatorSize + paragraph.length > targetChunkCharacters &&
                current.isNotEmpty()
            ) {
                chunks += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private fun splitOversized(paragraph: String): List<String> {
        if (paragraph.length <= maximumChunkCharacters) return listOf(paragraph)
        val sentences = paragraph.split(Regex("(?<=[.!?…])\\s+"))
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        sentences.forEach { sentence ->
            if (sentence.length > maximumChunkCharacters) {
                if (current.isNotEmpty()) {
                    parts += current.toString()
                    current = StringBuilder()
                }
                sentence.chunked(maximumChunkCharacters).forEach(parts::add)
            } else if (current.length + sentence.length + 1 > maximumChunkCharacters) {
                parts += current.toString()
                current = StringBuilder(sentence)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    private fun normalize(value: String): String = value
        .replace("\u0000", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { line -> line.replace(Regex("[\\t ]+"), " ").trim() }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
