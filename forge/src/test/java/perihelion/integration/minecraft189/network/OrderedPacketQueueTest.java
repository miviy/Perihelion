package perihelion.integration.minecraft189.network;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class OrderedPacketQueueTest {
    @Test
    public void delayChangesCannotReorderPackets() {
        OrderedPacketQueue<String> q = new OrderedPacketQueue<>(10, 100);
        assertTrue(q.offer("older", 500, 10));
        assertTrue(q.offer("newer", 100, 10));
        assertEquals(Collections.emptyList(), q.drain(100, false));
        assertEquals(Arrays.asList("older", "newer"), q.drain(500, false));
        assertEquals(0, q.size());
    }

    @Test
    public void pulseReleasesAllButDoesNotCloseTheQueue() {
        OrderedPacketQueue<Integer> q = new OrderedPacketQueue<>(3, 3);
        assertTrue(q.offer(1, Long.MAX_VALUE, 1));
        assertTrue(q.offer(2, Long.MAX_VALUE, 1));
        assertEquals(Arrays.asList(1, 2), q.drain(0, true));
        assertTrue(q.offer(3, 4, 1));
        assertEquals(Collections.singletonList(3), q.drain(4, false));
    }

    @Test
    public void countAndBytesAreBoundedAndRecoveredAfterDrain() {
        OrderedPacketQueue<Integer> q = new OrderedPacketQueue<>(2, 10);
        assertTrue(q.offer(1, 1, 6));
        assertFalse(q.offer(2, 1, 5));
        assertTrue(q.offer(2, 1, 4));
        assertFalse(q.offer(3, 1, 0));
        q.drain(1, false);
        assertTrue(q.offer(3, 1, 10));
    }

    @Test
    public void closureDiscardsAndRejectsLateNetworkOffers() throws Exception {
        OrderedPacketQueue<Integer> q = new OrderedPacketQueue<>(100, 100);
        q.offer(1, 1, 1);
        q.close();
        java.util.concurrent.FutureTask<Void> task =
                new java.util.concurrent.FutureTask<>(
                        () -> {
                            for (int i = 0; i < 100; i++) assertFalse(q.offer(i, 0, 1));
                            return null;
                        });
        Thread producer = new Thread(task);
        producer.start();
        task.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, q.size());
        assertTrue(q.drain(Long.MAX_VALUE, true).isEmpty());
        q.close();
    }

    @Test
    public void concurrentProducerAndConsumerPreserveOrder() throws Exception {
        OrderedPacketQueue<Integer> q = new OrderedPacketQueue<>(1000, 1000);
        Thread producer =
                new Thread(
                        () -> {
                            for (int i = 0; i < 1000; i++) q.offer(i, i, 1);
                        });
        producer.start();
        java.util.List<Integer> collected = new java.util.ArrayList<>();
        while (producer.isAlive()) collected.addAll(q.drain(1000, false));
        producer.join();
        collected.addAll(q.drain(1000, true));
        assertEquals(1000, collected.size());
        for (int i = 0; i < collected.size(); i++) assertEquals(i, collected.get(i).intValue());
    }
}
