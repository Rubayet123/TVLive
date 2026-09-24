package io.github.rubayet123.tvlive.data.network

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlin.math.min

/**
 * ExoPlayer DataSource wrapper that transparently strips fake image wrappers
 * (PNG / JPEG / WebP) commonly prepended to MPEG-TS video chunks by anti-hotlinking
 * streaming providers such as DAMITV / ondemand.st.
 */
@OptIn(UnstableApi::class)
class TsUnwrappingDataSource(
    private val upstream: DataSource
) : DataSource {

    companion object {
        private const val TAG = "TsUnwrappingDS"
        private const val TS_PACKET_SIZE = 188
        private const val MAX_WRAPPER_SEARCH_BYTES = 1024 * 1024 // 1 MB
        private const val INITIAL_CHUNK_SIZE = 64 * 1024
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return TsUnwrappingDataSource(upstreamFactory.createDataSource())
        }
    }

    private var currentDataSpec: DataSpec? = null
    private var isTsPotential = false
    private var headerChecked = false

    private var bufferedData: ByteArray? = null
    private var bufferReadPos = 0
    private var bufferValidLength = 0

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        headerChecked = false
        bufferedData = null
        bufferReadPos = 0
        bufferValidLength = 0

        val uriString = dataSpec.uri.toString().lowercase()
        isTsPotential = uriString.contains(".ts") ||
                uriString.contains("damitv") ||
                uriString.contains("ondemand.st") ||
                uriString.contains("/ts2/") ||
                uriString.contains("/papi/") ||
                uriString.contains("video/mp2t")

        return upstream.open(dataSpec)
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        // If we still have unread bytes in our unwrap buffer, serve them first
        if (bufferValidLength > 0 && bufferedData != null) {
            val toCopy = min(length, bufferValidLength)
            System.arraycopy(bufferedData!!, bufferReadPos, target, offset, toCopy)
            bufferReadPos += toCopy
            bufferValidLength -= toCopy
            if (bufferValidLength == 0) {
                bufferedData = null
            }
            return toCopy
        }

        // Check the start of the stream for fake image wrappers on the first read
        if (!headerChecked) {
            headerChecked = true

            // Read the first available chunk from upstream (non-blocking)
            var tempBuffer = ByteArray(INITIAL_CHUNK_SIZE)
            var totalRead = upstream.read(tempBuffer, 0, tempBuffer.size)

            if (totalRead <= 0) {
                return C.RESULT_END_OF_INPUT
            }

            if (isImageHeader(tempBuffer, totalRead)) {
                // If it looks like an image header, expand buffer if needed up to 1MB to find MPEG-TS sync pattern
                var syncOffset = findTsSyncOffset(tempBuffer, totalRead)

                while (syncOffset < 0 && totalRead < MAX_WRAPPER_SEARCH_BYTES) {
                    val expandBy = 64 * 1024
                    val newBuffer = ByteArray(tempBuffer.size + expandBy)
                    System.arraycopy(tempBuffer, 0, newBuffer, 0, totalRead)
                    tempBuffer = newBuffer

                    val readBytes = upstream.read(tempBuffer, totalRead, expandBy)
                    if (readBytes == C.RESULT_END_OF_INPUT) break
                    totalRead += readBytes
                    syncOffset = findTsSyncOffset(tempBuffer, totalRead)
                }

                if (syncOffset >= 0) {
                    Log.d(TAG, "Stripped fake image header wrapper of $syncOffset bytes")
                    val usableTsBytes = totalRead - syncOffset
                    val toCopy = min(length, usableTsBytes)
                    System.arraycopy(tempBuffer, syncOffset, target, offset, toCopy)

                    if (usableTsBytes > toCopy) {
                        bufferedData = tempBuffer
                        bufferReadPos = syncOffset + toCopy
                        bufferValidLength = usableTsBytes - toCopy
                    }
                    return toCopy
                } else {
                    Log.w(TAG, "Image header detected but MPEG-TS sync byte 0x47 pattern not found in first $totalRead bytes")
                }
            }

            // Normal payload (or unstripped), serve what was read into tempBuffer
            val toCopy = min(length, totalRead)
            System.arraycopy(tempBuffer, 0, target, offset, toCopy)
            if (totalRead > toCopy) {
                bufferedData = tempBuffer
                bufferReadPos = toCopy
                bufferValidLength = totalRead - toCopy
            }
            return toCopy
        }

        // Direct read from upstream for the rest of the stream
        return upstream.read(target, offset, length)
    }

    private fun isImageHeader(data: ByteArray, length: Int): Boolean {
        if (length < 12) return false

        // PNG: 0x89 'P' 'N' 'G' (0x89, 0x50, 0x4E, 0x47)
        val isPng = data[0] == 0x89.toByte() &&
                data[1] == 0x50.toByte() &&
                data[2] == 0x4E.toByte() &&
                data[3] == 0x47.toByte()

        // JPEG: 0xFF 0xD8 0xFF
        val isJpeg = data[0] == 0xFF.toByte() &&
                data[1] == 0xD8.toByte() &&
                data[2] == 0xFF.toByte()

        // WebP: RIFF....WEBP
        val isWebp = data[0] == 'R'.code.toByte() &&
                data[1] == 'I'.code.toByte() &&
                data[2] == 'F'.code.toByte() &&
                data[3] == 'F'.code.toByte() &&
                data[8] == 'W'.code.toByte() &&
                data[9] == 'E'.code.toByte() &&
                data[10] == 'B'.code.toByte() &&
                data[11] == 'P'.code.toByte()

        return isPng || isJpeg || isWebp
    }

    private fun findTsSyncOffset(data: ByteArray, length: Int): Int {
        val maxOffset = min(length - (2 * TS_PACKET_SIZE), MAX_WRAPPER_SEARCH_BYTES)
        for (i in 0 until maxOffset) {
            if (data[i] == 0x47.toByte() &&
                data[i + TS_PACKET_SIZE] == 0x47.toByte() &&
                data[i + 2 * TS_PACKET_SIZE] == 0x47.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        bufferedData = null
        bufferReadPos = 0
        bufferValidLength = 0
        currentDataSpec = null
        upstream.close()
    }
}
