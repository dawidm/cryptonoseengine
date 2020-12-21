/*
 * Cryptonose
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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EngineMessageQueue {

    public static final Logger logger = Logger.getLogger(EngineMessageQueue.class.getName());

    private static final int MESSAGE_FREQUENCY_MS = 100;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<EngineMessage> messagesQueue = new LinkedBlockingQueue<>();
    private final EngineMessageReceiver engineMessageReceiver;

    public EngineMessageQueue(EngineMessageReceiver engineMessageReceiver) {
        this.engineMessageReceiver = engineMessageReceiver;
        executorService.scheduleWithFixedDelay(this::sendMessages, MESSAGE_FREQUENCY_MS, MESSAGE_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    }

    public void addMessage(EngineMessage engineMessage) {
        messagesQueue.add(engineMessage);
    }

    private void sendMessages() {
        try {
            while (messagesQueue.size() > 0) {
                engineMessageReceiver.message(messagesQueue.poll());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }
}
