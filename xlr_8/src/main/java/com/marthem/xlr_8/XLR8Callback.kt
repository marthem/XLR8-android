package com.marthem.xlr_8

import android.os.ConditionVariable
import android.util.Log
import okhttp3.*
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.*

internal class XLR8Callback @JvmOverloads constructor(
    private val originalRequest: Request,
    private val mCall: Call,
    eventListener: EventListener? = null,
    responseCallback: Callback? = null
) : UrlRequest.Callback() {
    private val eventListener: EventListener?
    private val responseCallback: Callback?
    private var followCount = 0
    private var mResponse: Response
    private var mException: IOException? = null
    private val mResponseConditon = ConditionVariable()
    private val mBytesReceived = ByteArrayOutputStream()
    private val mReceiveChannel = Channels.newChannel(mBytesReceived)

    @Throws(IOException::class)
    fun waitForDone(): Response {
        mResponseConditon.block()
        if (mException != null) {
            throw mException as IOException
        }
        return mResponse
    }

    override fun onRedirectReceived(
        request: UrlRequest,
        info: UrlResponseInfo,
        newLocationUrl: String
    ) {
        if (followCount > MAX_FOLLOW_COUNT) {
            request.cancel()
        }
        followCount += 1
        val client = OkHttpClient()
        if (originalRequest.url.isHttps && newLocationUrl.startsWith("http://") && client.followSslRedirects) {
            request.followRedirect()
        } else if (!originalRequest.url.isHttps && newLocationUrl.startsWith("https://") && client.followSslRedirects) {
            request.followRedirect()
        } else if (client.followRedirects) {
            request.followRedirect()
        } else {
            request.cancel()
        }
    }

    override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
        mResponse = responseFromResponse(mResponse, info)
        if (eventListener != null) {
            eventListener.responseHeadersEnd(mCall, mResponse)
            eventListener.responseBodyStart(mCall)
        }
        request.read(ByteBuffer.allocateDirect(32 * 1024))
    }

    @Throws(Exception::class)
    override fun onReadCompleted(
        request: UrlRequest,
        info: UrlResponseInfo,
        byteBuffer: ByteBuffer
    ) {
        byteBuffer.flip()
        try {
            mReceiveChannel.write(byteBuffer)
        } catch (e: IOException) {
            Log.i( TAG, "IOException during ByteBuffer read. Details: ", e)
            throw e
        }
        byteBuffer.clear()
        request.read(byteBuffer)
    }

    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
        eventListener?.responseBodyEnd(mCall, info.receivedByteCount)
        val contentTypeString = mResponse.header("content-type")
        val contentType = (contentTypeString ?: "text/plain; charset=\"utf-8\"").toMediaTypeOrNull()
        val responseBody = mBytesReceived.toByteArray().toResponseBody(contentType)
        val newRequest = originalRequest.newBuilder().url(info.url).build()
        mResponse = mResponse.newBuilder().body(responseBody).request(newRequest).build()
        mResponseConditon.open()
        eventListener?.callEnd(mCall)

        if (responseCallback != null) {
            try {
                responseCallback.onResponse(mCall, mResponse)
            } catch (e: IOException) {
                // Pass
            }
        }
    }

    override fun onFailed(
        request: UrlRequest,
        info: UrlResponseInfo,
        error: CronetException
    ) {
        val e = IOException("Cronet Exception Occurred", error)
        mException = e
        mResponseConditon.open()
        eventListener?.callFailed(mCall, e)
        responseCallback?.onFailure(mCall, e)
    }

    override fun onCanceled(request: UrlRequest, info: UrlResponseInfo) {
        mResponseConditon.open()
        eventListener?.callEnd(mCall)
    }

    companion object {
        private const val TAG = "Callback"
        private const val MAX_FOLLOW_COUNT = 20
        private fun protocolFromNegotiatedProtocol(responseInfo: UrlResponseInfo): Protocol {
            val negotiatedProtocol =
                responseInfo.negotiatedProtocol.toLowerCase(Locale.ROOT)
            return when {
                negotiatedProtocol.contains("quic") -> Protocol.QUIC
                negotiatedProtocol.contains("h2") -> Protocol.HTTP_2
                negotiatedProtocol.contains("1.1") -> Protocol.HTTP_1_1
                else -> Protocol.HTTP_1_0
            }
        }

        private fun headersFromResponse(responseInfo: UrlResponseInfo): Headers {
            val headers =
                responseInfo.allHeadersAsList
            val headerBuilder = Headers.Builder()
            for ((key, value) in headers) {
                try {
                    if (key.equals("content-encoding", ignoreCase = true)) {
                        // Remove content-encoding header as it's already handled by cronet
                        continue
                    }
                    headerBuilder.add(key, value)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid HTTP header/value: $key$value")
                }
            }
            return headerBuilder.build()
        }

        private fun responseFromResponse(
            response: Response,
            responseInfo: UrlResponseInfo
        ): Response {
            val protocol = protocolFromNegotiatedProtocol(responseInfo)
            val headers = headersFromResponse(responseInfo)
            return response.newBuilder()
                .receivedResponseAtMillis(System.currentTimeMillis())
                .protocol(protocol)
                .code(responseInfo.httpStatusCode)
                .message(responseInfo.httpStatusText)
                .headers(headers)
                .build()
        }
    }

    init {
        mResponse = Response.Builder()
            .sentRequestAtMillis(System.currentTimeMillis())
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_0)
            .code(0)
            .message("")
            .build()
        this.responseCallback = responseCallback
        this.eventListener = eventListener
    }
}