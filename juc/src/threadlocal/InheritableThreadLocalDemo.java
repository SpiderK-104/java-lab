package threadlocal;

/**
 * InheritableThreadLocal 与它的线程池陷阱
 * <p>
 * ThreadLocal：每个线程独立一份变量副本，线程之间隔离。
 * InheritableThreadLocal：子线程创建时会继承父线程 ThreadLocal 的值（在 Thread 构造时拷贝）。
 * <p>
 * 大坑：线程池场景失效。线程池里的线程是复用的，不会新建，
 * 所以「创建时继承」这个动作只发生一次（线程第一次创建时），后续任务提交时不会重新继承。
 * 结果就是：任务 A 设置的上下文被任务 B 读到，串数据。
 * <p>
 * 解决：阿里开源的 Transmittable-ThreadLocal（TTL），重写了线程池的包装，
 * 在任务提交时捕获上下文、执行时回放，专门解决线程池父子线程上下文传递。
 * 业务里如 MDC 日志 traceId 跨线程池传递，就是用它。
 */
public class InheritableThreadLocalDemo {

    static InheritableThreadLocal<String> context = new InheritableThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        // 场景一：普通 new Thread，子线程能继承到父线程的值
        context.set("父线程的traceId");
        new Thread(() -> {
            System.out.println("子线程继承到的值=" + context.get());
        }).start();
        Thread.sleep(100);

        // 场景二：线程池复用线程，继承失效
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(1);
        context.set("任务1的上下文");
        pool.submit(() -> System.out.println("线程池任务1读到=" + context.get()));
        Thread.sleep(100);
        context.set("任务2的上下文");
        pool.submit(() -> System.out.println("线程池任务2读到=" + context.get() + "（可能还是任务1的，因为线程被复用）"));
        pool.shutdown();
    }
}
