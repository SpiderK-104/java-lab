package thread;

/**
 * 废弃的线程 API：stop() 和 suspend() 为什么被淘汰
 * <p>
 * stop()：直接终止线程，会立即释放该线程持有的所有锁，导致对象处于半更新状态。
 *         比如转账刚扣了 A 的钱还没加到 B 就被 stop，数据不一致，且无法恢复。
 *         替代方案：用 interrupt() 协作式中断，由线程自己决定何时安全退出。
 * <p>
 * suspend()：挂起线程但不释放锁。如果目标线程挂起时正持有某把锁，
 *            其他线程要这把锁就会一直等，极易死锁。
 *            替代方案：用 wait()/notify() 或 LockSupport.park()/unpark()，它们会释放锁或可精确控制。
 * <p>
 * 注意：这两个方法已被标记 @Deprecated，代码中只做说明，不再实际调用。
 */
public class DeprecatedThreadApiDemo {

    // 账户：演示 stop 破坏一致性的问题场景
    static class Account {
        int from;
        int to;

        // 转账两步：先扣后加，两步之间如果被强行终止，钱就凭空消失
        void transfer() {
            from -= 100;
            // 【危险点】旧代码在这里 stop()，from 已扣、to 未加 → 数据不一致
            to += 100;
        }
    }

    // 正确做法：协作式中断，线程在安全点自己检查并退出
    static void safeWay() throws InterruptedException {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // 干一段活，安全点主动检查中断标记
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复标记后退出
                    break;
                }
            }
        });
        t.start();
        t.interrupt(); // 请求中断，而不是强杀
        t.join();
    }

    public static void main(String[] args) throws InterruptedException {
        Account acc = new Account();
        acc.from = 1000;
        acc.to = 0;
        acc.transfer();
        System.out.println("正常转账后：from=" + acc.from + ", to=" + acc.to + "（总和恒为 1000）");

        safeWay();
        System.out.println("协作式中断：线程在安全点自行退出，数据不会被撕裂");
    }
}
