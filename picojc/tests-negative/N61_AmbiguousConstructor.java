interface N61Side {
}

class N61Base {
}

class N61Narrow extends N61Base {
}

class N61Actual extends N61Narrow implements N61Side {
}

class N61Pick {
	N61Pick(N61Base value) {
	}

	N61Pick(N61Side value) {
	}

	N61Pick(N61Narrow value) {
	}
}

public class N61_AmbiguousConstructor {
	public static void main(String[] args) {
		N61Pick value = new N61Pick(new N61Actual());
	}
}
