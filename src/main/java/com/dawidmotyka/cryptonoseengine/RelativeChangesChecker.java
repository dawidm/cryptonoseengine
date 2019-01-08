package com.dawidmotyka.cryptonoseengine;

import com.dawidmotyka.exchangeutils.chartdataprovider.ChartDataProvider;
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

    //delay between getting candles for consecutive currencies
    public static final double MIN_CANDLE_HIGH_LOW = 1e-10;

    //the key is pairname,period_seconds
    private Map<String,RelativeChangesInfo> relativeChangesInfoMap=new HashMap<>();

    public RelativeChangesChecker(ChartDataProvider chartDataProvider, int numCandles) {
       chartDataProvider.subscribeChartCandles((chartCandlesMap) -> {
           logger.fine("updating relative changes data");
           for(Map.Entry<String,ChartCandle[]> currentEntry : chartCandlesMap.entrySet()) {
               double[] highLowDiffArray = Arrays.stream(currentEntry.getValue()).
                       map(chartCandle -> Math.abs(chartCandle.getHigh()-chartCandle.getLow())).
                       mapToDouble(val -> val).toArray();
               OptionalDouble optionalHighLowDiff = Arrays.stream(highLowDiffArray).
                       filter(value -> value>MIN_CANDLE_HIGH_LOW).
                       average();
               if(optionalHighLowDiff.isPresent()) {
                   double highLowDiff = optionalHighLowDiff.getAsDouble();
                   double highLowDiffRelativeStdDeviation = new StandardDeviation().evaluate(highLowDiffArray)/highLowDiff;
                   relativeChangesInfoMap.put(currentEntry.getKey(),new RelativeChangesInfo(new Double(highLowDiff), new Double(highLowDiffRelativeStdDeviation)));
               }
           }
       },numCandles);
    }

    public double getRelativeChangeValue(String pair, long timePeriodSeconds, double priceChange) throws NoDataException {
        Double highLowDiff = relativeChangesInfoMap.get(pair + timePeriodSeconds).getHighLowDiff();
        if(highLowDiff!=null)
            return priceChange/highLowDiff.doubleValue();
        else
            throw new NoDataException();
    }

    public double getHighLowDiffRelativeStdDeviation(String pair, long timePeriodSeconds) throws NoDataException {
        RelativeChangesInfo relativeChangesInfo = relativeChangesInfoMap.get(pair+timePeriodSeconds);
        if(relativeChangesInfo!=null)
            return relativeChangesInfo.getHighLowDiffRelativeStdDeviation();
        else
            throw new NoDataException();
    }

    public void setRelativeChange(PriceChanges priceChanges) {
        RelativeChangesInfo relativeChangesInfo=relativeChangesInfoMap.get(priceChanges.getCurrencyPair() +","+ priceChanges.getTimePeriodSeconds());
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
