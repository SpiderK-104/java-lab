package collection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * CopyOnWriteArrayList：写时复制容器
 * <p>
 * 核心：读不加锁，写的时候先把整个数组复制一份，在新数组上改，改完再把引用指过去。
 * 所以读操作完全无锁，性能极高；写操作要加锁 + 复制整个数组，开销大。
 * <p>
 * 适合：读极多、写极少，且能容忍「读到的是旧数据」（弱一致性）的场景。
 * 比如事件监听器列表、配置缓存、黑白名单。
 * 不适合：写频繁（每次复制数组的代价很大），或要求实时一致性的场景。
 * <p>
 * 迭代器是弱一致的：遍历时其他线程新增/删除的元素看不到，也不会抛 ConcurrentModificationException。
 * CopyOnWriteArraySet 底层就是包了一层 CopyOnWriteArrayList。
 */
public class CopyOnWriteDemo {

    private final List<String> listeners = new CopyOnWriteArrayList<>();

    public void addListener(String name) {
        // 【关键】写操作内部会复制整个数组，加 synchronized 锁，代价高
        listeners.add(name);
    }

    public void notifyAllListeners(String event) {
        // 读操作无锁，遍历的是调用那一刻的快照，安全且快
        for (String listener : listeners) {
            System.out.println(listener + " 收到事件 " + event);
        }
    }

    public static void main(String[] args) {
        CopyOnWriteDemo demo = new CopyOnWriteDemo();
        demo.addListener("监听器A");
        demo.addListener("监听器B");
        demo.notifyAllListeners("order.created");
        System.out.println("结论：读多写少用 CopyOnWrite，写频繁会疯狂复制数组导致性能差");

        CopyOnWriteArraySet<String> set = new CopyOnWriteArraySet<>();
        set.add("x");
        System.out.println("CopyOnWriteArraySet 底层 = CopyOnWriteArrayList");
    }
}
