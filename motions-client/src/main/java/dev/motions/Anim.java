package dev.motions;

/** Tiny exponential-smoothing animation value. Cheap: no allocations per frame. */
public final class Anim {
    private float cur, target;
    private long last = System.nanoTime();

    public Anim(float v) { cur = target = v; }

    public void to(float t) { target = t; }

    public void snap(float v) { cur = target = v; last = System.nanoTime(); }

    public float get() {
        long n = System.nanoTime();
        float dt = Math.min((n - last) / 1e9f, 0.1f);
        last = n;
        MotionsConfig c = MotionsConfig.get();
        if (!c.animations) { cur = target; return cur; }
        if (cur != target) {
            cur += (target - cur) * (1f - (float) Math.exp(-dt * 16f * c.animSpeed));
            if (Math.abs(target - cur) < 0.002f) cur = target;
        }
        return cur;
    }
}
