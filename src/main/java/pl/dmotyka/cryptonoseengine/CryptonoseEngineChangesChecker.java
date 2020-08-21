/*
 * Cryptonose2
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import pl.dmotyka.exchangeutils.tickerprovider.Ticker;

/**
 * Created by dawid on 9/29/17.
 */
public class CryptonoseEngineChangesChecker {

    private static final Logger logger = Logger.getLogger(CryptonoseEngineChangesChecker.class.getName());

    private long timePeriods[];
    private Integer timeframeMultipler=1;
    private Map<String, List<Ticker>> tickersMap = new HashMap<>();

    public CryptonoseEngineChangesChecker(long[] timePeriods) {
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
        synchronized (currentPairTickersList) {
            currentPairTickersList.add(ticker);
        }
    }

    public PriceChanges[] checkChanges(String pair) {
        logger.finest(String.format("checking changes for %s",pair));
        List<Ticker> currentPairTickersList = tickersMap.get(pair);
        if(currentPairTickersList==null)
            return null;
        synchronized (currentPairTickersList) {
            Ticker lastTicker = currentPairTickersList.get(currentPairTickersList.size() - 1);
            List<PriceChanges> priceChangesList = new ArrayList<>();
            Iterator<Ticker> iterator = currentPairTickersList.iterator();
            long currentTimeSnapshot = lastTicker.getTimestampSeconds();
            long minValidTimestamp = currentTimeSnapshot - timePeriods[timePeriods.length - 1] * timeframeMultipler;
            while (iterator.hasNext() && iterator.next().getTimestampSeconds() < minValidTimestamp)
                iterator.remove();
            for (long currentTimePeriod : timePeriods) {
                long minValidTimestampForPeriod = currentTimeSnapshot - currentTimePeriod * timeframeMultipler;
                Ticker minTicker = currentPairTickersList.stream().
                        filter(t -> t.getTimestampSeconds() > minValidTimestampForPeriod).
                        min(Comparator.comparingDouble(Ticker::getValue)).orElse(null);
                if (minTicker == null) //valid tickers list is empty
                    continue;
                Ticker maxTicker = currentPairTickersList.stream().
                        filter(t -> t.getTimestampSeconds() > minValidTimestampForPeriod).
                        max(Comparator.comparingDouble(Ticker::getValue)).orElse(null);
                Ticker maxAfterMinTicker;
                if (maxTicker.getTimestampSeconds() >= minTicker.getTimestampSeconds())
                    maxAfterMinTicker = maxTicker;
                else
                    maxAfterMinTicker = currentPairTickersList.stream().
                            filter(t -> t.getTimestampSeconds() > minTicker.getTimestampSeconds()).
                            max(Comparator.comparingDouble(Ticker::getValue)).orElse(minTicker);
                Ticker minAfterMaxTicker;
                if (minTicker.getTimestampSeconds() >= maxTicker.getTimestampSeconds())
                    minAfterMaxTicker = minTicker;
                else
                    minAfterMaxTicker = currentPairTickersList.stream().
                            filter(t -> t.getTimestampSeconds() > maxTicker.getTimestampSeconds()).
                            min(Comparator.comparingDouble(Ticker::getValue)).orElse(maxTicker);
                PriceChanges priceChanges = new PriceChanges(pair,
                        currentTimePeriod,
                        lastTicker.getValue(),
                        minTicker.getValue(),
                        minTicker.getTimestampSeconds(),
                        maxTicker.getValue(),
                        maxTicker.getTimestampSeconds(),
                        maxAfterMinTicker.getValue(),
                        minAfterMaxTicker.getValue());
                priceChangesList.add(priceChanges);
            }
            return priceChangesList.toArray(new PriceChanges[priceChangesList.size()]);
        }
    }

    public void setTimeframeMultipler(int multipler) {
        timeframeMultipler= multipler;
    }

    public boolean hasntReceivedTickersYet() {
        return tickersMap.isEmpty();
    }

}
