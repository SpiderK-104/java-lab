package aqs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AQS 的两种模式：独占（EXCLUSIVE）与共享（SHARED）
 * <p>
 * AQS 核心是一个 volatile int state + 一个 FIFO 双向队列。
 * 两种模式下 state 的含义完全不同：
 * 1. 独占模式（ReentrantLock）：state 表示锁的持有计数，0=无人持有，1=持有一次，重入则累加。
 * 2. 共享模式（Semaphore）：state 表示剩余许可数；CountDownLatch 里 state 表示倒计数值。
 * <p>
 * 独占：同一时刻只有一个线程能拿到锁（tryAcquire 返回 true）。
 * 共享：多个线程可同时成功（tryAcquireShared 返回 >=0 表示成功，还可能唤醒后继线程）。
 * <p>
 * 本 demo 用现成工具类演示两种模式的行为差异，AQS 源码细节参考笔记文字部分。
 */
public class AqsModeDemo {

    public static void main(String[] args) throws InterruptedException {
        // 独占模式：ReentrantLock，state 是重入次数
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        lock.lock(); // 重入，state 变成 2
        System.out.println("ReentrantLock 重入次数(独占 state)=" + lock.getHoldCount());
        lock.unlock();
        lock.unlock();

        // 共享模式：Semaphore，state 是许可数，3 个线程可同时拿到许可
        Semaphore sem = new Semaphore(3);
        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                try {
                    sem.acquire(); // 拿一个许可，state 减 1
                    System.out.println(Thread.currentThread().getName() + " 拿到许可，剩余=" + sem.availablePermits());
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    sem.release(); // 归还许可，state 加 1
                }
            }).start();
        }

        // 共享模式：CountDownLatch，state 是计数，减到 0 时一次性唤醒所有等待线程
        CountDownLatch latch = new CountDownLatch(2);
        new Thread(() -> { latch.countDown(); latch.countDown(); }).start();
        latch.await(); // 计数归零前阻塞，归零后所有等待线程一起通过
        System.out.println("CountDownLatch 归零，等待线程全部放行");
    }
}
