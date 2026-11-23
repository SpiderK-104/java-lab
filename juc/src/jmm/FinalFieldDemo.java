package jmm;

/**
 * final 域内存语义：为什么 final 字段是线程安全的
 * <p>
 * 两条规则（JMM 对 final 的专门承诺）：
 * 1. 只要在构造方法里把 final 字段写完整，且构造过程中「没有把 this 逸出」，
 *    那么其他线程看到这个对象时，一定能看到 final 字段的正确值，不需要额外同步。
 * 2. final 引用指向的对象，其字段是在构造结束后才对其他线程可见（防止半初始化对象被看到）。
 * <p>
 * 反例：构造过程中把 this 发布出去（this 逸出），别的线程可能读到还没赋值完的 final 字段。
 * 这就是「final 也不是绝对安全」的唯一破口：构造期间不要让 this 逃逸。
 */
public class FinalFieldDemo {

    // 正确写法：final 字段在构造里一次性写死，构造期间 this 不逸出
    static class SafeObject {
        final int x;
        final int y;

        SafeObject(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // 错误写法：构造过程中把 this 逸出给外部线程
    static class EscapeObject {
        final int value;

        EscapeObject(EscapeHolder holder) {
            // 【关键】this 在构造未完成时就发布了，其他线程可能看到 value 的默认值 0
            holder.obj = this;
            this.value = 100;
        }
    }

    static class EscapeHolder {
        EscapeObject obj;
    }

    public static void main(String[] args) throws InterruptedException {
        // 场景一：正常发布，final 可见性有保证
        SafeObject safe = new SafeObject(1, 2);
        System.out.println("安全对象 x=" + safe.x + ", y=" + safe.y);

        // 场景二：this 逸出，可能看到 value=0（此 demo 用于说明风险，是否复现看 JVM 调度）
        EscapeHolder holder = new EscapeHolder();
        new Thread(() -> new EscapeObject(holder)).start();
        Thread.sleep(10);
        System.out.println("逸出对象 value=" + holder.obj.value + "  （理论应为 100，风险场景可能看到 0）");
    }
}
