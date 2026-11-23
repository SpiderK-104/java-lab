package pool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Executors 工厂方法的坑：为什么阿里规约禁止直接使用
 * <p>
 * newFixedThreadPool / newSingleThreadExecutor：用无界 LinkedBlockingQueue，
 *   任务提交速度 > 处理速度时队列无限膨胀 → OOM。
 * newCachedThreadPool / newScheduledThreadPool：最大线程数 Integer.MAX_VALUE，
 *   高并发下无限创建线程 → OOM。
 * <p>
 * 正确做法：手动 new ThreadPoolExecutor，指定有界队列、合理的核心/最大线程数、拒绝策略。
 * <p>
 * 本 demo 只展示各工厂方法底层参数，不真的压测（真压会 OOM），结论看注释。
 */
public class ExecutorsPitfallDemo {

    public static void main(String[] args) {
        // 固定线程池：core=max=n，队列无界 → 队列堆积 OOM
        ExecutorService fixed = Executors.newFixedThreadPool(4);

        // 单线程：core=max=1，队列无界 → 同上
        ExecutorService single = Executors.newSingleThreadExecutor();

        // 缓存线程池：core=0，max=Integer.MAX_VALUE，60s 空闲回收 → 线程爆炸 OOM
        ExecutorService cached = Executors.newCachedThreadPool();

        // 定时线程池：max=Integer.MAX_VALUE → 同 cached 的风险
        ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);

        // 推荐：手写，参数全部可控
        ThreadPoolExecutor safe = new ThreadPoolExecutor(
                4, 8,           // core / max 线程数
                60, TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(1000), // 【关键】有界队列
                new ThreadPoolExecutor.CallerRunsPolicy());          // 拒绝策略：调用者自己跑

        System.out.println("fixed 底层 = " + fixed.getClass().getSimpleName());
        System.out.println("手写线程池队列容量 = " + safe.getQueue().remainingCapacity());
        System.out.println("结论：生产环境手写 ThreadPoolExecutor，别用 Executors 默认工厂");

        fixed.shutdown(); single.shutdown(); cached.shutdown(); scheduled.shutdown(); safe.shutdown();
    }
}
