package com.yuan.live2d.client.live2d;

public final class Live2DInteractionSchedulerCheck {
    private Live2DInteractionSchedulerCheck() {}

    public static void main(String[] args) {
        check();
    }

    public static void check() {
        java.util.Random random = new java.util.Random(7);
        Live2DInteractionScheduler scheduler = new Live2DInteractionScheduler(random);
        int first = scheduler.poll(12f, true, 1_000L);
        assert (first & Live2DInteractionScheduler.FLAG_RANDOM_MOTION) != 0
                : "first poll must allow an immediate random motion";
        long next = scheduler.nextAtMillisForTest();
        assert next > 1_000L + 12f * 800 : "interval must include jitter";
        int before = scheduler.poll(12f, true, next - 1L);
        assert before == 0 : "must not fire before the scheduled time";
        int fired = scheduler.poll(12f, true, next);
        assert (fired & Live2DInteractionScheduler.FLAG_RANDOM_MOTION) != 0
                : "scheduled poll must fire";
        assert scheduler.nextAtMillisForTest() > next : "must reschedule after firing";
    }
}
