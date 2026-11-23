package deadlock;

/**
 * 活锁与饥饿：除了死锁，还要知道这两个并发问题
 * <p>
 * 死锁：多个线程互相持有对方需要的锁，都阻塞，谁也动不了。
 * 活锁：线程没有被阻塞，一直在积极地重试，但因为互相礼让，永远完不成任务。
 *       比如两人过马路，你让我我让你，反复侧身，谁也过不去。
 *       解决：重试时加随机退避时间，打破同步性。
 * 饥饿：某些线程因为优先级低或锁不公平，一直拿不到资源，永远得不到执行机会。
 *       解决：用公平锁（new ReentrantLock(true)），或提升优先级（但优先级只是提示）。
 * <p>
 * 本 demo 演示活锁：两个线程互相礼让，不停让路却永远撞在一起。
 */
public class LivelockAndStarvationDemo {

    static class Person {
        String name;
        boolean giveWay = false; // 是否正在让路

        Person(String name) { this.name = name; }

        // 每次相遇都让路，两个人都让 → 一起往同一个方向挪 → 永远相遇
        void react(Person other) {
            giveWay = !other.giveWay; // 【关键】机械地镜像对方动作，形成活锁
            System.out.println(name + " 让路=" + giveWay);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Person a = new Person("甲");
        Person b = new Person("乙");

        for (int i = 0; i < 3; i++) {
            a.react(b);
            b.react(a);
            System.out.println("两人还在原地打转，任务没进展 → 活锁");
        }
        System.out.println("解决思路：让路时加 Thread.sleep(随机毫秒)，打破双方完全同步的礼让");

        // 饥饿：非公平锁下，理论上有线程长期抢不到锁（此处只演示公平锁的写法）
        java.util.concurrent.locks.ReentrantLock fairLock = new java.util.concurrent.locks.ReentrantLock(true);
        System.out.println("公平锁=" + fairLock.isFair() + "，可缓解饥饿（但吞吐量更低）");
    }
}
