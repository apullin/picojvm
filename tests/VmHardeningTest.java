/*
 * Regression tests for interpreter hardening:
 *  - tableswitch must compare the full 32-bit selector, not the low half
 *  - INT_MIN / -1 and INT_MIN % -1 follow Java semantics (UB in C)
 *  - array clone() copies by element kind, not unconditionally len*4
 */
public class VmHardeningTest {
    static int pick(int v) {
        switch (v) {
            case 100000: return 1;
            case 100001: return 2;
            case 100002: return 3;
            case 100003: return 4;
            default: return 9;
        }
    }

    public static void main(String[] args) {
        int bad = 0;

        if (pick(100000) != 1) bad++;
        if (pick(100001) != 2) bad++;
        if (pick(100003) != 4) bad++;
        if (pick(34465) != 9) bad++;   // == 100001 & 0xFFFF, collides in 16-bit
        if (pick(-31071) != 9) bad++;  // sign-aliased low half of 100001
        if (pick(0) != 9) bad++;

        int min = Integer.MIN_VALUE;
        int negOne = args.length - 1;  // -1 at runtime, defeats constant folding
        if (min / negOne != min) bad++;
        if (min % negOne != 0) bad++;
        if ((-7) / negOne != 7) bad++;
        if (100 / (negOne + 8) != 14) bad++;
        if (100 % (negOne + 8) != 2) bad++;

        byte[] b = new byte[5];
        for (int i = 0; i < 5; i++) b[i] = (byte)(i + 1);
        byte[] b2 = b.clone();
        if (b2.length != 5) bad++;
        for (int i = 0; i < 5; i++) if (b2[i] != i + 1) bad++;

        int[] n = new int[4];
        for (int i = 0; i < 4; i++) n[i] = (i + 1) * 70000;
        int[] n2 = n.clone();
        if (n2.length != 4) bad++;
        for (int i = 0; i < 4; i++) if (n2[i] != (i + 1) * 70000) bad++;

        if (bad == 0) {
            Native.print("OK");
        } else {
            Native.print("BAD");
        }
        Native.putchar('\n');
        Native.halt();
    }
}
