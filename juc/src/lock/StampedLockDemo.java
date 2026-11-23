package lock;

import java.util.concurrent.locks.StampedLock;

/**
 * StampedLock：JDK8 引入，解决 ReentrantReadWriteLock 写饥饿问题
 * <p>
 * 三种模式：写锁、悲观读锁、乐观读。
 * 核心是乐观读：读的时候不加锁（不阻塞写线程），读完用一个 stamp 校验期间有没有被写线程改过，
 * 没被改就直接用了，被改了再升级成悲观读锁重读一次。读极多写极少的场景性能远高于读写锁。
 * <p>
 * 注意：StampedLock 不可重入，不支持 Condition。
 * 坑点：validate 返回 false 时要手动升级为悲观读锁重读，框架不会自动帮你做。
 */
public class StampedLockDemo {

    private final StampedLock lock = new StampedLock();
    private int x, y;

    // 写：拿写锁，返回 stamp 用于释放
    void movePoint(int dx, int dy) {
        long stamp = lock.writeLock();
        try {
            x += dx;
            y += dy;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // 读：先乐观读，校验失败再升级为悲观读
    double distance() {
        // 【关键】tryOptimisticRead 不加锁，返回一个 stamp
        long stamp = lock.tryOptimisticRead();
        int curX = x, curY = y;
        // 校验：从乐观读到读完这段时间，有没有写线程改过数据
        if (!lock.validate(stamp)) {
            // 被改过，升级为悲观读锁，重新读一次
            stamp = lock.readLock();
            try {
                curX = x;
                curY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return Math.sqrt(curX * curX + curY * curY);
    }

    public static void main(String[] args) {
        StampedLockDemo demo = new StampedLockDemo();
        demo.movePoint(3, 4);
        System.out.println("距离=" + demo.distance()); // 5.0
    }
}
