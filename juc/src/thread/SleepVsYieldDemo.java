package thread;

/**
 * sleep 与 yield 的细节区别
 * <p>
 * sleep(long)：
 * 1. 让当前线程进入 TIMED_WAITING，一定交出 CPU，到点后才回到 RUNNABLE
 * 2. 不释放锁（抱着锁睡觉）
 * 3. 时间到了不保证立刻执行，只是重新参与调度
 * <p>
 * yield()：
 * 1. 只是「提示」调度器我愿意让出 CPU，调度器完全可以无视
 * 2. 不释放锁，线程状态依然是 RUNNABLE，不会进入 WAITING
 * 3. 让出后线程马上又可能被再次选中，所以不能靠它控制执行顺序
 * <p>
 * 共同点：都不释放锁，都涉及线程调度。区别核心：sleep 会真的阻塞（状态变 TIMED_WAITING），yield 只是建议。
 */
public class SleepVsYieldDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "sleeper");

        Thread yielder = new Thread(() -> {
            Thread.yield(); // 提示让出，状态仍是 RUNNABLE
        }, "yielder");

        sleeper.start();
        yielder.start();

        // 【关键】观察状态：sleep 中的线程是 TIMED_WAITING
        Thread.sleep(100);
        System.out.println("sleep 中的线程状态 = " + sleeper.getState() + "  （TIMED_WAITING）");
        System.out.println("yield 的线程状态   = " + yielder.getState() + "  （RUNNABLE 或已终止，绝不会是 WAITING）");

        sleeper.join();
    }
}
