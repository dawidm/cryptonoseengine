package com.dawidmotyka.cryptonoseengine;

import com.dawidmotyka.exchangeutils.chartdataprovider.ChartDataProvider;
import com.dawidmotyka.exchangeutils.chartdataprovider.CurrencyPairTimePeriod;
import com.dawidmotyka.exchangeutils.chartinfo.ChartCandle;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.logging.Logger;

/**
 * Created by dawid on 7/25/17.
 */
public class RelativeChangesChecker {

    Logger logger = Logger.getLogger(RelativeChangesChecker.class.getName());

    public class NoDataException extends Exception {}

    public static final double MIN_CANDLE_HIGH_LOW = 1e-10;

    private Map<CurrencyPairTimePeriod,RelativeChangesInfo> relativeChangesInfoMap=new HashMap<>();

    public RelativeChangesChecker(ChartDataProvider chartDataProvider, int numCandles) {
       chartDataProvider.subscribeChartCandles((chartCandlesMap) -> {
           logger.fine("updating relative changes data");
           for(Map.Entry<CurrencyPairTimePeriod,ChartCandle[]> currentEntry : chartCandlesMap.entrySet()) {
               CurrencyPairTimePeriod currencyPairTimePeriod = currentEntry.getKey();
               ChartCandle[] chartCandles = currentEntry.getValue();
               double[] highLowDiffArray = Arrays.stream(chartCandles).
                       map(chartCandle -> Math.abs(chartCandle.getHigh()-chartCandle.getLow())).
                       mapToDouble(val -> val).toArray();
               OptionalDouble optionalHighLowDiff = Arrays.stream(highLowDiffArray).
                       filter(value -> value>MIN_CANDLE_HIGH_LOW).
                       average();
               if(optionalHighLowDiff.isPresent()) {
                   double highLowDiff = optionalHighLowDiff.getAsDouble();
                   double highLowDiffRelativeStdDeviation = new StandardDeviation().evaluate(highLowDiffArray)/highLowDiff;
                   relativeChangesInfoMap.put(currencyPairTimePeriod,new RelativeChangesInfo(new Double(highLowDiff), new Double(highLowDiffRelativeStdDeviation)));
               }
           }
       });
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
            priceChanges.setRelativeLastPriceChange(priceChanges.getLastChange() / relativeChangesInfo.highLowDiff);
            priceChanges.setHighLowDiffRelativeStdDev(relativeChangesInfo.getHighLowDiffRelativeStdDeviation());
        }
    }

    public void setRelativeChanges(PriceChanges[] priceChanges) {
        for(PriceChanges priceChange : priceChanges)
            setRelativeChange(priceChange);
    }

}
