package locksupport;

import java.util.concurrent.locks.LockSupport;

/**
 * 中断标记对比：park 被唤醒不清除标记，wait/sleep/join 抛异常会清除标记
 * <p>
 * 这是面试高频对比点：
 * 1. wait() / sleep() / join()：被中断时抛 InterruptedException，同时清除中断标记。
 *    所以 catch 之后 isInterrupted() 是 false，需要手动 Thread.currentThread().interrupt() 恢复。
 * 2. LockSupport.park()：被 interrupt() 唤醒后直接返回，不抛异常，也不清除中断标记。
 *    所以 park 返回后 isInterrupted() 仍是 true。
 * <p>
 * 记住一句话：抛异常的那组会清标记，park 不清。
 */
public class InterruptFlagCompareDemo {

    public static void main(String[] args) throws InterruptedException {
        // 场景一：sleep 被中断 → 抛异常 + 清除标记
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // 【关键】sleep 抛异常时标记已被清除，此处 isInterrupted() = false
                System.out.println("[sleep] catch 中标记=" + Thread.currentThread().isInterrupted() + "（false，被清了）");
            }
        });
        sleeper.start();
        Thread.sleep(100);
        sleeper.interrupt();
        sleeper.join();

        // 场景二：park 被中断 → 不抛异常 + 不清除标记
        Thread parker = new Thread(() -> {
            LockSupport.park();
            // 【关键】park 返回后标记还在，isInterrupted() = true
            System.out.println("[park] 唤醒后标记=" + Thread.currentThread().isInterrupted() + "（true，没清）");
        });
        parker.start();
        Thread.sleep(100);
        parker.interrupt();
        parker.join();
    }
}
