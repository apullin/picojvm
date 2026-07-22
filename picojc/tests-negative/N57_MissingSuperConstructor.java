class N57Base {
    N57Base(int value) {
    }
}

class N57Child extends N57Base {
    N57Child() {
    }
}

public class N57_MissingSuperConstructor {
    public static void main(String[] args) {
        N57Child value = new N57Child();
    }
}
