package com.test.agenttrade

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.test.agenttrade.data.AppPrefs
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.wallet.WalletConnectManager

class OptimAIApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
        // Only the stored wallet session here — this process also hosts the
        // keyboard, which must come up fast in other apps.
        WalletConnectManager.attach(this)
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { StockApiClient.http })) }
                .crossfade(true)
                .build()
        }
    }
}
