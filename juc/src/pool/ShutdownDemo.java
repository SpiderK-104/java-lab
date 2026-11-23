package pool;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 线程池关闭三兄弟：shutdown / shutdownNow / awaitTermination
 * <p>
 * shutdown()：温和关闭。不接收新任务，但队列里的已提交任务继续执行完。
 * shutdownNow()：粗暴关闭。尝试中断正在执行的任务，返回队列中还没开始执行的任务列表。
 *               注意是「尝试」中断，任务内部不响应中断的话照样跑完。
 * awaitTermination(timeout)：阻塞等待线程池彻底关闭，通常配合 shutdown 使用，
 *                          超过时间还没关完就返回 false，可以做兜底强关。
 * <p>
 * 优雅关闭三连：shutdown() → awaitTermination(等待) → 超时则 shutdownNow()。
 * <p>
 * 另一个知识点：allowCoreThreadTimeOut(true) 默认 false，
 * 核心线程空闲时不会被回收；设为 true 后核心线程空闲超时也会被回收。
 */
public class ShutdownDemo {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            pool.submit(() -> {
                try {
                    Thread.sleep(300);
                    System.out.println("任务 " + taskId + " 执行完");
                } catch (InterruptedException e) {
                    System.out.println("任务 " + taskId + " 被中断");
                }
            });
        }

        // 优雅关闭：先温和关闭，再等待，超时就强关
        pool.shutdown(); // ① 不接新任务，队列任务继续跑
        if (!pool.awaitTermination(2, TimeUnit.SECONDS)) { // ② 最多等 2 秒
            List<Runnable> notRun = pool.shutdownNow(); // ③ 超时强关，返回未执行任务
            System.out.println("未执行任务数=" + notRun.size());
        }
        System.out.println("线程池已关闭=" + pool.isShutdown());

        // 核心线程超时回收开关演示
        java.util.concurrent.ThreadPoolExecutor tp = new java.util.concurrent.ThreadPoolExecutor(
                2, 4, 30, TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
        System.out.println("默认 allowCoreThreadTimeOut=" + tp.allowsCoreThreadTimeOut()); // false
        tp.allowCoreThreadTimeOut(true); // 设为 true 后核心线程空闲也会被回收
        System.out.println("设置后=" + tp.allowsCoreThreadTimeOut());
        tp.shutdown();
    }
}
