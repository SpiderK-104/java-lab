# JUC 笔记补充

这份是原笔记的补充，只写缺失的知识点，风格参照项目里的 Stream_API.md。每个知识点先讲清楚是什么、为什么，再给一段能体现核心思想的代码，代码块上面写类说明。代码太长的不贴全文，直接说参考 java-lab 里哪个类。所有 demo 都在 juc/src 下，能直接跑。

## 一、并发基础与 JMM

### 1.1 happens-before 的正确理解

很多人以为 happens-before 是「阻止重排序」的机制，其实它是 JVM 对程序员的可见性承诺：如果 A happens-before B，那么 A 的写入对 B 一定可见，不管中间怎么重排序。

只要满足 happens-before 规则，JVM 就保证可见性；不满足的场景，JVM 可以随意重排序，看到什么结果都不奇怪。

常用的几条规则，记住就够用：

- 程序顺序规则：同一个线程里，前面的操作 happens-before 后面的操作
- volatile 规则：对 volatile 变量的写，happens-before 后续对这个变量的读
- 传递性：A hb B，B hb C，那么 A hb C
- 线程启动：Thread.start() happens-before 该线程里的任何操作
- 线程终止：线程的所有操作 happens-before 其他线程检测到它结束（join 返回）

### 1.2 volatile 为什么救不了 i++

结论：volatile 保证可见性和禁止重排序，但不保证原子性，i++ 用它照样丢数据。

底层原因：i++ 看着是一行，实际是三步，读 i 的值、加 1、写回 i。volatile 只能保证读这一步读到最新值、写这一步立刻刷回主内存，管不住三步中间被别的线程插进来。

两个线程同时 i++，都读到 0，都算出 1，都写回 1，最后 i 是 1 而不是 2，这就是丢失更新。

解决：用 AtomicInteger（CAS）或者 synchronized，把三步合成一个原子操作。

类说明：下面这段对比 volatile int 和 AtomicInteger，10 个线程各加 10000 次，看谁丢数据。核心就在 count++ 这行不是原子的。

```java
static volatile int count = 0;                    // 可见性有，原子性没有
static AtomicInteger atomicCount = new AtomicInteger(0);

// 每个线程里循环：
count++;                    // 读-改-写三步，多线程交叉 → 丢数据
atomicCount.incrementAndGet(); // CAS，三步合成一步 → 不丢
```

完整代码参考 juc/src/jmm/VolatileNotAtomicDemo.java，实测 volatile 版只有 5 万多（期望 10 万），AtomicInteger 版恒等于 10 万。

### 1.3 final 域的内存语义

JMM 对 final 字段有专门承诺，这是 final 线程安全的原因：

- 只要在构造方法里把 final 字段写完整，且构造过程中没有把 this 发布出去，其他线程拿到这个对象时，一定能看到 final 字段的正确值，不需要额外同步
- final 引用指向的对象，它的字段在构造结束后才对其他线程可见，避免别人看到半初始化的对象

唯一的破口是 this 逸出：构造过程中把 this 传给别的线程，对方可能读到还没赋值完的 final 字段。所以构造期间不要让 this 逃逸。

类说明：SafeObject 是正确写法，EscapeObject 在构造里把 this 发布出去，可能读到默认值 0。

```java
// 正确：构造完 this 才发布，final 可见性有保证
class SafeObject {
    final int x;
    SafeObject(int x) { this.x = x; }
}

// 错误：构造中途把 this 逸出
class EscapeObject {
    final int value;
    EscapeObject(EscapeHolder holder) {
        holder.obj = this;   // 关键：this 在构造完成前就发布了
        this.value = 100;
    }
}
```

完整代码参考 juc/src/jmm/FinalFieldDemo.java。

### 1.4 伪共享（高频）

现象：多个变量在同一个 CPU 缓存行里（一行通常 64 字节，能放 8 个 long），多线程各改各的变量，却互相让对方的缓存行失效，来回 invalidation，性能暴跌。变量本身没共享，共享的是它们所在的缓存行，所以叫「伪」共享。

解决：让热点变量独占一个缓存行。两种做法：

- 手动 padding，在变量之间塞 7 个 long 凑够 64 字节
- 加 @Contended 注解（JDK8 是 sun.misc.Contended，JDK9+ 是 jdk.internal.vm.annotation.Contended），JDK8 用户类需要 -XX:-RestrictContended 才生效

LongAdder 的 Cell 数组、Disruptor 的 RingBuffer 都用了这个思路。

类说明：Bad 里 a、b 紧挨着大概率在同一缓存行，Good 里塞了 7 个 long 强制分开，两个线程各狂写一个变量，对比耗时。

```java
static class Bad {            // 伪共享
    volatile long a;
    volatile long b;          // 和 a 在同一缓存行，互相失效
}
static class Good {           // 消除伪共享
    volatile long a;
    long p1,p2,p3,p4,p5,p6,p7; // 填充 7 个 long，凑够一行
    volatile long b;
}
```

完整代码参考 juc/src/jmm/FalseSharingDemo.java。

## 二、线程基础

### 2.1 stop()、suspend() 为什么被淘汰

这两个 API 已被标记废弃，面试常问原因：

- stop()：直接终止线程，会立即释放该线程持有的所有锁，对象停在半更新状态。比如转账刚扣了 A 的钱还没加到 B 就被 stop，数据不一致且无法恢复。替代方案是 interrupt() 协作式中断，由线程自己在安全点退出
- suspend()：挂起线程但不释放锁。如果它挂起时正持有一把锁，其他线程要这把锁就会一直等，极易死锁。替代方案是 wait()/notify() 或 LockSupport.park()/unpark()

类说明：DeprecatedThreadApiDemo 用账户转账说明 stop 的撕裂风险，并演示 interrupt 的协作式退出写法。

### 2.2 sleep 与 yield 的细节

对比要分清：

- sleep(long)：进入 TIMED_WAITING，一定交出 CPU，到点才回 RUNNABLE；不释放锁（抱着锁睡）；到点也不保证立刻执行
- yield()：只是提示调度器「我愿意让出 CPU」，调度器可以完全无视；不释放锁；线程状态还是 RUNNABLE，不会进入 WAITING；让出后马上可能又被选中，所以不能靠它控制顺序

一句话：sleep 真的阻塞（状态变 TIMED_WAITING），yield 只是建议（状态不变）。

类说明：SleepVsYieldDemo 里起两个线程，sleep 中的线程 getState() 是 TIMED_WAITING，yield 的线程永远是 RUNNABLE 或已终止。

```java
Thread.sleep(500);   // → TIMED_WAITING，不释放锁
Thread.yield();      // → 仍是 RUNNABLE，只是提示让出，不保证生效
```

完整代码参考 juc/src/thread/SleepVsYieldDemo.java。

### 2.3 线程优先级不可靠

Java 线程优先级 1 到 10（MIN=1，NORM=5，MAX=10），映射到操作系统线程优先级。但不同 OS 的支持和映射规则不一样，有些系统直接忽略，而且优先级只影响被调度的概率，不保证执行顺序。

结论：业务代码不要靠 setPriority 控制执行先后，要顺序就用锁、队列、join 这些同步手段。

代码参考 juc/src/thread/ThreadPriorityDemo.java。

## 三、锁模块

### 3.1 synchronized 锁升级的三个细节

笔记里有锁升级流程，补三个容易考错的点：

- 锁只升不降：偏向锁 → 轻量级锁 → 重量级锁是单向的，JDK1.6 之后不支持降级。很多候选人以为锁能降级，这是坑
- 批量重偏向：同一个类的偏向锁被撤销达到阈值（默认 20 次），JVM 认为这个类不适合偏向，会把该类的对象批量重新偏向；达到 40 次则批量撤销偏向锁
- 偏向锁已被移除：JDK9 起偏向锁不再默认开启，JDK15 起默认关闭（还可用 -XX:+UseBiasedLocking 手动开），后续版本已彻底移除这个选项。新版本面试要答「现在没有偏向锁了」

### 3.2 AQS 重点（非常重要）

AQS 核心是一个 volatile int state 加一个 FIFO 双向队列。补两块笔记里缺的。

两种模式和 state 的含义完全不同：

- 独占模式（ReentrantLock）：state 表示锁持有计数，0 是无人持有，每重入一次加 1
- 共享模式（Semaphore、CountDownLatch、读写锁的读锁）：Semaphore 里 state 是剩余许可数，CountDownLatch 里 state 是倒计数值

独占是同一时刻只有一个线程成功（tryAcquire 返回 true）；共享是多个线程可同时成功（tryAcquireShared 返回大于等于 0 就算成功，还会唤醒后继线程）。

类说明：AqsModeDemo 用现成工具类演示两种模式，ReentrantLock 看重入次数，Semaphore 看许可数，CountDownLatch 看计数归零。

Node 的 waitStatus 五种状态：

| 状态 | 值 | 含义 |
|------|----|------|
| CANCELLED | 1 | 节点因超时或中断取消，不再参与排队 |
| SIGNAL | -1 | 后继节点在等锁，当前节点释放后要唤醒它 |
| CONDITION | -2 | 节点在 Condition 条件队列里 |
| PROPAGATE | -3 | 共享模式下唤醒向后传播 |
| 0 | 0 | 初始状态 |

两条队列是完全不同的东西，别混：

- AQS 同步队列：抢锁失败的线程在这里排队等锁
- Condition 条件队列：调用 await() 的线程释放锁后进这里等条件

await() 把线程从同步队列搬到条件队列，signal() 再把节点搬回同步队列重新抢锁。

类说明：ConditionQueueDemo 完整演示 await 和 signal 之间的节点迁移，加打印看状态变化。

```java
condition.await();  // 释放锁 + 从同步队列移到条件队列，状态变 WAITING
condition.signal(); // 把节点从条件队列搬回同步队列，状态变 RUNNABLE，重新抢锁
```

完整代码参考 juc/src/aqs/ConditionQueueDemo.java。

### 3.3 ReentrantReadWriteLock 与锁降级

读写锁规则：读锁共享，写锁独占。state 高 16 位存读锁计数，低 16 位存写锁计数，读锁支持可重入。

锁降级（允许）：持有写锁 → 获取读锁 → 释放写锁。为什么要降级：写完数据还要读，如果直接放写锁，中间可能被别人改掉；先占住读锁再放写锁，就能读到自己刚写的值。

锁升级（不支持）：持有读锁时直接拿写锁会死锁。因为读锁是共享的，别人也拿着读锁，写锁要等所有读锁释放，而自己又拿着读锁不放，互相等。

弊端：读多写少时写线程可能长期拿不到锁，写饥饿。改进版是 StampedLock。

类说明：LockDowngradeDemo 演示写锁 → 读锁 → 放写锁的完整降级流程。

```java
writeLock.lock();
try {
    value = 100;
    readLock.lock();      // 关键：写锁没放，先拿读锁
} finally {
    writeLock.unlock();   // 放写锁，手里只剩读锁，降级完成
}
// 用读锁读 value，不会被别人改
```

完整代码参考 juc/src/lock/LockDowngradeDemo.java。

### 3.4 StampedLock

JDK8 推出，解决 ReadWriteLock 写饥饿，适合读极多写极少的场景。三种模式：写锁、悲观读锁、乐观读。

核心是乐观读：读的时候不加锁（不阻塞写线程），读完用一个 stamp 校验期间有没有被改过，没改就直接用，改了再升级成悲观读锁重读一次。

两个坑：StampedLock 不可重入、不支持 Condition；validate 失败要手动升级悲观读锁重读，框架不会帮你做。

类说明：StampedLockDemo 写了个坐标点，distance() 用乐观读，校验失败才升级悲观读锁。

```java
long stamp = lock.tryOptimisticRead(); // 乐观读，不加锁
int curX = x, curY = y;
if (!lock.validate(stamp)) {           // 关键：校验期间是否被改
    stamp = lock.readLock();           // 被改 → 升级悲观读锁重读
    try { curX = x; curY = y; } finally { lock.unlockRead(stamp); }
}
```

完整代码参考 juc/src/lock/StampedLockDemo.java。

### 3.5 三大工具类对比（必问）

| 工具 | 作用 | 能否复用 | 底层 |
|------|------|----------|------|
| CountDownLatch | 等一组线程完成，计数减到 0 放行 | 不可复用 | AQS 共享模式，state 是计数 |
| CyclicBarrier | 一组线程互相等待，到齐屏障点再一起走 | 可 reset 复用 | ReentrantLock + Condition，不是 AQS |
| Semaphore | 控制同时访问资源的许可数 | 可复用 | AQS 共享模式，state 是许可数 |

关键坑：CountDownLatch 和 Semaphore 是 AQS 共享模式，但 CyclicBarrier 不是 AQS 实现的，是 ReentrantLock + Condition，很多人记混。

类说明：ToolsCompareDemo 三个场景各跑一遍，看它们的行为差异。

完整代码参考 juc/src/tools/ToolsCompareDemo.java。

## 四、CAS 模块

### 4.1 LongAdder 原理（重点）

AtomicInteger 高并发下大量线程 CAS 同一个变量，竞争失败就自旋，越争越慢。LongAdder 的思路是分散竞争点：

- 内部维护一个 Cell 数组（分段），线程按自己的 probe 哈希到不同 Cell 上各自累加，热点被打散
- 只有哈希到同一个 Cell 冲突时才 CAS 自旋或扩容 Cells，竞争烈度从 O(N) 降到接近 O(1)
- 最后 sum() 把所有 Cell 和 base 加起来

缺点：sum() 不是强一致的，求和过程中可能有并发修改，读到的是近似值。适合统计、埋点、QPS 这类最终一致的计数，不适合需要精确读最新值的状态位（那种用 AtomicLong）。

笔记里已有的 juc/src/cas/LongAdderDemo.java 是用法示例，原理对照这段理解即可。

### 4.2 Unsafe 内存屏障与 VarHandle

JDK8 的 CAS 和内存屏障靠 sun.misc.Unsafe，太底层也不安全。JDK9 引入 VarHandle 作为替代，提供类型安全的原子操作和内存屏障：

- 原子操作：get、set、compareAndSet（CAS）、getAndAdd
- 内存屏障：fullFence、acquireFence、releaseFence、loadLoad、storeStore，对应旧版 Unsafe 的 loadFence 和 storeFence

类说明：VarHandleDemo 用 MethodHandles.lookup().findVarHandle 拿到字段句柄，做 CAS 和原子自增，再调一次 fullFence 演示屏障。

```java
VarHandle VALUE = MethodHandles.lookup().findVarHandle(VarHandleDemo.class, "value", int.class);
VALUE.compareAndSet(demo, 0, 10);  // CAS
VALUE.getAndAdd(demo, 5);          // 原子自增
VarHandle.fullFence();             // 全屏障，禁止前后读写重排序
```

完整代码参考 juc/src/cas/VarHandleDemo.java。

## 五、中断与 LockSupport

### 5.1 park 唤醒不清除中断标记（高频对比）

这是面试高频对比点，记住一句话：抛异常的那组会清标记，park 不清。

- wait()、sleep()、join()：被中断时抛 InterruptedException，同时清除中断标记。所以 catch 之后 isInterrupted() 是 false，要手动 Thread.currentThread().interrupt() 恢复
- LockSupport.park()：被 interrupt() 唤醒后直接返回，不抛异常，也不清除标记。park 返回后 isInterrupted() 仍是 true

类说明：InterruptFlagCompareDemo 起两个线程，一个 sleep 一个 park，都中断后打标记，直接看出 false 和 true 的差别。

```java
// sleep：抛异常 + 清标记
try { Thread.sleep(5000); }
catch (InterruptedException e) {
    System.out.println(Thread.currentThread().isInterrupted()); // false
}

// park：不抛异常 + 不清标记
LockSupport.park();
System.out.println(Thread.currentThread().isInterrupted()); // true
```

完整代码参考 juc/src/locksupport/InterruptFlagCompareDemo.java。parkNanos、parkUntil 是带超时的版本；LockSupport 场景下也可能虚假唤醒，所以调用方一般要放在 while 循环里检查条件。

## 六、ThreadLocal

### 6.1 InheritableThreadLocal 与 TTL（高频）

- ThreadLocal：每个线程独立一份变量副本，线程间隔离
- InheritableThreadLocal：子线程创建时会继承父线程的 ThreadLocal 值，拷贝发生在 Thread 构造的那一刻
- 大坑：线程池场景失效。池里的线程是复用的，不会新建，继承动作只在线程第一次创建时发生一次，后续提交任务不会重新继承。结果就是任务 A 设的上下文被任务 B 读到，串数据
- 解决：阿里开源的 Transmittable-ThreadLocal（TTL），在任务提交时捕获上下文、执行时回放，专门解决线程池里的上下文传递。业务里 MDC 日志 traceId 跨线程池传递就靠它

类说明：InheritableThreadLocalDemo 先演示 new Thread 能继承，再演示线程池复用导致读到旧值。

```java
static InheritableThreadLocal<String> context = new InheritableThreadLocal<>();
context.set("父线程traceId");
new Thread(() -> System.out.println(context.get())).start(); // 能继承到

// 线程池：线程复用，第二个任务可能读到第一个任务的值 → 用 TTL 解决
```

完整代码参考 juc/src/threadlocal/InheritableThreadLocalDemo.java。

### 6.2 ThreadLocalMap 用开放寻址法

面试挖坑点：ThreadLocalMap 处理哈希冲突用的是开放寻址法（线性探测），不是 HashMap 的链地址法。

核心设计：

- 每个 Thread 持有一个 ThreadLocalMap
- key 是 ThreadLocal 的弱引用，value 是强引用（所以不 remove 会内存泄漏）
- 冲突时 index 加 1 往后找空槽，遇到 null 就放下

类说明：ThreadLocalMapHashDemo 用一个小数组模拟线性探测，冲突就往后挪一格，直观看出和链表法的区别。

```java
int index = hash & (SIZE - 1);
while (table[index] != null) {
    index = (index + 1) & (SIZE - 1); // 关键：线性探测，不是挂链表
}
table[index] = value;
```

完整代码参考 juc/src/threadlocal/ThreadLocalMapHashDemo.java。补充一句：用完 ThreadLocal 记得 remove()，尤其线程池场景，否则弱引用 key 被回收后 value 永远清不掉。

## 七、线程池

### 7.1 Executors 工厂方法的坑

阿里规约禁止直接用 Executors 默认工厂，原因就是无界：

| 工厂方法 | 问题 | 后果 |
|----------|------|------|
| newFixedThreadPool | 用无界 LinkedBlockingQueue | 任务堆积，队列无限膨胀 OOM |
| newSingleThreadExecutor | 用无界 LinkedBlockingQueue | 同上 OOM |
| newCachedThreadPool | 最大线程数 Integer.MAX_VALUE | 高并发无限创建线程 OOM |
| newScheduledThreadPool | 最大线程数 Integer.MAX_VALUE | 同上 OOM |

正确做法：手动 new ThreadPoolExecutor，指定有界队列、合理的核心和最大线程数、明确的拒绝策略。

类说明：ExecutorsPitfallDemo 打印各工厂的底层参数，并给出手写线程池的写法。

```java
new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(1000),                  // 关键：有界队列
    new ThreadPoolExecutor.CallerRunsPolicy());      // 拒绝策略：调用者自己跑
```

完整代码参考 juc/src/pool/ExecutorsPitfallDemo.java。

### 7.2 ScheduledThreadPoolExecutor 的静默坑

定时任务里如果抛出未捕获异常，这个任务会结束，而且异常被吞进 Future，控制台往往没有任何日志，后续周期执行再也不触发。排查时非常隐蔽：任务莫名其妙不跑了，还没报错。

解决：在任务内部 try-catch 兜底记日志，或给 Future 加回调主动 get() 触发异常。

类说明：ScheduledPoolExceptionDemo 里任务故意 1/0，本应打印三四次，实际只打一次就停，且没有堆栈。

完整代码参考 juc/src/pool/ScheduledPoolExceptionDemo.java。

### 7.3 线程池关闭三兄弟

- shutdown()：温和关闭，不接新任务，队列里已提交的任务继续跑完
- shutdownNow()：粗暴关闭，尝试中断正在执行的任务，返回队列里还没开始的任务列表。注意是「尝试」中断，任务不响应中断的话照样跑完
- awaitTermination(timeout)：阻塞等待线程池彻底关闭，通常配合 shutdown 用，超时还没关完就返回 false

优雅关闭三连：shutdown() → awaitTermination(等待) → 超时则 shutdownNow()。

另一个点：allowCoreThreadTimeOut 默认 false，核心线程空闲时不会被回收；设为 true 后核心线程超时也会被回收。

完整代码参考 juc/src/pool/ShutdownDemo.java。

## 八、CompletableFuture

笔记已有基础用法，补四个坑。

### 8.1 默认线程池坑

不传 executor 时用 ForkJoinPool.commonPool，全应用所有 CompletableFuture 共享这一个池。一个阻塞 IO 任务占满公共池，其他所有异步任务全部卡死。IO 密集任务务必传自定义线程池。

### 8.2 带不带 Async 后缀的区别

- thenApply()：用上一步任务的线程执行，不是新线程
- thenApplyAsync()：提交给线程池执行，可能换线程

很多人以为回调都是异步的，其实不带 Async 的就是在上一步线程里同步执行。

### 8.3 allOf 异常传播

allOf 里只要有一个任务异常，allOf().join() 就抛 CompletionException，一个失败全失败。想保留部分成功结果，要在每个 future 上单独 exceptionally 兜底。

### 8.4 completeExceptionally

手动让一个 future 以异常完成，用于主动通知失败，所有等待方会立即收到。

类说明：CompletableFuturePitfallDemo 把四个坑放一起，Async 后缀用打印线程名区分，异常传播用 exceptionally 兜底演示。

```java
.thenApply(x -> ...)        // 上一步线程执行
.thenApplyAsync(x -> ..., pool) // 线程池执行

CompletableFuture<Void> all = CompletableFuture.allOf(ok, fail);
all.join();  // 关键：fail 异常 → 这里抛 CompletionException

future.completeExceptionally(new RuntimeException("主动失败"));
```

完整代码参考 juc/src/cf/CompletableFuturePitfallDemo.java。

## 九、并发容器（原笔记缺得最多的一块）

### 9.1 ConcurrentHashMap（重中之重）

原理演进：

- JDK7：分段锁 Segment，每个段一把锁，不同段可并发，默认 16 段
- JDK8：取消分段锁，改成 CAS + synchronized 锁单个桶的头节点，锁粒度更细，并发度更高

几个点：

- size() 返回 int，超过 Integer.MAX_VALUE 会截断；mappingCount() 返回 long，推荐用后者
- 扩容时其他线程可以 helpTransfer 协助搬迁，加快扩容
- 坑一：computeIfAbsent 的映射函数里再对同一个 map 做 put/remove，会死循环或死锁
- 坑二：不能当成 HashMap 做复合操作，get() 和 put() 两步之间仍有并发窗口，要用 putIfAbsent、compute、merge 这些原子方法

类说明：ConcurrentHashMapDemo 用 compute 原子累加，10 线程各加 1 万，对比错误的 get-then-put 写法。

```java
// 正确：compute 是原子的，读改写在同一把桶锁内完成
map.compute(key, (k, v) -> v == null ? 1 : v + 1);

// 错误：两步之间有并发窗口，会丢更新
Integer v = map.get(key);
map.put(key, v + 1);
```

完整代码参考 juc/src/collection/ConcurrentHashMapDemo.java。

### 9.2 CopyOnWriteArrayList

写时复制：读不加锁，写的时候先把整个数组复制一份，在新数组上改，改完把引用指过去。所以读极快、写很慢（要复制整个数组）。

适合读极多写极少、能容忍读到旧数据的场景，比如事件监听器、配置缓存、黑白名单。迭代器是弱一致的，遍历时不会抛 ConcurrentModificationException。CopyOnWriteArraySet 底层就是包了一层它。

类说明：CopyOnWriteDemo 演示监听器列表的添加和通知，类注释里写了适用场景。

完整代码参考 juc/src/collection/CopyOnWriteDemo.java。

### 9.3 ConcurrentLinkedQueue 与阻塞队列

ConcurrentLinkedQueue 是无锁非阻塞队列，CAS 实现，高并发下性能优于阻塞队列。

阻塞队列的 API 分四组，别混：

| 方式 | 抛异常 | 返回特殊值 | 阻塞 | 超时阻塞 |
|------|--------|------------|------|----------|
| 入队 | add | offer | put | offer(e, t) |
| 出队 | remove | poll | take | poll(t) |
| 检查 | element | peek | 无 | 无 |

常见实现：

- ArrayBlockingQueue：数组，有界，必须给容量
- LinkedBlockingQueue：链表，可无界（默认 Integer.MAX_VALUE），Executors 的无界坑就来自它
- PriorityBlockingQueue：无界，按优先级出队
- DelayQueue：无界，元素到期才能取出，元素实现 Delayed 接口，适合定时任务和缓存过期

类说明：BlockingQueueDemo 把四种队列各跑一遍，还演示了 offer 满时返回 false 和延迟队列的到期取出。

完整代码参考 juc/src/collection/BlockingQueueDemo.java。

## 十、实战与故障排查

### 10.1 活锁与饥饿

除了死锁，还有两个并发问题：

- 活锁：线程没阻塞，一直在积极重试，但因为互相礼让，永远完不成任务。比如两人过马路，你让我我让你，反复侧身谁也过不去。解决：重试加随机退避时间，打破同步性
- 饥饿：某些线程因为优先级低或锁不公平，一直拿不到资源，永远得不到执行。解决：用公平锁 new ReentrantLock(true)，但吞吐量更低

类说明：LivelockAndStarvationDemo 用两个人机械互相让路演示活锁，最后打印公平锁缓解饥饿。

完整代码参考 juc/src/deadlock/LivelockAndStarvationDemo.java。

### 10.2 jstat 输出指标怎么读

命令：jstat -gcutil pid 1000，表示每 1000 毫秒打印一次 GC 情况。输出列的含义：

- S0、S1：两个 Survivor 区的使用率
- E：Eden 区使用率
- O：老年代使用率，这个持续接近 100% 要警惕 Full GC
- M：元空间使用率；CCS：压缩类空间使用率
- YGC、YGCT：年轻代 GC 的次数和总耗时
- FGC、FGCT：Full GC 的次数和总耗时，FGC 频繁是重点排查信号
- GCT：所有 GC 的总耗时

实战看点：FGC 次数短时间内快速增长、O 一直居高不下、GCT 占用 CPU 时间比例过高，都是内存问题的信号，接着用 jmap 抓堆快照分析。

### 10.3 复合操作要用原子方法

再强调一次：ConcurrentHashMap 只能保证单个操作线程安全，不能替代 HashMap 做复合操作。map.get() 和 map.put() 之间仍然可能插入别的线程。要用 compute、merge、putIfAbsent 这些原子方法（例子见 9.1）。

### 10.4 分布式锁补充

Redisson：成熟的 Redis 客户端，分布式锁支持看门狗自动续期、可重入、公平锁、读写锁、信号量等，生产推荐直接用它的 RedissonLock。

RedLock 红锁：Redis 官方提的多实例分布式锁算法，思路是在多个独立 Redis 实例上加锁，多数成功才算成功，避免单实例故障。但它争议很大，分布式系统专家 Martin Kleppmann 和 Redis 作者有过著名辩论，核心问题是时钟漂移和 GC 停顿会导致锁失效。Redis 官方现在不推荐生产环境用 Redlock，更推荐单实例加主从的方案，或用 Redisson 封装好的实现。

## 十一、DCL 双重检查锁单例（面试经典）

笔记里只有 synchronized 静态单例，补上 DCL。重点是 instance 必须加 volatile。

new 一个对象分三步：分配内存、调用构造初始化、把引用指向这块内存。不加 volatile 时 JVM 可能把后两步重排序，先让 instance 指向了还没初始化的半对象，另一个线程第一次检查看到 instance 不为空就直接拿走，用到未初始化字段就出错。volatile 禁止这个重排序，保证对象初始化完成后引用才可见。

双重检查的意义：外层 if 避免每次都加锁（性能），内层 if 防止多线程同时通过外层检查后重复创建。

类说明：DclSingletonDemo 是完整的 DCL 写法，注意 instance 的 volatile 和两个 if。

```java
public class DclSingletonDemo {
    // 关键：volatile 禁止 new 的三步重排序，防止半初始化对象逸出
    private static volatile DclSingletonDemo instance;
    private DclSingletonDemo() {}

    public static DclSingletonDemo getInstance() {
        if (instance == null) {                  // 第一重：不加锁快速判断
            synchronized (DclSingletonDemo.class) {
                if (instance == null) {          // 第二重：防止重复创建
                    instance = new DclSingletonDemo();
                }
            }
        }
        return instance;
    }
}
```

完整代码参考 juc/src/singleton/DclSingletonDemo.java。

## 补充优先级（如果需要按顺序复习）

最高优先级：ConcurrentHashMap、AQS 共享模式、AQS waitStatus、三大工具类、DCL、InheritableThreadLocal 与 TTL、LongAdder。

次优先级：StampedLock、读写锁降级与升级、伪共享、CompletableFuture Async 后缀、公共 ForkJoin 池坑、线程池工厂坑。

补充细节：偏向锁移除、批量重偏向、park 不清中断标记、活锁与饥饿、阻塞队列、CopyOnWriteArrayList。
