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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import pl.dmotyka.exchangeutils.binance.BinanceExchangeSpecs;
import pl.dmotyka.exchangeutils.bitfinex.BitfinexExchangeSpecs;
import pl.dmotyka.exchangeutils.exchangespecs.ExchangeSpecs;
import pl.dmotyka.exchangeutils.pairdataprovider.PairSelectionCriteria;
import pl.dmotyka.exchangeutils.poloniex.PoloniexExchangeSpecs;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptonoseGenericEngineTest {

    Logger logger = Logger.getLogger(CryptonoseGenericEngine.class.getName());

    @Test
    public synchronized void testCryptonoseGenericEngineBinance() throws InterruptedException {
        var exchangeSpecs = new BinanceExchangeSpecs();
        var pairSelectionCriteria = new PairSelectionCriteria("BTC",1000);
        testCryptonoseGenericEngine(exchangeSpecs, pairSelectionCriteria);
    }

    @Test
    public synchronized void testCryptonoseGenericEngineBitfinex() throws InterruptedException {
        var exchangeSpecs = new BitfinexExchangeSpecs();
        var pairSelectionCriteria = new PairSelectionCriteria("BTC",100);
        testCryptonoseGenericEngine(exchangeSpecs, pairSelectionCriteria);
    }

    @Test
    public synchronized void testCryptonoseGenericEnginePoloniex() throws InterruptedException {
        var exchangeSpecs = new PoloniexExchangeSpecs();
        var pairSelectionCriteria = new PairSelectionCriteria("BTC",100);
        testCryptonoseGenericEngine(exchangeSpecs, pairSelectionCriteria);
    }

    private void checkPriceChanges(PriceChanges priceChanges, long[] validTimePeriods) {
        assertTrue(priceChanges.getCurrencyPair().length()>0);
        var timePeriods = new HashSet<Long>();
        Arrays.stream(validTimePeriods).forEach(timePeriods::add);
        assertTrue(timePeriods.contains(priceChanges.getTimePeriodSeconds()));
        assertTrue(priceChanges.getLastPrice() > 0);
        assertTrue(priceChanges.getMaxPrice() > 0);
        assertTrue(priceChanges.getMinPrice() > 0);
    }

    private synchronized void testCryptonoseGenericEngine(ExchangeSpecs exchangeSpecs, PairSelectionCriteria pairSelectionCriteria) throws InterruptedException {
        var pairSelectionCriterias = new PairSelectionCriteria[] {pairSelectionCriteria};
        var timePeriods = new long[] {300, 900};
        var engineChangesReceiver = new EngineChangesReceiver() {
            @Override
            public void receiveChanges(List<PriceChanges> priceChangesList) {
                synchronized (CryptonoseGenericEngineTest.this) {
                    assertTrue(priceChangesList.size() > 0);
                    checkPriceChanges(priceChangesList.get(0), timePeriods);
                    CryptonoseGenericEngineTest.this.notify();
                }
            }
            @Override
            public void receiveChanges(PriceChanges priceChanges) {
                synchronized (CryptonoseGenericEngineTest.this) {
                    checkPriceChanges(priceChanges, timePeriods);
                    CryptonoseGenericEngineTest.this.notify();
                }
            }
        };
        CryptonoseGenericEngine engine =
                CryptonoseGenericEngine.withProvidedMarkets(
                        exchangeSpecs,
                        engineChangesReceiver,
                        timePeriods,
                        10,
                        pairSelectionCriterias);
        engine.setEngineMessageReceiver(msg -> logger.info(msg.getMessage()));
        new Thread(engine::start).start();
        new Thread(() -> assertThrows(IllegalStateException.class, engine::start)).start();
        wait();
    }
}