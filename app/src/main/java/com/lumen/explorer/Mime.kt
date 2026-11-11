package com.lumen.explorer

import java.util.Locale

/** Extension → kind / MIME. Covers common formats plus Apple, RAW, camera, e-book, disk-image types. */
object Mime {
    private val kinds = HashMap<String, Kind>(1024)
    private val mimes = HashMap<String, String>(1024)

    private fun reg(kind: Kind, mime: String?, exts: String) {
        for (e in exts.split(' ')) {
            if (e.isEmpty()) continue
            kinds[e] = kind
            if (mime != null) mimes[e] = mime
        }
    }

    init {
        // images
        reg(Kind.IMAGE, "image/jpeg", "jpg jpeg jpe jfif")
        reg(Kind.IMAGE, "image/png", "png apng")
        reg(Kind.IMAGE, "image/gif", "gif")
        reg(Kind.IMAGE, "image/webp", "webp")
        reg(Kind.IMAGE, "image/bmp", "bmp dib")
        reg(Kind.IMAGE, "image/heic", "heic")
        reg(Kind.IMAGE, "image/heif", "heif hif")
        reg(Kind.IMAGE, "image/avif", "avif")
        reg(Kind.IMAGE, "image/x-icon", "ico cur")
        reg(Kind.IMAGE, "image/tiff", "tif tiff")
        reg(Kind.IMAGE, "image/svg+xml", "svg")
        reg(Kind.IMAGE, "image/vnd.adobe.photoshop", "psd psb")
        reg(Kind.IMAGE, "image/jp2", "jp2 j2k jpx")
        reg(Kind.IMAGE, "image/x-adobe-dng", "dng")
        reg(Kind.IMAGE, "image/x-canon-cr2", "cr2 cr3 crw")
        reg(Kind.IMAGE, "image/x-nikon-nef", "nef nrw")
        reg(Kind.IMAGE, "image/x-sony-arw", "arw sr2 srf")
        reg(Kind.IMAGE, "image/x-olympus-orf", "orf")
        reg(Kind.IMAGE, "image/x-panasonic-rw2", "rw2")
        reg(Kind.IMAGE, "image/x-pentax-pef", "pef")
        reg(Kind.IMAGE, "image/x-fuji-raf", "raf")
        reg(Kind.IMAGE, "image/x-raw", "raw 3fr erf kdc mrw srw x3f")
        reg(Kind.IMAGE, "image/vnd.apple.livp", "livp")
        reg(Kind.IMAGE, "image/x-xcf", "xcf kra ora")
        reg(Kind.IMAGE, "image/x-pcx", "pcx tga ppm pgm pbm pnm exr hdr")
        // video
        reg(Kind.VIDEO, "video/mp4", "mp4 m4v f4v")
        reg(Kind.VIDEO, "video/quicktime", "mov qt")
        reg(Kind.VIDEO, "video/x-matroska", "mkv mk3d")
        reg(Kind.VIDEO, "video/webm", "webm")
        reg(Kind.VIDEO, "video/3gpp", "3gp 3gpp")
        reg(Kind.VIDEO, "video/3gpp2", "3g2")
        reg(Kind.VIDEO, "video/x-msvideo", "avi divx xvid")
        reg(Kind.VIDEO, "video/x-ms-wmv", "wmv asf")
        reg(Kind.VIDEO, "video/x-flv", "flv")
        reg(Kind.VIDEO, "video/mpeg", "mpg mpeg mpe m2v")
        reg(Kind.VIDEO, "video/mp2t", "ts mts m2ts")
        reg(Kind.VIDEO, "video/ogg", "ogv")
        reg(Kind.VIDEO, "video/x-vob", "vob")
        reg(Kind.VIDEO, "video/x-rm", "rm rmvb")
        reg(Kind.VIDEO, "video/hevc", "hevc h265 h264 265 264")
        // audio
        reg(Kind.AUDIO, "audio/mpeg", "mp3 mp2")
        reg(Kind.AUDIO, "audio/mp4", "m4a m4b m4r")
        reg(Kind.AUDIO, "audio/aac", "aac adts")
        reg(Kind.AUDIO, "audio/ogg", "ogg oga opus spx")
        reg(Kind.AUDIO, "audio/flac", "flac")
        reg(Kind.AUDIO, "audio/wav", "wav wave")
        reg(Kind.AUDIO, "audio/x-aiff", "aif aiff aifc")
        reg(Kind.AUDIO, "audio/x-caf", "caf")
        reg(Kind.AUDIO, "audio/x-ms-wma", "wma")
        reg(Kind.AUDIO, "audio/amr", "amr awb")
        reg(Kind.AUDIO, "audio/midi", "mid midi kar")
        reg(Kind.AUDIO, "audio/x-matroska", "mka")
        reg(Kind.AUDIO, "audio/x-ape", "ape wv mpc dsf dff")
        reg(Kind.AUDIO, "audio/x-mpegurl", "m3u m3u8 pls")
        // documents
        reg(Kind.PDF, "application/pdf", "pdf")
        reg(Kind.DOCUMENT, "application/msword", "doc dot")
        reg(Kind.DOCUMENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx dotx docm")
        reg(Kind.DOCUMENT, "application/vnd.oasis.opendocument.text", "odt ott")
        reg(Kind.DOCUMENT, "application/rtf", "rtf")
        reg(Kind.DOCUMENT, "application/vnd.apple.pages", "pages")
        reg(Kind.DOCUMENT, "application/x-abiword", "abw wps wpd")
        reg(Kind.SHEET, "application/vnd.ms-excel", "xls xlt")
        reg(Kind.SHEET, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx xlsm xltx")
        reg(Kind.SHEET, "application/vnd.oasis.opendocument.spreadsheet", "ods ots")
        reg(Kind.SHEET, "application/vnd.apple.numbers", "numbers")
        reg(Kind.SHEET, "text/csv", "csv")
        reg(Kind.SHEET, "text/tab-separated-values", "tsv")
        reg(Kind.SLIDES, "application/vnd.ms-powerpoint", "ppt pot pps")
        reg(Kind.SLIDES, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx pptm potx ppsx")
        reg(Kind.SLIDES, "application/vnd.oasis.opendocument.presentation", "odp otp")
        reg(Kind.SLIDES, "application/vnd.apple.keynote", "key")
        reg(Kind.EBOOK, "application/epub+zip", "epub")
        reg(Kind.EBOOK, "application/x-mobipocket-ebook", "mobi azw azw3 kfx fb2 djvu cbz cbr")
        // text / code
        reg(Kind.TEXT, "text/plain", "txt text log nfo ini cfg conf md markdown rst srt vtt ass sub lrc")
        reg(Kind.TEXT, "text/html", "html htm xhtml mhtml")
        reg(Kind.CODE, "application/json", "json jsonl geojson")
        reg(Kind.CODE, "text/xml", "xml xsd xsl xslt plist webloc rss atom")
        reg(Kind.CODE, "text/x-yaml", "yaml yml toml")
        reg(Kind.CODE, "text/css", "css scss sass less")
        reg(Kind.CODE, "text/javascript", "js mjs cjs jsx ts tsx vue svelte")
        reg(Kind.CODE, "text/x-java", "java kt kts gradle groovy scala")
        reg(Kind.CODE, "text/x-c", "c h cpp cc cxx hpp hh m mm cs go rs swift dart lua r jl pl pm")
        reg(Kind.CODE, "text/x-python", "py pyw ipynb rb php sh bash zsh bat cmd ps1 sql proto graphql")
        reg(Kind.CODE, "text/plain", "gitignore env properties lock editorconfig makefile cmake")
        // archives
        reg(Kind.ARCHIVE, "application/zip", "zip jar war aar")
        reg(Kind.ARCHIVE, "application/x-7z-compressed", "7z")
        reg(Kind.ARCHIVE, "application/vnd.rar", "rar")
        reg(Kind.ARCHIVE, "application/x-tar", "tar")
        reg(Kind.ARCHIVE, "application/gzip", "gz tgz")
        reg(Kind.ARCHIVE, "application/x-bzip2", "bz2 tbz tbz2")
        reg(Kind.ARCHIVE, "application/x-xz", "xz txz")
        reg(Kind.ARCHIVE, "application/x-lzma", "lzma lz zst z cab arj lzh ace")
        // android / apple / disk
        reg(Kind.APK, "application/vnd.android.package-archive", "apk apks xapk apkm")
        reg(Kind.DISK, "application/x-apple-diskimage", "dmg")
        reg(Kind.DISK, "application/x-iso9660-image", "iso img bin cue nrg mdf vhd vhdx vmdk")
        reg(Kind.DISK, "application/octet-stream", "ipa pkg deb rpm msi exe dll appimage")
        reg(Kind.FONT, "font/ttf", "ttf")
        reg(Kind.FONT, "font/otf", "otf")
        reg(Kind.FONT, "font/woff", "woff woff2 eot")
    }

    fun kindOf(name: String, isDir: Boolean): Kind =
        if (isDir) Kind.FOLDER else kinds[Paths.ext(name)] ?: Kind.OTHER

    fun kindOfExt(ext: String): Kind = kinds[ext.lowercase(Locale.ROOT)] ?: Kind.OTHER

    fun mimeOf(name: String): String = mimes[Paths.ext(name)] ?: "*/*"

    fun typeLabel(e: FileEntry): String {
        if (e.isDir) return "File folder"
        if (e.ext.isEmpty()) return "File"
        return when (e.ext) {
            "heic", "heif", "hif" -> "HEIC image"
            "mov", "qt" -> "QuickTime movie"
            "m4a" -> "Apple MPEG-4 audio"
            "caf" -> "Core Audio file"
            "aif", "aiff", "aifc" -> "AIFF audio"
            "pages" -> "Pages document"
            "numbers" -> "Numbers spreadsheet"
            "key" -> "Keynote presentation"
            "dmg" -> "Apple disk image"
            "ipa" -> "iOS app"
            "plist" -> "Property list"
            "apk" -> "Android package"
            else -> if (e.kind == Kind.OTHER) "${e.ext.uppercase(Locale.ROOT)} file" else "${e.ext.uppercase(Locale.ROOT)} ${e.kind.label.lowercase(Locale.ROOT)}"
        }
    }

    /** Videos whose container/codec commonly can't be played by the built-in (platform) player. */
    fun needsExternalPlayer(ext: String): Boolean = ext in setOf(
        "mov", "qt", "avi", "wmv", "asf", "flv", "mpg", "mpeg", "mpe", "m2v", "vob", "rm", "rmvb", "divx", "xvid",
        "ts", "mts", "m2ts", "hevc", "h265", "265", "mk3d", "ogv"
    )

    enum class ArchiveType { ZIP, SEVEN_Z, RAR, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZ, BZ2, XZ }

    fun archiveTypeOf(name: String): ArchiveType? {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> ArchiveType.TAR_GZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> ArchiveType.TAR_BZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> ArchiveType.TAR_XZ
            n.endsWith(".tar") -> ArchiveType.TAR
            n.endsWith(".7z") -> ArchiveType.SEVEN_Z
            n.endsWith(".rar") -> ArchiveType.RAR
            n.endsWith(".gz") -> ArchiveType.GZ
            n.endsWith(".bz2") -> ArchiveType.BZ2
            n.endsWith(".xz") -> ArchiveType.XZ
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".war") || n.endsWith(".aar") ||
                n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm") ||
                n.endsWith(".epub") || n.endsWith(".cbz") || n.endsWith(".docx") || n.endsWith(".xlsx") || n.endsWith(".pptx") -> ArchiveType.ZIP
            else -> null
        }
    }

    /** True for archives the user can "Extract all" on (RAR is unsupported without a native lib). */
    fun isExtractable(name: String): Boolean {
        val t = archiveTypeOf(name) ?: return false
        if (t == ArchiveType.RAR) return false
        val n = name.lowercase(Locale.ROOT)
        return !(n.endsWith(".docx") || n.endsWith(".xlsx") || n.endsWith(".pptx") || n.endsWith(".epub") || n.endsWith(".apk") || n.endsWith(".jar") || n.endsWith(".aar") || n.endsWith(".war"))
            || n.endsWith(".zip")
    }
}
