package net.exmo.sreGame.games.quakechasm.util;

import java.util.Objects;

/** Immutable 2-tuple. Ported from quakechasm's misc.Pair. */
public final class Pair<L, R> {
    private final L left;
    private final R right;

    public Pair(L left, R right) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    public L getLeft() { return left; }
    public R getRight() { return right; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair<?, ?> p)) return false;
        return Objects.equals(left, p.left) && Objects.equals(right, p.right);
    }

    @Override
    public int hashCode() { return Objects.hash(left, right); }
}
