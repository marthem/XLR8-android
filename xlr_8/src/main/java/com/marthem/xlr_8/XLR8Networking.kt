package com.marthem.xlr_8

import android.content.Context
import com.google.android.gms.net.CronetProviderInstaller
import okhttp3.Request
import okio.Buffer
import org.chromium.net.CronetEngine
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors

internal object XLR8Networking {
    private var cronetEngine: CronetEngine? = null
    private val executorService = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun init(context: Context) {
        CronetProviderInstaller.installProvider(context).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                initializeCronetEngine(context)
            }
        }
    }

    @Synchronized
    private fun initializeCronetEngine(context: Context) {
        if (cronetEngine == null) {
            val cacheDir = File(context.cacheDir, "cronet-cache")
            cacheDir.mkdirs()
            cronetEngine = CronetEngine.Builder(context)
                .enableBrotli(true)
                .enableHttp2(true)
                .enableQuic(true)
                .setStoragePath(cacheDir.absolutePath)
                .enableHttpCache(
                    CronetEngine.Builder.HTTP_CACHE_DISK,
                    10 * 1024 * 1024.toLong()
                ) // 10 MegaBytes
                .build()
            URL.setURLStreamHandlerFactory(cronetEngine!!.createURLStreamHandlerFactory())
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun buildRequest(request: Request, callback: UrlRequest.Callback?): UrlRequest {
        val url = request.url.toString()
        val requestBuilder = cronetEngine!!.newUrlRequestBuilder(
            url,
            callback,
            executorService
        )
        requestBuilder.setHttpMethod(request.method)
        val headers = request.headers
        var i = 0
        while (i < headers.size) {
            if (headers.name(i).equals("Accept-Encoding", ignoreCase = true)) {
                i += 1
                continue
            }
            requestBuilder.addHeader(headers.name(i), headers.value(i))
            i += 1
        }
        val requestBody = request.body
        if (requestBody != null) {
            val contentType = requestBody.contentType()
            if (contentType != null) {
                requestBuilder.addHeader("Content-Type", contentType.toString())
            }
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            requestBuilder.setUploadDataProvider(
                UploadDataProviders.create(buffer.readByteArray()),
                executorService
            )
        }
        return requestBuilder.build()
    }

    @JvmStatic
    fun cronetEngine(): CronetEngine? {
        return cronetEngine
    }
}