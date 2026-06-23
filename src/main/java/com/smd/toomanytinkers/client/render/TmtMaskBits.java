package com.smd.toomanytinkers.client.render;

public final class TmtMaskBits {

    public static final int SIZE = 16 * 16;
    public static final TmtMaskBits EMPTY = new TmtMaskBits(0L, 0L, 0L, 0L);

    private final long bits0;
    private final long bits1;
    private final long bits2;
    private final long bits3;
    private final int hash;

    private TmtMaskBits(long bits0, long bits1, long bits2, long bits3) {
        this.bits0 = bits0;
        this.bits1 = bits1;
        this.bits2 = bits2;
        this.bits3 = bits3;
        this.hash = computeHash();
    }

    public boolean get(int index) {
        if (index < 0 || index >= SIZE) {
            return false;
        }
        long bit = 1L << (index & 63);
        switch (index >>> 6) {
            case 0:
                return (bits0 & bit) != 0L;
            case 1:
                return (bits1 & bit) != 0L;
            case 2:
                return (bits2 & bit) != 0L;
            default:
                return (bits3 & bit) != 0L;
        }
    }

    public boolean isEmpty() {
        return (bits0 | bits1 | bits2 | bits3) == 0L;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TmtMaskBits)) {
            return false;
        }
        TmtMaskBits other = (TmtMaskBits) obj;
        return bits0 == other.bits0
                && bits1 == other.bits1
                && bits2 == other.bits2
                && bits3 == other.bits3;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    private int computeHash() {
        int result = Long.hashCode(bits0);
        result = 31 * result + Long.hashCode(bits1);
        result = 31 * result + Long.hashCode(bits2);
        result = 31 * result + Long.hashCode(bits3);
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long bits0;
        private long bits1;
        private long bits2;
        private long bits3;

        public void set(int index) {
            if (index < 0 || index >= SIZE) {
                return;
            }
            long bit = 1L << (index & 63);
            switch (index >>> 6) {
                case 0:
                    bits0 |= bit;
                    break;
                case 1:
                    bits1 |= bit;
                    break;
                case 2:
                    bits2 |= bit;
                    break;
                default:
                    bits3 |= bit;
                    break;
            }
        }

        public TmtMaskBits build() {
            if ((bits0 | bits1 | bits2 | bits3) == 0L) {
                return EMPTY;
            }
            return new TmtMaskBits(bits0, bits1, bits2, bits3);
        }
    }
}
