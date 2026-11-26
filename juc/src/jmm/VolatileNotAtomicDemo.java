package jmm;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * volatile 保证可见性，但不保证原子性（i++ 的经典坑）
 * <p>
 * 核心：i++ 看似一行，实际是三步——读 i、改 +1、写回。
 * volatile 只能保证每次读都能读到最新值、每次写立刻刷回主内存，
 * 但它管不了「读-改-写」这三步中间被别的线程插进来。
 * <p>
 * 结论：
 * 1. volatile 适合「一个线程写，多个线程读」的状态标志位
 * 2. 计数、累加这种读改写场景，要用 AtomicInteger / LongAdder / synchronized
 */
public class VolatileNotAtomicDemo {

    // 加 volatile：可见性有了，但++仍然不是原子操作
    static volatile int count = 0;
    // 对比：AtomicInteger 用 CAS 保证读改写三步原子
    static AtomicInteger atomicCount = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        int threadNum = 10;
        int loop = 10000;
        Thread[] ts = new Thread[threadNum];

        for (int i = 0; i < threadNum; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < loop; j++) {
                    count++;              // 三步操作，多线程交叉 → 丢失更新
                    atomicCount.incrementAndGet(); // CAS 原子递增，不会丢
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();

        System.out.println("期望值 = " + (threadNum * loop));
        System.out.println("volatile int 结果 = " + count + "  （几乎总是偏小）");
        System.out.println("AtomicInteger 结果 = " + atomicCount.get() + "  （恒等于期望值）");
    }
}
