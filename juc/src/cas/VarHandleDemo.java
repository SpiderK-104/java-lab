package cas;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Unsafe 的替代者：VarHandle（JDK9+）与内存屏障
 * <p>
 * 背景：JDK8 里 CAS 和内存屏障靠 sun.misc.Unsafe，但 Unsafe 太底层、不安全、要反射获取。
 * JDK9 引入 VarHandle，提供类型安全的方式做原子操作和内存屏障。
 * <p>
 * 核心能力：
 * 1. 原子读写：get / set / getAndAdd / compareAndSet（CAS）
 * 2. 内存屏障：fullFence()、acquireFence()、releaseFence()、loadLoad()、storeStore() 等
 *    —— 对应旧版 Unsafe 的 loadFence()/storeFence()。
 * <p>
 * 注意：VarHandle 引用的字段要 non-static 时用 findVarHandle，static 用 findStaticVarHandle；
 * 字段必须声明成非 final（否则不能写）。
 */
public class VarHandleDemo {

    volatile int value = 0;

    // 【关键】通过 MethodHandles 拿到当前类 value 字段的 VarHandle
    private static final VarHandle VALUE;
    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(VarHandleDemo.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    public static void main(String[] args) {
        VarHandleDemo demo = new VarHandleDemo();

        // CAS：把 0 改成 10，成功返回 true
        boolean ok = VALUE.compareAndSet(demo, 0, 10);
        System.out.println("CAS 成功=" + ok + ", 当前值=" + demo.value);

        // 原子自增
        int old = (int) VALUE.getAndAdd(demo, 5);
        System.out.println("旧值=" + old + ", 新值=" + demo.value);

        // 内存屏障：等价于 Unsafe 的 fullFence，禁止前后的读写重排序
        VarHandle.fullFence();
        System.out.println("fullFence 之后的读写不会被重排序到屏障之前");
    }
}
