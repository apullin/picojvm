class Animal {
	int id;
	Animal(int i) { id = i; }
	int sound() { return 63; } // '?'
}

class Cow extends Animal {
	Cow(int i) { id = i; }
	int sound() { return 77; } // 'M' for moo
	int milk() { return 42; } // '*'
}

class Pig extends Animal {
	Pig(int i) { id = i; }
	int sound() { return 79; } // 'O' for oink
}

class T51_ObjectCast {
	public static void main() {
		// Upcast (implicit)
		Animal a1 = new Cow(1);
		Animal a2 = new Pig(2);

		// Virtual dispatch through base ref
		Native.putchar(a1.sound()); // 'M'
		Native.putchar(a2.sound()); // 'O'

		// Downcast
		Cow c = (Cow) a1;
		Native.putchar(c.milk()); // '*'

		// instanceof before cast
		if (a1 instanceof Cow) {
			Native.putchar(89); // 'Y'
		}
		if (a2 instanceof Cow) {
			Native.putchar(78); // 'N' — should NOT print
		}
		if (a2 instanceof Pig) {
			Native.putchar(89); // 'Y'
		}

		// Null checks
		Animal a3 = null;
		if (a3 instanceof Animal) {
			Native.putchar(78); // should NOT print
		} else {
			Native.putchar(48); // '0' — null is not instanceof anything
		}
	}
}
