interface Speakable {
	int speak();
}

interface Countable {
	int count();
}

interface Marked {
}

interface Special extends Marked {
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

class GuideDog extends Dog implements Special {
	GuideDog(int l) { super(l); }
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

		Native.putchar(d instanceof Speakable ? 49 : 48); // '1'
		Native.putchar(c instanceof Countable ? 49 : 48); // '0'

		// Membership is inherited through both a superclass and an interface.
		Speakable inherited = new GuideDog(2);
		Native.putchar(inherited instanceof Countable ? 50 : 48); // '2'
		Countable asCount = (Countable)inherited;
		tally(asCount); // '2'
		GuideDog guide = new GuideDog(1);
		Native.putchar(guide instanceof Marked ? 73 : 48); // 'I'
		Marked marked = (Marked)guide;
		Native.putchar(marked != null ? 89 : 48); // 'Y'
		Native.putchar(10);
	}
}
