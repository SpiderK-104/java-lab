package cas;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产级 CAS 计数器示例
 * <p>
 * 要点：
 * 1. 使用 AtomicInteger（底层 CAS）
 * 2. 不手写自旋（JDK 已优化）
 * 3. 方法命名清晰、可复用
 * 4. 适合高并发计数场景
 */
public class AtomicCounterDemo {

    private final AtomicInteger count = new AtomicInteger(0);

    /**
     * 递增并返回最新值
     */
    public int increment() {
        return count.incrementAndGet();
    }

    /**
     * 获取当前值
     */
    public int getCount() {
        return count.get();
    }

    // ============ 测试 ============
    public static void main(String[] args) throws InterruptedException {
        AtomicCounterDemo counter = new AtomicCounterDemo();

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        // 每个线程
        for (int i = 0; i < threadCount; i++) {
            // 每个线程各自独立的对同一个counter做1000次自增操作
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    counter.increment();
                }
            });
            threads[i].start();
        }
        // 等待上面这些线程全部跑完。
        for (Thread t : threads) {
            // 代表："我（主线程）先在这儿等着，等你 t 执行完了再往下走。"
            t.join();
        }
        // 最后结果一定是10000
        System.out.println("Final count: " + counter.getCount());
    }
}