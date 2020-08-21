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

/**
 * Created by dawid on 7/14/17.
 */
public class PriceChanges {
    private final String currencyPair;
    private final long timePeriodSeconds;
    private final double lastPrice;
    private final double minPrice;
    private final long minPriceTimestamp;
    private final double maxPrice;
    private final long maxPriceTimestamp;
    private final double maxAfterMinPrice;
    private final long maxAfterMinTimestamp;
    private final double minAfterMaxPrice;
    private final long minAfterMaxTimestamp;
    private Double relativePriceChange;
    private Double relativeLastPriceChange;
    private Double relativeDropPriceChange;
    private Double relativeRisePriceChange;
    private Double highLowDiffRelativeStdDev;

    public PriceChanges(String currencyPair, long timePeriodSeconds, double lastPrice, double minPrice, long minPriceTimestamp, double maxPrice, long maxPriceTimestamp, double maxAfterMinPrice, long maxAfterMinTimestamp, double minAfterMaxPrice, long minAfterMaxTimestamp) {
        this.currencyPair = currencyPair;
        this.timePeriodSeconds = timePeriodSeconds;
        this.lastPrice = lastPrice;
        this.minPrice = minPrice;
        this.minPriceTimestamp = minPriceTimestamp;
        this.maxPrice = maxPrice;
        this.maxPriceTimestamp = maxPriceTimestamp;
        this.maxAfterMinPrice = maxAfterMinPrice;
        this.maxAfterMinTimestamp = maxAfterMinTimestamp;
        this.minAfterMaxPrice = minAfterMaxPrice;
        this.minAfterMaxTimestamp = minAfterMaxTimestamp;
    }

    public double getPercentChange() {
        //price risin
        if (maxPriceTimestamp>minPriceTimestamp) {
            return 100*(maxPrice-minPrice)/minPrice;
        }
        //price droppin
        else {
            return 100*(minPrice-maxPrice)/maxPrice;
        }
    }

    public double getChange() {
        //price risin
        if (maxPriceTimestamp>minPriceTimestamp) {
            return maxPrice-minPrice;
        }
        //price droppin
        else {
            return minPrice-maxPrice;
        }
    }

    public double getDropChange() {
        return minAfterMaxPrice-maxPrice;
    }

    public double getDropPercentChange() {
        return 100*(minAfterMaxPrice-maxPrice)/maxPrice;
    }

    public double getRiseChange() {
        return minPrice-maxAfterMinPrice;
    }

    public double getRisePercentChange() {
        return 100*(minPrice-maxAfterMinPrice)/maxAfterMinPrice;
    }

    public double getLastChange() {
        double riseChange=lastPrice-minPrice;
        double dropChange=maxPrice-lastPrice;
        if(riseChange>dropChange)
            return riseChange;
        return -dropChange;
    }

    public double getLastPercentChange() {
        double riseChange=lastPrice-minPrice;
        double dropChange=maxPrice-lastPrice;
        if(riseChange>dropChange)
            return 100*riseChange/minPrice;
        return -100*dropChange/lastPrice;
    }

    public long getChangeTimeSeconds() {
        return Math.abs(minPriceTimestamp-maxPriceTimestamp);
    }

    public long getDropChangeTimeSeconds() {
        return minAfterMaxTimestamp - minPriceTimestamp;
    }

    public long getRiseChangeTimeSeconds() {
        return maxAfterMinTimestamp - minPriceTimestamp;
    }

    public long getPriceChangeAgeSeconds() {
        return System.currentTimeMillis()/1000-Math.max(minPriceTimestamp,maxPriceTimestamp);
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public long getTimePeriodSeconds() {
        return timePeriodSeconds;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public double getMaxAfterMinPrice() {
        return maxAfterMinPrice;
    }

    public double getMinAfterMaxPrice() {
        return minAfterMaxPrice;
    }

    public Double getRelativePriceChange() {
        return relativePriceChange;
    }

    public Double getRelativeLastPriceChange() {
        return relativeLastPriceChange;
    }

    public Double getRelativeDropPriceChange() {
        return relativeDropPriceChange;
    }

    public Double getRelativeRisePriceChange() {
        return relativeRisePriceChange;
    }

    public Double getHighLowDiffRelativeStdDev() {
        return highLowDiffRelativeStdDev;
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
