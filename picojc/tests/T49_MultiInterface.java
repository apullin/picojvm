interface Speakable {
	int speak();
}

interface Countable {
	int count();
}

class Dog implements Speakable, Countable {
	int legs;
	Dog(int l) { legs = l; }
	public int speak() { return 87; } // 'W' for woof
	public int count() { return legs + 48; } // '4'
}

class Cat implements Speakable {
	public int speak() { return 77; } // 'M' for meow
}

class T49_MultiInterface {
	static void hear(Speakable s) {
		Native.putchar(s.speak());
	}
	static void tally(Countable c) {
		Native.putchar(c.count());
	}

	public static void main() {
		Dog d = new Dog(4);
		Cat c = new Cat();
		hear(d);  // 'W'
		hear(c);  // 'M'
		tally(d); // '4'
		// Interface + inheritance: Dog implements both
		Speakable s = new Dog(3);
		hear(s);  // 'W'
	}
}
