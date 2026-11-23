package thread;

/**
 * 线程优先级：只是提示，不能依赖
 * <p>
 * Java 线程优先级 1~10（MIN=1，NORM=5，MAX=10），映射到操作系统的线程优先级。
 * 但不同 OS 对优先级的支持和映射规则不一样，有些系统直接忽略，
 * 而且优先级不能保证执行顺序，只能影响被调度的概率。
 * <p>
 * 结论：业务代码不要靠 setPriority 控制执行先后，需要顺序就显式用锁、队列、join 等同步手段。
 */
public class ThreadPriorityDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread low = new Thread(() -> count(), "低优先级");
        Thread high = new Thread(() -> count(), "高优先级");

        low.setPriority(Thread.MIN_PRIORITY);   // 1
        high.setPriority(Thread.MAX_PRIORITY);  // 10

        low.start();
        high.start();
        low.join();
        high.join();

        // 结果只是「高优先级大概率先跑完」，跨平台、跨机器结果不保证，这就是不能依赖的原因
        System.out.println("优先级仅为调度提示，高优先级不保证一定先执行完");
    }

    static void count() {
        long sum = 0;
        for (int i = 0; i < 100_000_000; i++) sum += i;
        System.out.println(Thread.currentThread().getName() + " 完成");
    }
}
