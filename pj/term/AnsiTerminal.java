package pj.term;

import pj.Native;

/*
 * Terminal presenter for CellSurface.
 *
 * On ANSI-capable terminals it uses a retained diff. On plain output it emits
 * a full line-based dump, which keeps smoke tests deterministic.
 */
public class AnsiTerminal {
    public final int width;
    public final int height;
    private final byte[] prev;
    private boolean begun;

    public AnsiTerminal(int width, int height) {
        this.width = width;
        this.height = height;
        this.prev = new byte[width * height];
    }

    public static AnsiTerminal openAuto() {
        return new AnsiTerminal(Terminal.cols(), Terminal.rows());
    }

    public void begin() {
        if (begun) return;
        begun = true;
        if (Terminal.hasAnsi()) {
            Terminal.enterAlt();
            Terminal.hideCursor();
            Terminal.clear();
        }
    }

    public void end() {
        if (!begun) return;
        if (Terminal.hasAnsi()) {
            Terminal.showCursor();
            Terminal.leaveAlt();
        }
        begun = false;
    }

    public void present(CellSurface s) {
        if (Terminal.hasAnsi()) presentAnsi(s);
        else presentPlain(s);
    }

    private void presentAnsi(CellSurface s) {
        for (int y = 0; y < s.height; y++) {
            for (int x = 0; x < s.width; x++) {
                int idx = y * s.width + x;
                byte cur = s.cells[idx];
                if (!begun || prev[idx] != cur) {
                    Terminal.move(x, y);
                    Native.putchar(cur & 0xFF);
                    prev[idx] = cur;
                }
            }
        }
    }

    private void presentPlain(CellSurface s) {
        for (int y = 0; y < s.height; y++) {
            Terminal.writeBytes(s.cells, y * s.width, s.width);
            Native.putchar('\n');
        }
    }
}
