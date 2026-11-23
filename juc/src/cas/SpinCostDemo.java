package cas;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自旋开销演示：持续自旋 + 手动中断，让你在运行中随时抓 RUNNABLE + compareAndSetInt 现场
 */
public class SpinCostDemo {

    public static void main(String[] args) throws InterruptedException, IOException {
        int threadCount = 200;
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger progress = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        long start = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        count.incrementAndGet();
                        progress.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
            threads[i].start();
        }

        Thread.sleep(500);

        System.out.println("========================================");
        System.out.println("  所有 " + threadCount + " 个线程正在持续自旋 incrementAndGet！");
        System.out.println("  此时抓栈能看到 RUNNABLE + compareAndSetInt 现场");
        System.out.println("========================================");
        System.out.println();
        System.out.println("  PowerShell 命令：");
        System.out.println("  jps                                          ← 拿到 SpinCostDemo PID");
        System.out.println("  jstack -l <PID> | Select-String 'Thread-' -Context 0,10");
        System.out.println();
        System.out.println("  正在跑的总操作数: " + progress.get() + "（会不断增长）");
        System.out.println("========================================");
        System.out.println();
        System.out.println("  现在开 PowerShell 抓栈！抓完按回车停止程序...");
        System.out.println();

        Thread printer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println("  [运行中] 总 incrementAndGet 次数: " + progress.get()
                        + "  已耗时: " + (System.currentTimeMillis() - start) + "ms");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        printer.setDaemon(true);
        printer.start();

        System.in.read();

        for (Thread t : threads) t.interrupt();
        for (Thread t : threads) t.join();

        long totalCost = System.currentTimeMillis() - start;
        System.out.println();
        System.out.println("Final count: " + count.get());
        System.out.println("Total operations: " + progress.get());
        System.out.println("Total time: " + totalCost + "ms");
    }
}