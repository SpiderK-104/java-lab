package collection;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 阻塞队列全家桶：put/take 阻塞，offer/poll 非阻塞
 * <p>
 * 四组 API 要分清：
 * throw    ：add/remove/element（失败抛异常）
 * false/null：offer/poll/peek（失败返回特殊值，不阻塞）
 * block    ：put/take（队列满/空时阻塞等待）
 * timeout  ：offer(e,t)/poll(t)（阻塞但带超时）
 * <p>
 * 常见实现：
 * ArrayBlockingQueue   ：数组实现，有界，必须指定容量
 * LinkedBlockingQueue  ：链表实现，可无界（默认 Integer.MAX_VALUE），Executors 无界队列坑就来自它
 * PriorityBlockingQueue：无界，按优先级出队
 * DelayQueue           ：无界，元素到期才能取出，用 Delayed 接口，适合定时任务/缓存过期
 * <p>
 * ConcurrentLinkedQueue 则是无锁非阻塞队列（CAS 实现），不在阻塞队列范畴。
 */
public class BlockingQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        arrayAndLinked();
        priorityQueue();
        delayQueue();
    }

    static void arrayAndLinked() throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2); // 有界，容量 2
        queue.put("a");
        queue.put("b");
        // queue.put("c"); // 队列满，put 会阻塞在这里
        boolean ok = queue.offer("c"); // 【关键】offer 不阻塞，满了立刻返回 false
        System.out.println("ArrayBlockingQueue offer 满时返回=" + ok);
        System.out.println("take=" + queue.take()); // 空时阻塞

        BlockingQueue<String> linked = new LinkedBlockingQueue<>(); // 默认无界 → OOM 风险来源
        System.out.println("LinkedBlockingQueue 默认剩余容量=" + linked.remainingCapacity());
    }

    static void priorityQueue() {
        PriorityBlockingQueue<Integer> pq = new PriorityBlockingQueue<>();
        pq.add(3); pq.add(1); pq.add(2);
        System.out.println("PriorityBlockingQueue 出队顺序=" + pq.poll() + "," + pq.poll() + "," + pq.poll()); // 1,2,3
    }

    // 延迟队列：元素必须实现 Delayed，没到期 take 不出来
    static void delayQueue() throws InterruptedException {
        DelayQueue<DelayTask> dq = new DelayQueue<>();
        dq.put(new DelayTask("订单超时关闭", 500));
        System.out.println("DelayQueue 中元素到期后才可取出（此处阻塞等待 500ms）");
        System.out.println("取出=" + dq.take().name);
    }

    static class DelayTask implements Delayed {
        String name;
        long expireAt;

        DelayTask(String name, long delayMs) {
            this.name = name;
            this.expireAt = System.currentTimeMillis() + delayMs;
        }

        // 返回剩余延迟，<=0 表示到期可取
        public long getDelay(TimeUnit unit) {
            return unit.convert(expireAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        public int compareTo(Delayed o) {
            return Long.compare(this.expireAt, ((DelayTask) o).expireAt);
        }
    }
}
