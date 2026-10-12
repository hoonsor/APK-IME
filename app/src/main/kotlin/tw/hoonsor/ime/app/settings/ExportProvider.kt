package tw.hoonsor.ime.app.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * 把匯出的字典檔分享給其他 App（Google Drive、Dropbox、Gmail、LINE…）。
 * 只提供 cache/export/ 底下的檔案、唯讀，不對外公開（exported=false，靠分享時的暫時授權讀取）。
 * 用自己的小型 Provider 而非 AndroidX FileProvider，維持 App 不依賴 AndroidX。
 */
class ExportProvider : ContentProvider() {

    override fun onCreate() = true

    private fun fileFor(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        val dir = exportDir(ctx)
        val f = File(dir, name)
        // 只允許 export 資料夾內的檔案（擋掉 ../ 之類的路徑）
        if (f.parentFile?.canonicalPath != dir.canonicalPath || !f.exists()) throw FileNotFoundException(name)
        return f
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val f = fileFor(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row: List<Any?> = cols.map { c ->
            when (c) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length() as Any
                else -> null
            }
        }
        return MatrixCursor(cols).apply { addRow(row.toTypedArray()) }
    }

    override fun getType(uri: Uri): String = if (uri.lastPathSegment?.endsWith(".csv") == true) "text/csv" else "text/plain"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        fun authority(ctx: Context) = ctx.packageName + ".export"

        fun exportDir(ctx: Context): File = File(ctx.cacheDir, "export").apply { mkdirs() }

        /** 寫入匯出檔（同時清掉舊的匯出檔），回傳分享用的 content:// 網址。 */
        fun write(ctx: Context, name: String, bytes: ByteArray): Uri {
            val dir = exportDir(ctx)
            dir.listFiles()?.forEach { it.delete() }
            File(dir, name).writeBytes(bytes)
            return Uri.Builder().scheme("content").authority(authority(ctx)).appendPath(name).build()
        }
    }
}
