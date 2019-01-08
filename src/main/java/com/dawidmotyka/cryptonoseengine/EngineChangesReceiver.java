package com.dawidmotyka.cryptonoseengine;

import java.util.List;

/**
 * Created by dawid on 7/14/17.
 */
public interface EngineChangesReceiver {
    void receiveChanges(List<PriceChanges> priceChangesList);
    void receiveChanges(PriceChanges priceChanges);
}
