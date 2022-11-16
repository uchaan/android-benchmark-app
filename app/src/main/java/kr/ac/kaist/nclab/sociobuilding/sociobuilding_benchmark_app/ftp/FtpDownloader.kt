package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp

import android.content.Context
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class FtpDownloader (private val hostname: String, private val port: Int, private val username: String, private val password: String) {

    data class FtpAccount(val username: String, val password:String)

    private fun remotePath():String {
        return "" // deleted due to the security issue
    }

    fun download(srcFilename: String, context: Context): Boolean {
        val ftp = FTPClient()
        ftp.connect(hostname, port)
        val dirname = remotePath()
        Timber.d("uname: ${username}, passwd: ${password} dirname: ${dirname}, filename: ${srcFilename}")

        if (!ftp.login(username, password)) {
            Timber.e("ftp login failed")
            return false
        }

        ftp.setFileType(FTP.BINARY_FILE_TYPE) // necessary?
        ftp.setFileTransferMode(FTP.BINARY_FILE_TYPE)

        ftp.enterLocalPassiveMode()

        if (!ftp.changeWorkingDirectory(dirname)) {
            Timber.e("ftp failed to change workdir 1 ")
            return false
        }

        File.createTempFile(srcFilename, null, context.cacheDir)

        val cacheFile = File(context.cacheDir, srcFilename)
        val fos = FileOutputStream(cacheFile)

        // download files
        if (!ftp.retrieveFile(srcFilename, fos)) {
            Timber.e("ftp failed to retrieve file")
            return false
        }

        fos.close()

        if (!ftp.logout()) {
            Timber.e("ftp logout failed")
            return false
        }

        ftp.disconnect()
        return true
    }

    /*
    If successfully download, return ByteArray.
    If failed, return null
     */
    fun download(srcFilename: String): ByteArray? {
        val ftp = FTPClient()
        ftp.connect(hostname, port)
        val dirname = remotePath()
        Timber.d("uname: ${username}, passwd: ${password} dirname: ${dirname}, filename: ${srcFilename}")

        if (!ftp.login(username, password)) {
            Timber.e("ftp login failed")
            return null
        }

        ftp.setFileType(FTP.BINARY_FILE_TYPE) // necessary?
        ftp.setFileTransferMode(FTP.BINARY_FILE_TYPE)

        ftp.enterLocalPassiveMode()

        if (!ftp.changeWorkingDirectory(dirname)) {
            Timber.e("ftp failed to change workdir 1 ")
            return null
        }

        // download files
        val bos = ByteArrayOutputStream()
        if (!ftp.retrieveFile(srcFilename, bos)) {
            Timber.e("ftp failed to retrieve file")
            return null
        }

        val evidenceBuffer = bos.toByteArray()
        bos.close()

        if (!ftp.logout()) {
            Timber.e("ftp logout failed")
            return null
        }

        if (evidenceBuffer.isEmpty()) {
            Timber.e("Something wrong with retrieve. ftp get 0 byte array.")
            return null
        }

        ftp.disconnect()
        return evidenceBuffer
    }



}