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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import pl.dmotyka.exchangeutils.chartdataprovider.ChartDataProvider;
import pl.dmotyka.exchangeutils.chartdataprovider.CurrencyPairTimePeriod;
import pl.dmotyka.exchangeutils.chartinfo.ChartCandle;

/**
 * Created by dawid on 7/25/17.
 */
public class RelativeChangesChecker {

    Logger logger = Logger.getLogger(RelativeChangesChecker.class.getName());

    public class NoDataException extends Exception {}

    // chart candle is used for calculation only if its price change if higher than last price multiplied by this value
    public static final double MIN_CANDLE_CHANGE = 0.0001;

    private final AtomicBoolean useMedianHighLowDiff = new AtomicBoolean(false);
    private Map<CurrencyPairTimePeriod,RelativeChangesInfo> relativeChangesInfoMap=new HashMap<>();

    public RelativeChangesChecker(ChartDataProvider chartDataProvider, int numCandles) {
       chartDataProvider.subscribeChartCandles((chartCandlesMap) -> {
           logger.fine("updating relative changes data");
           for(Map.Entry<CurrencyPairTimePeriod,ChartCandle[]> currentEntry : chartCandlesMap.entrySet()) {
               CurrencyPairTimePeriod currencyPairTimePeriod = currentEntry.getKey();
               ChartCandle[] chartCandles = currentEntry.getValue();
               if (chartCandles.length == 0)
                   continue;
               double[] highLowDiffArray = Arrays.stream(chartCandles).
                       map(chartCandle -> Math.abs(chartCandle.getHigh()-chartCandle.getLow())).
                       mapToDouble(val -> val).toArray();
               double lastClosePrice = chartCandles[chartCandles.length-1].getClose();
               highLowDiffArray = Arrays.stream(highLowDiffArray).filter(value -> value > lastClosePrice * MIN_CANDLE_CHANGE).toArray();
               OptionalDouble optionalHighLowDiff;
               if (useMedianHighLowDiff.get()) {
                   optionalHighLowDiff = calcMedian(highLowDiffArray);
               } else {
                   optionalHighLowDiff = Arrays.stream(highLowDiffArray).average();
               }
               if(optionalHighLowDiff.isPresent()) {
                   double highLowDiff = optionalHighLowDiff.getAsDouble();
                   double highLowDiffRelativeStdDeviation = new StandardDeviation().evaluate(highLowDiffArray)/highLowDiff;
                   relativeChangesInfoMap.put(currencyPairTimePeriod,new RelativeChangesInfo(highLowDiff, highLowDiffRelativeStdDeviation));
               }
           }
       });
    }

    private OptionalDouble calcMedian(double[] highLowDiffArray) {
        OptionalDouble optionalHighLowDiff;
        double[] sortedDiffs = Arrays.stream(highLowDiffArray).sorted().toArray();
        if (sortedDiffs.length == 0 ) {
            optionalHighLowDiff = OptionalDouble.empty();
        } else if (sortedDiffs.length % 2 == 0) {
            optionalHighLowDiff = OptionalDouble.of((sortedDiffs[sortedDiffs.length/2] + sortedDiffs[sortedDiffs.length/2-1]) / 2);
        } else {
            optionalHighLowDiff = OptionalDouble.of(sortedDiffs[sortedDiffs.length/2]);
        }
        return optionalHighLowDiff;
    }

    public double getRelativeChangeValue(String pair, long timePeriodSeconds, double priceChange) throws NoDataException {
        Double highLowDiff = relativeChangesInfoMap.get(new CurrencyPairTimePeriod(pair,(int)timePeriodSeconds)).getHighLowDiff();
        if(highLowDiff!=null)
            return priceChange/highLowDiff.doubleValue();
        else
            throw new NoDataException();
    }

    public double getHighLowDiffRelativeStdDeviation(String pair, long timePeriodSeconds) throws NoDataException {
        RelativeChangesInfo relativeChangesInfo = relativeChangesInfoMap.get(new CurrencyPairTimePeriod(pair,(int)timePeriodSeconds));
        if(relativeChangesInfo!=null)
            return relativeChangesInfo.getHighLowDiffRelativeStdDeviation();
        else
            throw new NoDataException();
    }

    public void setRelativeChange(PriceChanges priceChanges) {
        RelativeChangesInfo relativeChangesInfo=relativeChangesInfoMap.get(new CurrencyPairTimePeriod(priceChanges.getCurrencyPair(),(int)priceChanges.getTimePeriodSeconds()));
        if(relativeChangesInfo!=null && !relativeChangesInfo.isEmpty()) {
            priceChanges.setRelativePriceChange(priceChanges.getChange() / relativeChangesInfo.getHighLowDiff());
            priceChanges.setRelativeLastPriceChange(priceChanges.getLastChange() / relativeChangesInfo.getHighLowDiff());
            priceChanges.setRelativeDropPriceChange(priceChanges.getDropChange() / relativeChangesInfo.getHighLowDiff());
            priceChanges.setRelativeRisePriceChange(priceChanges.getRiseChange() / relativeChangesInfo.getHighLowDiff());
            priceChanges.setHighLowDiffRelativeStdDev(relativeChangesInfo.getHighLowDiffRelativeStdDeviation());
        }
    }

    public void setRelativeChanges(PriceChanges[] priceChanges) {
        for(PriceChanges priceChange : priceChanges)
            setRelativeChange(priceChange);
    }

    // call to switch changes checker to use median instead of average high-low differences
    public void setUseMedianHighLowDiff() {
        useMedianHighLowDiff.set(true);
    }

}
