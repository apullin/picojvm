package pj.term;

/*
 * Retained text cell surface.
 *
 * Uses a compact byte buffer so it is realistic on 8-bit targets and easy to
 * blit through a terminal or character display backend.
 */
public class CellSurface {
    public final int width;
    public final int height;
    public final byte[] cells;

    public CellSurface(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new byte[width * height];
        clear(' ');
    }

    public int index(int x, int y) {
        return y * width + x;
    }

    public void clear(int ch) {
        byte fill = (byte)ch;
        for (int i = 0; i < cells.length; i++) cells[i] = fill;
    }

    public void put(int x, int y, int ch) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        cells[index(x, y)] = (byte)ch;
    }

    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return ' ';
        return cells[index(x, y)] & 0xFF;
    }

    public void write(int x, int y, String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) put(x + i, y, s.charAt(i));
    }

    public void hline(int x, int y, int len, int ch) {
        for (int i = 0; i < len; i++) put(x + i, y, ch);
    }

    public void vline(int x, int y, int len, int ch) {
        for (int i = 0; i < len; i++) put(x, y + i, ch);
    }

    public void box(int x, int y, int w, int h) {
        if (w < 2 || h < 2) return;
        hline(x + 1, y, w - 2, '-');
        hline(x + 1, y + h - 1, w - 2, '-');
        vline(x, y + 1, h - 2, '|');
        vline(x + w - 1, y + 1, h - 2, '|');
        put(x, y, '+');
        put(x + w - 1, y, '+');
        put(x, y + h - 1, '+');
        put(x + w - 1, y + h - 1, '+');
    }
}
