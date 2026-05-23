package com.example.data

import com.example.api.PaperResponse
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object DocxExporter {

    fun exportToDocx(
        paper: PaperResponse,
        title: String,
        author: String,
        school: String,
        subject: String,
        year: String,
        outputStream: OutputStream
    ) {
        val zip = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        writeZipEntry(zip, "[Content_Types].xml", getContentTypesXml())

        // 2. _rels/.rels
        writeZipEntry(zip, "_rels/.rels", getRelsXml())

        // 3. word/_rels/document.xml.rels
        writeZipEntry(zip, "word/_rels/document.xml.rels", getDocRelsXml())

        // Parse footnotes out of the paper content to build word/footnotes.xml and update word/document.xml
        val footnoteList = mutableListOf<String>()
        
        val docXml = getDocumentXml(paper, title, author, school, subject, year, footnoteList)
        val footnotesXml = getFootnotesXml(footnoteList)

        // 4. word/document.xml
        writeZipEntry(zip, "word/document.xml", docXml)

        // 5. word/footnotes.xml
        writeZipEntry(zip, "word/footnotes.xml", footnotesXml)

        zip.close()
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zip.putNextEntry(entry)
        val writer = OutputStreamWriter(zip, StandardCharsets.UTF_8)
        writer.write(content)
        writer.flush()
        zip.closeEntry()
    }

    private fun getContentTypesXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
              <Override PartName="/word/footnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"/>
            </Types>
        """.trimIndent().trim()
    }

    private fun getRelsXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent().trim()
    }

    private fun getDocRelsXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rIdFootnotes" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes" Target="footnotes.xml"/>
            </Relationships>
        """.trimIndent().trim()
    }

    private fun getFootnotesXml(footnotes: List<String>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:footnotes xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:footnote w:type="separator" w:id="-1">
    <w:p><w:pPr><w:spacing w:before="0" w:after="0"/></w:pPr><w:r><w:separator/></w:r></w:p>
  </w:footnote>
  <w:footnote w:type="continuationSeparator" w:id="0">
    <w:p><w:pPr><w:spacing w:before="0" w:after="0"/></w:pPr><w:r><w:continuationSeparator/></w:r></w:p>
  </w:footnote>""")

        for ((index, note) in footnotes.withIndex()) {
            val fId = index + 1
            sb.append("""
  <w:footnote w:id="$fId">
    <w:p>
      <w:pPr>
        <w:pStyle w:val="FootnoteText"/>
        <w:spacing w:line="240" w:lineRule="auto" w:before="0" w:after="0"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rStyle w:val="FootnoteReference"/>
          <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
          <w:sz w:val="20"/>
        </w:rPr>
        <w:footnoteRef/>
      </w:r>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
          <w:sz w:val="20"/>
        </w:rPr>
        <w:t xml:space="preserve"> ${escapeXml(note)}</w:t>
      </w:r>
    </w:p>
  </w:footnote>""")
        }

        sb.append("\n</w:footnotes>")
        return sb.toString()
    }

    private fun getDocumentXml(
        paper: PaperResponse,
        title: String,
        author: String,
        school: String,
        subject: String,
        year: String,
        footnoteList: MutableList<String>
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>""")

        // --- COVER PAGE ---
        sb.append(getCenteredBoldParagraph(title.uppercase(), fontSize = 28, spaceAfter = 1200)) // Large title
        sb.append(getCenteredParagraph("MAKALAH", fontSize = 24, bold = true, spaceAfter = 1800))
        sb.append(getCenteredParagraph("Disusun Oleh:", fontSize = 24, bold = true, spaceAfter = 400))
        sb.append(getCenteredParagraph(author, fontSize = 24, bold = true, spaceAfter = 2200))
        
        if (subject.isNotBlank()) {
            sb.append(getCenteredParagraph("Mata Pelajaran: " + subject, fontSize = 24, bold = true, spaceAfter = 400))
        }
        sb.append(getCenteredParagraph(school.uppercase(), fontSize = 24, bold = true, spaceAfter = 200))
        sb.append(getCenteredParagraph(year, fontSize = 24, bold = true, spaceAfter = 0))
        
        // Page break after cover
        sb.append(getPageBreak())

        // --- KATA PENGANTAR ---
        sb.append(getHeading1Paragraph("KATA PENGANTAR"))
        sb.append(getSmartParagraphs(paper.kataPengantar, footnoteList))
        sb.append(getPageBreak())

        // --- DAFTAR ISI ---
        sb.append(getHeading1Paragraph("DAFTAR ISI"))
        sb.append(getTOCLine("KATA PENGANTAR", "ii"))
        sb.append(getTOCLine("DAFTAR ISI", "iii"))
        sb.append(getTOCLine("BAB I PENDAHULUAN", "1"))
        sb.append(getTOCSubLine("A. Latar Belakang", "1"))
        sb.append(getTOCSubLine("B. Rumusan Masalah", "2"))
        sb.append(getTOCSubLine("C. Tujuan", "2"))
        sb.append(getTOCLine("BAB II PEMBAHASAN", "3"))
        
        var charIdx = 'A'
        for (sec in paper.bab2) {
            sb.append(getTOCSubLine("$charIdx. ${sec.subjudul}", "3"))
            charIdx++
        }
        sb.append(getTOCLine("BAB III PENUTUP", "9"))
        sb.append(getTOCSubLine("A. Kesimpulan", "9"))
        sb.append(getTOCSubLine("B. Saran", "10"))
        sb.append(getTOCLine("DAFTAR PUSTAKA", "11"))
        sb.append(getPageBreak())

        // --- BAB I PENDAHULUAN ---
        sb.append(getHeading1Paragraph("BAB I\nPENDAHULUAN"))
        sb.append(getHeading2Paragraph("A. Latar Belakang"))
        sb.append(getSmartParagraphs(paper.bab1.latarBelakang, footnoteList))

        sb.append(getHeading2Paragraph("B. Rumusan Masalah"))
        sb.append(getSmartListParagraphs(paper.bab1.rumusanMasalah, footnoteList))

        sb.append(getHeading2Paragraph("C. Tujuan"))
        sb.append(getSmartListParagraphs(paper.bab1.tujuan, footnoteList))
        sb.append(getPageBreak())

        // --- BAB II PEMBAHASAN ---
        sb.append(getHeading1Paragraph("BAB II\nPEMBAHASAN"))
        charIdx = 'A'
        for (sec in paper.bab2) {
            sb.append(getHeading2Paragraph("$charIdx. ${sec.subjudul}"))
            sb.append(getSmartParagraphs(sec.konten, footnoteList))
            charIdx++
        }
        sb.append(getPageBreak())

        // --- BAB III PENUTUP ---
        sb.append(getHeading1Paragraph("BAB III\nPENUTUP"))
        sb.append(getHeading2Paragraph("A. Kesimpulan"))
        sb.append(getSmartParagraphs(paper.bab3.kesimpulan, footnoteList))

        sb.append(getHeading2Paragraph("B. Saran"))
        sb.append(getSmartParagraphs(paper.bab3.saran, footnoteList))
        sb.append(getPageBreak())

        // --- DAFTAR PUSTAKA ---
        sb.append(getHeading1Paragraph("DAFTAR PUSTAKA"))
        for (bib in paper.daftarPustaka) {
            sb.append(getHangingParagraph(bib))
        }

        // Section configurations (A4 Layout, 4 4 3 3 in twips)
        // 1 cm = 567 twips
        // top/left = 4 cm = 2268 twips. bottom/right = 3 cm = 1701 twips
        sb.append("""
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="2268" w:left="2268" w:bottom="1701" w:right="1701" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>""")

        return sb.toString()
    }

    private fun getPageBreak(): String {
        return """
            <w:p>
              <w:r>
                <w:br w:type="page"/>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun getCenteredBoldParagraph(text: String, fontSize: Int = 24, spaceAfter: Int = 240): String {
        return """
            <w:p>
              <w:pPr>
                <w:jc w:val="center"/>
                <w:spacing w:before="0" w:after="$spaceAfter" w:line="360" w:lineRule="auto"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                  <w:b/>
                  <w:sz w:val="$fontSize"/>
                </w:rPr>
                <w:t>${escapeXml(text)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun getCenteredParagraph(text: String, fontSize: Int = 24, bold: Boolean = false, spaceAfter: Int = 240): String {
        val bTag = if (bold) "<w:b/>" else ""
        return """
            <w:p>
              <w:pPr>
                <w:jc w:val="center"/>
                <w:spacing w:before="0" w:after="$spaceAfter" w:line="360" w:lineRule="auto"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                  $bTag
                  <w:sz w:val="$fontSize"/>
                </w:rPr>
                <w:t>${escapeXml(text)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun getHeading1Paragraph(text: String): String {
        val lines = text.split("\n")
        val sb = StringBuilder()
        sb.append("<w:p>")
        sb.append("""
            <w:pPr>
              <w:jc w:val="center"/>
              <w:spacing w:before="120" w:after="240" w:line="360" w:lineRule="auto"/>
              <w:keepNext/>
            </w:pPr>
        """.trimIndent())
        
        for ((idx, line) in lines.withIndex()) {
            if (idx > 0) {
                sb.append("<w:r><w:br/></w:r>")
            }
            sb.append("""
                <w:r>
                  <w:rPr>
                    <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                    <w:b/>
                    <w:sz w:val="28"/>
                  </w:rPr>
                  <w:t>${escapeXml(line)}</w:t>
                </w:r>
            """.trimIndent())
        }
        sb.append("</w:p>")
        return sb.toString()
    }

    private fun getHeading2Paragraph(text: String): String {
        return """
            <w:p>
              <w:pPr>
                <w:jc w:val="left"/>
                <w:spacing w:before="240" w:after="120" w:line="360" w:lineRule="auto"/>
                <w:keepNext/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                  <w:b/>
                  <w:sz w:val="24"/>
                </w:rPr>
                <w:t>${escapeXml(text)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun getSmartParagraphs(text: String, footnoteList: MutableList<String>): String {
        val paragraphs = text.split("\n").filter { it.trim().isNotBlank() }
        val sb = StringBuilder()

        for (p in paragraphs) {
            val isNumbered = p.trim().matches(Regex("^\\d+\\.\\s.*"))
            if (isNumbered) {
                // List style formatting
                sb.append("<w:p>")
                sb.append("""
                    <w:pPr>
                      <w:jc w:val="both"/>
                      <w:spacing w:before="0" w:after="120" w:line="480" w:lineRule="exact"/>
                      <w:ind w:left="720" w:hanging="360"/>
                    </w:pPr>
                """.trimIndent())
                sb.append(parseRunsWithFootnotes(p.trim(), footnoteList))
                sb.append("</w:p>")
            } else {
                // Regular paragraph indentation style (first line indent: 720 twips = 1.27 cm)
                sb.append("<w:p>")
                sb.append("""
                    <w:pPr>
                      <w:jc w:val="both"/>
                      <w:spacing w:before="0" w:after="0" w:line="480" w:lineRule="exact"/>
                      <w:ind w:firstLine="720"/>
                    </w:pPr>
                """.trimIndent())
                sb.append(parseRunsWithFootnotes(p.trim(), footnoteList))
                sb.append("</w:p>")
            }
        }
        return sb.toString()
    }

    private fun getSmartListParagraphs(items: List<String>, footnoteList: MutableList<String>): String {
        val sb = StringBuilder()
        for ((idx, item) in items.withIndex()) {
            val listNumPrefix = "${idx + 1}. "
            sb.append("<w:p>")
            sb.append("""
                <w:pPr>
                  <w:jc w:val="both"/>
                  <w:spacing w:before="0" w:after="120" w:line="480" w:lineRule="exact"/>
                  <w:ind w:left="720" w:hanging="360"/>
                </w:pPr>
            """.trimIndent())
            
            // Render the number prefix
            sb.append("""
                <w:r>
                  <w:rPr>
                    <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                    <w:sz w:val="24"/>
                  </w:rPr>
                  <w:t>$listNumPrefix</w:t>
                </w:r>
            """.trimIndent())

            sb.append(parseRunsWithFootnotes(item.trim(), footnoteList))
            sb.append("</w:p>")
        }
        return sb.toString()
    }

    private fun getHangingParagraph(text: String): String {
        return """
            <w:p>
              <w:pPr>
                <w:jc w:val="both"/>
                <w:spacing w:before="0" w:after="120" w:line="360" w:lineRule="auto"/>
                <w:ind w:left="720" w:hanging="720"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                  <w:sz w:val="24"/>
                </w:rPr>
                <w:t>${escapeXml(text)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun getTOCLine(title: String, page: String): String {
        return """
            <w:p>
              <w:pPr>
                <w:tabs>
                  <w:tab w:val="right" w:leader="dot" w:pos="8200"/>
                </w:tabs>
                <w:spacing w:before="60" w:after="60" w:line="360" w:lineRule="auto"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                  <w:b/>
                  <w:sz w:val="24"/>
                </w:rPr>
                <w:t>${escapeXml(title)}</w:t>
              </w:r>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                  <w:b/>
                  <w:sz w:val="24"/>
                </w:rPr>
                <w:tab/>
                <w:t>${escapeXml(page)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun getTOCSubLine(title: String, page: String): String {
        return """
            <w:p>
              <w:pPr>
                <w:tabs>
                  <w:tab w:val="right" w:leader="dot" w:pos="8200"/>
                </w:tabs>
                <w:ind w:left="360"/>
                <w:spacing w:before="40" w:after="40" w:line="360" w:lineRule="auto"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                  <w:sz w:val="24"/>
                </w:rPr>
                <w:t>${escapeXml(title)}</w:t>
              </w:r>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                  <w:sz w:val="24"/>
                </w:rPr>
                <w:tab/>
                <w:t>${escapeXml(page)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun parseRunsWithFootnotes(text: String, footnoteList: MutableList<String>): String {
        val regex = Regex("\\[footnote:(.*?)\\]")
        var lastIndex = 0
        val sb = StringBuilder()

        val matches = regex.findAll(text)
        for (match in matches) {
            val start = match.range.first
            if (start > lastIndex) {
                sb.append(getTextRun(text.substring(lastIndex, start)))
            }
            
            // Register footnote
            var content = match.groupValues[1]
            footnoteList.add(content)
            val footnoteId = footnoteList.size

            // Insert OpenXML Footnote Reference
            sb.append("""
                <w:r>
                  <w:rPr>
                    <w:rStyle w:val="FootnoteReference"/>
                    <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                    <w:sz w:val="24"/>
                  </w:rPr>
                  <w:footnoteReference w:id="$footnoteId"/>
                </w:r>
            """.trimIndent())

            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            sb.append(getTextRun(text.substring(lastIndex)))
        }

        return sb.toString()
    }

    private fun getTextRun(subtext: String): String {
        return """
            <w:r>
              <w:rPr>
                <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                <w:sz w:val="24"/>
              </w:rPr>
              <w:t xml:space="preserve">${escapeXml(subtext)}</w:t>
            </w:p>
        """.replace("</w:p>", "").trimIndent() // Clean redundant tags
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            when (c) {
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '&' -> sb.append("&amp;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    if (c.code < 32 && c != '\n' && c != '\r' && c != '\t') {
                        // Skip or translate invalid XML chars
                        sb.append(" ")
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }
}
