package com.yuan.live2d.client.live2d;

import java.util.Random;

final class Live2DInteractionScheduler {
    static final int FLAG_RANDOM_MOTION = 1;
    static final int FLAG_CLICK_REACTION = 2;
    static final int FLAG_RANDOM_EXPRESSION = 4;

    private final Random random;
    private long nextAtMillis = Long.MIN_VALUE;

    Live2DInteractionScheduler(Random random) {
        this.random = random;
    }

    int poll(float intervalSeconds, boolean expressionEnabled, long nowMillis) {
        if (nextAtMillis == Long.MIN_VALUE) {
            nextAtMillis = 0;
            schedule(intervalSeconds, nowMillis);
            return FLAG_RANDOM_MOTION;
        }
        if (nowMillis < nextAtMillis) return 0;
        int flags = FLAG_RANDOM_MOTION;
        if (expressionEnabled && random.nextFloat() < .3f) flags |= FLAG_RANDOM_EXPRESSION;
        schedule(intervalSeconds, nowMillis);
        return flags;
    }

    long nextAtMillisForTest() {
        return nextAtMillis;
    }

    private void schedule(float intervalSeconds, long nowMillis) {
        float jitter = .8f + .4f * random.nextFloat();
        nextAtMillis = nowMillis + (long) (intervalSeconds * jitter * 1000);
    }
}
