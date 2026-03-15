class Base {
	int val;
	Base(int v) { val = v; }
	int getVal() { return val; }
}

class Mid extends Base {
	int mid;
	Mid(int v, int m) {
		val = v;
		mid = m;
	}
	int getMid() { return mid; }
}

class Leaf extends Mid {
	int leaf;
	Leaf(int v, int m, int l) {
		val = v;
		mid = m;
		leaf = l;
	}
}

class T54_InheritField {
	public static void main() {
		Leaf obj = new Leaf(65, 66, 67);
		Native.putchar(obj.val);     // 'A' (inherited from Base)
		Native.putchar(obj.mid);     // 'B' (inherited from Mid)
		Native.putchar(obj.leaf);    // 'C' (own field)
		Native.putchar(obj.getVal()); // 'A' (virtual dispatch)
		Native.putchar(obj.getMid()); // 'B' (virtual dispatch)

		// Polymorphic access
		Base b = new Leaf(68, 69, 70);
		Native.putchar(b.getVal());  // 'D'

		// Cross-class static fields
		Counter.count = 72;
		Native.putchar(Counter.count); // 'H'
		Counter.count += 1;
		Native.putchar(Counter.count); // 'I'
	}
}

class Counter {
	static int count;
}
