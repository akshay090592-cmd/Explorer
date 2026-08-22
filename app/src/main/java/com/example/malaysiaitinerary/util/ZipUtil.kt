package com.example.malaysiaitinerary.util

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtil {
    fun zip(files: List<File>, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            for (file in files) {
                if (file.isDirectory) {
                    zipDirectory(file, file.name, out)
                } else {
                    val data = ByteArray(1024)
                    val fi = FileInputStream(file)
                    val origin = BufferedInputStream(fi, 1024)
                    val entry = ZipEntry(file.name)
                    out.putNextEntry(entry)
                    var count: Int
                    while (origin.read(data, 0, 1024).also { count = it } != -1) {
                        out.write(data, 0, count)
                    }
                    origin.close()
                }
            }
        }
    }

    private fun zipDirectory(directory: File, baseName: String, out: ZipOutputStream) {
        val files = directory.listFiles() ?: return
        val buffer = ByteArray(1024)
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(file, "$baseName/${file.name}", out)
            } else {
                val fi = FileInputStream(file)
                val origin = BufferedInputStream(fi, 1024)
                val entry = ZipEntry("$baseName/${file.name}")
                out.putNextEntry(entry)
                var count: Int
                while (origin.read(buffer, 0, 1024).also { count = it } != -1) {
                    out.write(buffer, 0, count)
                }
                origin.close()
            }
        }
    }

    fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zin ->
            var ze: ZipEntry?
            while (zin.nextEntry.also { ze = it } != null) {
                val file = File(targetDirectory, ze!!.name)
                if (ze!!.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    val fout = FileOutputStream(file)
                    val buffer = ByteArray(1024)
                    var count: Int
                    while (zin.read(buffer).also { count = it } != -1) {
                        fout.write(buffer, 0, count)
                    }
                    zin.closeEntry()
                    fout.close()
                }
            }
        }
    }
}
