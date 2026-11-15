package locksupport;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * LockSupport 常见使用场景 Demo
 * <p>
 * 每个方法演示一个典型场景：
 * 1. 基础 park/unpark
 * 2. unpark 先于 park（不丢信号）
 * 3. 超时自动唤醒
 * 4. 中断唤醒（不抛异常）
 * 5. 生产者-消费者（最简版）
 * 6. 多条件队列（Condition 的 LockSupport 版思路）
 */
public class LockSupportDemo {

    public static void main(String[] args) throws Exception {
        // 按需打开，一次跑一个
//        basicParkUnpark();
         unparkBeforePark();
        // parkWithTimeout();
        // parkAndInterrupt();
        // producerConsumer();
        // multiConditionLike();
    }

    // ============================================================
    // 场景 1：基础用法 —— 一个线程等，一个线程唤醒
    // ============================================================
    static void basicParkUnpark() throws Exception {
        System.out.println("===== 场景1：基础 park/unpark =====");

        Thread t = new Thread(() -> {
            System.out.println("[工作线程] 准备 park");
            LockSupport.park();   // 阻塞，等 permit
            System.out.println("[工作线程] 被 unpark 唤醒");
        });

        t.start();
        TimeUnit.SECONDS.sleep(2);

        System.out.println("[主线程] unpark");
        LockSupport.unpark(t);

        t.join();
    }

    // ============================================================
    // 场景 2：unpark 先于 park —— 信号不丢（核心优势）
    // ============================================================
    static void unparkBeforePark() throws Exception {
        System.out.println("\n===== 场景2：unpark 先于 park =====");

        Thread t = new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(1); // 确保主线程先 unpark
            } catch (InterruptedException ignored) {
            }

            System.out.println("[工作线程] 准备 park（但 permit 已存在）");
            LockSupport.park(); // permit 已存在，立刻返回
            System.out.println("[工作线程] 没阻塞，直接通过");
        });

        t.start();

        // 先 unpark
        LockSupport.unpark(t);
        System.out.println("[主线程] 提前 unpark（信号不丢）");

        t.join();
    }

    // ============================================================
    // 场景 3：超时自动唤醒 —— parkNanos / parkUntil
    // ============================================================
    static void parkWithTimeout() throws Exception {
        System.out.println("\n===== 场景3：超时自动唤醒 =====");

        Thread t = new Thread(() -> {
            System.out.println("[工作线程] park 3秒");
            // 参数单位：纳秒
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(3));
            System.out.println("[工作线程] 超时自动唤醒");
        });

        t.start();
        t.join();
    }

    // ============================================================
    // 场景 4：中断唤醒 —— park 响应 interrupt，但不抛异常
    // ============================================================
    static void parkAndInterrupt() throws Exception {
        System.out.println("\n===== 场景4：中断唤醒 =====");

        Thread t = new Thread(() -> {
            System.out.println("[工作线程] park 等待中断");
            LockSupport.park();

            // 必须自己检查中断状态
            boolean interrupted = Thread.currentThread().isInterrupted();
            System.out.println("[工作线程] 被唤醒，中断状态=" + interrupted);
        });

        t.start();
        TimeUnit.SECONDS.sleep(2);

        System.out.println("[主线程] interrupt()");
        t.interrupt(); // 唤醒，但不抛异常

        t.join();
    }

    // ============================================================
    // 场景 5：生产者-消费者（最简版，无锁）
    // ============================================================
    static void producerConsumer() throws Exception {
        System.out.println("\n===== 场景5：生产者-消费者 =====");

        Object[] queue = new Object[1];
        Thread[] consumer = new Thread[1];

        consumer[0] = new Thread(() -> {
            System.out.println("[消费者] 等待数据...");
            while (queue[0] == null) {
                LockSupport.park(); // 没数据就 park
            }
            System.out.println("[消费者] 消费: " + queue[0]);
        });

        consumer[0].start();
        TimeUnit.SECONDS.sleep(1);

        // 生产者
        new Thread(() -> {
            queue[0] = "hello";
            System.out.println("[生产者] 生产数据");
            LockSupport.unpark(consumer[0]);
        }).start();

        consumer[0].join();
    }

    // ============================================================
    // 场景 6：多条件队列（一个 Lock + 多个 Condition 的底层思路）
    // ============================================================
    static void multiConditionLike() throws Exception {
        System.out.println("\n===== 场景6：多条件队列（LockSupport 版思路） =====");

        // 模拟两个等待队列
        Thread[] producers = new Thread[1];
        Thread[] consumers = new Thread[1];

        // 消费者等待
        consumers[0] = new Thread(() -> {
            System.out.println("[消费者] park 等待");
            LockSupport.park();
            System.out.println("[消费者] 被唤醒");
        });

        // 生产者等待
        producers[0] = new Thread(() -> {
            System.out.println("[生产者] park 等待");
            LockSupport.park();
            System.out.println("[生产者] 被唤醒");
        });

        consumers[0].start();
        producers[0].start();
        TimeUnit.SECONDS.sleep(1);

        // 只唤醒消费者
        System.out.println("[主线程] 只 unpark 消费者");
        LockSupport.unpark(consumers[0]);

        TimeUnit.SECONDS.sleep(1);
        System.out.println("[主线程] 再 unpark 生产者");
        LockSupport.unpark(producers[0]);

        consumers[0].join();
        producers[0].join();
    }
}