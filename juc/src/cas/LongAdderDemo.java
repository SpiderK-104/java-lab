package cas;

import java.util.concurrent.atomic.LongAdder;

/**
 * LongAdder 计数器示例
 * <p>
 * 与 AtomicInteger 的核心区别：
 * 1. 底层继承 Striped64，使用分段累加策略
 * 2. 将单个热点变量拆分为 CPU 核数个 Cell 槽，线程通过 probe 哈希到各自槽位写入
 * 3. 只有哈希冲突时才会 CAS 自旋，竞争烈度从 O(N) 降到 O(1)
 * 4. sum() 最终合并所有槽的值，适合高并发计数/累加场景
 * <p>
 * 适用场景：统计、埋点、QPS 计数（不要求强一致，最终一致即可）
 * 不适用：需要精确读取最新值的状态位（此时用 AtomicLong）
 */
public class LongAdderDemo {
    static LongAdder adder = new LongAdder();

    public static void main(String[] args) throws InterruptedException {
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) adder.increment();
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        System.out.println(adder.sum()); // 10000（join 后读，精确）
    }
}