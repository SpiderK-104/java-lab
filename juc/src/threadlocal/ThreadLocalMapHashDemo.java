package threadlocal;

/**
 * ThreadLocalMap 的哈希冲突处理：开放寻址法（线性探测）
 * <p>
 * 面试挖坑点：ThreadLocalMap 不是链表法（不像 HashMap 用数组+链表/红黑树），
 * 它用的是开放寻址法中的线性探测——冲突了就往后找下一个空位置放。
 * <p>
 * 核心设计：
 * 1. key 是 ThreadLocal 弱引用，value 是强引用（所以不 remove 会内存泄漏）
 * 2. 每个 Thread 持有一个 ThreadLocalMap
 * 3. 冲突时 index+1 往后探测，遇到 null 槽就放下
 * <p>
 * 本 demo 用极简数组模拟线性探测过程，说明开放寻址的核心思想，非 ThreadLocalMap 源码。
 */
public class ThreadLocalMapHashDemo {

    static final int SIZE = 8;
    static final String[] table = new String[SIZE];

    // 线性探测：冲突就往后挪一格，直到找到空位
    static void put(int hash, String value) {
        int index = hash & (SIZE - 1); // 取模定位（ThreadLocalMap 用 0x7fffffff & 长度-1）
        while (table[index] != null) {
            System.out.println("  槽 " + index + " 被占，往后探测");
            index = (index + 1) & (SIZE - 1); // 【关键】线性探测，不是挂链表
        }
        table[index] = value;
        System.out.println("  放入槽 " + index + " -> " + value);
    }

    public static void main(String[] args) {
        System.out.println("放 key1：");
        put(1, "value1");
        System.out.println("放 key9（hash 后也落到槽1，冲突）：");
        put(9, "value9");
        System.out.println("放 key2：");
        put(2, "value2");
        System.out.println("结论：冲突时向后找空槽，这就是开放寻址法，区别于 HashMap 的链地址法");
    }
}
