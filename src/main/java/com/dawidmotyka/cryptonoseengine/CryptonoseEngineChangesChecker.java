package com.dawidmotyka.cryptonoseengine;

import com.dawidmotyka.exchangeutils.tickerprovider.Ticker;

import java.util.*;
import java.util.logging.Logger;

/**
 * Created by dawid on 9/29/17.
 */
public class CryptonoseEngineChangesChecker {

    private static final Logger logger = Logger.getLogger(CryptonoseEngineChangesChecker.class.getName());

    private int timePeriods[];
    private Integer timeframeMultipler=1;
    private Map<String, List<Ticker>> tickersMap = new HashMap<>();

    public CryptonoseEngineChangesChecker(int[] timePeriods) {
        this.timePeriods = timePeriods;
    }

    //TODO optimize searching for min max every ticker insert
    public PriceChanges[] checkChanges(Ticker ticker) {
        insertTicker(ticker);
        return checkChanges(ticker.getPair());
    }

    public void insertTicker(Ticker ticker) {
        logger.finest(String.format("inserting ticker for %s",ticker.getPair()));
        List<Ticker> currentPairTickersList = tickersMap.get(ticker.getPair());
        if (currentPairTickersList == null) {
            currentPairTickersList = new LinkedList<>();
            tickersMap.put(ticker.getPair(), currentPairTickersList);
        }
        currentPairTickersList.add(ticker);
    }

    public PriceChanges[] checkChanges(String pair) {
        logger.finest(String.format("checking changes for %s",pair));
        List<Ticker> currentPairTickersList = tickersMap.get(pair);
        if(currentPairTickersList==null)
            return null;
        Ticker lastTicker = currentPairTickersList.get(currentPairTickersList.size()-1);
        List<PriceChanges> priceChangesList = new ArrayList<>();
        Iterator<Ticker> iterator = currentPairTickersList.iterator();
        long currentTimeSnapshot = lastTicker.getTimestampSeconds();
        long minValidTimestamp = currentTimeSnapshot - timePeriods[timePeriods.length - 1]*timeframeMultipler;
        while (iterator.hasNext() && iterator.next().getTimestampSeconds() < minValidTimestamp)
            iterator.remove();
        for (long currentTimePeriod : timePeriods) {
            long minValidTimestampForPeriod = currentTimeSnapshot - currentTimePeriod*timeframeMultipler;
            Ticker minTicker = currentPairTickersList.stream().
                    filter(t -> t.getTimestampSeconds() > minValidTimestampForPeriod).
                    min(Comparator.comparingDouble(Ticker::getValue)).get();
            Ticker maxTicker = currentPairTickersList.stream().
                    filter(t -> t.getTimestampSeconds() > minValidTimestampForPeriod).
                    max(Comparator.comparingDouble(Ticker::getValue)).get();
            PriceChanges priceChanges = new PriceChanges(pair,
                    currentTimePeriod,
                    lastTicker.getValue(),
                    minTicker.getValue(),
                    minTicker.getTimestampSeconds(),
                    maxTicker.getValue(),
                    maxTicker.getTimestampSeconds());
            priceChangesList.add(priceChanges);
        }
        return priceChangesList.toArray(new PriceChanges[priceChangesList.size()]);
    }

    public void setTimeframeMultipler(int multipler) {
        timeframeMultipler= multipler;
    }

    public boolean hasntReceivedTickersYet() {
        return tickersMap.isEmpty();
    }

}
