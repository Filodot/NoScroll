package com.filodot.noscroll.data.learning

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLearningMaterialGatewayTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val gateway get() = AndroidLearningMaterialGateway(context)

    @Test
    fun `extracts paragraphs from docx document xml`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>Первый абзац</w:t></w:r></w:p>
                <w:p><w:r><w:t>Второй</w:t></w:r><w:r><w:t> абзац</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }

        assertEquals("Первый абзац\n\nВторой абзац", gateway.extractDocx(bytes))
    }

    @Test
    fun `extracts page text from ordinary pdf without crypto providers`() {
        PDFBoxResourceLoader.init(context)
        val bytes = ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA, 12f)
                    content.newLineAtOffset(72f, 720f)
                    content.showText("Learning PDF")
                    content.endText()
                }
                document.save(output)
            }
            output.toByteArray()
        }

        val sections = gateway.extractPdf(bytes)

        assertEquals(1, sections.size)
        assertEquals(1, sections.single().pageNumber)
        assertTrue(sections.single().text.contains("Learning PDF"))
    }
}
