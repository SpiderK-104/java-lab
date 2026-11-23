package cas;

import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * ABA 问题演示 + AtomicStampedReference 解决
 */
public class ABADemo {
    // 初始值 A，版本号 1
    private static AtomicStampedReference<String> ref =
            new AtomicStampedReference<>("A", 1);

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            int stamp = ref.getStamp();
            System.out.println("T1 读取到值：" + ref.getReference() + "，版本：" + stamp);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            // CAS：期望 A，期望版本 stamp，更新为 C，版本+1
            boolean success = ref.compareAndSet("A", "C", stamp, stamp + 1);
            System.out.println("T1 CAS 结果：" + success + "，当前值：" + ref.getReference());
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            // 先把 A -> B
            ref.compareAndSet("A", "B", ref.getStamp(), ref.getStamp() + 1);
            System.out.println("T2 修改为 B，版本：" + ref.getStamp());
            // 再把 B -> A（产生 ABA）
            ref.compareAndSet("B", "A", ref.getStamp(), ref.getStamp() + 1);
            System.out.println("T2 修改回 A，版本：" + ref.getStamp());
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }
}