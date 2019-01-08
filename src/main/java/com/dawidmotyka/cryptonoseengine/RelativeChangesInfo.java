package com.dawidmotyka.cryptonoseengine;

/**
 * Created by dawid on 4/9/18.
 */
public class RelativeChangesInfo {
    Double highLowDiff;
    Double highLowDiffRelativeStdDeviation;
    public RelativeChangesInfo(Double highLowDiff, Double highLowDiffRelativeStdDeviation) {
        this.highLowDiff = highLowDiff;
        this.highLowDiffRelativeStdDeviation = highLowDiffRelativeStdDeviation;
    }
    public static RelativeChangesInfo createEmpty() {
        return new RelativeChangesInfo(null,null);
    }
    public boolean isEmpty() {
        return highLowDiff==null && highLowDiffRelativeStdDeviation==null;
    }
    public Double getHighLowDiff() {
        return highLowDiff;
    }
    public Double getHighLowDiffRelativeStdDeviation() {
        return highLowDiffRelativeStdDeviation;
    }
}