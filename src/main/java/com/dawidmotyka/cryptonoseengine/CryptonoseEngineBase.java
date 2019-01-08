/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dawidmotyka.cryptonoseengine;

import com.dawidmotyka.exchangeutils.chartdataprovider.ChartDataReceiver;
import com.dawidmotyka.exchangeutils.chartinfo.ChartCandle;

/**
 * @author dawid
 */
public abstract class CryptonoseEngineBase {

    protected EngineChangesReceiver engineChangesReceiver;
    protected EngineTransactionHeartbeatReceiver engineUpdateHeartbeatReceiver;
    protected EngineMessageReceiver engineMessageReceiver;

    public void setEngineUpdateHeartbeatReceiver(EngineTransactionHeartbeatReceiver engineUpdateHeartbeatReceiver) {
        this.engineUpdateHeartbeatReceiver = engineUpdateHeartbeatReceiver;
    }

    public void setEngineMessageReceiver(EngineMessageReceiver engineMessageReceiver) {
        this.engineMessageReceiver=engineMessageReceiver;
    }

    public abstract void start();
    public abstract void stop();
    public abstract void useChartDataOpenCloseAsTicker();
    public abstract void autoRefreshPairData(int intervalMinutes);
    public abstract void subscribeChartData(ChartDataReceiver chartDataReceiver, int numCandles);
    public abstract void requestAdditionalChartDataPeriodSeconds(int periodsSeconds);
    public abstract ChartCandle[] requestCandlesGeneration(String symbol, int periodSeconds) throws IllegalArgumentException;
}
