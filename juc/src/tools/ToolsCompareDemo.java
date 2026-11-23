package tools;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

/**
 * 三大并发工具类对比：CountDownLatch / CyclicBarrier / Semaphore
 * <p>
 * CountDownLatch：一个或多个线程等一组线程完成，计数减到 0 放行。不可复用（计数归零后不能重置）。
 *                 底层是 AQS 共享模式，state = 计数值。
 * CyclicBarrier ：一组线程互相等待，都到齐了再一起往下走。可 reset 复用。
 *                 底层是 ReentrantLock + Condition，不是 AQS（这是常见踩坑点）。
 * Semaphore     ：控制同时访问资源的线程数（许可）。可复用。
 *                 底层是 AQS 共享模式，state = 许可数。
 * <p>
 * 三者都基于 AQS 吗？不是。CyclicBarrier 是锁 + 条件队列实现的，别记混。
 */
public class ToolsCompareDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("===== CountDownLatch：等 3 个任务完成 =====");
        countDownLatchDemo();
        System.out.println("===== CyclicBarrier：3 个线程到齐一起出发 =====");
        cyclicBarrierDemo();
        System.out.println("===== Semaphore：最多 2 个线程同时访问 =====");
        semaphoreDemo();
    }

    static void countDownLatchDemo() throws InterruptedException {
        int n = 3;
        CountDownLatch latch = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            new Thread(() -> {
                System.out.println(Thread.currentThread().getName() + " 完成任务");
                latch.countDown(); // 计数减 1
            }).start();
        }
        latch.await(); // 阻塞直到计数归零
        System.out.println("所有任务完成，主线程继续");
    }

    static void cyclicBarrierDemo() throws InterruptedException {
        int n = 3;
        // 【关键】到齐后触发的 barrierAction，由最后一个到达的线程执行
        CyclicBarrier barrier = new CyclicBarrier(n, () -> System.out.println("人齐了，一起出发！"));
        for (int i = 0; i < n; i++) {
            new Thread(() -> {
                try {
                    System.out.println(Thread.currentThread().getName() + " 到达屏障");
                    barrier.await(); // 等其他人，到齐后一起放行
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        Thread.sleep(200);
    }

    static void semaphoreDemo() throws InterruptedException {
        Semaphore sem = new Semaphore(2); // 2 个许可 = 最多 2 个线程并发
        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                try {
                    sem.acquire(); // 拿许可，没有就阻塞
                    System.out.println(Thread.currentThread().getName() + " 进入，剩余许可=" + sem.availablePermits());
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    sem.release(); // 还许可
                }
            }).start();
        }
        Thread.sleep(800);
    }
}
