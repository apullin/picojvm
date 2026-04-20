package pj.term;

import pj.text.Format;

/*
 * Drawing helpers layered on top of CellSurface.
 */
public class Draw {
    public static void fillRect(CellSurface s, int x, int y, int w, int h, int ch) {
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) s.put(x + xx, y + yy, ch);
        }
    }

    public static void clearRect(CellSurface s, int x, int y, int w, int h) {
        fillRect(s, x, y, w, h, ' ');
    }

    public static int writeInt(CellSurface s, int x, int y, int v) {
        byte[] buf = new byte[12];
        int len = Format.writeInt(buf, 0, v);
        for (int i = 0; i < len; i++) s.put(x + i, y, buf[i] & 0xFF);
        return x + len;
    }

    public static int writeHex(CellSurface s, int x, int y, int v, int digits) {
        byte[] buf = new byte[8];
        int len = Format.writeHex(buf, 0, v, digits);
        for (int i = 0; i < len; i++) s.put(x + i, y, buf[i] & 0xFF);
        return x + len;
    }
}
