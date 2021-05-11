/*
 * Cryptonose
 *
 * Copyright © 2019-2021 Dawid Motyka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package pl.dmotyka.cryptonoseengine;

/**
 * Created by dawid on 7/14/17.
 */
public class PriceChanges {
    private final String currencyPair;
    private final long timePeriodSeconds;
    private final double lastPrice;
    private final long lastPriceTimestampSec;
    private final double minPrice;
    private final long minPriceTimestampSec;
    private final double maxPrice;
    private final long maxPriceTimestampSec;
    private final double maxAfterMinPrice;
    private final long maxAfterMinTimestampSec;
    private final double minAfterMaxPrice;
    private final long minAfterMaxTimestampSec;
    private Double relativePriceChange;
    private Double relativeLastPriceChange;
    private Double relativeDropPriceChange;
    private Double relativeRisePriceChange;
    private Double highLowDiffRelativeStdDev;

    public PriceChanges(String currencyPair, long timePeriodSeconds, double lastPrice, long lastPriceTimestampSec, double minPrice, long minPriceTimestampSec, double maxPrice, long maxPriceTimestampSec, double maxAfterMinPrice, long maxAfterMinTimestampSec, double minAfterMaxPrice, long minAfterMaxTimestampSec) {
        this.currencyPair = currencyPair;
        this.timePeriodSeconds = timePeriodSeconds;
        this.lastPrice = lastPrice;
        this.lastPriceTimestampSec = lastPriceTimestampSec;
        this.minPrice = minPrice;
        this.minPriceTimestampSec = minPriceTimestampSec;
        this.maxPrice = maxPrice;
        this.maxPriceTimestampSec = maxPriceTimestampSec;
        this.maxAfterMinPrice = maxAfterMinPrice;
        this.maxAfterMinTimestampSec = maxAfterMinTimestampSec;
        this.minAfterMaxPrice = minAfterMaxPrice;
        this.minAfterMaxTimestampSec = minAfterMaxTimestampSec;
    }

    // if maximum price is more recent: 100*(maxPrice-minPrice)/minPrice
    // otherwise: 100*(minPrice-maxPrice)/maxPrice
    public double getPercentChange() {
        //price risin
        if (maxPriceTimestampSec > minPriceTimestampSec) {
            return 100*(maxPrice-minPrice)/minPrice;
        }
        //price droppin
        else {
            return 100*(minPrice-maxPrice)/maxPrice;
        }
    }

    // if maximum price is more recent: maxPrice-minPrice
    // otherwise: minPrice-maxPrice
    public double getChange() {
        //price risin
        if (maxPriceTimestampSec > minPriceTimestampSec) {
            return maxPrice-minPrice;
        }
        //price droppin
        else {
            return minPrice-maxPrice;
        }
    }

    // get change between maximum price and the lowest consecutive price, reversed
    public double getDropChange() {
        return minAfterMaxPrice-maxPrice;
    }

    // get percent change between maximum price and the lowest consecutive price, reversed
    public double getDropPercentChange() {
        return 100*(minAfterMaxPrice-maxPrice)/maxPrice;
    }

    // get change between minimum price and the highest consecutive price
    public double getRiseChange() {
        return minPrice-maxAfterMinPrice;
    }

    // get percent change between minimum price and the highest consecutive price
    public double getRisePercentChange() {
        return 100*(minPrice-maxAfterMinPrice)/maxAfterMinPrice;
    }

    // get change between last price and highest/lowest price, depending which one has bigger absolute value
    public double getLastChange() {
        double riseChange=lastPrice-minPrice;
        double dropChange=maxPrice-lastPrice;
        if(riseChange>dropChange)
            return riseChange;
        return -dropChange;
    }

    // get percent change between last price and highest/lowest price, depending which one has bigger absolute value
    public double getLastPercentChange() {
        double riseChange=lastPrice-minPrice;
        double dropChange=maxPrice-lastPrice;
        if(riseChange>dropChange)
            return 100*riseChange/minPrice;
        return -100*dropChange/lastPrice;
    }

    // get time of change for getChange() in seconds
    public long getChangeTimeSeconds() {
        return Math.abs(minPriceTimestampSec - maxPriceTimestampSec);
    }

    // get time of change for getDropChange() in seconds
    public long getDropChangeTimeSeconds() {
        return minAfterMaxTimestampSec - minPriceTimestampSec;
    }

    // get time of change for getRiseChange() in seconds
    public long getRiseChangeTimeSeconds() {
        return maxAfterMinTimestampSec - minPriceTimestampSec;
    }

    // time (seconds) difference between current timestamp and the higher of the two: min price timestamp, max price timestamp
    public long getPriceChangeAgeSeconds() {
        return System.currentTimeMillis()/1000-Math.max(minPriceTimestampSec, maxPriceTimestampSec);
    }

    // the most recent price
    public double getLastPrice() {
        return lastPrice;
    }

    // time period for which price changes are calculated
    public long getTimePeriodSeconds() {
        return timePeriodSeconds;
    }

    // currency pair for which price changes are calculated
    public String getCurrencyPair() {
        return currencyPair;
    }

    // get minimum price in the whole period
    public double getMinPrice() {
        return minPrice;
    }

    // get maximum price in the whole period
    public double getMaxPrice() {
        return maxPrice;
    }

    // get maximum price, but only with timestamp higher that minimum price
    public double getMaxAfterMinPrice() {
        return maxAfterMinPrice;
    }

    // get minimum price, but only with timestamp higher that maximum price
    public double getMinAfterMaxPrice() {
        return minAfterMaxPrice;
    }

    // relative value for getChange() and getPercentChange()
    public Double getRelativePriceChange() {
        return relativePriceChange;
    }

    // relative value for getLastChange() and getLastPercentChange()
    public Double getRelativeLastPriceChange() {
        return relativeLastPriceChange;
    }

    // relative value for getDropChange() and getDropPercentChange()
    public Double getRelativeDropPriceChange() {
        return relativeDropPriceChange;
    }

    // relative value for getRiseChange() and getRisePercentChange()
    public Double getRelativeRisePriceChange() {
        return relativeRisePriceChange;
    }

    public Double getHighLowDiffRelativeStdDev() {
        return highLowDiffRelativeStdDev;
    }

    // get bigger of two values: min price timestamp and max price timestamp
    public long getFinalPriceTimestampSec() {
        return Math.max(maxPriceTimestampSec, minPriceTimestampSec);
    }

    // get lower of two values: min price timestamp and max price timestamp
    public long getReferencePriceTimestampSec() {
        return Math.min(maxPriceTimestampSec, minPriceTimestampSec);
    }

    // for getLastChange() and getLastPercentChange, get timestamp of the price which was the reference for the calculation
    public long getReferenceToLastPriceTimestampSec() {
        if (getLastPercentChange() > 0) {
            return minPriceTimestampSec;
        } else {
            return maxPriceTimestampSec;
        }
    }

    // get timestamp of the most recent price
    public long getLastPriceTimestampSec() {
        return lastPriceTimestampSec;
    }

    public void setRelativePriceChange(Double relativePriceChange) {
        this.relativePriceChange = relativePriceChange;
    }

    public void setRelativeLastPriceChange(Double relativeLastPriceChange) {
        this.relativeLastPriceChange = relativeLastPriceChange;
    }

    public void setRelativeDropPriceChange(Double relativeDropPriceChange) {
        this.relativeDropPriceChange = relativeDropPriceChange;
    }

    public void setRelativeRisePriceChange(Double relativeRisePriceChange) {
        this.relativeRisePriceChange = relativeRisePriceChange;
    }

    public void setHighLowDiffRelativeStdDev(Double highLowDiffRelativeStdDev) {
        this.highLowDiffRelativeStdDev = highLowDiffRelativeStdDev;
    }
}
