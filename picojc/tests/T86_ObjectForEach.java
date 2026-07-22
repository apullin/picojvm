class T86Item {
	int value;

	T86Item(int value) {
		this.value = value;
	}
}

public class T86_ObjectForEach {
	public static void main(String[] args) {
		T86Item[] items = new T86Item[2];
		items[0] = new T86Item(65);
		items[1] = new T86Item(66);
		for (T86Item item : items) {
			Native.putchar(item.value);
		}
		Native.putchar(10);
	}
}
