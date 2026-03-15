enum Color {
	RED, GREEN, BLUE
}

enum Dir {
	NORTH, EAST, SOUTH, WEST;

	static int opposite(int d) {
		return (d + 2) % 4;
	}
}

class T44_Enum {
	public static void main() {
		Native.putchar(Color.RED + '0');
		Native.putchar(Color.GREEN + '0');
		Native.putchar(Color.BLUE + '0');

		int c = Color.GREEN;
		switch (c) {
			case 0: Native.putchar('R'); break;
			case 1: Native.putchar('G'); break;
			case 2: Native.putchar('B'); break;
		}

		Native.putchar(Dir.NORTH + 'A');
		Native.putchar(Dir.WEST + 'A');

		Native.putchar(Dir.opposite(Dir.NORTH) + '0');
		Native.putchar(Dir.opposite(Dir.EAST) + '0');
	}
}
