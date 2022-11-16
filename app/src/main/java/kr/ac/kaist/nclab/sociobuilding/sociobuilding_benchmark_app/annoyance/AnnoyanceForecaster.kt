package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.annoyance

import android.content.Context
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.AppConfig
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.Config
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.FtpDownloader
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.schema.ScanReport
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.sum
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.jetbrains.kotlinx.multik.ndarray.operations.toFloatArray
import timber.log.Timber
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

class AnnoyanceForecaster(private val context: Context) {

    private val ftp = FtpDownloader(
        Config.FTP_HOSTNAME,
        Config.FTP_PORT,
        Config.FTP_USERNAME,
        Config.FTP_PASSWORD
    )
    private val actuators = arrayOf("coffeeGrinder", "vacuumCleaner")
    private val doorStates = arrayOf("open-open", "open-close", "close-open", "close-close")
    private val diffResults = mutableMapOf<String, Double>()

    /*
    settings

    - 3 evidences per 1 door state.
    - 4 door states per actuator. (2 doors)
    - 2 actuators.
    : 24 evidences per one request.
     */

    /*
    if cache hit ratio = 100%, do not download from server
    if cache hit ratio = 0%, download all from server
    */
    private fun isCached(): Boolean {
        return AppConfig.CACHED
    }

    /* give annoyance Forecasting result */
    fun annoyanceForecasting(
        backgroundNoise: ByteArray,
        beaconId: String?,
        wifiReport: ScanReport?
    ): Map<String, Double> {

        var evidenceBuffer: ByteArray? = null
        var synthesizedNoise: NDArray<Float, D1>? = null
        var backgroundDBA: Double = 0.0
        var synthesizedDBA: Double = 0.0

        // calculate dBA of background noise
        backgroundDBA = calculateDBA(mk.ndarray(AWeighting.aWeightingSignal((mk.ndarray(backgroundNoise).asType<Float>().toFloatArray()))))

        Timber.d("calculated dBA of background: $backgroundDBA")

        for (actuator in actuators) {
            for (state in doorStates) {
                for (i in 1..3) {

                    // retrieve nearby evidence
                    evidenceBuffer = retrieveEvidence(actuator, state, i)

                    // synthesize background noise buffer & evidence buffer
                    if (evidenceBuffer != null) {
                        synthesizedNoise = synthesizeNoise(backgroundNoise, evidenceBuffer)
                    } else {
                        Timber.e("evidence Buffer is null !")
                    }

                    if (synthesizedNoise != null) {
                        // calculate dBA of synthesized noise
                        synthesizedDBA = calculateDBA(synthesizedNoise)
                    } else {
                        Timber.e("synthesized Noise is null !")
                    }

                    Timber.d("calculated dBA of synthesized: $synthesizedDBA")

                    Timber.d("diff of synthesized and background: ${synthesizedDBA - backgroundDBA}")
                    // calculate dBA diff of two and save it to map
                    diffResults["${actuator}_${state}_${i}"] = (synthesizedDBA - backgroundDBA)

                    evidenceBuffer = null
                    synthesizedNoise = null
                }
            }
        }

        return diffResults
    }

    /* retrieve nearby evidence */
    private fun retrieveEvidence(actuator: String, state: String, index: Int): ByteArray? {

        var evidenceBuffer: ByteArray? = null

        val targetFilename = "${actuator}_${state}_${index}.pcm"

        // cache-hit 100%
        if (isCached()) {
            // from local cache
//            if (ftp.download(targetFilename, context)) {
//                Timber.d("download $targetFilename to cacheDir -> true")
//            } else {
//                Timber.d("download $targetFilename to cacheDir -> false")
//            }
            evidenceBuffer = File(context.cacheDir, targetFilename).readBytes()
        }

        // cache-hit 0%
        else {
            try {
                evidenceBuffer = ftp.download(targetFilename)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (evidenceBuffer == null) {
                    Timber.e("failed to retrieve $targetFilename from ftp server")
                } else {
                    Timber.d("successfully retrieve $targetFilename as ByteArray from ftp server")
                }
            }
        }

        return evidenceBuffer
    }

    /* synthesize bg noise & actuator noises */
    private fun synthesizeNoise(noise1: ByteArray, noise2: ByteArray): NDArray<Float, D1> {

        var flag = true
        if (noise1.size > noise2.size) {
            flag = false
        }

        return if (flag) {
            mk.ndarray(AWeighting.aWeightingSignal((mk.ndarray(noise1) + mk.ndarray(noise2)[0..noise1.size]).asType<Float>().toFloatArray()))
        } else {
            mk.ndarray(AWeighting.aWeightingSignal((mk.ndarray(noise1)[0..noise2.size] + mk.ndarray(noise2)[0..noise1.size]).asType<Float>().toFloatArray()))
        }
    }

    /* calculate dBA */
    private fun calculateDBA(noise: NDArray<Float, D1>): Double {
        return 20 * log10(sqrt((noise * noise).sum())).toDouble()
    }
}

