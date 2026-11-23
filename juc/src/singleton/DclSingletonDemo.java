package singleton;

/**
 * DCL 双重检查锁单例：面试经典，为什么 instance 必须 volatile
 * <p>
 * new 一个对象分三步：
 * 1. 分配内存空间
 * 2. 初始化对象（调用构造方法填字段）
 * 3. 把引用指向这块内存（instance = 对象）
 * <p>
 * 不加 volatile 时，JVM 可能把 2 和 3 重排序：先让 instance 指向了还没初始化的「半对象」，
 * 另一个线程在第一次检查时看到 instance != null，直接拿走一个半成品，用到未初始化的字段就出错。
 * volatile 禁止这个重排序，保证对象初始化完成后引用才可见。
 * <p>
 * 双重检查的意义：外层 if 避免每次都加锁（性能），内层 if 防止多线程同时通过外层检查后重复创建。
 */
public class DclSingletonDemo {

    // 【关键】 volatile 禁止 new 对象的指令重排序，防止半初始化对象逸出
    private static volatile DclSingletonDemo instance;

    private DclSingletonDemo() {}

    public static DclSingletonDemo getInstance() {
        if (instance == null) {                    // 第一重检查：不加锁快速判断，提升性能
            synchronized (DclSingletonDemo.class) {
                if (instance == null) {            // 第二重检查：防止并发下重复创建
                    instance = new DclSingletonDemo();
                }
            }
        }
        return instance;
    }

    public static void main(String[] args) {
        DclSingletonDemo a = DclSingletonDemo.getInstance();
        DclSingletonDemo b = DclSingletonDemo.getInstance();
        System.out.println("是否同一个实例=" + (a == b));
    }
}
