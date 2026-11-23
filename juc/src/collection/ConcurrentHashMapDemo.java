package collection;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap：原理演进与常见坑
 * <p>
 * JDK7：分段锁 Segment，每个段一把锁，不同段可并发，默认 16 段。
 * JDK8：取消分段锁，改成 CAS + synchronized 锁单个桶的头节点，锁粒度更细，并发度更高。
 * <p>
 * size() 返回 int（超过 Integer.MAX_VALUE 会截断），mappingCount() 返回 long，推荐后者。
 * 扩容时其他线程可以 helpTransfer 协助搬迁，加快扩容。
 * <p>
 * 坑1：computeIfAbsent 的映射函数里再对同一个 map 做 put/remove，会死锁或死循环（递归更新）。
 * 坑2：不能拿它当 HashMap 用做复合操作——get() 和 put() 两步之间仍有并发窗口，
 *      要用 putIfAbsent / compute / merge 这些原子方法。
 */
public class ConcurrentHashMapDemo {

    private final ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

    // 正确：复合操作用原子方法，一步到位
    public int increment(String key) {
        // 【关键】compute 是原子的，整个读改写过程在同一把桶锁内完成
        return map.compute(key, (k, v) -> v == null ? 1 : v + 1);
    }

    // 错误示范（不要这么写）：两步之间其他线程可能插进来，丢失更新
    public int wrongIncrement(String key) {
        Integer v = map.get(key);   // 第一步
        v = (v == null ? 0 : v) + 1;
        map.put(key, v);            // 第二步：中间有并发窗口
        return v;
    }

    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMapDemo demo = new ConcurrentHashMapDemo();
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < 10000; j++) demo.increment("cnt"); });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        System.out.println("原子 compute 结果=" + demo.map.get("cnt") + "（应为 100000）");
        System.out.println("size()=" + demo.map.size() + ", mappingCount()=" + demo.map.mappingCount());
        System.out.println("坑：computeIfAbsent 内再改同一个 map 会死循环；复合操作要用 compute/merge");
    }
}
