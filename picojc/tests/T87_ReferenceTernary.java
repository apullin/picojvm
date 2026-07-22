class T87Base {
	int value;

	T87Base(int value) {
		this.value = value;
	}
}

class T87Child extends T87Base {
	T87Child(int value) {
		super(value);
	}
}

public class T87_ReferenceTernary {
	static T87Base choose(boolean child) {
		return child ? new T87Child(65) : new T87Base(66);
	}

	public static void main(String[] args) {
		Native.putchar(choose(true).value);
		Native.putchar(choose(false).value);
		Native.putchar(10);
	}
}
