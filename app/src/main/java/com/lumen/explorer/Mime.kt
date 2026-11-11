package com.lumen.explorer

/**
 * Extension -> (mime, kind, friendly name). Deliberately huge: includes Apple formats
 * (HEIC/HEIF, MOV, M4A, CAF, AIFF, Pages/Numbers/Keynote, DMG, IPA ...), camera RAW,
 * Matroska/WebM, Office, OpenDocument, e-books, fonts, CAD, subtitles, disk images, etc.
 */
object Mime {
    private class Info(val mime: String, val kind: Kind, val label: String)

    private val table = HashMap<String, Info>(1024)

    private fun add(kind: Kind, mime: String, label: String, vararg exts: String) {
        for (e in exts) table[e] = Info(mime, kind, label)
    }

    init {
        // ---- Images (incl. Apple HEIC/HEIF and RAW) ----
        add(Kind.IMAGE, "image/jpeg", "JPEG Image", "jpg", "jpeg", "jpe", "jfif", "pjpeg", "pjp")
        add(Kind.IMAGE, "image/png", "PNG File", "png", "apng")
        add(Kind.IMAGE, "image/gif", "GIF File", "gif")
        add(Kind.IMAGE, "image/webp", "WebP Image", "webp")
        add(Kind.IMAGE, "image/bmp", "BMP File", "bmp", "dib")
        add(Kind.IMAGE, "image/heic", "HEIC Image", "heic", "hic")
        add(Kind.IMAGE, "image/heic-sequence", "HEIC Sequence", "heics")
        add(Kind.IMAGE, "image/heif", "HEIF Image", "heif", "hif")
        add(Kind.IMAGE, "image/heif-sequence", "HEIF Sequence", "heifs")
        add(Kind.IMAGE, "image/avif", "AVIF Image", "avif", "avifs")
        add(Kind.IMAGE, "image/jxl", "JPEG XL Image", "jxl")
        add(Kind.IMAGE, "image/svg+xml", "SVG File", "svg", "svgz")
        add(Kind.IMAGE, "image/tiff", "TIFF Image", "tif", "tiff")
        add(Kind.IMAGE, "image/x-icon", "Icon", "ico", "cur")
        add(Kind.IMAGE, "image/vnd.adobe.photoshop", "Photoshop Document", "psd", "psb")
        add(Kind.IMAGE, "image/x-xcf", "GIMP Image", "xcf")
        add(Kind.IMAGE, "image/vnd.djvu", "DjVu Image", "djvu", "djv")
        add(Kind.IMAGE, "image/x-tga", "TGA Image", "tga")
        add(Kind.IMAGE, "image/vnd.radiance", "HDR Image", "hdr")
        add(Kind.IMAGE, "image/x-exr", "OpenEXR Image", "exr")
        add(Kind.IMAGE, "image/x-portable-pixmap", "PPM Image", "ppm", "pgm", "pbm", "pnm")
        add(Kind.IMAGE, "image/vnd.microsoft.icon", "Icon", "icon")
        add(Kind.IMAGE, "image/x-adobe-dng", "DNG Raw Image", "dng")
        add(Kind.IMAGE, "image/x-canon-cr2", "Canon Raw (CR2)", "cr2")
        add(Kind.IMAGE, "image/x-canon-cr3", "Canon Raw (CR3)", "cr3")
        add(Kind.IMAGE, "image/x-canon-crw", "Canon Raw (CRW)", "crw")
        add(Kind.IMAGE, "image/x-nikon-nef", "Nikon Raw (NEF)", "nef", "nrw")
        add(Kind.IMAGE, "image/x-sony-arw", "Sony Raw (ARW)", "arw", "srf", "sr2")
        add(Kind.IMAGE, "image/x-olympus-orf", "Olympus Raw (ORF)", "orf")
        add(Kind.IMAGE, "image/x-panasonic-rw2", "Panasonic Raw (RW2)", "rw2", "raw")
        add(Kind.IMAGE, "image/x-pentax-pef", "Pentax Raw (PEF)", "pef")
        add(Kind.IMAGE, "image/x-fuji-raf", "Fujifilm Raw (RAF)", "raf")
        add(Kind.IMAGE, "image/x-samsung-srw", "Samsung Raw (SRW)", "srw")
        add(Kind.IMAGE, "image/x-raw", "Raw Image", "3fr", "erf", "kdc", "mef", "mos", "x3f", "dcr", "iiq", "rwl")
        add(Kind.IMAGE, "application/zip", "Live Photo Bundle", "livp")
        add(Kind.IMAGE, "image/x-pict", "PICT Image", "pict", "pct")
        add(Kind.IMAGE, "image/x-sgi", "SGI Image", "sgi")
        add(Kind.IMAGE, "image/x-pcx", "PCX Image", "pcx")
        add(Kind.IMAGE, "image/jp2", "JPEG 2000 Image", "jp2", "j2k", "jpf", "jpx")
        add(Kind.IMAGE, "image/wbmp", "WBMP Image", "wbmp")

        // ---- Video (incl. Apple MOV / M4V) ----
        add(Kind.VIDEO, "video/mp4", "MP4 Video", "mp4", "mp4v", "f4v")
        add(Kind.VIDEO, "video/quicktime", "QuickTime Movie", "mov", "qt")
        add(Kind.VIDEO, "video/x-m4v", "M4V Video", "m4v")
        add(Kind.VIDEO, "video/x-matroska", "Matroska Video", "mkv", "mk3d")
        add(Kind.VIDEO, "video/webm", "WebM Video", "webm")
        add(Kind.VIDEO, "video/3gpp", "3GP Video", "3gp", "3gpp")
        add(Kind.VIDEO, "video/3gpp2", "3G2 Video", "3g2", "3gp2")
        add(Kind.VIDEO, "video/x-msvideo", "AVI Video", "avi")
        add(Kind.VIDEO, "video/x-ms-wmv", "WMV Video", "wmv")
        add(Kind.VIDEO, "video/x-ms-asf", "ASF Video", "asf")
        add(Kind.VIDEO, "video/x-flv", "Flash Video", "flv")
        add(Kind.VIDEO, "video/mpeg", "MPEG Video", "mpg", "mpeg", "mpe", "m1v", "m2v", "vob")
        add(Kind.VIDEO, "video/mp2t", "MPEG Transport Stream", "ts", "mts", "m2ts", "tsv_")
        add(Kind.VIDEO, "video/ogg", "Ogg Video", "ogv")
        add(Kind.VIDEO, "video/x-ms-vob", "DVD Video Object", "vob_")
        add(Kind.VIDEO, "video/divx", "DivX Video", "divx")
        add(Kind.VIDEO, "video/x-rm", "RealMedia Video", "rm", "rmvb")
        add(Kind.VIDEO, "video/x-dv", "DV Video", "dv")
        add(Kind.VIDEO, "video/x-prores", "ProRes Video", "prores")
        add(Kind.VIDEO, "video/x-mxf", "MXF Video", "mxf")
        add(Kind.VIDEO, "video/vnd.avchd", "AVCHD Video", "avchd")

        // ---- Audio (incl. Apple M4A / CAF / AIFF) ----
        add(Kind.AUDIO, "audio/mpeg", "MP3 Audio", "mp3", "mp2", "mpga")
        add(Kind.AUDIO, "audio/mp4", "M4A Audio", "m4a", "m4b", "m4p", "m4r")
        add(Kind.AUDIO, "audio/aac", "AAC Audio", "aac", "adts")
        add(Kind.AUDIO, "audio/x-caf", "Core Audio (CAF)", "caf")
        add(Kind.AUDIO, "audio/aiff", "AIFF Audio", "aif", "aiff", "aifc")
        add(Kind.AUDIO, "audio/x-wav", "WAV Audio", "wav", "wave")
        add(Kind.AUDIO, "audio/flac", "FLAC Audio", "flac")
        add(Kind.AUDIO, "audio/ogg", "Ogg Audio", "ogg", "oga", "spx")
        add(Kind.AUDIO, "audio/opus", "Opus Audio", "opus")
        add(Kind.AUDIO, "audio/x-ms-wma", "WMA Audio", "wma")
        add(Kind.AUDIO, "audio/amr", "AMR Audio", "amr")
        add(Kind.AUDIO, "audio/amr-wb", "AMR-WB Audio", "awb")
        add(Kind.AUDIO, "audio/midi", "MIDI Sequence", "mid", "midi", "kar", "rmi")
        add(Kind.AUDIO, "audio/x-matroska", "Matroska Audio", "mka")
        add(Kind.AUDIO, "audio/x-mpegurl", "Playlist", "m3u", "m3u8", "pls")
        add(Kind.AUDIO, "audio/x-ape", "Monkey's Audio", "ape")
        add(Kind.AUDIO, "audio/x-wavpack", "WavPack Audio", "wv")
        add(Kind.AUDIO, "audio/x-dsf", "DSD Audio", "dsf", "dff")
        add(Kind.AUDIO, "audio/ac3", "Dolby Digital", "ac3", "eac3")
        add(Kind.AUDIO, "audio/x-realaudio", "RealAudio", "ra", "ram")
        add(Kind.AUDIO, "audio/3gpp", "3GA Audio", "3ga")
        add(Kind.AUDIO, "audio/x-ms-wax", "Windows Media Audio", "wax")
        add(Kind.AUDIO, "audio/x-mod", "Tracker Module", "mod", "xm", "it", "s3m")

        // ---- Documents ----
        add(Kind.DOC, "application/pdf", "PDF Document", "pdf")
        add(Kind.DOC, "application/msword", "Word 97–2003 Document", "doc", "dot")
        add(Kind.DOC, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word Document", "docx", "docm")
        add(Kind.DOC, "application/vnd.openxmlformats-officedocument.wordprocessingml.template", "Word Template", "dotx")
        add(Kind.DOC, "application/vnd.ms-excel", "Excel 97–2003 Worksheet", "xls", "xlt")
        add(Kind.DOC, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel Worksheet", "xlsx", "xlsm", "xlsb")
        add(Kind.DOC, "application/vnd.openxmlformats-officedocument.spreadsheetml.template", "Excel Template", "xltx")
        add(Kind.DOC, "application/vnd.ms-powerpoint", "PowerPoint 97–2003 Presentation", "ppt", "pps", "pot")
        add(Kind.DOC, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint Presentation", "pptx", "pptm")
        add(Kind.DOC, "application/vnd.openxmlformats-officedocument.presentationml.slideshow", "PowerPoint Slide Show", "ppsx")
        add(Kind.DOC, "application/vnd.openxmlformats-officedocument.presentationml.template", "PowerPoint Template", "potx")
        add(Kind.DOC, "application/vnd.oasis.opendocument.text", "OpenDocument Text", "odt", "ott")
        add(Kind.DOC, "application/vnd.oasis.opendocument.spreadsheet", "OpenDocument Spreadsheet", "ods", "ots")
        add(Kind.DOC, "application/vnd.oasis.opendocument.presentation", "OpenDocument Presentation", "odp", "otp")
        add(Kind.DOC, "application/vnd.oasis.opendocument.graphics", "OpenDocument Drawing", "odg")
        add(Kind.DOC, "application/vnd.apple.pages", "Pages Document", "pages")
        add(Kind.DOC, "application/vnd.apple.numbers", "Numbers Spreadsheet", "numbers")
        add(Kind.DOC, "application/vnd.apple.keynote", "Keynote Presentation", "key", "keynote")
        add(Kind.DOC, "application/rtf", "Rich Text Document", "rtf")
        add(Kind.DOC, "application/epub+zip", "EPUB Book", "epub")
        add(Kind.DOC, "application/x-mobipocket-ebook", "Kindle Book", "mobi", "azw", "azw3", "prc")
        add(Kind.DOC, "application/x-fictionbook+xml", "FictionBook", "fb2")
        add(Kind.DOC, "application/x-cbz", "Comic Book (CBZ)", "cbz")
        add(Kind.DOC, "application/x-cbr", "Comic Book (CBR)", "cbr", "cb7", "cbt")
        add(Kind.DOC, "application/postscript", "PostScript", "ps", "eps", "ai")
        add(Kind.DOC, "application/vnd.ms-xpsdocument", "XPS Document", "xps", "oxps")
        add(Kind.DOC, "application/vnd.visio", "Visio Drawing", "vsd", "vsdx")
        add(Kind.DOC, "application/vnd.ms-project", "Project File", "mpp")
        add(Kind.DOC, "application/vnd.ms-outlook", "Outlook Message", "msg")
        add(Kind.DOC, "application/vnd.ms-publisher", "Publisher Document", "pub")
        add(Kind.DOC, "application/onenote", "OneNote Section", "one", "onetoc2")
        add(Kind.DOC, "message/rfc822", "E-mail Message", "eml", "mht", "mhtml")
        add(Kind.DOC, "text/vcard", "Contact Card", "vcf", "vcard")
        add(Kind.DOC, "text/calendar", "Calendar Event", "ics", "ical")
        add(Kind.DOC, "application/x-latex", "LaTeX Document", "tex", "latex")
        add(Kind.DOC, "application/x-dvi", "DVI Document", "dvi")
        add(Kind.DOC, "application/vnd.sqlite3", "SQLite Database", "db", "sqlite", "sqlite3", "db3")
        add(Kind.DOC, "application/vnd.ms-access", "Access Database", "mdb", "accdb")
        add(Kind.DOC, "application/x-font-ttf", "TrueType Font", "ttf", "ttc")
        add(Kind.DOC, "font/otf", "OpenType Font", "otf")
        add(Kind.DOC, "font/woff", "Web Font", "woff", "woff2", "eot")
        add(Kind.DOC, "application/x-subrip", "Subtitles", "srt", "sub", "ssa", "ass", "vtt", "sbv", "lrc")
        add(Kind.DOC, "application/x-torrent", "Torrent", "torrent")
        add(Kind.DOC, "application/vnd.google-earth.kml+xml", "KML Place", "kml", "kmz")
        add(Kind.DOC, "application/gpx+xml", "GPS Track", "gpx", "fit", "tcx")
        add(Kind.DOC, "application/dxf", "CAD Drawing", "dwg", "dxf", "dwf")
        add(Kind.DOC, "model/stl", "3D Model", "stl", "obj", "fbx", "glb", "gltf", "3ds", "blend", "dae", "usdz", "step", "stp", "iges")
        add(Kind.DOC, "application/x-x509-ca-cert", "Security Certificate", "crt", "cer", "pem", "der", "p12", "pfx", "key_")
        add(Kind.DOC, "application/pgp-keys", "PGP Key", "asc", "gpg", "pgp")
        add(Kind.DOC, "application/x-apple-plist", "Property List", "plist", "mobileconfig", "strings")
        add(Kind.DOC, "application/x-apple-webloc", "Web Location", "webloc", "url", "lnk", "desktop")
        add(Kind.DOC, "application/x-shortcut", "Shortcut", "inetloc")

        // ---- Text & code ----
        add(Kind.TEXT, "text/plain", "Text Document", "txt", "text", "log", "nfo", "me", "readme", "asc_")
        add(Kind.TEXT, "text/markdown", "Markdown File", "md", "markdown", "mdx", "rst", "adoc")
        add(Kind.TEXT, "text/html", "HTML File", "html", "htm", "xhtml")
        add(Kind.TEXT, "text/css", "CSS File", "css", "scss", "sass", "less")
        add(Kind.TEXT, "application/javascript", "JavaScript File", "js", "mjs", "cjs", "jsx")
        add(Kind.TEXT, "application/typescript", "TypeScript File", "tsx", "ts_")
        add(Kind.TEXT, "application/json", "JSON File", "json", "jsonl", "geojson", "json5", "map")
        add(Kind.TEXT, "application/xml", "XML Document", "xml", "xsl", "xslt", "xsd", "rss", "atom", "plist_")
        add(Kind.TEXT, "text/csv", "CSV File", "csv")
        add(Kind.TEXT, "text/tab-separated-values", "TSV File", "tsv")
        add(Kind.TEXT, "application/x-yaml", "YAML File", "yml", "yaml")
        add(Kind.TEXT, "text/x-toml", "TOML File", "toml")
        add(Kind.TEXT, "text/x-ini", "Configuration Settings", "ini", "cfg", "conf", "config", "properties", "env", "gitignore", "editorconfig")
        add(Kind.TEXT, "text/x-java", "Java Source", "java")
        add(Kind.TEXT, "text/x-kotlin", "Kotlin Source", "kt", "kts")
        add(Kind.TEXT, "text/x-python", "Python File", "py", "pyw", "ipynb")
        add(Kind.TEXT, "text/x-csrc", "C Source", "c", "h")
        add(Kind.TEXT, "text/x-c++src", "C++ Source", "cpp", "cc", "cxx", "hpp", "hh", "hxx")
        add(Kind.TEXT, "text/x-csharp", "C# Source", "cs", "csx")
        add(Kind.TEXT, "text/x-swift", "Swift Source", "swift")
        add(Kind.TEXT, "text/x-objc", "Objective-C Source", "m", "mm")
        add(Kind.TEXT, "text/x-go", "Go Source", "go")
        add(Kind.TEXT, "text/x-rust", "Rust Source", "rs")
        add(Kind.TEXT, "text/x-ruby", "Ruby Source", "rb", "erb")
        add(Kind.TEXT, "text/x-php", "PHP File", "php", "phtml")
        add(Kind.TEXT, "text/x-sh", "Shell Script", "sh", "bash", "zsh", "fish", "ksh")
        add(Kind.TEXT, "text/x-bat", "Windows Batch File", "bat", "cmd", "ps1", "psm1", "vbs")
        add(Kind.TEXT, "text/x-sql", "SQL File", "sql")
        add(Kind.TEXT, "text/x-gradle", "Gradle Script", "gradle")
        add(Kind.TEXT, "text/x-lua", "Lua Script", "lua")
        add(Kind.TEXT, "text/x-perl", "Perl Script", "pl", "pm")
        add(Kind.TEXT, "text/x-r", "R Script", "r")
        add(Kind.TEXT, "text/x-dart", "Dart Source", "dart")
        add(Kind.TEXT, "text/x-scala", "Scala Source", "scala")
        add(Kind.TEXT, "text/x-vue", "Vue Component", "vue", "svelte")
        add(Kind.TEXT, "text/x-diff", "Patch File", "diff", "patch")
        add(Kind.TEXT, "text/x-smali", "Smali Source", "smali")
        add(Kind.TEXT, "text/x-makefile", "Makefile", "mk", "cmake")
        add(Kind.TEXT, "text/x-proto", "Protocol Buffer", "proto", "graphql", "gql")

        // ---- Archives & disk images (incl. Apple DMG / PKG) ----
        add(Kind.ARCHIVE, "application/zip", "ZIP Archive", "zip", "zipx")
        add(Kind.ARCHIVE, "application/x-rar-compressed", "RAR Archive", "rar")
        add(Kind.ARCHIVE, "application/x-7z-compressed", "7-Zip Archive", "7z")
        add(Kind.ARCHIVE, "application/x-tar", "TAR Archive", "tar")
        add(Kind.ARCHIVE, "application/gzip", "GZ Archive", "gz", "gzip", "tgz")
        add(Kind.ARCHIVE, "application/x-bzip2", "BZ2 Archive", "bz2", "tbz", "tbz2")
        add(Kind.ARCHIVE, "application/x-xz", "XZ Archive", "xz", "txz")
        add(Kind.ARCHIVE, "application/zstd", "Zstandard Archive", "zst", "zstd", "tzst")
        add(Kind.ARCHIVE, "application/x-lzma", "LZMA Archive", "lzma", "lz", "lz4")
        add(Kind.ARCHIVE, "application/x-compress", "Compressed Archive", "z")
        add(Kind.ARCHIVE, "application/x-cab", "Cabinet Archive", "cab")
        add(Kind.ARCHIVE, "application/x-arj", "ARJ Archive", "arj", "lzh", "lha", "ace")
        add(Kind.ARCHIVE, "application/x-iso9660-image", "Disc Image File", "iso", "img", "bin", "cue", "nrg", "mdf")
        add(Kind.ARCHIVE, "application/x-apple-diskimage", "Apple Disk Image", "dmg", "sparseimage", "sparsebundle")
        add(Kind.ARCHIVE, "application/vnd.apple.installer+xml", "macOS Installer Package", "pkg", "mpkg")
        add(Kind.ARCHIVE, "application/x-ipa", "iOS App", "ipa")
        add(Kind.ARCHIVE, "application/java-archive", "JAR File", "jar", "war", "ear")
        add(Kind.ARCHIVE, "application/x-deb", "Debian Package", "deb", "rpm", "pkg_")
        add(Kind.ARCHIVE, "application/x-cpio", "CPIO Archive", "cpio")
        add(Kind.ARCHIVE, "application/x-msi", "Windows Installer", "msi", "msix", "appx")
        add(Kind.ARCHIVE, "application/x-ms-dos-executable", "Application", "exe", "dll", "sys")
        add(Kind.ARCHIVE, "application/x-virtualbox-vdi", "Virtual Disk", "vdi", "vmdk", "vhd", "vhdx", "qcow2")

        // ---- Android packages ----
        add(Kind.APK, "application/vnd.android.package-archive", "Android Package", "apk")
        add(Kind.APK, "application/vnd.android.package-archive", "Android App Bundle", "aab")
        add(Kind.APK, "application/vnd.android.package-archive", "Split APK Bundle", "apks", "xapk", "apkm")
    }

    fun extOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    private fun compoundExt(name: String): String? {
        val n = name.lowercase()
        return when {
            n.endsWith(".tar.gz") -> "tgz"
            n.endsWith(".tar.bz2") -> "tbz2"
            n.endsWith(".tar.xz") -> "txz"
            n.endsWith(".tar.zst") -> "tzst"
            else -> null
        }
    }

    private fun info(name: String): Info? = table[compoundExt(name) ?: extOf(name)]

    fun kindOf(name: String): Kind = info(name)?.kind ?: Kind.OTHER

    /** Best MIME for intents. Falls back to the generic type so "Open with" always works. */
    fun mimeOf(name: String): String = info(name)?.mime ?: "*/*"

    fun typeLabel(name: String, isDir: Boolean): String {
        if (isDir) return "File folder"
        info(name)?.let { return it.label }
        val e = extOf(name)
        return if (e.isEmpty()) "File" else "${e.uppercase()} File"
    }

    fun extensionsOf(kind: Kind): List<String> = table.filter { it.value.kind == kind && !it.key.endsWith("_") }.keys.sorted()

    fun mimeForKind(kind: Kind): String = when (kind) {
        Kind.IMAGE -> "image/*"; Kind.VIDEO -> "video/*"; Kind.AUDIO -> "audio/*"
        else -> "*/*"
    }

    /** Archive handling type, or null if [name] is not an archive we can open. */
    enum class ArchiveType { ZIP, SEVEN_Z, RAR, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZ, BZ2, XZ }

    fun archiveTypeOf(name: String): ArchiveType? {
        val n = name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> ArchiveType.TAR_GZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> ArchiveType.TAR_BZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> ArchiveType.TAR_XZ
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".aab") || n.endsWith(".war") ||
                n.endsWith(".epub") || n.endsWith(".cbz") || n.endsWith(".docx") || n.endsWith(".xlsx") || n.endsWith(".pptx") ||
                n.endsWith(".livp") || n.endsWith(".xapk") || n.endsWith(".apks") || n.endsWith(".zipx") || n.endsWith(".ipa") ||
                n.endsWith(".odt") || n.endsWith(".ods") || n.endsWith(".odp") || n.endsWith(".kmz") || n.endsWith(".pages") ||
                n.endsWith(".numbers") || n.endsWith(".key") -> ArchiveType.ZIP
            n.endsWith(".7z") -> ArchiveType.SEVEN_Z
            n.endsWith(".rar") -> ArchiveType.RAR
            n.endsWith(".tar") -> ArchiveType.TAR
            n.endsWith(".gz") -> ArchiveType.GZ
            n.endsWith(".bz2") -> ArchiveType.BZ2
            n.endsWith(".xz") -> ArchiveType.XZ
            else -> null
        }
    }

    /** Is this an archive the user will expect "Extract all…" for? (Excludes docx/apk etc.) */
    fun isExtractable(name: String): Boolean {
        val n = name.lowercase()
        val t = archiveTypeOf(name) ?: return false
        if (t != ArchiveType.ZIP) return t != ArchiveType.RAR
        return n.endsWith(".zip") || n.endsWith(".zipx") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".livp") ||
            n.endsWith(".epub") || n.endsWith(".cbz") || n.endsWith(".xapk") || n.endsWith(".apks") || n.endsWith(".ipa") || n.endsWith(".kmz") ||
            n.endsWith(".docx") || n.endsWith(".xlsx") || n.endsWith(".pptx")
    }
}
