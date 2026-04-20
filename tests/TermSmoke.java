import pj.term.AnsiTerminal;
import pj.term.CellSurface;

public class TermSmoke {
    public static void main(String[] args) {
        CellSurface s = new CellSurface(20, 5);
        AnsiTerminal t = new AnsiTerminal(20, 5);

        s.box(0, 0, 20, 5);
        s.write(2, 1, "picoJSE");
        s.write(2, 2, "pj.term smoke");
        s.write(2, 3, "io/ui ready");

        t.begin();
        t.present(s);
        t.end();
    }
}
