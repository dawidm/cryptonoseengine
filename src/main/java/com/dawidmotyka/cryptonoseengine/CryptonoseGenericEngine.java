package com.dawidmotyka.cryptonoseengine;

import com.dawidmotyka.dmutils.RepeatTillSuccess;
import com.dawidmotyka.exchangeutils.NotImplementedException;
import com.dawidmotyka.exchangeutils.chartdataprovider.ChartDataProvider;
import com.dawidmotyka.exchangeutils.chartdataprovider.ChartDataReceiver;
import com.dawidmotyka.exchangeutils.chartinfo.ChartCandle;
import com.dawidmotyka.exchangeutils.exchangespecs.ExchangeSpecs;
import com.dawidmotyka.exchangeutils.pairdataprovider.PairDataProvider;
import com.dawidmotyka.exchangeutils.pairdataprovider.PairSelectionCriteria;
import com.dawidmotyka.exchangeutils.pairsymbolconverter.PairSymbolConverter;
import com.dawidmotyka.exchangeutils.tickerprovider.Ticker;
import com.dawidmotyka.exchangeutils.tickerprovider.TickerProvider;
import com.dawidmotyka.exchangeutils.tickerprovider.TickerReceiver;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Created by dawid on 12/28/17.
 */
public class CryptonoseGenericEngine extends CryptonoseEngineBase {

    private final Logger logger = Logger.getLogger(CryptonoseGenericEngine.class.getName());

    public static final int GET_DATA_RETRY_INTERVAL=60000;

    private ExchangeSpecs exchangeSpecs;
    private List<Integer> timePeriods=new ArrayList<>(5);
    private String[] pairs;
    private PairSelectionCriteria[] pairSelectionCriteria;
    private TickerProvider tickerProvider;
    private ChartDataProvider chartDataProvider;
    private RelativeChangesChecker relativeChangesChecker;
    private CryptonoseEngineChangesChecker cryptonoseEngineChangesChecker;
    private ScheduledExecutorService scheduledExecutorService;
    private AtomicBoolean stoppedAtomicBoolean=new AtomicBoolean(false);
    private final Object refreshPairDataLock = new Object();
    private final Object startTickerEngineLock = new Object();
    private final Set<ChartDataReceiver> chartDataSubscribers=new HashSet<>();
    private int relativeChangeNumCandles;
    private AtomicInteger chartDataProviderNumCandles;
    private boolean useChartDataOpenCloseAsTicker=false;

    private CryptonoseGenericEngine(ExchangeSpecs exchangeSpecs,
                                    EngineChangesReceiver engineChangesReceiver,
                                    int[] timePeriods,
                                    int relativeChangeNumCandles,
                                    PairSelectionCriteria[] pairSelectionCriteria,
                                    String[] pairs) {
        this.exchangeSpecs=exchangeSpecs;
        Arrays.stream(timePeriods).forEach(timePeriod->this.timePeriods.add(timePeriod));
        this.relativeChangeNumCandles =relativeChangeNumCandles;
        chartDataProviderNumCandles=new AtomicInteger(relativeChangeNumCandles);
        super.engineChangesReceiver=engineChangesReceiver;
        cryptonoseEngineChangesChecker = new CryptonoseEngineChangesChecker(timePeriods);
        this.pairSelectionCriteria=pairSelectionCriteria;
        this.pairs=pairs;
    }

    public static CryptonoseGenericEngine withProvidedCurrencyPairs(ExchangeSpecs exchangeSpecs, EngineChangesReceiver engineChangesReceiver, int[] timePeriods, int relativeChangeNumCandles, String[] paris) {
        return new CryptonoseGenericEngine(exchangeSpecs,engineChangesReceiver,timePeriods,relativeChangeNumCandles,null,paris);
    }

    public static CryptonoseGenericEngine withProvidedMarkets(ExchangeSpecs exchangeSpecs, EngineChangesReceiver engineChangesReceiver, int[] timePeriods,int relativeChangeNumCandles,PairSelectionCriteria[] pairSelectionCriteria) {
        return new CryptonoseGenericEngine(exchangeSpecs,engineChangesReceiver,timePeriods,relativeChangeNumCandles,pairSelectionCriteria,null);
    }

    public static CryptonoseGenericEngine withProvidedMarketsAndPairs(ExchangeSpecs exchangeSpecs, EngineChangesReceiver engineChangesReceiver, int[] timePeriods,int relativeChangeNumCandles,PairSelectionCriteria[] pairSelectionCriteria, String[] pairs) {
        return new CryptonoseGenericEngine(exchangeSpecs,engineChangesReceiver,timePeriods,relativeChangeNumCandles,pairSelectionCriteria,pairs);
    }

    @Override
    public void start() {
        if (fetchPairsData())
            startTickerEngine();
    }

    @Override
    public void stop() {
        stopFetchPairsData();
        stopTickerEngine();
        if(scheduledExecutorService!=null)
            scheduledExecutorService.shutdown();
    }

    @Override
    public void useChartDataOpenCloseAsTicker() {
        useChartDataOpenCloseAsTicker=true;
    }

    @Override
    public void autoRefreshPairData(int intervalMinutes) {
        if(scheduledExecutorService==null || scheduledExecutorService.isShutdown())
            scheduledExecutorService= Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleWithFixedDelay(()->refresh(),intervalMinutes,intervalMinutes, TimeUnit.MINUTES);
    }

    public void requestAdditionalChartDataPeriodSeconds(int periodSeconds) {
        timePeriods.add(periodSeconds);
    }

    public void subscribeChartData(ChartDataReceiver chartDataReceiver,int minimumNumCandles) {
        chartDataSubscribers.add(chartDataReceiver);
        if(chartDataProviderNumCandles.get()<minimumNumCandles)
            chartDataProviderNumCandles.set(minimumNumCandles);
    }

    public ChartCandle[] requestCandlesGeneration(String symbol, int periodSeconds) throws IllegalArgumentException {
        if(chartDataProvider!=null)
            return chartDataProvider.requestCandlesGeneration(symbol,periodSeconds);
        else return null;
    }

    public PriceChanges[] requestAllPairsChanges() {
        ArrayList<PriceChanges> changesArrayList = new ArrayList(pairs.length*timePeriods.size());
        for(String pair : pairs)
            changesArrayList.addAll(Arrays.asList(cryptonoseEngineChangesChecker.checkChanges(pair)));
        return changesArrayList.toArray(new PriceChanges[changesArrayList.size()]);
    }

    private void refresh() {
        logger.info("Refreshing pairs data and reconnecting websocket...");
        try {
            logger.info("Canceling refresh pair data if active...");
            stopFetchPairsData();
            logger.fine("Refreshing pairs data...");
            fetchPairsData();
            stopTickerEngine();
            startTickerEngine();
        } catch (Exception e) {
            logger.log(Level.SEVERE,"when restarting ticker engine",e);
        }
    }

    private boolean fetchPairsData() {
        synchronized (refreshPairDataLock) {
            stoppedAtomicBoolean.set(false);
            if (pairSelectionCriteria!=null) {
                engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.INFO, "Getting currency pairs..."));
                RepeatTillSuccess.planTask(this::getPairs, (e) -> logger.log(Level.WARNING, "when getting currency pairs", e), GET_DATA_RETRY_INTERVAL);
                if(pairs.length>0)
                    engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.INFO, "Got currency pairs"));
                else {
                    engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.NO_PAIRS, "Got 0 currency pairs"));
                    return false;
                }
            }
            engineMessageReceiver.message(
                    new EngineMessage(
                            EngineMessage.Type.INFO,
                            String.format("Selected %d pairs: %s",
                                    pairs.length,
                                    Arrays.stream(pairs).
                                            map(pair-> PairSymbolConverter.toFormattedString(exchangeSpecs,pair)).
                                            collect(Collectors.joining(", "))
                            )
                    )
            );
            if (stoppedAtomicBoolean.get())
                return false;
            engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.INFO, "Getting chart data..."));
            int[] timePeriodsArray = timePeriods.stream().mapToInt(timePeriod -> timePeriod.intValue()).toArray();
            chartDataProvider = new ChartDataProvider(exchangeSpecs, pairs, timePeriodsArray);
            chartDataProvider.enableCandlesGenerator();
            for (ChartDataReceiver currentChartDataSubscriber : chartDataSubscribers)
                chartDataProvider.subscribeChartCandles(currentChartDataSubscriber, chartDataProviderNumCandles.get());
            if(useChartDataOpenCloseAsTicker)
                enableUseLastCandleOpenCloseAsTicker();
            relativeChangesChecker = new RelativeChangesChecker(chartDataProvider, relativeChangeNumCandles);
        }
        RepeatTillSuccess.planTask(() -> chartDataProvider.refreshData(), (e) -> {
            engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.INFO, "Error getting chart data"));
            logger.log(Level.WARNING, "when getting chart data", e);
        }, GET_DATA_RETRY_INTERVAL);
        if (stoppedAtomicBoolean.get())
            return false;
        engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.INFO, "Successfully fetched chart data"));
        return true;
    }

    private void startTickerEngine() {
        synchronized (startTickerEngineLock) {
            if(stoppedAtomicBoolean.get())
                return;
            engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.INFO, "Starting ticker engine..."));
            tickerProvider = TickerProvider.forExchange(exchangeSpecs, new TickerReceiver() {
                @Override
                public void receiveTicker(Ticker ticker) {
                    handleTicker(ticker);
                }
                @Override
                public void receiveTickers(List<Ticker> tickers) { handleTickers(tickers); }
                @Override
                public void error(Throwable error) {
                    handleError(error);
                }
            }, pairs);
            RepeatTillSuccess.planTask(() -> tickerProvider.connect(), t -> logger.log(Level.WARNING, "when connecting ticker engine", t), GET_DATA_RETRY_INTERVAL);
            engineMessageReceiver.message(new EngineMessage(EngineMessage.Type.CONNECTED, "Connected"));
        }
    }

    private void stopFetchPairsData() {
        stoppedAtomicBoolean.set(true);
        synchronized (refreshPairDataLock) {
            if (chartDataProvider != null) {
                logger.info("aborting ChartDataProvider");
                chartDataProvider.abort();
            }
        }
    }

    private void stopTickerEngine() {
        synchronized (startTickerEngineLock) {
            if (tickerProvider != null) {
                logger.info("disconnecting ticker engine...");
                tickerProvider.disconnect();
                tickerProvider=null;
            }
        }
    }

    private void enableUseLastCandleOpenCloseAsTicker() {
        chartDataProvider.subscribeChartCandles((chartCandlesMap) -> {
            if(!cryptonoseEngineChangesChecker.hasntReceivedTickersYet())
                return;
            logger.fine("using open close prices from last chart candle");
            for (String pair : pairs)
                for (int timePeriod : timePeriods) {
                    ChartCandle[] candles = chartDataProvider.getCandleData(pair, timePeriod);
                    if(candles==null || candles.length<1) {
                        logger.warning(String.format("no candles for %s (%ds)", pair, timePeriod));
                    } else {
                        ChartCandle lastCandle = candles[candles.length - 1];
                        if ((System.currentTimeMillis() / 1000 - lastCandle.getTimestampSeconds()) < timePeriod) {
                            logger.fine(String.format("inserting %s %s (%ds) candle open and close to changes checker", new Date(lastCandle.getTimestampSeconds() * 1000).toString(), pair, timePeriod));
                            cryptonoseEngineChangesChecker.insertTicker(new Ticker(pair, lastCandle.getOpen(), lastCandle.getTimestampSeconds()));
                            cryptonoseEngineChangesChecker.insertTicker(new Ticker(pair, lastCandle.getClose(), System.currentTimeMillis() / 1000));
                        }
                    }
                }
        },1);
    }

    private void getPairs() throws IOException {
        try {
            Set<String> pairsArraySet = new HashSet<>();
            if(pairs!=null)
                pairsArraySet.addAll(Arrays.asList(pairs));
            PairDataProvider pairDataProvider = PairDataProvider.forExchange(exchangeSpecs);
            pairsArraySet.addAll(Arrays.asList(pairDataProvider.getPairsApiSymbols(pairSelectionCriteria)));
            pairs=pairsArraySet.toArray(new String[pairsArraySet.size()]);
        } catch (NotImplementedException e) {
            logger.log(Level.SEVERE,"when getting pairs",e);
        }
    }

    private synchronized void handleTicker(Ticker ticker) {
        logger.finest(String.format("received ticker %s",ticker.getPair()));
        chartDataProvider.insertTicker(ticker);
        if (engineUpdateHeartbeatReceiver != null)
            engineUpdateHeartbeatReceiver.receiveTransactionHeartbeat();
        PriceChanges[] priceChanges = cryptonoseEngineChangesChecker.checkChanges(ticker);
        if(relativeChangesChecker!=null)
            relativeChangesChecker.setRelativeChanges(priceChanges);
        engineChangesReceiver.receiveChanges(Arrays.asList(priceChanges));
    }

    //Checking changes after inserting all tickers. This is for checking changes after big buys/sells that get divided to multiple exchange transactions.
    private synchronized void handleTickers(List<Ticker> tickers) {
        logger.finest(String.format("received %d tickers",tickers.size()));
        for(Ticker ticker : tickers)
            chartDataProvider.insertTicker(ticker);
        if (engineUpdateHeartbeatReceiver != null)
            engineUpdateHeartbeatReceiver.receiveTransactionHeartbeat();
        tickers.stream().forEach(ticker -> cryptonoseEngineChangesChecker.insertTicker(ticker));
        PriceChanges[] priceChanges = cryptonoseEngineChangesChecker.checkChanges(tickers.get(0).getPair());
        if(relativeChangesChecker!=null)
            relativeChangesChecker.setRelativeChanges(priceChanges);
        engineChangesReceiver.receiveChanges(Arrays.asList(priceChanges));
    }

    private void handleError(Throwable error) {
        logger.log(Level.WARNING,"TickerProvider error",error);
    }

}
