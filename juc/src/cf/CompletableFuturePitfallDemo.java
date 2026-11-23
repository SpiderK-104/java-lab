package cf;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CompletableFuture 四个高频坑点
 * <p>
 * 1. 默认线程池坑：不传 executor 时用 ForkJoinPool.commonPool()，全应用所有 CompletableFuture 共享。
 *    一个阻塞 IO 任务占满公共池，其他所有异步任务全部卡死。IO 密集任务务必传自定义线程池。
 * 2. Async 后缀区分：不带 Async 的 thenApply 由「上一步任务的线程」执行；
 *    带 Async 的 thenApplyAsync 才提交给线程池执行。很多人以为都异步，其实不是。
 * 3. allOf 异常传播：只要有一个任务异常，allOf().join() 就抛 CompletionException，
 *    想保留部分成功结果，要在每个 future 上单独 exceptionally 兜底。
 * 4. completeExceptionally：可以手动让一个 future 异常完成，用于主动通知失败。
 */
public class CompletableFuturePitfallDemo {

    public static void main(String[] args) {
        asyncSuffixDemo();
        allOfExceptionDemo();
        completeExceptionallyDemo();
    }

    // 坑点2：Async 后缀决定「谁来执行后续步骤」
    static void asyncSuffixDemo() {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CompletableFuture.supplyAsync(() -> {
            System.out.println("[1] supplyAsync 线程=" + Thread.currentThread().getName());
            return 1;
        }, pool)
        .thenApply(x -> {
            // 【关键】不带 Async：仍由上面 supplyAsync 的线程执行（除非上一步已完成）
            System.out.println("[2] thenApply 线程=" + Thread.currentThread().getName() + "（同一个线程）");
            return x + 1;
        })
        .thenApplyAsync(x -> {
            // 【关键】带 Async：提交给线程池，可能换成另一个线程
            System.out.println("[3] thenApplyAsync 线程=" + Thread.currentThread().getName() + "（线程池重新调度）");
            return x + 1;
        }, pool).join();
        pool.shutdown();
    }

    // 坑点3：allOf 一个失败全失败，用 exceptionally 保住部分成功
    static void allOfExceptionDemo() {
        CompletableFuture<Integer> ok = CompletableFuture.supplyAsync(() -> 10);
        CompletableFuture<Integer> fail = CompletableFuture.supplyAsync(() -> { throw new RuntimeException("boom"); });

        CompletableFuture<Void> all = CompletableFuture.allOf(ok, fail);
        try {
            all.join(); // 【关键】只要 fail 异常，这里就抛 CompletionException
        } catch (CompletionException e) {
            System.out.println("[allOf] 捕获到=" + e.getCause().getMessage() + "，但 ok 的结果仍然可读=" + ok.join());
        }

        // 正确姿势：每个 future 自己兜底，allOf 就不会整体失败
        CompletableFuture<Integer> failSafe = fail.exceptionally(ex -> 0);
        CompletableFuture.allOf(ok, failSafe).join();
        System.out.println("[兜底后] ok=" + ok.join() + ", fail=" + failSafe.join());
    }

    // 坑点4：手动异常完成
    static void completeExceptionallyDemo() {
        CompletableFuture<String> future = new CompletableFuture<>();
        // 【关键】主动让 future 以异常结束，所有等待方立即收到
        future.completeExceptionally(new RuntimeException("主动标记失败"));
        try {
            future.join();
        } catch (CompletionException e) {
            System.out.println("[completeExceptionally] 收到异常=" + e.getCause().getMessage());
        }
    }
}
