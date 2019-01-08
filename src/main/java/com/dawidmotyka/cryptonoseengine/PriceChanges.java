package com.dawidmotyka.cryptonoseengine;

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
    private Double relativePriceChange;
    private Double relativeLastPriceChange;
    private Double highLowDiffRelativeStdDev;

    public PriceChanges(String currencyPair,
                        long timePeriodSeconds,
                        double lastPrice,
                        double minPrice,
                        long minPriceTimestamp,
                        double maxPrice,
                        long maxPriceTimestamp) {
        this.currencyPair = currencyPair;
        this.timePeriodSeconds = timePeriodSeconds;
        this.lastPrice = lastPrice;
        this.minPrice = minPrice;
        this.minPriceTimestamp = minPriceTimestamp;
        this.maxPrice = maxPrice;
        this.maxPriceTimestamp = maxPriceTimestamp;
        this.relativePriceChange=null;
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

    public double getLastChange() {
        double riseChange=lastPrice-minPrice;
        double dropChange=maxPrice-lastPrice;
        if(riseChange>dropChange)
            return riseChange;
        return -dropChange;
    }

    public long getChangeTimeSeconds() {
        return Math.abs(minPriceTimestamp-maxPriceTimestamp);
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

    public Double getRelativePriceChange() {
        return relativePriceChange;
    }

    public Double getHighLowDiffRelativeStdDev() {
        return highLowDiffRelativeStdDev;
    }

    public void setRelativePriceChange(Double relativePriceChange) {
        this.relativePriceChange = relativePriceChange;
    }

    public void setHighLowDiffRelativeStdDev(Double highLowDiffRelativeStdDev) {
        this.highLowDiffRelativeStdDev = highLowDiffRelativeStdDev;
    }

    public Double getRelativeLastPriceChange() {
        return relativeLastPriceChange;
    }

    public void setRelativeLastPriceChange(Double relativeLastPriceChange) {
        this.relativeLastPriceChange = relativeLastPriceChange;
    }
}
