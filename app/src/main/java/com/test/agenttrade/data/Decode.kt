package com.test.agenttrade.data

import kotlinx.serialization.json.JsonObject
import java.time.Instant

/** Response decoders — one per DTO, mirroring the iOS `init(from:)`s. */
internal object Decode {
    fun marketItem(o: JsonObject) = MarketItem(
        symbol = o.req("symbol"),
        ticker = o.req("ticker"),
        name = o.str("name"),
        underlyingName = o.str("underlying_name"),
        logoUrl = StockApiClient.absoluteUrl(o.str("logo_url")),
        referencePrice = o.dbl("reference_price"),
        priceChangePct24h = o.dbl("price_change_pct_24h"),
        volume24h = o.dbl("volume_24h"),
        marketCap = o.dbl("market_cap"),
        sparkline = o.doubles("sparkline"),
        error = o.str("error"),
    )

    fun marketResponse(o: JsonObject) = MarketResponse(
        total = o.int("total") ?: 0,
        items = o.arr("items")?.objects()?.mapNotNull { runCatching { marketItem(it) }.getOrNull() } ?: emptyList(),
    )

    fun quote(o: JsonObject): StockQuote {
        val fundamentals = o.obj("fundamentals")
        val market = o.obj("market")
        return StockQuote(
            symbol = o.req("symbol"),
            ticker = o.req("ticker"),
            referencePrice = o.reqDbl("reference_price"),
            priceChangePct24h = o.dbl("price_change_pct_24h"),
            marketCap = o.dbl("market_cap"),
            volume24h = o.dbl("volume_24h"),
            week52High = fundamentals?.dbl("week52_high"),
            week52Low = fundamentals?.dbl("week52_low"),
            isMarketOpen = market?.bool("is_open") ?: false,
            nextOpenAt = market?.instant("next_open_at"),
            nextCloseAt = market?.instant("next_close_at"),
        )
    }

    fun candleSeries(o: JsonObject) = CandleSeries(
        symbol = o.str("symbol").orEmpty(),
        ticker = o.str("ticker").orEmpty(),
        interval = o.str("interval").orEmpty(),
        candles = o.arr("candles")?.objects()?.mapNotNull { c ->
            val time = c.instant("open_time") ?: return@mapNotNull null
            Candle(
                openTime = time,
                open = c.dbl("open") ?: return@mapNotNull null,
                high = c.dbl("high") ?: return@mapNotNull null,
                low = c.dbl("low") ?: return@mapNotNull null,
                close = c.dbl("close") ?: return@mapNotNull null,
                volume = c.dbl("volume") ?: 0.0,
                session = c.str("session") ?: "regular",
            )
        } ?: emptyList(),
    )

    fun infoDetails(o: JsonObject): InfoDetails {
        val usd = o.obj("market_data")?.obj("quote")?.obj("USD")
        val md = o.obj("market_data")
        return InfoDetails(
            symbol = o.req("symbol"),
            ticker = o.str("ticker"),
            name = o.req("name"),
            underlyingName = o.str("underlying_name"),
            companyProfileSummary = o.str("company_profile_summary"),
            coinmarketcapAbout = o.str("coinmarketcap_about"),
            description = o.str("description"),
            logoUrl = StockApiClient.absoluteUrl(o.str("logo_url")),
            category = o.str("category"),
            tags = o.strings("tags"),
            websiteUrl = o.str("website_url"),
            twitterUrl = o.str("twitter_url"),
            contracts = o.arr("contracts")?.objects()?.mapNotNull { c ->
                val network = c.str("network") ?: return@mapNotNull null
                val address = c.str("address") ?: return@mapNotNull null
                InfoDetails.Contract(network, address)
            } ?: emptyList(),
            dividendYield = o.obj("dividend")?.dbl("dividendYield"),
            marketData = md?.let {
                InfoDetails.MarketData(
                    cmcRank = it.int("cmc_rank"),
                    circulatingSupply = it.dbl("circulating_supply"),
                    marketCap = usd?.dbl("market_cap"),
                    volume24h = usd?.dbl("volume_24h"),
                    percentChange1h = usd?.dbl("percent_change_1h"),
                    percentChange24h = usd?.dbl("percent_change_24h"),
                    percentChange7d = usd?.dbl("percent_change_7d"),
                    percentChange30d = usd?.dbl("percent_change_30d"),
                    percentChange90d = usd?.dbl("percent_change_90d"),
                )
            },
        )
    }

    fun tradeQuote(o: JsonObject) = TradeQuote(
        side = o.str("side") ?: "buy",
        ticker = o.req("ticker"),
        symbol = o.req("symbol"),
        payToken = o.req("pay_token"),
        payAmount = o.reqDbl("pay_amount"),
        receiveAmount = o.reqDbl("receive_amount"),
        pricePerToken = o.reqDbl("price_per_token"),
        priceImpactPercent = o.dbl("price_impact_percent"),
        minimumReceived = o.dbl("minimum_received"),
        slippagePercent = o.dbl("slippage_percent") ?: 0.5,
        transaction = o.obj("transaction")?.let {
            TradeQuote.UnsignedTransaction(
                to = it.req("to"),
                data = it.req("data"),
                value = it.str("value") ?: "0x0",
                gas = it.str("gas"),
                gasPrice = it.str("gas_price"),
            )
        },
        approval = o.obj("approval")?.let {
            TradeQuote.ApprovalNeeded(to = it.req("to"), data = it.req("data"), spender = it.str("spender").orEmpty())
        },
    )

    fun bnbBalance(o: JsonObject) = BnbBalance(token = o.req("token"), amount = o.reqDbl("amount"), usd = o.dbl("usd"))

    fun receipt(o: JsonObject) = BnbReceipt(
        hash = o.str("hash").orEmpty(),
        status = when (o.str("status")) {
            "success" -> BnbReceipt.Status.SUCCESS
            "reverted" -> BnbReceipt.Status.REVERTED
            else -> BnbReceipt.Status.PENDING
        },
        reason = o.str("reason"),
    )

    fun trackedTrade(o: JsonObject) = TrackedTrade(
        chain = o.str("chain") ?: "bnb",
        venue = o.str("venue") ?: "bstock",
        txId = o.req("tx_id"),
        ticker = o.req("ticker"),
        symbol = o.req("symbol"),
        side = o.req("side"),
        inputSymbol = o.req("input_symbol"),
        inputAmount = o.reqDbl("input_amount"),
        outputSymbol = o.req("output_symbol"),
        outputAmount = o.reqDbl("output_amount"),
        pricePerToken = o.reqDbl("price_per_token"),
        signature = o.str("signature") ?: o.req("tx_id"),
        createdAt = o.instant("created_at") ?: Instant.EPOCH,
    )

    private fun trades(o: JsonObject, key: String) =
        o.arr(key)?.objects()?.mapNotNull { runCatching { trackedTrade(it) }.getOrNull() } ?: emptyList()

    fun portfolio(o: JsonObject) = PortfolioResponse(address = o.str("address").orEmpty(), trades = trades(o, "trades"))

    fun transactionsPage(o: JsonObject) = TransactionsPage(
        address = o.str("address").orEmpty(),
        total = o.int("total") ?: 0,
        offset = o.int("offset") ?: 0,
        limit = o.int("limit") ?: 0,
        hasMore = o.bool("has_more") ?: false,
        transactions = trades(o, "transactions"),
    )

    private fun sources(o: JsonObject) = o.arr("sources")?.objects()?.mapNotNull { s ->
        val url = s.str("url") ?: return@mapNotNull null
        AskSource(id = s.str("id") ?: url, title = s.str("title") ?: url, publisher = s.str("publisher"), url = url)
    } ?: emptyList()

    fun askResult(o: JsonObject) = AskResult(
        understood = o.bool("understood") ?: false,
        summary = o.str("summary"),
        note = o.str("note"),
        sources = sources(o),
    )

    fun askProResult(o: JsonObject) = AskProResult(
        previewType = when (o.str("preview_type")) {
            "quote_card" -> AskProResult.PreviewType.QUOTE_CARD
            "quote" -> AskProResult.PreviewType.QUOTE
            "technical" -> AskProResult.PreviewType.TECHNICAL
            else -> AskProResult.PreviewType.NONE
        },
        answer = o.str("answer").orEmpty(),
        stock = o.obj("stock")?.let { runCatching { marketItem(it) }.getOrNull() },
        trade = o.obj("trade")?.let { t -> t.str("side")?.let { AskProResult.Trade(it, t.dbl("input_amount")) } },
        // A card this build can't read still leaves the text answer.
        technical = o.obj("technical")?.let { runCatching { technical(it) }.getOrNull() },
        sources = sources(o),
    )

    private fun technical(o: JsonObject) = AskProTechnical(
        kind = o.req("kind"),
        indicator = o.str("indicator"),
        snapshot = o.obj("snapshot")?.let(::snapshot),
        liquidity = o.obj("liquidity")?.let { l ->
            TechnicalLiquidity(
                ticker = l.str("ticker").orEmpty(),
                symbol = l.str("symbol").orEmpty(),
                tiers = l.arr("tiers")?.objects()?.map {
                    TechnicalLiquidity.Tier(it.dbl("usd") ?: 0.0, it.bool("available") ?: false, it.dbl("price_impact_percent"))
                } ?: emptyList(),
                activityRatio = l.dbl("activity_ratio"),
            )
        },
        scan = o.obj("scan")?.let { s ->
            TechnicalScan(
                interval = s.str("interval").orEmpty(),
                rows = s.arr("rows")?.objects()?.map {
                    TechnicalScan.Row(
                        venue = it.str("venue").orEmpty(),
                        ticker = it.str("ticker").orEmpty(),
                        symbol = it.str("symbol"),
                        logoUrl = StockApiClient.absoluteUrl(it.str("logo_url")),
                        price = it.dbl("price"),
                        rsi = it.dbl("rsi"),
                        state = it.str("state"),
                    )
                } ?: emptyList(),
            )
        },
    )

    private fun snapshot(o: JsonObject) = TechnicalSnapshot(
        ticker = o.str("ticker").orEmpty(),
        symbol = o.str("symbol").orEmpty(),
        interval = o.str("interval").orEmpty(),
        status = o.str("status").orEmpty(),
        candleCount = o.int("candle_count") ?: 0,
        price = o.dbl("price"),
        summary = o.obj("summary")?.let { s ->
            TechnicalSnapshot.Summary(
                score = s.int("score") ?: 0,
                label = s.str("label").orEmpty(),
                signals = s.arr("signals")?.objects()?.map {
                    TechnicalSnapshot.Signal(it.str("name").orEmpty(), it.str("detail").orEmpty(), it.str("bias") ?: "neutral")
                } ?: emptyList(),
            )
        },
        rsi = o.obj("rsi")?.let { TechnicalSnapshot.Rsi(it.dbl("value") ?: 50.0, it.str("state") ?: "neutral", it.int("streak") ?: 0) },
        macd = o.obj("macd")?.let {
            TechnicalSnapshot.Macd(it.dbl("macd") ?: 0.0, it.dbl("signal") ?: 0.0, it.dbl("histogram") ?: 0.0, it.str("cross"), it.int("cross_bars_ago"))
        },
        bollinger = o.obj("bollinger")?.let {
            TechnicalSnapshot.Bollinger(
                it.dbl("upper") ?: 0.0, it.dbl("middle") ?: 0.0, it.dbl("lower") ?: 0.0,
                it.dbl("percent_b") ?: 0.0, it.dbl("bandwidth_percent") ?: 0.0, it.bool("squeeze") ?: false,
            )
        },
        levels = o.obj("levels")?.let {
            TechnicalSnapshot.Levels(
                supports = it.doubles("supports") ?: emptyList(),
                resistances = it.doubles("resistances") ?: emptyList(),
                high = it.dbl("high") ?: 0.0,
                low = it.dbl("low") ?: 0.0,
                rangePosition = it.dbl("range_position") ?: 0.5,
                since = it.instant("since") ?: Instant.now(),
            )
        },
        performance = o.obj("performance")?.let {
            TechnicalSnapshot.Performance(
                it.dbl("change_1d"), it.dbl("change_7d"), it.dbl("change_30d"), it.dbl("daily_move_percent"),
                it.doubles("sparkline") ?: emptyList(),
            )
        },
        offHours = o.obj("off_hours")?.let { h ->
            TechnicalSnapshot.OffHours(
                inProgress = h.bool("in_progress") ?: false,
                changePercent = h.dbl("change_percent"),
                fromPrice = h.dbl("from_price"),
                toPrice = h.dbl("to_price"),
                points = h.arr("points")?.objects()?.mapNotNull { p ->
                    val t = p.instant("t") ?: return@mapNotNull null
                    TechnicalSnapshot.OffHoursPoint(t, p.dbl("close") ?: return@mapNotNull null, p.bool("closed") ?: false)
                } ?: emptyList(),
            )
        },
        series = o.arr("series")?.objects()?.mapNotNull { p ->
            val t = p.instant("t") ?: return@mapNotNull null
            TechnicalSnapshot.Point(
                t = t,
                close = p.dbl("close") ?: return@mapNotNull null,
                rsi = p.dbl("rsi"),
                macd = p.dbl("macd"),
                macdSignal = p.dbl("macd_signal"),
                macdHistogram = p.dbl("macd_histogram"),
                bbUpper = p.dbl("bb_upper"),
                bbMiddle = p.dbl("bb_middle"),
                bbLower = p.dbl("bb_lower"),
                ema20 = p.dbl("ema20"),
                ema50 = p.dbl("ema50"),
            )
        } ?: emptyList(),
    )

    fun analysis(o: JsonObject): SharedContentAnalysis {
        val preview = o.obj("preview")
        return SharedContentAnalysis(
            requestedUrl = o.str("requested_url"),
            answer = o.str("answer").orEmpty(),
            stocks = o.arr("stocks")?.objects()?.mapNotNull { s ->
                val ticker = s.str("ticker") ?: return@mapNotNull null
                SharedContentAnalysis.Match(ticker, s.str("symbol") ?: ticker, s.str("name") ?: ticker)
            } ?: emptyList(),
            preview = SharedContentAnalysis.Preview(
                title = preview?.str("title"),
                description = preview?.str("description"),
                siteName = preview?.str("site_name"),
                author = preview?.str("author"),
            ),
        )
    }
}
