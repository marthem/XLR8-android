# XLR8-android

An [OkHttp interceptor][1] which route all requests over QUIC Protocol (HTTP/3).

- HTTP/3
- [All requests are handled by Cronet Engine (Chromium network stack)](https://developer.android.com/guide/topics/connectivity/cronet).
- Backward compatibility for non-QUIC servers (HTTP 1 & 2)
- Require Google mobile services to be installed/enabled on the device 


## Usage
```kotlin
val okHttpClient: OkHttpClient = OkHttpClient().newBuilder()
        .addInterceptor(XLR8Interceptor(applicationContext))
        .build()
```

## Downloading

Top-level build.gradle:

```groovy
allprojects {
	repositories {
		maven { url 'https://jitpack.io' }
	}
}

```

App-level build.gradle:

```groovy
   dependencies {
      implementation 'com.github.marthem:XLR8-android:1.0.1'
   }
```

## License

The library is open-sourced software licensed under the [MIT license](https://opensource.org/licenses/MIT).

[1]: https://github.com/square/okhttp/wiki/Interceptors
