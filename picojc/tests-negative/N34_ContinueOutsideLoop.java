public class N34_ContinueOutsideLoop {
    public static void main(String[] args) {
        switch (args.length) {
            case 0: continue;
        }
        Native.halt();
    }
}
