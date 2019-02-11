/*
 * Cryptonose2
 *
 * Copyright © 2019 Dawid Motyka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package pl.dmotyka.cryptonoseengine;

import pl.dmotyka.exchangeutils.tickerprovider.Ticker;

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
