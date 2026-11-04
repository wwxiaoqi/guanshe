package com.wwxiaoqi.guanshe.core;

/**
 * 「今日配色」：按"距 1970-01-01 的天数"算一个稳定序号。
 *
 * 承诺（见 specs/palette-library）：同一自然日任意次进入都给出同一组，跨天可以变化。
 * 所以这里只能是纯函数 —— 不读时钟、不落盘、不用随机种子。
 */
public final class Today {

    private Today() {
    }

    /** @return [0, total) 内的稳定序号 */
    public static int pick(int daysSinceEpoch, int total) {
        if (total <= 0) return 0;
        long x = daysSinceEpoch * 2654435761L;      // Knuth 乘法散列
        x ^= (x >>> 15);
        x *= 2246822519L;
        x ^= (x >>> 13);
        return (int) Math.floorMod(x, (long) total);
    }

    /** 把一个毫秒时间戳换算成"距 1970-01-01 的天数"（按 UTC 算，不依赖时区数据库）。 */
    public static int daysSinceEpoch(long millis) {
        return (int) Math.floorDiv(millis, 86400000L);
    }
}
