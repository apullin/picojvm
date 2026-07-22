interface N62Side {
}

class N62Base {
}

class N62Narrow extends N62Base {
}

class N62Actual extends N62Narrow implements N62Side {
}

public class N62_AmbiguousMethod {
	static int pick(N62Base value) {
		return 1;
	}

	static int pick(N62Side value) {
		return 2;
	}

	static int pick(N62Narrow value) {
		return 3;
	}

	public static void main(String[] args) {
		Native.putchar(48 + pick(new N62Actual()));
	}
}
