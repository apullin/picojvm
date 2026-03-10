class Animal {
    int sound() {
        return 63;  // '?'
    }
}

class Dog extends Animal {
    int sound() {
        return 68;  // 'D'
    }
}

class Cat extends Animal {
    int sound() {
        return 67;  // 'C'
    }
}

public class T17_Inheritance {
    public static void main(String[] args) {
        Animal a = new Dog();
        Native.putchar(a.sound());  // 'D' via virtual dispatch
        a = new Cat();
        Native.putchar(a.sound());  // 'C' via virtual dispatch
        Native.putchar(10);
        Native.halt();
    }
}
