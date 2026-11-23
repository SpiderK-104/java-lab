package pool;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ScheduledThreadPoolExecutor 的坑：任务抛异常会导致后续定时执行静默停止
 * <p>
 * 核心：定时任务里如果抛出未捕获异常，Future 会让这个任务结束，
 * 而且控制台往往没有任何日志（异常被吞进 Future），后续周期执行就不再触发了。
 * 排查时非常隐蔽：任务莫名其妙不跑了，还没有报错。
 * <p>
 * 解决：在定时任务内部 try-catch 兜底，把异常记日志；
 * 或重写 afterExecute 统一处理，或给 Future 加回调主动 get() 触发异常。
 */
public class ScheduledPoolExceptionDemo {

    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);

        // 反面教材：任务里抛异常，且异常被 Future 吞掉，print 两次后就不动了
        pool.scheduleAtFixedRate(() -> {
            System.out.println("危险任务执行中...");
            int i = 1 / 0; // 【关键】模拟未捕获异常 → 这个定时任务之后再也不执行，且无日志
        }, 0, 1, TimeUnit.SECONDS);

        Thread.sleep(3500);
        System.out.println("观察：危险任务本应打印 3~4 次，实际只打印 1 次就停了，且没有异常堆栈");
        pool.shutdownNow();
    }
}
