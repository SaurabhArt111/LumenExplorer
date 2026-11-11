import com.lumen.explorer.*
import java.io.File

var fails = 0
fun check(name: String, ok: Boolean) { println((if (ok) "PASS " else "FAIL ") + name); if (!ok) fails++ }

fun main() {
    val root = File(System.getProperty("java.io.tmpdir"), "lumen-test-" + System.nanoTime()).also { it.mkdirs() }
    // ---- Mime
    check("heic is image", Mime.kindOf("IMG_0001.HEIC") == Kind.IMAGE && Mime.mimeOf("a.heic") == "image/heic")
    check("mov is video quicktime", Mime.kindOf("clip.MOV") == Kind.VIDEO && Mime.mimeOf("clip.mov") == "video/quicktime")
    check("m4a caf aiff audio", listOf("a.m4a", "a.caf", "a.aiff").all { Mime.kindOf(it) == Kind.AUDIO })
    check("pages/numbers/key doc", listOf("a.pages", "a.numbers", "a.key").all { Mime.kindOf(it) == Kind.DOC })
    check("dmg ipa archive", Mime.kindOf("a.dmg") == Kind.ARCHIVE && Mime.kindOf("a.ipa") == Kind.ARCHIVE)
    check("tar.gz compound", Mime.archiveTypeOf("x.tar.gz") == Mime.ArchiveType.TAR_GZ && ZipCore.baseNameForExtract("x.tar.gz") == "x")
    check("unknown ext label", Mime.typeLabel("x.abcd", false) == "ABCD File" && Mime.typeLabel("noext", false) == "File")
    check("folder label", Mime.typeLabel("d", true) == "File folder")
    check("raw cr2 image", Mime.kindOf("a.CR2") == Kind.IMAGE)
    check("apk kind", Mime.kindOf("a.apk") == Kind.APK)
    check("hidden file ext", Mime.extOf(".gitignore") == "")
    check("unknown mime fallback", Mime.mimeOf("zzz.unknown") == "*/*")
    // ---- natural sort
    val names = listOf("file10.txt", "file2.txt", "File1.txt", "file02.txt")
    val sorted = names.sortedWith { a, b -> naturalCompare(a, b) }
    check("natural sort $sorted", sorted.first() == "File1.txt" && sorted.last() == "file10.txt")
    // ---- size/date fmt
    check("fmtSize", fmtSize(1536) == "1.5 KB" && fmtSize(5) == "5 B" && fmtSize(10L * 1024 * 1024) == "10.0 MB")
    // ---- grouping
    val now = System.currentTimeMillis()
    val es = listOf(
        FileEntry("/a/apple.txt", "apple.txt", false, 10, now),
        FileEntry("/a/banana.txt", "banana.txt", false, 2_000_000, now - 800L * 86_400_000),
        FileEntry("/a/1.txt", "1.txt", false, 0, now - 86_400_000L),
        FileEntry("/a/dir", "dir", true, 0, now),
    )
    val gn = groupEntries(es, GroupBy.NAME, true, now).map { it.title }
    check("group name $gn", gn == listOf("0 – 9", "A", "B", "D"))
    val gd = groupEntries(es, GroupBy.DATE, false, now).map { it.title }
    check("group date $gd", gd.first() == "Today" && gd.last() == "A long time ago")
    val gs = groupEntries(es, GroupBy.SIZE, true, now).map { it.title }
    check("group size $gs", gs.contains("Empty") && gs.any { it.startsWith("Medium") })
    // ---- ops
    val a = File(root, "a").also { it.mkdirs() }
    val b = File(root, "b").also { it.mkdirs() }
    File(a, "one.txt").writeText("hello")
    File(a, "sub").mkdirs(); File(a, "sub/two.bin").writeBytes(ByteArray(300_000) { (it % 251).toByte() })
    val r1 = FileOps.transfer(listOf(File(a, "one.txt").path, File(a, "sub").path), b.path, move = false, policy = Conflict.REPLACE)
    check("copy ok ${r1.errors}", r1.errors.isEmpty() && File(b, "one.txt").readText() == "hello" && File(b, "sub/two.bin").length() == 300_000L && File(a, "one.txt").exists())
    val r2 = FileOps.transfer(listOf(File(a, "one.txt").path), a.path, move = false, policy = Conflict.REPLACE)
    check("copy in same dir -> - Copy", File(a, "one - Copy.txt").exists() && r2.errors.isEmpty())
    val r2b = FileOps.transfer(listOf(File(a, "one.txt").path), a.path, move = false, policy = Conflict.REPLACE)
    check("second copy -> - Copy (2)", File(a, "one - Copy (2).txt").exists())
    File(b, "one.txt").writeText("DIFFERENT")
    check("conflicts detected", FileOps.findConflicts(listOf(File(a, "one.txt").path), b.path) == listOf("one.txt"))
    FileOps.transfer(listOf(File(a, "one.txt").path), b.path, false, Conflict.SKIP)
    check("skip keeps dst", File(b, "one.txt").readText() == "DIFFERENT")
    FileOps.transfer(listOf(File(a, "one.txt").path), b.path, false, Conflict.KEEP_BOTH)
    check("keep both -> (1)", File(b, "one (1).txt").readText() == "hello")
    FileOps.transfer(listOf(File(a, "one.txt").path), b.path, false, Conflict.REPLACE)
    check("replace overwrites", File(b, "one.txt").readText() == "hello")
    val into = FileOps.transfer(listOf(a.path), File(a, "sub").path, false, Conflict.REPLACE)
    check("folder into itself refused", into.errors.isNotEmpty())
    val mv = FileOps.transfer(listOf(File(b, "one (1).txt").path), a.path, move = true, policy = Conflict.REPLACE)
    check("move", !File(b, "one (1).txt").exists() && File(a, "one (1).txt").exists() && mv.errors.isEmpty())
    check("rename", FileOps.rename(File(a, "one (1).txt").path, "renamed.txt").isSuccess && File(a, "renamed.txt").exists())
    check("rename collision fails", FileOps.rename(File(a, "renamed.txt").path, "one.txt").isFailure)
    check("rename bad name fails", FileOps.rename(File(a, "renamed.txt").path, "a/b").isFailure)
    check("create folder", FileOps.createFolder(a.path, "New folder").isSuccess && FileOps.createFolder(a.path, "New folder").isFailure)
    // ---- search
    val s1 = FileOps.search(root.path, "two", false).map { it.name }
    check("search two $s1", s1 == listOf("two.bin") || s1.toSet() == setOf("two.bin"))
    check("search ext:txt", FileOps.search(root.path, "ext:txt", false).all { it.ext == "txt" } && FileOps.search(root.path, "ext:txt", false).isNotEmpty())
    check("search wildcard", FileOps.search(root.path, "o*.txt", false).isNotEmpty())
    check("search size>", FileOps.search(root.path, "size>100kb", false).all { it.size >= 102400 })
    check("search kind:folder", FileOps.search(root.path, "kind:folder sub", false).all { it.isDir })
    check("largest", FileOps.largest(root.path, 1, 0).first().size == 300_000L)
    // ---- zip
    val zip = File(root, "out.zip")
    val zr = ZipCore.compress(listOf(a.path), zip, 6)
    check("zip created ${zr.errors}", zip.length() > 0 && zr.errors.isEmpty())
    val items = ZipCore.list(zip)
    check("zip list has a/sub/two.bin", items.any { it.name == "a/sub/two.bin" && it.size == 300_000L })
    val ex = File(root, "ex")
    val er = ZipCore.extract(zip, ex)
    check("zip extract ${er.errors}", File(ex, "a/sub/two.bin").length() == 300_000L && File(ex, "a/one.txt").readText() == "hello")
    val er2 = ZipCore.extract(zip, ex, policy = Conflict.KEEP_BOTH)
    check("extract keep both", File(ex, "a/one (1).txt").exists())
    val stored = File(root, "stored.zip"); ZipCore.compress(listOf(File(a, "one.txt").path), stored, 0)
    check("stored zip", ZipCore.list(stored).size == 1 && ZipCore.extract(stored, File(root, "ex2")).done == 1)
    // zip slip
    val evil = File(root, "evil.zip")
    java.util.zip.ZipOutputStream(evil.outputStream()).use { z -> z.putNextEntry(java.util.zip.ZipEntry("../../escape.txt")); z.write(1); z.closeEntry(); z.putNextEntry(java.util.zip.ZipEntry("ok.txt")); z.write(2); z.closeEntry() }
    val evr = ZipCore.extract(evil, File(root, "ex3"))
    check("zip-slip blocked", evr.errors.isNotEmpty() && !File(root.parentFile, "escape.txt").exists() && File(root, "ex3/ok.txt").exists())
    check("zip names", ZipCore.suggestedZipName(listOf("/x/photos")) == "photos.zip" && ZipCore.suggestedZipName(listOf("/x/a.txt", "/x/b")) == "Archive.zip" && ZipCore.baseNameForExtract("a.tar.gz") == "a")
    // cancel
    val pr = Progress(); pr.cancel()
    val cz = ZipCore.compress(listOf(a.path), File(root, "cancel.zip"), 6, pr)
    check("cancel removes partial zip", cz.cancelled && !File(root, "cancel.zip").exists())
    // ---- recycle bin
    val vol = File(root, "vol").also { it.mkdirs() }
    val f1 = File(vol, "docs/tab\tname.txt").also { it.parentFile.mkdirs(); it.writeText("x") }
    val d1 = File(vol, "folder").also { it.mkdirs(); File(it, "inner.txt").writeText("yy") }
    val bin = RecycleBin(vol.path)
    val moved = bin.moveToBin(listOf(f1.path, d1.path))
    check("bin moved 2", moved.size == 2 && !f1.exists() && !d1.exists())
    val li = bin.list()
    check("bin list tab-escape", li.size == 2 && li.any { it.name == "tab\tname.txt" && it.original == f1.path } && li.any { it.isDir && it.size == 2L })
    check("bin nomedia", File(vol, ".LumenBin/.nomedia").exists())
    File(vol, "folder").mkdirs()
    val rr = bin.restore(li.map { it.id }.toSet())
    check("bin restore ${rr.errors}", f1.exists() && File(vol, "folder (1)/inner.txt").exists() && bin.list().isEmpty())
    bin.moveToBin(listOf(f1.path)); 
    check("bin purge none", bin.purgeOlderThan(1) == 0)
    check("bin purge old", bin.purgeOlderThan(1, System.currentTimeMillis() + 3 * 86_400_000L) == 1 && bin.list().isEmpty())
    bin.moveToBin(listOf(File(vol, "folder (1)").path)); bin.empty()
    check("bin empty", bin.list().isEmpty() && (File(vol, ".LumenBin").listFiles()!!.none { it.name != ".nomedia" && it.name != "index.tsv" }))
    // ---- hash/text/dups
    File(a, "dup1.bin").writeBytes(ByteArray(5000) { 7 }); File(b, "dup2.bin").writeBytes(ByteArray(5000) { 7 }); File(b, "other.bin").writeBytes(ByteArray(5000) { 8 })
    val dups = FileOps.findDuplicates(root.path, 1000)
    check("duplicates ${dups.size}", dups.any { g -> g.map { it.name }.toSet() == setOf("dup1.bin", "dup2.bin") })
    check("sha256 empty-ish", FileOps.hash(File(a, "one.txt").path, "SHA-256") == "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
    check("readText ok/binary", FileOps.readText(File(a, "one.txt").path) == "hello" && FileOps.readText(File(a, "sub/two.bin").path) == null)
    FileOps.writeText(File(a, "one.txt").path, "bye"); check("writeText", File(a, "one.txt").readText() == "bye")
    check("tally", FileOps.tally(listOf(a.path)).let { it.files >= 3 && it.folders >= 1 })
    check("crumbs", Paths.crumbs("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0", "Internal storage").map { it.first } == listOf("Internal storage", "DCIM", "Camera"))
    check("parent", Paths.parent("/a/b") == "/a" && Paths.parent("/a") == "/" && Paths.parent("/") == null)
    check("hashAll", hashAll(File(a, "one.txt").path, listOf("MD5", "SHA-256")).let { it.size == 2 && it[0].length == 32 && it[1].length == 64 })
    check("folderSizes", folderSizes(root.path).let { it.isNotEmpty() && it.first().second >= it.last().second })
    root.deleteRecursively()
    println(if (fails == 0) "ALL PASSED" else "$fails FAILED")
    if (fails > 0) System.exit(1)
}
