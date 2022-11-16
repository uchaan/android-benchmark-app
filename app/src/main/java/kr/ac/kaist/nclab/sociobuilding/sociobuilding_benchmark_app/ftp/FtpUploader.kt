package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp

import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class FtpUploader(private val hostname: String, private val port: Int, private val username: String, private val password: String) {
    companion object {
        const val TAG = "FtpUploader"
    }

    data class FtpAccount(val username: String, val password:String)

    private fun remotePath():String {
        return "" // deleted due to the security issue
    }

    fun createNewDirectory(name: String): Boolean {
        val ftp = FTPClient()
        ftp.connect(hostname, port)
        val dirname = remotePath()
        if (!ftp.login(username, password)) {
            Timber.e("ftp login failed")
            return false
        }
        ftp.enterLocalPassiveMode()

        if (!ftp.changeWorkingDirectory(dirname)) {
            Timber.e("ftp failed to change workdir 1 ")
            return false
        }

        if (!ftp.makeDirectory(name)) {
            Timber.e("ftp failed to create directory named $name")
            return false
        }

        return true
    }

    fun upload(file: File, dateTime: String):Boolean {
        return upload(file, file.name, dateTime)
    }

    fun upload(text: String, filename: String, dateTime: String):Boolean {
        val input = text.byteInputStream(Charsets.UTF_8)
        val result = upload(input, filename, dateTime)
        input.close()
        return result
    }

    fun upload(input: InputStream, filename: String, dateTime: String):Boolean {
        val ftp = FTPClient()
        ftp.connect(hostname, port)
        val dirname = remotePath() + dateTime
        Timber.d("uname: ${username}, passwd: ${password} dirname: ${dirname}, filename: ${filename}")
        if (!ftp.login(username, password)) {
            Timber.e("ftp login failed")
            return false
        }
        ftp.enterLocalPassiveMode()

        ftp.setFileType(FTP.BINARY_FILE_TYPE)

        if (!ftp.changeWorkingDirectory(dirname)) {
            Timber.e("ftp failed to change workdir 1 ")
            return false
        }

        val bufIn = BufferedInputStream(input)
        if (!ftp.storeFile(filename, bufIn)) {
            Timber.e("ftp store file failed: ${ftp.replyString}")
            return false
        }
        bufIn.close()

        if (!ftp.logout()) {
            Timber.e("ftp logout failed")
            return false
        }
        ftp.disconnect()
        return true
    }

    fun upload(file: File, filename: String, dateTime: String):Boolean {
        val ftp = FTPClient()
        ftp.connect(hostname, port)
        val dirName = remotePath() + dateTime
        Timber.d("uname: $username, passwd: $password dirname: $dirName, filename: $filename")
        if (!ftp.login(username, password)) {
            Timber.e("ftp login failed")
            return false
        }
        ftp.enterLocalPassiveMode()

        if (!ftp.changeWorkingDirectory(dirName)) {
            Timber.e("ftp failed to change workdir 1 ")
            return false
        }

        val fileIn = FileInputStream(file)
        if (!ftp.storeFile(filename, fileIn)) {
            Timber.e("ftp store file failed: ${ftp.replyString}")

            return false
        }

        fileIn.close()
        if (!ftp.logout()) {
            Timber.e("ftp logout failed")
            return false
        }
        ftp.disconnect()
        return true
    }
}
