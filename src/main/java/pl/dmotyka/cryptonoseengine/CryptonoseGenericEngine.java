/*
 * Cryptonose
 *
 * Copyright © 2019-2020 Dawid Motyka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package pl.dmotyka.cryptonoseengine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.dawidmotyka.dmutils.runtime.RepeatTillSuccess;
import pl.dmotyka.exchangeutils.chartdataprovider.ChartDataProvider;
import pl.dmotyka.exchangeutils.chartdataprovider.ChartDataReceiver;
import pl.dmotyka.exchangeutils.chartdataprovider.CurrencyPairTimePeriod;
import pl.dmotyka.exchangeutils.chartdataprovider.PeriodNumCandles;
import pl.dmotyka.exchangeutils.chartinfo.ChartCandle;
import pl.dmotyka.exchangeutils.chartinfo.ChartTimePeriod;
import pl.dmotyka.exchangeutils.exchangespecs.ExchangeSpecs;
import pl.dmotyka.exchangeutils.pairdataprovider.PairDataProvider;
import pl.dmotyka.exchangeutils.pairdataprovider.PairSelectionCriteria;
import pl.dmotyka.exchangeutils.pairsymbolconverter.PairSymbolConverter;
import pl.dmotyka.exchangeutils.tickerprovider.Ticker;
import pl.dmotyka.exchangeutils.tickerprovider.TickerProvider;
import pl.dmotyka.exchangeutils.tickerprovider.TickerReceiver;

/**
 * Created by dawid on 12/28/17.
 */
public class CryptonoseGenericEngine {

    private final Logger logger = Logger.getLogger(CryptonoseGenericEngine.class.getName());

    public static final int GET_DATA_RETRY_INTERVAL=60000;

    private final ExchangeSpecs exchangeSpecs;
    private final PairSymbolConverter pairSymbolConverter;
    private final EngineChangesReceiver engineChangesReceiver;
    private EngineTransactionHeartbeatReceiver engineUpdateHeartbeatReceiver;
    private EngineMessageReceiver engineMessageReceiver;
    private final Set<ChartDataReceiver> chartDataSubscribers=new HashSet<>();
    private final List<PeriodNumCandles> periodsNumCandles;
    private final int relativeChangeNumCandles;
    private final PairSelectionCriteria[] pairSelectionCriteria;
    private Integer refreshIntervalMinutes = null;
    private final String[] pairsManual;
    private boolean initEngineWithLowerPeriodChartData=false;
    private int checkChangesDelayMs = 0;

    private String[] pairsAll;

    private TickerProvider tickerProvider;
    private ChartDataProvider chartDataProvider;
    private ChartDataProvider chartDataProviderInitEngine;
    private RelativeChangesChecker relativeChangesChecker;
    private final CryptonoseEngineChangesChecker cryptonoseEngineChangesChecker;
    private final ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> refreshScheduledFuture;
    private final Set<String> changesCheckingTempDisableSet = Collections.synchronizedSet(new HashSet<>());


    private final ReentrantLock fetchPairDataLock = new ReentrantLock();
    private final ReentrantLock startTickerEngineLock = new ReentrantLock();
    // is refreshing (reconnecting when connection was previously active)
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    // silent refresh doesn't send connected and disconnected messages to message receiver
    private final AtomicBoolean silentRefresh = new AtomicBoolean(false);
    // to discriminate between ticker reconnecting on it's own and by a request
    private final AtomicBoolean isWaitingForTickerConnection = new AtomicBoolean(false);
    // stopped means than engine was already started, and then stop was requested
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    // started means than engine start was requested (which is possible only once)
    private final AtomicBoolean started = new AtomicBoolean(false);

    private CryptonoseGenericEngine(ExchangeSpecs exchangeSpecs,
                                    EngineChangesReceiver engineChangesReceiver,
                                    long[] timePeriods,
                                    int relativeChangeNumCandles,
                                    PairSelectionCriteria[] pairSelectionCriteria,
                                    String[] pairs) {
        if (timePeriods.length < 1)
            throw new IllegalArgumentException("empty timePeriods");
        this.exchangeSpecs = exchangeSpecs;
        pairSymbolConverter = exchangeSpecs.getPairSymbolConverter();
        periodsNumCandles = Arrays.stream(timePeriods).mapToObj(timePeriod -> new PeriodNumCandles(timePeriod, relativeChangeNumCandles)).collect(Collectors.toList());
        this.relativeChangeNumCandles=relativeChangeNumCandles;
        this.engineChangesReceiver=engineChangesReceiver;
        cryptonoseEngineChangesChecker = new CryptonoseEngineChangesChecker(timePeriods);
        this.pairSelectionCriteria=pairSelectionCriteria;
        this.pairsManual=pairs;
        scheduledExecutorService= Executors.newScheduledThreadPool(10);
    }

    public static CryptonoseGenericEngine withProvidedCurrencyPairs(ExchangeSpecs exchangeSpecs, EngineChangesReceiver engineChangesReceiver, long[] timePeriods, int relativeChangeNumCandles, String[] paris) {
        return new CryptonoseGenericEngine(exchangeSpecs,engineChangesReceiver,timePeriods,relativeChangeNumCandles,null,paris);
    }

    public static CryptonoseGenericEngine withProvidedMarkets(ExchangeSpecs exchangeSpecs, EngineChangesReceiver engineChangesReceiver, long[] timePeriods,int relativeChangeNumCandles,PairSelectionCriteria[] pairSelectionCriteria) {
        return new CryptonoseGenericEngine(exchangeSpecs,engineChangesReceiver,timePeriods,relativeChangeNumCandles,pairSelectionCriteria,null);
    }

    public static CryptonoseGenericEngine withProvidedMarketsAndPairs(ExchangeSpecs exchangeSpecs, EngineChangesReceiver engineChangesReceiver, long[] timePeriods,int relativeChangeNumCandles,PairSelectionCriteria[] pairSelectionCriteria, String[] pairs) {
        return new CryptonoseGenericEngine(exchangeSpecs,engineChangesReceiver,timePeriods,relativeChangeNumCandles,pairSelectionCriteria,pairs);
    }

    public static ChartTimePeriod[] getAvailableTimePeriods(ExchangeSpecs exchangeSpecs) {
        return ChartDataProvider.getAvailableTimePeriods(exchangeSpecs);
    };

    public void setEngineMessageReceiver(EngineMessageReceiver engineMessageReceiver) {
        this.engineMessageReceiver=engineMessageReceiver;
    }

    public void setEngineUpdateHeartbeatReceiver(EngineTransactionHeartbeatReceiver engineUpdateHeartbeatReceiver) {
        this.engineUpdateHeartbeatReceiver = engineUpdateHeartbeatReceiver;
    }

    public void setCheckChangesDelayMs(int checkChangesDelayMs) {
        this.checkChangesDelayMs = checkChangesDelayMs;
    }

    // should be called before starting engine
    public void autoRefreshPairData(int intervalMinutes) {
        if (started.get())
            throw new RuntimeException("Should be called before starting engine");
        this.refreshIntervalMinutes = intervalMinutes;
    }

    //should be called before starting engine
    public void enableInitEngineWithLowerPeriodChartData() {
        if (started.get())
            throw new RuntimeException("Should be called before starting engine");
        this.initEngineWithLowerPeriodChartData=true;
    }

    //should be called before starting engine
    public void requestAdditionalChartData(PeriodNumCandles periodNumCandles) {
        if (started.get())
            throw new RuntimeException("Should be called before starting engine");
        periodsNumCandles.add(periodNumCandles);
    }

    // start the engine
    // call only once, use reconnect() for reconnections
    public void start() {
        if (started.get())
            throw new IllegalStateException("Engine can be started once");
        if ((pairSelectionCriteria == null || pairSelectionCriteria.length==0) && pairsManual.length==0) {
            engineMessage(new EngineMessage(EngineMessage.Type.NO_PAIRS, "Got 0 currency pairs"));
            return;
        }
        started.set(true);
        engineMessage(new EngineMessage(EngineMessage.Type.CONNECTING, "Connecting..."));
        if (fetchPairsData())
            startTickerProvider();
        if (refreshIntervalMinutes != null && !stopped.get()) {
            refreshScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                engineMessage(new EngineMessage(EngineMessage.Type.AUTO_REFRESHING, "Auto refreshing paris data..."));
                refresh(false, true);
            }, refreshIntervalMinutes, refreshIntervalMinutes, TimeUnit.MINUTES);
        }
    }

    // stop the engine
    // call only once, use reconnect() for reconnections
    public void stop() {
        stopped.set(true);
        if (refreshScheduledFuture != null)
            refreshScheduledFuture.cancel(false);
        scheduledExecutorService.shutdown();
        stopFetchPairsData();
        stopTickerEngine();
    }

    // get all currency pairs (will return null if engine is before getting pairs data)
    public String[] getAllPairs() {
        return pairsAll;
    }

    // subscribe chart data for pairs specified for the engine
    public void subscribeChartData(ChartDataReceiver chartDataReceiver) {
        chartDataSubscribers.add(chartDataReceiver);
    }

    // request the up-to-date chart candles for specified criteria
    public ChartCandle[] requestCandlesGeneration(CurrencyPairTimePeriod currencyPairTimePeriod) throws IllegalArgumentException {
        if(chartDataProvider!=null)
            return chartDataProvider.requestCandlesGeneration(currencyPairTimePeriod);
        else return null;
    }

    // get chart candles for specified criteria
    // it will not include the most recent candle which isn't closed yet
    public ChartCandle[] getCandleData(CurrencyPairTimePeriod currencyPairTimePeriod) {
        return chartDataProvider.getCandleData(currencyPairTimePeriod);
    }

    // get price changes for all pairs for pairs specified in engine
    // this should be called when engine is connected and working
    public PriceChanges[] requestAllPairsChanges() {
        if (pairsAll == null)
            return new PriceChanges[] {};
        ArrayList<PriceChanges> changesArrayList = new ArrayList(pairsAll.length*periodsNumCandles.size());
        for(String pair : pairsAll) {
            PriceChanges[] priceChanges = cryptonoseEngineChangesChecker.checkChanges(pair);
            if(priceChanges==null)
                continue;
            if(relativeChangesChecker!=null)
                relativeChangesChecker.setRelativeChanges(priceChanges);
            changesArrayList.addAll(Arrays.asList(priceChanges));
        }
        return changesArrayList.toArray(new PriceChanges[changesArrayList.size()]);
    }

    // reconnect the engine
    public void reconnect() {
        refresh(true, false);
    }

    // stopTickerFirst - stop ticker connection before getting initial pairs data
    // silent - don't send engine messages: connected/disconnected
    private void refresh(boolean stopTickerFirst, boolean silent) {
        if (!isRefreshing.get() && !startTickerEngineLock.isLocked() && !fetchPairDataLock.isLocked()) {
            silentRefresh.set(silent);
            isRefreshing.set(true);
            engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.RECONNECTING, "reconnecting"));
            if (stopTickerFirst)
                stopTickerEngine();
            logger.info("Refreshing pairs data and reconnecting websocket...");
            try {
                logger.info("Canceling refresh pair data if active...");
                stopFetchPairsData();
                logger.fine("Refreshing pairs data...");
                fetchPairsData();
                if (!stopTickerFirst)
                    stopTickerEngine();
                startTickerProvider();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "when restarting ticker provider", e);
            }
        }
    }

    // get pairs data - pair names and chart candles for them
    // when fetching data is already in progress, method does nothing
    private boolean fetchPairsData() {
        if (fetchPairDataLock.isLocked()) {
            logger.warning("fetching pairs data already in progress");
            return false;
        }
        fetchPairDataLock.lock();
        try {
            if (pairSelectionCriteria != null) {
                engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Getting currency pairs..."));
                RepeatTillSuccess.planTask(this::getPairs, (e) -> logger.log(Level.WARNING, "when getting currency pairs", e), GET_DATA_RETRY_INTERVAL);
                if (pairsAll.length > 0)
                    engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Got currency pairs"));
                else {
                    engineMessage(new EngineMessage(EngineMessage.Type.NO_PAIRS, "Got 0 currency pairs"));
                    return false;
                }
            }
            engineMessage(
                    new EngineMessage(
                            EngineMessage.Type.INFO,
                            String.format("Selected %d pairs: %s",
                                    pairsAll.length,
                                    Arrays.stream(pairsAll).
                                            map(pair -> pairSymbolConverter.toFormattedString(pair)).
                                                  collect(Collectors.joining(", "))
                            )
                    )
            );
            if (stopped.get())
                return false;
            engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Getting chart data..."));
            chartDataProvider = new ChartDataProvider(exchangeSpecs, pairsAll, periodsNumCandles.toArray(new PeriodNumCandles[periodsNumCandles.size()]));
            chartDataProvider.enableCandlesGenerator();
            for (ChartDataReceiver currentChartDataSubscriber : chartDataSubscribers) {
                chartDataProvider.subscribeChartCandles(currentChartDataSubscriber);
            }
            relativeChangesChecker = new RelativeChangesChecker(chartDataProvider, relativeChangeNumCandles);
            RepeatTillSuccess.planTask(() -> chartDataProvider.refreshData(), (e) -> {
                engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Error getting chart data"));
                logger.log(Level.WARNING, "when getting chart data", e);
            }, GET_DATA_RETRY_INTERVAL);
            if (stopped.get())
                return false;
            engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Successfully fetched chart data"));
            if (initEngineWithLowerPeriodChartData) {
                PeriodNumCandles additionalPeriodNumCandles = checkGenTickersFromChartData();
                if (additionalPeriodNumCandles != null) {
                    engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Getting additional chart data..."));
                    chartDataProviderInitEngine = new ChartDataProvider(exchangeSpecs, pairsAll, new PeriodNumCandles[] {additionalPeriodNumCandles});
                    chartDataProviderInitEngine.subscribeChartCandles(chartCandlesMap -> handleAdditionalChartData(chartCandlesMap));
                    RepeatTillSuccess.planTask(() -> chartDataProviderInitEngine.refreshData(), (e) -> {
                        engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Error getting additional chart data"));
                        logger.log(Level.WARNING, "when getting chart data", e);
                    }, GET_DATA_RETRY_INTERVAL);
                } else {
                    logger.fine("using lowest period chart data as tickers");
                    useLowestPeriodCandlesAsTickers();
                }
                if (stopped.get())
                    return false;
                engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Successfully fetched additional chart data"));
            }
            return true;
        }
        finally {
            fetchPairDataLock.unlock();
        }
    }

    // connect ticker provider to start receiving tickers (called after fetching pairs data)
    // when starting provider is in progress, method does nothing
    private void startTickerProvider() {
        if (startTickerEngineLock.isLocked()) {
            logger.warning("ticker provider is already starting");
            return;
        }
        startTickerEngineLock.lock();
        try {
            if (stopped.get())
                return;
            engineMessage(new EngineMessage(EngineMessage.Type.INFO, "Starting ticker provider..."));
            tickerProvider = exchangeSpecs.getTickerProvider(new TickerReceiver() {
                @Override
                public void receiveTicker(Ticker ticker) {
                    handleTicker(ticker, false);
                }

                @Override
                public void receiveTickers(Ticker[] tickers) {
                    handleTickers(tickers, false);
                }

                @Override
                public void error(Throwable error) {
                    handleError(error);
                }
            }, pairsAll);
            RepeatTillSuccess.planTask(() -> {
                tickerProvider.connect(tickerProviderConnectionState -> {
                    switch (tickerProviderConnectionState) {
                        case CONNECTED:
                            if (isRefreshing.get()) {
                                isRefreshing.set(false);
                                engineMessage(new EngineMessage(EngineMessage.Type.AUTO_REFRESHING_DONE, "Engine refresh done"));
                                if (!silentRefresh.get()) {
                                    engineMessage(new EngineMessage(EngineMessage.Type.CONNECTED, "Connected"));
                                }
                            } else
                                engineMessage(new EngineMessage(EngineMessage.Type.CONNECTED, "Connected"));
                            break;
                        case DISCONNECTED:
                            if (!isRefreshing.get()) {
                                engineMessage(new EngineMessage(EngineMessage.Type.DISCONNECTED, "Disconnected"));
                            }
                            break;
                        case RECONNECTING:
                            if (isRefreshing.get()) {
                                tickerProvider.disconnect();
                            }
                            break;
                    }
                });
            }, t -> logger.log(Level.WARNING, "when connecting ticker provider", t), GET_DATA_RETRY_INTERVAL);
        }
        finally {
            startTickerEngineLock.unlock();
        }
    }

    private void stopFetchPairsData() {
        if (chartDataProvider != null) {
            logger.info("aborting ChartDataProvider");
            chartDataProvider.abort();
        }
        if (chartDataProviderInitEngine != null) {
            logger.info("aborting ChartDataProviderInitEngine");
            chartDataProviderInitEngine.abort();
        }
    }

    private void stopTickerEngine() {
        synchronized (startTickerEngineLock) {
            if (tickerProvider != null) {
                logger.info("disconnecting ticker provider...");
                tickerProvider.disconnect();
            }
        }
    }

    // get pair names according to criteria specified in pairSelectionCriteria
    private void getPairs() throws IOException {
        Set<String> pairsArraySet = new HashSet<>();
        if(pairsManual!=null)
            pairsArraySet.addAll(Arrays.asList(pairsManual));
        PairDataProvider pairDataProvider = exchangeSpecs.getPairDataProvider();
        pairsArraySet.addAll(Arrays.asList(pairDataProvider.getPairsApiSymbols(pairSelectionCriteria)));
        pairsAll=pairsArraySet.toArray(new String[pairsArraySet.size()]);
    }

    // check if for used exchange, lower period chart data (than engine time periods) could be used to generate "tickers"
    //  such "tickers" are used as initial data about recent price changes
    // returns PeriodNumCandles if tickers can be generated, otherwise null
    private PeriodNumCandles checkGenTickersFromChartData() {
        long minPeriod = periodsNumCandles.stream().mapToLong(PeriodNumCandles::getPeriodSeconds).min().getAsLong();
        long maxPeriod = periodsNumCandles.stream().mapToLong(PeriodNumCandles::getPeriodSeconds).max().getAsLong();
        int minAvailableExchangePeriod = Arrays.stream(exchangeSpecs.getChartInfo().getAvailablePeriods()).
                mapToInt(chartTimePeriod -> (int)chartTimePeriod.getPeriodLengthSeconds()).
                min().getAsInt();
        double MAX_MULTIPLIER = 1440;
        if(((double)minPeriod)/minAvailableExchangePeriod < MAX_MULTIPLIER && minPeriod!=minAvailableExchangePeriod) {
            int numCandles = (int)(maxPeriod/minAvailableExchangePeriod);
            return new PeriodNumCandles(minAvailableExchangePeriod,numCandles);
        }
        return null;
    }

    // if multiple time intervals are used, use lower intervals chart data to generate "tickers"
    // see handleAdditionalChartData()
    private void useLowestPeriodCandlesAsTickers() {
        long minTimePeriod = periodsNumCandles.stream().mapToLong(PeriodNumCandles::getPeriodSeconds).min().getAsLong();
        handleAdditionalChartData(chartDataProvider.getAllCandleDataForPeriod(minTimePeriod));
    }

    // Uses chart candles of lower periods than engine periods,
    // converts close prices from lower periods to "tickers" and use them to calculate price changes.
    // Should be used before starting ticker engine.
    private void handleAdditionalChartData(Map<CurrencyPairTimePeriod,ChartCandle[]> chartCandlesMap) {
        long maxTimePeriod = periodsNumCandles.stream().mapToLong(PeriodNumCandles::getPeriodSeconds).max().getAsLong();
        chartCandlesMap.entrySet().stream().forEach(entry-> {
            CurrencyPairTimePeriod currencyPairTimePeriod = entry.getKey();
            ChartCandle[] chartCandles = entry.getValue();
            List<Ticker> tickersList = new ArrayList<>(2 * chartCandles.length);
            Arrays.stream(chartCandles).forEach(chartCandle -> {
                int currentTimestampSeconds=(int)(System.currentTimeMillis()/1000);
                if(chartCandle.getTimestampSeconds()>currentTimestampSeconds-maxTimePeriod) {
                    tickersList.add(new Ticker(currencyPairTimePeriod.getCurrencyPairSymbol(), chartCandle.getOpen(), chartCandle.getTimestampSeconds()));
                    tickersList.add(new Ticker(currencyPairTimePeriod.getCurrencyPairSymbol(), chartCandle.getClose(), chartCandle.getTimestampSeconds() + currencyPairTimePeriod.getTimePeriodSeconds()));
                }
            });
            if (tickersList.size() > 0)
                handleTickers(tickersList.toArray(new Ticker[0]), true);
        });
    }

    // isInitTicker - set true for tickers created at initialization (not send by ticker provider),
    //  engine update heartbeat wouldn't be sent and tickers wouldn't be sent to chart data provider
    private void handleTicker(Ticker ticker, boolean isInitTicker) {
        logger.finest(String.format("received ticker %s",ticker.getPair()));
        if (!isInitTicker) {
            chartDataProvider.insertTicker(ticker);
            if (engineUpdateHeartbeatReceiver != null)
                engineUpdateHeartbeatReceiver.receiveTransactionHeartbeat();
        }
        cryptonoseEngineChangesChecker.insertTicker(ticker);
        if (checkChangesDelayMs > 0 && !changesCheckingTempDisableSet.contains(ticker.getPair())) {
            changesCheckingTempDisableSet.add(ticker.getPair());
            scheduledExecutorService.schedule(() -> {
                changesCheckingTempDisableSet.remove(ticker.getPair());
                checkChangesForPair(ticker.getPair());
            }, checkChangesDelayMs, TimeUnit.MILLISECONDS);
        } else
            checkChangesForPair(ticker.getPair());
    }

    // Checking changes after inserting multiple tickers FOR ONE CURRENCY PAIR. This is for checking changes after big buys/sells that get split to multiple exchange transactions.
    //
    // areInitTickers - set true for tickers created at initialization (not send by ticker provider),
    //  engine update heartbeat wouldn't be sent and tickers wouldn't be sent to chart data provider
    private void handleTickers(Ticker[] tickers, boolean areInitTickers) {
        logger.finest(String.format("received %d tickers",tickers.length));
        if (!areInitTickers) {
            for (Ticker ticker : tickers)
                chartDataProvider.insertTicker(ticker);
            if (engineUpdateHeartbeatReceiver != null)
                engineUpdateHeartbeatReceiver.receiveTransactionHeartbeat();
        }
        Arrays.stream(tickers).forEach(ticker -> cryptonoseEngineChangesChecker.insertTicker(ticker));
        if (checkChangesDelayMs > 0 && !changesCheckingTempDisableSet.contains(tickers[0].getPair())) {
            changesCheckingTempDisableSet.add(tickers[0].getPair());
            scheduledExecutorService.schedule(() -> {
                changesCheckingTempDisableSet.remove(tickers[0].getPair());
                checkChangesForPair(tickers[0].getPair());
            }, checkChangesDelayMs, TimeUnit.MILLISECONDS);
        } else
            checkChangesForPair(tickers[0].getPair());
    }

    private void checkChangesForPair(String pair) {
        PriceChanges[] priceChanges = cryptonoseEngineChangesChecker.checkChanges(pair);
        if(relativeChangesChecker!=null)
            relativeChangesChecker.setRelativeChanges(priceChanges);
        engineChangesReceiver.receiveChanges(Arrays.asList(priceChanges));
    }

    private void handleError(Throwable error) {
        logger.log(Level.WARNING,"TickerProvider error",error);
    }

    private void engineMessage(EngineMessage msg) {
        if (engineMessageReceiver != null)
            engineMessageReceiver.message(msg);
    }
}
