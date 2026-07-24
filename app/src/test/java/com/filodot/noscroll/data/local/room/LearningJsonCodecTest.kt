package com.filodot.noscroll.data.local.room

import com.filodot.noscroll.core.learning.model.ActivityContent
import com.filodot.noscroll.core.learning.model.ChoiceOption
import com.filodot.noscroll.core.learning.model.CodeCompletionContent
import com.filodot.noscroll.core.learning.model.CodeFixContent
import com.filodot.noscroll.core.learning.model.CodeLanguage
import com.filodot.noscroll.core.learning.model.CodeOutputContent
import com.filodot.noscroll.core.learning.model.CodeTestCase
import com.filodot.noscroll.core.learning.model.EvidenceSelectionContent
import com.filodot.noscroll.core.learning.model.FillBlankContent
import com.filodot.noscroll.core.learning.model.FlashcardContent
import com.filodot.noscroll.core.learning.model.MatchingContent
import com.filodot.noscroll.core.learning.model.MatchingItem
import com.filodot.noscroll.core.learning.model.MatchingPair
import com.filodot.noscroll.core.learning.model.MiniCodeContent
import com.filodot.noscroll.core.learning.model.MultipleChoiceContent
import com.filodot.noscroll.core.learning.model.NumericAnswerContent
import com.filodot.noscroll.core.learning.model.OrderingContent
import com.filodot.noscroll.core.learning.model.OrderingItem
import com.filodot.noscroll.core.learning.model.ScenarioContent
import com.filodot.noscroll.core.learning.model.ShortAnswerContent
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import com.filodot.noscroll.core.learning.model.SourceCitation
import com.filodot.noscroll.core.learning.model.TeachBackContent
import com.filodot.noscroll.core.learning.model.TrueFalseContent
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningJsonCodecTest {
    @Test
    fun `all activity payloads round trip through stable json`() {
        contents().forEach { content ->
            assertEquals(
                content,
                LearningJsonCodec.decodeContent(
                    expectedKind = content.kind,
                    value = LearningJsonCodec.encodeContent(content),
                ),
            )
        }
    }

    @Test
    fun `ordered ids and source locations retain their order`() {
        val ids = listOf("third", "first", "second")
        val citations = listOf(
            SourceCitation("source", "chunk-2", pageNumber = 2, sectionTitle = "B"),
            SourceCitation("source", "chunk-1", pageNumber = 1, sectionTitle = "A"),
        )

        assertEquals(ids, LearningJsonCodec.decodeStringList(LearningJsonCodec.encodeStrings(ids)))
        assertEquals(
            citations,
            LearningJsonCodec.decodeCitations(LearningJsonCodec.encodeCitations(citations)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `payload cannot be decoded as another activity kind`() {
        val content = contents().first()

        LearningJsonCodec.decodeContent(
            expectedKind = com.filodot.noscroll.core.learning.model.ActivityKind.FLASHCARD,
            value = LearningJsonCodec.encodeContent(content),
        )
    }

    private fun contents(): List<ActivityContent> {
        val options = listOf(
            ChoiceOption("a", "Alpha"),
            ChoiceOption("b", "Beta"),
            ChoiceOption("c", "Gamma"),
        )
        val tests = listOf(
            CodeTestCase("visible", "2", "4"),
            CodeTestCase("hidden", "3", "6", hidden = true),
        )
        return listOf(
            SingleChoiceContent(options, "a"),
            MultipleChoiceContent(options, setOf("a", "b")),
            TrueFalseContent("Two is even", expected = true, correction = null),
            OrderingContent(
                items = listOf(
                    OrderingItem("3", "Third"),
                    OrderingItem("1", "First"),
                    OrderingItem("2", "Second"),
                ),
                correctOrderIds = listOf("1", "2", "3"),
            ),
            MatchingContent(
                left = listOf(MatchingItem("l1", "One"), MatchingItem("l2", "Two")),
                right = listOf(MatchingItem("r1", "I"), MatchingItem("r2", "II")),
                correctPairs = listOf(MatchingPair("l1", "r1"), MatchingPair("l2", "r2")),
            ),
            FillBlankContent("x = {{blank}}", setOf("1", "one")),
            ShortAnswerContent(rubric = "Mention assignment"),
            NumericAnswerContent(expected = 3.14, tolerance = 0.01),
            FlashcardContent("Answer"),
            EvidenceSelectionContent(options, setOf("b")),
            ScenarioContent(
                options = options,
                correctOptionId = "c",
                consequenceByOptionId = mapOf(
                    "a" to "First",
                    "b" to "Second",
                    "c" to "Correct",
                ),
            ),
            CodeOutputContent(CodeLanguage.PYTHON, "print(2)", setOf("2")),
            CodeCompletionContent(
                CodeLanguage.SQL,
                "SELECT {{code}} FROM users",
                setOf("*"),
                tests,
            ),
            CodeFixContent(CodeLanguage.PYTHON, "print((", tests),
            MiniCodeContent(CodeLanguage.SQL, "SELECT 1", tests),
            TeachBackContent("Explain simply", listOf("First", "Second")),
        )
    }
}
