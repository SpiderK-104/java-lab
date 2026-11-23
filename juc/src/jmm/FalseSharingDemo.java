package jmm;

/**
 * 伪共享（False Sharing）演示：看似无关的变量，其实拖慢彼此
 * <p>
 * 核心：CPU 缓存以「缓存行」为单位（通常 64 字节），一个缓存行能放 8 个 long。
 * 两个线程各改一个 long，如果这两个 long 恰好落在同一缓存行，
 * 一个线程写会让另一个线程的缓存行失效，来回 invalidation，性能暴跌。
 * 变量本身没有共享，共享的是它们所在的缓存行，所以叫「伪」共享。
 * <p>
 * 解决：让每个热点变量独占一个缓存行——手动 padding（本例），
 * 或 JDK8 用 @sun.misc.Contended 注解（需 -XX:-RestrictContended 才对用户类生效）。
 * LongAdder 的 Cell 数组、Disruptor 的 RingBuffer 都用了这个思路。
 */
public class FalseSharingDemo {

    // 不加 padding：a、b 紧挨着，大概率在同一个缓存行
    static class Bad {
        volatile long a;
        volatile long b;
    }

    // 加 padding：a 和 b 之间塞 7 个 long，凑够 64 字节，强制分到不同缓存行
    static class Good {
        volatile long a;
        long p1, p2, p3, p4, p5, p6, p7;
        volatile long b;
    }

    private static final int LOOP = 10_000_000;

    public static void main(String[] args) throws InterruptedException {
        // 注：JIT 可能把没用到的字段优化掉，可用 -XX:-RestrictContended 或真实业务观察更准
        long t1 = System.currentTimeMillis();
        runBad();
        long t2 = System.currentTimeMillis();
        runGood();
        long t3 = System.currentTimeMillis();

        System.out.println("伪共享耗时   = " + (t2 - t1) + " ms");
        System.out.println("消除后耗时   = " + (t3 - t2) + " ms");
    }

    // 两个线程分别狂写 a 和 b，互相使缓存行失效
    static void runBad() throws InterruptedException {
        Bad obj = new Bad();
        Thread t1 = new Thread(() -> { for (int i = 0; i < LOOP; i++) obj.a = i; });
        Thread t2 = new Thread(() -> { for (int i = 0; i < LOOP; i++) obj.b = i; });
        t1.start(); t2.start();
        t1.join(); t2.join();
    }

    static void runGood() throws InterruptedException {
        Good obj = new Good();
        Thread t1 = new Thread(() -> { for (int i = 0; i < LOOP; i++) obj.a = i; });
        Thread t2 = new Thread(() -> { for (int i = 0; i < LOOP; i++) obj.b = i; });
        t1.start(); t2.start();
        t1.join(); t2.join();
    }
}
