package com.marthem.xlr_8

import android.content.Context
import com.marthem.xlr_8.XLR8Networking.buildRequest
import com.marthem.xlr_8.XLR8Networking.cronetEngine
import com.marthem.xlr_8.XLR8Networking.init
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class XLR8Interceptor(context: Context?) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return if (cronetEngine() != null) {
            // Proceed with Cronet
            proceedWithCronet(chain.request(), chain.call())
        } else {
            // Backward compatibility
            chain.proceed(chain.request())
        }
    }

    @Throws(IOException::class)
    private fun proceedWithCronet(request: Request, call: Call): Response {
        val callback = XLR8Callback(request, call)
        val urlRequest = buildRequest(request, callback)
        urlRequest.start()
        return callback.waitForDone()
    }

    init {
        init(context!!)
    }
}