class FieldBase {
	int value;
	static int shared;

	void setBase() {
		value = 65;
		shared = 67;
	}

	int baseValue() {
		return value;
	}
}

class FieldChild extends FieldBase {
	int value;
	static int shared;

	void setChild() {
		value = 66;
		shared = 68;
	}

	int childValue() {
		return value;
	}
}

class T78_FieldHiding {
	public static void main() {
		FieldChild child = new FieldChild();
		child.setBase();
		child.setChild();
		Native.putchar(child.baseValue());  // 'A'
		Native.putchar(child.childValue()); // 'B'
		Native.putchar(FieldBase.shared);   // 'C'
		Native.putchar(FieldChild.shared);  // 'D'
		Native.putchar(child.value);        // 'B'
		FieldBase base = child;
		Native.putchar(base.value);         // 'A'
		Native.putchar(10);
	}
}
