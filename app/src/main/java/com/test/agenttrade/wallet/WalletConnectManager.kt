package com.test.agenttrade.wallet

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import com.test.agenttrade.BuildConfig
import com.test.agenttrade.data.AppPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges the app to an external wallet (MetaMask, Trust Wallet) over
 * WalletConnect v2 (Reown Sign, dApp side) on BNB Smart Chain — the Android
 * port of the iOS `WalletConnectManager`. Every order is signed and broadcast
 * by the user's own wallet; nothing here holds a key.
 *
 * Debug builds can also use a simulated wallet (Settings → Developer) so the
 * order flow can be exercised on an emulator with no wallet app installed;
 * its "transactions" never reach a chain and are never recorded server-side.
 */
object WalletConnectManager {
    private const val TAG = "WalletConnect"
    private const val CHAIN_ID = WalletSession.REQUIRED_CHAIN_ID
    private const val REDIRECT = "agenttrade://wc"

    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _session = MutableStateFlow<WalletSession?>(null)
    val session: StateFlow<WalletSession?> = _session.asStateFlow()

    private val _isConnecting = MutableStateFlow<WalletApp?>(null)
    /** Which wallet a connect is in flight for, if any. */
    val isConnecting: StateFlow<WalletApp?> = _isConnecting.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var sdkReady = false
    private var sdkStarted = false
    /** Completes once Reown's Sign client has finished initializing. */
    private val sdkInitialized = CompletableDeferred<Unit>()
    private var pendingConnect: CompletableDeferred<Sign.Model.ApprovedSession>? = null
    private var pendingConnectApp: WalletApp? = null
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<Sign.Model.JsonRpcResponse>>()
    /** Responses that arrived before their request's `onSuccess` registered it. */
    private val earlyResponses = ConcurrentHashMap<Long, Sign.Model.JsonRpcResponse>()

    /** Debug-only switch for the simulated wallet. */
    var simulatedMode: Boolean
        get() = BuildConfig.DEBUG && AppPrefs.getString(KEY_SIMULATED) == "1"
        set(value) = AppPrefs.putString(KEY_SIMULATED, if (value) "1" else null)
    private const val KEY_SIMULATED = "simulatedWallet"

    /**
     * Cheap: only the stored session. The keyboard runs in this same process
     * and never needs the WalletConnect SDK, so the SDK itself (~0.7s on the
     * main thread) starts later, from the app's UI — see `ensureSdk`.
     */
    fun attach(application: Application) {
        app = application
        _session.value = WalletSessionStore.load()
    }

    /** Starts the Reown SDK once; called after the app's first frame, and before any wallet request. */
    fun ensureSdk() {
        if (sdkStarted) return
        sdkStarted = true
        configureSdk()
    }

    private suspend fun awaitSdk() {
        ensureSdk()
        try {
            withTimeout(20_000) { sdkInitialized.await() }
        } catch (_: TimeoutCancellationException) {
            throw WalletException("WalletConnect is still starting. Try again in a moment.")
        }
    }

    private fun configureSdk() {
        runCatching {
            val metadata = Core.Model.AppMetaData(
                name = "OptimAI Agentic",
                description = "Trade what you read.",
                url = "https://optimai.network",
                icons = listOf("https://optimai.network/favicon.ico"),
                redirect = REDIRECT,
            )
            CoreClient.initialize(
                application = app,
                projectId = BuildConfig.REOWN_PROJECT_ID,
                metaData = metadata,
                connectionType = ConnectionType.AUTOMATIC,
                onError = { error -> Log.w(TAG, "core init: ${error.throwable.message}") },
            )
            SignClient.initialize(
                Sign.Params.Init(core = CoreClient),
                onSuccess = {
                    sdkReady = true
                    sdkInitialized.complete(Unit)
                    reconcileStoredSession()
                },
                onError = { error -> Log.w(TAG, "sign init: ${error.throwable.message}") },
            )
            SignClient.setDappDelegate(delegate)
        }.onFailure { Log.w(TAG, "Reown setup failed", it) }
    }

    /** A stored session the relay no longer knows about is gone for good. */
    private fun reconcileStoredSession() {
        val stored = _session.value ?: return
        if (stored.isSimulated) return
        val active = runCatching { SignClient.getActiveSessionByTopic(stored.topic) }.getOrNull()
        if (active == null) scope.launch { clearSession() }
    }

    // MARK: - Connect / disconnect

    suspend fun connect(walletApp: WalletApp) {
        if (_session.value != null || _isConnecting.value != null) return
        _isConnecting.value = walletApp
        _lastError.value = null
        try {
            if (simulatedMode) {
                delay(700)
                val fake = "0x" + UUID.randomUUID().toString().replace("-", "").padEnd(40, '0').take(40)
                setSession(
                    WalletSession(
                        address = fake, chainId = CHAIN_ID, topic = UUID.randomUUID().toString(),
                        walletName = "Simulated ${walletApp.displayName}", connectedAt = System.currentTimeMillis(),
                        walletDeepLinkBase = walletApp.deepLinkBase, isSimulated = true,
                    ),
                )
                return
            }
            connectReal(walletApp)
        } catch (e: Exception) {
            _lastError.value = (e as? WalletException)?.message ?: "Couldn't connect the wallet. Please try again."
        } finally {
            _isConnecting.value = null
        }
    }

    private suspend fun connectReal(walletApp: WalletApp) {
        awaitSdk()
        val pairing = CoreClient.Pairing.create { error -> Log.w(TAG, "pairing: ${error.throwable.message}") }
            ?: throw WalletException("Couldn't start a WalletConnect pairing.")
        // Proposed as optional: the wallet is free to approve whatever chain
        // is active in it, and the session records what it actually approved.
        val namespaces = mapOf(
            "eip155" to Sign.Model.Namespace.Proposal(
                chains = listOf("eip155:$CHAIN_ID"),
                methods = listOf("personal_sign", "eth_sendTransaction", "wallet_switchEthereumChain", "wallet_addEthereumChain"),
                events = listOf("chainChanged", "accountsChanged"),
            ),
        )
        val approved = CompletableDeferred<Sign.Model.ApprovedSession>()
        pendingConnect = approved
        pendingConnectApp = walletApp
        val uri = CompletableDeferred<String>()
        SignClient.connect(
            Sign.Params.Connect(namespaces = null, optionalNamespaces = namespaces, properties = null, scopedProperties = null, pairing = pairing),
            onSuccess = { url -> uri.complete(url) },
            onError = { error -> uri.completeExceptionally(WalletException(error.throwable.message ?: "Couldn't connect the wallet.")) },
        )
        openWallet(walletApp, uri.await())

        val session = try {
            withTimeout(5 * 60_000) { approved.await() }
        } catch (_: TimeoutCancellationException) {
            throw WalletException.timedOut
        } finally {
            pendingConnect = null
        }
        val account = session.accounts.firstOrNull() ?: session.namespaces["eip155"]?.accounts?.firstOrNull()
            ?: throw WalletException.rejected
        val parts = account.split(":")
        val newSession = WalletSession(
            address = parts.last(),
            chainId = parts.getOrNull(1)?.toIntOrNull() ?: CHAIN_ID,
            topic = session.topic,
            walletName = session.metaData?.name ?: walletApp.displayName,
            connectedAt = System.currentTimeMillis(),
            walletDeepLinkBase = walletApp.deepLinkBase,
        )
        setSession(newSession)
        // Best-effort, non-blocking: nudge the wallet onto BSC right away.
        if (!newSession.isOnRequiredChain) scope.launch { runCatching { switchToBnbSmartChain(newSession) } }
    }

    suspend fun disconnect() {
        val current = _session.value
        if (current != null && !current.isSimulated) {
            runCatching { awaitSdk() }
            runCatching {
                SignClient.disconnect(Sign.Params.Disconnect(current.topic), onSuccess = {}, onError = {})
            }
        }
        clearSession()
    }

    private fun setSession(session: WalletSession) {
        _session.value = session
        WalletSessionStore.save(session)
    }

    private fun clearSession() {
        _session.value = null
        WalletSessionStore.save(null)
    }

    // MARK: - Requests

    /**
     * Sends an `eth_sendTransaction` for the connected wallet to sign and
     * broadcast, bringing the wallet forward for approval, and returns the
     * transaction hash once it responds.
     */
    suspend fun sendTransaction(to: String, data: String, value: String, gas: String?): String {
        var session = _session.value ?: throw WalletException.notConnected
        if (session.isSimulated) {
            delay(1200)
            return "0xSIMULATEDTX" + Integer.toHexString((to + data + value).hashCode())
        }
        // A transaction tagged with whatever chain the wallet is on would go
        // to that chain — ask it onto BSC first, and stop if it won't go.
        if (!session.isOnRequiredChain) {
            switchToBnbSmartChain(session)
            session = _session.value?.takeIf { it.isOnRequiredChain } ?: throw WalletException.wrongChain
        }
        val params = buildJsonArray {
            add(buildJsonObject {
                put("from", session.address)
                put("to", to)
                put("data", data)
                put("value", value)
                if (gas != null) put("gas", gas)
            })
        }.toString()
        val result = sendRequest(session, "eth_sendTransaction", params)
        return result ?: throw WalletException.rejected
    }

    /**
     * Asks the wallet to switch to BNB Smart Chain (falling back to
     * `wallet_addEthereumChain`), updating the stored chain only once the
     * wallet confirms.
     */
    private suspend fun switchToBnbSmartChain(session: WalletSession) {
        if (session.isOnRequiredChain) return
        val hexChain = "0x" + CHAIN_ID.toString(16)
        val switched = runCatching {
            withTimeout(90_000) {
                sendRequest(session, "wallet_switchEthereumChain", buildJsonArray { add(buildJsonObject { put("chainId", hexChain) }) }.toString())
            }
        }.isSuccess
        if (!switched) {
            val add = buildJsonArray {
                add(buildJsonObject {
                    put("chainId", hexChain)
                    put("chainName", "BNB Smart Chain")
                    putJsonObject("nativeCurrency") {
                        put("name", "BNB")
                        put("symbol", "BNB")
                        put("decimals", 18)
                    }
                    putJsonArray("rpcUrls") { add(JsonPrimitive("https://bsc-dataseed.binance.org/")) }
                    putJsonArray("blockExplorerUrls") { add(JsonPrimitive("https://bscscan.com")) }
                })
            }.toString()
            val added = runCatching { withTimeout(90_000) { sendRequest(session, "wallet_addEthereumChain", add) } }.isSuccess
            if (!added) return
        }
        setSession(session.copy(chainId = CHAIN_ID))
    }

    /** Sends a request, brings the wallet forward, and awaits its JSON-RPC result. */
    private suspend fun sendRequest(session: WalletSession, method: String, params: String): String? {
        awaitSdk()
        val response = CompletableDeferred<Sign.Model.JsonRpcResponse>()
        val sent = CompletableDeferred<Long>()
        SignClient.request(
            Sign.Params.Request(sessionTopic = session.topic, method = method, params = params, chainId = "eip155:${session.chainId}", expiry = null),
            onSuccess = { request ->
                earlyResponses.remove(request.requestId)?.let { response.complete(it) }
                    ?: pendingRequests.put(request.requestId, response)
                sent.complete(request.requestId)
            },
            onError = { error -> sent.completeExceptionally(WalletException(error.throwable.message ?: "The wallet didn't receive the request.")) },
        )
        val id = sent.await()
        session.walletApp?.let { bringToFront(it) }
        try {
            return when (val result = response.await()) {
                is Sign.Model.JsonRpcResponse.JsonRpcResult -> result.result?.toString()?.trim('"')
                is Sign.Model.JsonRpcResponse.JsonRpcError -> throw WalletException.rejected
            }
        } finally {
            pendingRequests.remove(id)
        }
    }

    // MARK: - Opening the wallet

    private fun openWallet(walletApp: WalletApp, pairingUri: String) {
        val pm = app.packageManager
        // The `wc:` URI handed straight to the chosen wallet's package…
        val direct = Intent(Intent.ACTION_VIEW, Uri.parse(pairingUri)).setPackage(walletApp.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (direct.resolveActivity(pm) != null && start(direct)) return
        // …its own scheme…
        val native = Intent(Intent.ACTION_VIEW, Uri.parse("${walletApp.nativeScheme}://wc?uri=${Uri.encode(pairingUri)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (native.resolveActivity(pm) != null && start(native)) return
        // …or its universal link (which lands on the store if it isn't installed).
        val universal = Intent(Intent.ACTION_VIEW, Uri.parse("${walletApp.deepLinkBase}/wc?uri=${Uri.encode(pairingUri)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(universal)) throw WalletException.notInstalled
    }

    private fun bringToFront(walletApp: WalletApp) {
        val launch = app.packageManager.getLaunchIntentForPackage(walletApp.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch != null && start(launch)) return
        start(Intent(Intent.ACTION_VIEW, Uri.parse("${walletApp.nativeScheme}://")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(intent: Intent): Boolean = try {
        app.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    // MARK: - Delegate

    private val delegate = object : SignClient.DappDelegate {
        override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
            pendingConnect?.complete(approvedSession)
        }

        override fun onSessionRejected(rejectedSession: Sign.Model.RejectedSession) {
            pendingConnect?.completeExceptionally(WalletException.rejected)
        }

        override fun onSessionUpdate(updatedSession: Sign.Model.UpdatedSession) {}

        /**
         * MetaMask doesn't update a session's namespaces when the user
         * switches network in the wallet — it emits `chainChanged` instead.
         */
        override fun onSessionEvent(sessionEvent: Sign.Model.SessionEvent) {
            if (sessionEvent.name != "chainChanged") return
            val chain = parseChainId(sessionEvent.data) ?: return
            scope.launch { _session.value?.let { setSession(it.copy(chainId = chain)) } }
        }

        override fun onSessionEvent(sessionEvent: Sign.Model.Event) {
            if (sessionEvent.name != "chainChanged") return
            val current = _session.value ?: return
            if (sessionEvent.topic != current.topic) return
            val chain = parseChainId(sessionEvent.data) ?: sessionEvent.chainId.substringAfterLast(":").toIntOrNull() ?: return
            scope.launch { setSession(current.copy(chainId = chain)) }
        }

        override fun onSessionExtend(session: Sign.Model.Session) {}

        override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
            val topic = (deletedSession as? Sign.Model.DeletedSession.Success)?.topic
            if (topic == null || topic == _session.value?.topic) scope.launch { clearSession() }
        }

        override fun onSessionRequestResponse(response: Sign.Model.SessionRequestResponse) {
            val id = response.result.id
            pendingRequests.remove(id)?.complete(response.result) ?: earlyResponses.put(id, response.result)
        }

        override fun onProposalExpired(proposal: Sign.Model.ExpiredProposal) {
            pendingConnect?.completeExceptionally(WalletException.timedOut)
        }

        override fun onRequestExpired(request: Sign.Model.ExpiredRequest) {
            pendingRequests.remove(request.id)?.completeExceptionally(WalletException.timedOut)
        }

        override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {}

        override fun onError(error: Sign.Model.Error) {
            Log.w(TAG, "sign: ${error.throwable.message}")
        }
    }

    /** "56", "0x38", or a JSON number. */
    private fun parseChainId(data: String): Int? {
        val text = data.trim().trim('"')
        return if (text.startsWith("0x")) text.drop(2).toIntOrNull(16) else text.toIntOrNull()
    }
}
