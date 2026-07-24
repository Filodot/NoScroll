package com.filodot.noscroll.core.learning.importing

import com.filodot.noscroll.core.learning.model.LearningSourceType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningMaterialProcessorTest {
    @Test
    fun `normalizes sections and keeps page metadata in bounded chunks`() {
        var id = 0
        val processor = LearningMaterialProcessor(
            targetChunkCharacters = 200,
            maximumChunkCharacters = 240,
            maximumMaterialCharacters = 10_000,
            idGenerator = { "chunk-${id++}" },
        )
        val document = LearningMaterialDocument(
            title = "  Конспект  ",
            type = LearningSourceType.PDF,
            sections = listOf(
                MaterialSection(
                    text = "  Первый   абзац.\r\n\r\n" + "Второе предложение. ".repeat(15),
                    pageNumber = 1,
                ),
                MaterialSection(text = "Текст второй страницы.", pageNumber = 2),
            ),
        )

        val result = processor.prepare(
            courseId = "course",
            sourceId = "source",
            document = document,
            importedAt = Instant.EPOCH,
        )

        assertEquals("Конспект", result.source.title)
        assertEquals(LearningSourceType.PDF, result.source.type)
        assertTrue(result.chunks.size >= 2)
        assertTrue(result.chunks.all { it.text.length <= 240 })
        assertEquals(1, result.chunks.first().pageNumber)
        assertEquals(2, result.chunks.last().pageNumber)
        assertEquals(result.chunks.indices.toList(), result.chunks.map { it.position })
        assertTrue(result.chunks.zipWithNext().all { (left, right) ->
            left.characterEnd <= right.characterStart
        })
    }

    @Test
    fun `content hash changes with normalized source content`() {
        val processor = LearningMaterialProcessor(idGenerator = { "chunk" })
        val first = processor.prepare(
            "course",
            "source",
            LearningMaterialDocument(
                "A",
                LearningSourceType.PLAIN_TEXT,
                listOf(MaterialSection("один  два")),
            ),
            Instant.EPOCH,
        )
        val equalAfterNormalization = processor.prepare(
            "course",
            "source",
            LearningMaterialDocument(
                "B",
                LearningSourceType.PLAIN_TEXT,
                listOf(MaterialSection("один два")),
            ),
            Instant.EPOCH,
        )
        val different = processor.prepare(
            "course",
            "source",
            LearningMaterialDocument(
                "C",
                LearningSourceType.PLAIN_TEXT,
                listOf(MaterialSection("три")),
            ),
            Instant.EPOCH,
        )

        assertEquals(first.source.contentHash, equalAfterNormalization.source.contentHash)
        assertNotEquals(first.source.contentHash, different.source.contentHash)
    }

    @Test(expected = LearningMaterialImportException::class)
    fun `rejects empty material`() {
        LearningMaterialProcessor().prepare(
            "course",
            "source",
            LearningMaterialDocument(
                "Empty",
                LearningSourceType.PLAIN_TEXT,
                listOf(MaterialSection(" \n\t ")),
            ),
            Instant.EPOCH,
        )
    }
}
