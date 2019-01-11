package com.dawidmotyka.cryptonoseengine;

import com.dawidmotyka.exchangeutils.chartdataprovider.ChartDataReceiver;
import com.dawidmotyka.exchangeutils.chartinfo.ChartCandle;
import com.dawidmotyka.exchangeutils.hitbtc.HitBtcDataProvider;
import com.dawidmotyka.exchangeutils.hitbtc.HitBtcWebSocket;
import com.dawidmotyka.exchangeutils.hitbtc.TransactionReceiver;
import com.dawidmotyka.exchangeutils.poloniex.Transaction;
import com.dawidmotyka.exchangeutils.tickerprovider.Ticker;

import java.util.Arrays;

/**
 * Created by dawid on 9/29/17.
 */
public class CryptonoseHitBtcWebSocketEngine extends CryptonoseEngineBase implements TransactionReceiver {

    private final double minVolume;
    private HitBtcWebSocket hitBtcWebSocket;
    private final HitBtcDataProvider hitBtcDataProvider = new HitBtcDataProvider();
    private final CryptonoseEngineChangesChecker cryptonoseEngineChangesChecker;
    private EngineChangesReceiver engineChangesReceiver;
    private EngineTransactionHeartbeatReceiver engineTransactionHeartbeatReceiver;
    private EngineMessageReceiver engineMessageReceiver;

    public CryptonoseHitBtcWebSocketEngine(int[] periods, double minVolume) {
        cryptonoseEngineChangesChecker = new CryptonoseEngineChangesChecker(periods);
        this.minVolume = minVolume;
    }

    public void start() {
        try {
            engineMessageReceiver.message(new EngineMessage(EngineMessage.INFO, "Getting pair volumes..."));
            String pairs[] = Arrays.stream(hitBtcDataProvider.getSimplePairInfos()).
                    filter((simplePairInfo -> simplePairInfo.getVolume() > minVolume && simplePairInfo.getPairApiSymbol().contains("/BTC"))).
                    map((simplePairInfo -> simplePairInfo.getPairApiSymbol())).
                    toArray(String[]::new);
            engineMessageReceiver.message(new EngineMessage(EngineMessage.INFO, String.format("%d pairs satisfy the volume condition.", pairs.length)));
            hitBtcWebSocket = new HitBtcWebSocket(pairs, this);
            hitBtcWebSocket.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void stop() {
        hitBtcWebSocket.disconnect("requested disconnect");
    }

    private void sendEngineMessage(EngineMessage msg) {
        if (engineMessageReceiver != null)
            engineMessageReceiver.message(msg);
    }

    @Override
    public void receive(Transaction transaction) {
        PriceChanges[] priceChangesArray = cryptonoseEngineChangesChecker.checkChanges(new Ticker(transaction.getPairName(), transaction.getRate(), transaction.getDate().getTime() / 1000));
        engineChangesReceiver.receiveChanges(Arrays.asList(priceChangesArray));
    }

    @Override
    public void notifyCheckChanges(String pair) {

    }

    @Override
    public void heartbeat() {
        if (engineTransactionHeartbeatReceiver != null)
            engineTransactionHeartbeatReceiver.receiveTransactionHeartbeat();
    }

    @Override
    public void wsConnected() {
        sendEngineMessage(new EngineMessage(EngineMessage.CONNECTED, "WebSocket connected"));
    }

    @Override
    public void wsDisconnected(int code, String reason, boolean remote) {
        sendEngineMessage(new EngineMessage(EngineMessage.DISCONNECTED, "WebSocket disconnected: " + reason));
    }

    @Override
    public void wsException(Exception e) {
        sendEngineMessage(new EngineMessage(EngineMessage.ERROR, e.getLocalizedMessage()));
    }

    @Override
    public void setEngineUpdateHeartbeatReceiver(EngineTransactionHeartbeatReceiver engineUpdateHeartbeatReceiver) {
        this.engineTransactionHeartbeatReceiver = engineUpdateHeartbeatReceiver;
    }

    @Override
    public void setEngineMessageReceiver(EngineMessageReceiver engineMessageReceiver) {
        this.engineMessageReceiver = engineMessageReceiver;
    }

    @Override
    public void autoRefreshPairData(int intervalMinutes) {
        throw new Error("not implemented");
    }

    @Override
    public void subscribeChartData(ChartDataReceiver chartDataReceiver, int numCandles) {
        throw new Error("not implemented");
    }

    @Override
    public void useChartDataOpenCloseAsTicker() {
        throw new Error("not implemented");
    }

    @Override
    public ChartCandle[] requestCandlesGeneration(String symbol, int periodSeconds) throws IllegalArgumentException {
        throw new Error("not implemented");
    }

    @Override
    public void requestAdditionalChartDataPeriodSeconds(int periodsSeconds) {
        throw new Error("not implemented");
    }
}