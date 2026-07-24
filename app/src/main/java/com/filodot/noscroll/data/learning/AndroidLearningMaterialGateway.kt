package com.filodot.noscroll.data.learning

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import androidx.core.net.toUri
import com.filodot.noscroll.core.learning.importing.LearningMaterialDocument
import com.filodot.noscroll.core.learning.importing.LearningMaterialGateway
import com.filodot.noscroll.core.learning.importing.LearningMaterialImportException
import com.filodot.noscroll.core.learning.importing.MaterialSection
import com.filodot.noscroll.core.learning.model.LearningSourceType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class AndroidLearningMaterialGateway(
    context: Context,
) : LearningMaterialGateway {
    private val applicationContext = context.applicationContext
    private val resolver: ContentResolver = applicationContext.contentResolver

    override suspend fun read(reference: String): LearningMaterialDocument = withContext(Dispatchers.IO) {
        val uri = reference.toUri()
        val metadata = metadata(uri)
        if (metadata.size != null && metadata.size > MAX_FILE_BYTES) {
            throw failure(
                LearningMaterialImportException.Reason.FILE_TOO_LARGE,
                "Файл больше ${MAX_FILE_BYTES / 1_000_000} МБ",
            )
        }
        val type = detectType(metadata.name, resolver.getType(uri))
        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readLimited(MAX_FILE_BYTES) }
                ?: throw failure(
                    LearningMaterialImportException.Reason.READ_FAILED,
                    "Не удалось открыть выбранный файл",
                )
        } catch (error: LearningMaterialImportException) {
            throw error
        } catch (error: Exception) {
            throw failure(
                LearningMaterialImportException.Reason.READ_FAILED,
                "Не удалось прочитать выбранный файл",
                error,
            )
        }

        try {
            when (type) {
                LearningSourceType.PLAIN_TEXT,
                LearningSourceType.MARKDOWN,
                -> LearningMaterialDocument(
                    title = metadata.name,
                    type = type,
                    sections = listOf(MaterialSection(decodeText(bytes))),
                )

                LearningSourceType.DOCX -> LearningMaterialDocument(
                    title = metadata.name,
                    type = type,
                    sections = listOf(MaterialSection(extractDocx(bytes))),
                )

                LearningSourceType.PDF -> LearningMaterialDocument(
                    title = metadata.name,
                    type = type,
                    sections = extractPdf(bytes),
                )

                else -> throw failure(
                    LearningMaterialImportException.Reason.UNSUPPORTED_FORMAT,
                    "Поддерживаются PDF, DOCX, TXT и Markdown",
                )
            }
        } catch (error: LearningMaterialImportException) {
            throw error
        } catch (error: Exception) {
            throw failure(
                LearningMaterialImportException.Reason.CORRUPTED_FILE,
                "Файл повреждён или имеет неподдерживаемую структуру",
                error,
            )
        }
    }

    internal fun extractPdf(bytes: ByteArray): List<MaterialSection> {
        PDFBoxResourceLoader.init(applicationContext)
        return PDDocument.load(bytes).use { document ->
            if (document.isEncrypted) {
                throw failure(
                    LearningMaterialImportException.Reason.PASSWORD_PROTECTED,
                    "PDF с паролем пока не поддерживается",
                )
            }
            if (document.numberOfPages > MAX_PDF_PAGES) {
                throw failure(
                    LearningMaterialImportException.Reason.FILE_TOO_LARGE,
                    "В PDF больше $MAX_PDF_PAGES страниц",
                )
            }
            (1..document.numberOfPages).mapNotNull { page ->
                val text = PDFTextStripper().apply {
                    startPage = page
                    endPage = page
                    sortByPosition = true
                }.getText(document).trim()
                text.takeIf(String::isNotBlank)?.let {
                    MaterialSection(text = it, pageNumber = page, title = "Страница $page")
                }
            }
        }
    }

    internal fun extractDocx(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val xml = zip.readLimited(MAX_DOCX_XML_BYTES)
                    return parseWordDocument(xml)
                }
            }
        }
        throw failure(
            LearningMaterialImportException.Reason.CORRUPTED_FILE,
            "В DOCX не найден основной документ",
        )
    }

    private fun parseWordDocument(xml: ByteArray): String {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(ByteArrayInputStream(xml), Charsets.UTF_8.name())
        }
        val output = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "t") {
                if (parser.next() == XmlPullParser.TEXT) output.append(parser.text)
            } else if (event == XmlPullParser.END_TAG && parser.name == "p") {
                output.append("\n\n")
            } else if (event == XmlPullParser.END_TAG && parser.name == "tab") {
                output.append(' ')
            }
            event = parser.next()
        }
        return output.toString().trim()
    }

    private fun metadata(uri: Uri): FileMetadata {
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        return FileMetadata(name = name ?: uri.lastPathSegment ?: "Материал", size = size)
    }

    private fun detectType(fileName: String, mimeType: String?): LearningSourceType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mimeType == "application/pdf" || extension == "pdf" -> LearningSourceType.PDF
            mimeType == DOCX_MIME || extension == "docx" -> LearningSourceType.DOCX
            extension in setOf("md", "markdown") || mimeType == "text/markdown" ->
                LearningSourceType.MARKDOWN
            mimeType?.startsWith("text/") == true || extension in setOf("txt", "text") ->
                LearningSourceType.PLAIN_TEXT
            else -> throw failure(
                LearningMaterialImportException.Reason.UNSUPPORTED_FORMAT,
                "Формат .$extension не поддерживается",
            )
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        val offset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        return bytes.copyOfRange(offset, bytes.size).toString(Charsets.UTF_8)
    }

    private fun InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw failure(
                    LearningMaterialImportException.Reason.FILE_TOO_LARGE,
                    "Файл превышает допустимый объём",
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private data class FileMetadata(
        val name: String,
        val size: Long?,
    )

    private companion object {
        const val MAX_FILE_BYTES = 20_000_000
        const val MAX_DOCX_XML_BYTES = 8_000_000
        const val MAX_PDF_PAGES = 500
        const val DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        fun failure(
            reason: LearningMaterialImportException.Reason,
            message: String,
            cause: Throwable? = null,
        ) = LearningMaterialImportException(reason, message, cause)
    }
}
