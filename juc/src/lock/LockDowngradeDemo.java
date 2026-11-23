package lock;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 读写锁的锁降级：持有写锁时获取读锁，再释放写锁
 * <p>
 * 读锁共享、写锁独占。ReentrantReadWriteLock 的 state 高 16 位存读锁计数，低 16 位存写锁计数。
 * <p>
 * 锁降级（允许）：写锁 → 获取读锁 → 释放写锁。
 * 为什么要降级：写完数据后要读取，如果直接释放写锁，中间可能被别的线程改掉；
 * 先占住读锁再放写锁，就能保证读到的是自己刚写的值。
 * <p>
 * 锁升级（不支持）：读锁 → 想拿写锁，会直接死锁——因为读锁是共享的，
 * 其他线程也持读锁，写锁要等所有读锁释放，而自己又拿着读锁不放，互相等。
 * <p>
 * 弊端：读多写少时，写线程可能长期拿不到锁（写饥饿）；改进版是 StampedLock。
 */
public class LockDowngradeDemo {

    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rw.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rw.writeLock();
    private int value = 0;

    public void updateThenRead() {
        writeLock.lock();
        try {
            value = 100;                 // ① 拿写锁，改数据
            readLock.lock();             // ② 降级：写锁还没放，先拿读锁
            System.out.println("当前写锁持有数=" + rw.getWriteHoldCount());
        } finally {
            writeLock.unlock();          // ③ 释放写锁，降级完成，手里只剩读锁
        }
        try {
            System.out.println("降级后读到的值=" + value); // ④ 用读锁读，值不会被别人改
        } finally {
            readLock.unlock();
        }
    }

    public static void main(String[] args) {
        new LockDowngradeDemo().updateThenRead();
        // 注意：读锁升级写锁的代码这里不演示，运行会死锁。记住结论：不支持升级即可。
    }
}
