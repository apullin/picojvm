import pj.term.AnsiTerminal;
import pj.term.CellSurface;
import pj.term.Draw;
import pj.term.Keys;
import pj.term.Terminal;

public class TermDemo {
    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public static void main(String[] args) {
        int w = Terminal.cols();
        int h = Terminal.rows();
        if (w < 30) w = 30;
        if (w > 70) w = 70;
        if (h < 10) h = 10;
        if (h > 20) h = 20;

        CellSurface s = new CellSurface(w, h);
        AnsiTerminal t = new AnsiTerminal(w, h);
        int x = w / 2;
        int y = h / 2;
        int key;

        t.begin();
        for (;;) {
            key = Terminal.keyRead();
            if (key == 'q' || key == 'Q') break;
            if (key == Keys.LEFT || key == 'h') x--;
            if (key == Keys.RIGHT || key == 'l') x++;
            if (key == Keys.UP || key == 'k') y--;
            if (key == Keys.DOWN || key == 'j') y++;

            x = clamp(x, 1, w - 2);
            y = clamp(y, 2, h - 2);

            s.clear(' ');
            s.box(0, 0, w, h);
            s.write(2, 0, " pj.term demo ");
            s.write(2, 1, "Arrows or hjkl move, q quits");
            s.write(2, h - 2, "ticks=");
            Draw.writeInt(s, 8, h - 2, Terminal.ticks());
            s.put(x, y, '@');
            t.present(s);

            if (!Terminal.hasRawKeys()) break;
        }
        t.end();
    }
}
