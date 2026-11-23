package aqs;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Condition 队列：await / signal 如何在两条队列之间迁移节点
 * <p>
 * 核心：AQS 里有两条完全不同的队列——
 * 1. 同步队列（CLH 队列）：抢锁失败的线程在这里排队等锁。
 * 2. 条件队列：调用 await() 的线程释放锁后，进入这里等待条件满足。
 * <p>
 * 流程：
 * await()  → 释放锁，线程从同步队列移到条件队列，状态变 WAITING
 * signal() → 把节点从条件队列移回同步队列，状态变 RUNNABLE，重新抢锁
 * <p>
 * waitStatus 取值（Node 节点）：
 * CANCELLED(1) 取消、SIGNAL(-1) 后继需要被唤醒、CONDITION(-2) 在条件队列、
 * PROPAGATE(-3) 共享模式传播唤醒、0 初始状态。
 * <p>
 * 本 demo 演示一个线程 await 等待、另一个线程 signal 唤醒的完整迁移过程。
 */
public class ConditionQueueDemo {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean ready = false;

    // 等待方：await 后在条件队列阻塞
    void waitForSignal() throws InterruptedException {
        lock.lock();
        try {
            while (!ready) {
                System.out.println("[等待方] 条件不满足，await() → 进入条件队列，释放锁");
                condition.await(); // 【关键】释放锁 + 进条件队列；被 signal 后重新抢锁才返回
                System.out.println("[等待方] 被唤醒，已从条件队列移回同步队列并重新拿到锁");
            }
        } finally {
            lock.unlock();
        }
    }

    // 通知方：改条件后 signal
    void doSignal() throws InterruptedException {
        Thread.sleep(200); // 确保等待方先进条件队列
        lock.lock();
        try {
            ready = true;
            System.out.println("[通知方] 条件置为 true，signal() → 把节点搬回同步队列");
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConditionQueueDemo demo = new ConditionQueueDemo();
        Thread waiter = new Thread(() -> {
            try { demo.waitForSignal(); } catch (InterruptedException e) { e.printStackTrace(); }
        }, "等待线程");
        waiter.start();
        demo.doSignal();
        waiter.join();
    }
}
