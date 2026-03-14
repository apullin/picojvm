public class T41_FileIO {
	public static void main(String[] args) {
		// Write a file
		byte[] name = new byte[8];
		name[0] = (byte)'t'; name[1] = (byte)'e'; name[2] = (byte)'s';
		name[3] = (byte)'t'; name[4] = (byte)'.'; name[5] = (byte)'t';
		name[6] = (byte)'x'; name[7] = (byte)'t';

		int r = Native.fileOpen(name, 8, 2); // mode 2 = write
		if (r != 0) {
			Native.print("FAIL: open write");
			return;
		}

		// Write "Hello File IO"
		byte[] msg = new byte[13];
		msg[0]=(byte)'H'; msg[1]=(byte)'e'; msg[2]=(byte)'l'; msg[3]=(byte)'l';
		msg[4]=(byte)'o'; msg[5]=(byte)' '; msg[6]=(byte)'F'; msg[7]=(byte)'i';
		msg[8]=(byte)'l'; msg[9]=(byte)'e'; msg[10]=(byte)' '; msg[11]=(byte)'I';
		msg[12]=(byte)'O';
		Native.fileWrite(msg, 0, 13);
		Native.fileClose();

		// Read it back
		r = Native.fileOpen(name, 8, 1); // mode 1 = read
		if (r != 0) {
			Native.print("FAIL: open read");
			return;
		}

		byte[] buf = new byte[64];
		int n = Native.fileRead(buf, 0, 64);
		Native.fileClose();

		// Print what we read
		for (int i = 0; i < n; i++) {
			Native.putchar(buf[i] & 0xFF);
		}
		Native.putchar(10); // newline

		// Also test byte-at-a-time read
		r = Native.fileOpen(name, 8, 1);
		int count = 0;
		int ch = Native.fileReadByte();
		while (ch >= 0) {
			count++;
			ch = Native.fileReadByte();
		}
		Native.fileClose();

		// Print byte count
		Native.print("Bytes: ");
		if (count == 13) {
			Native.print("13");
		} else {
			Native.print("WRONG");
		}
		Native.putchar(10);

		Native.print("OK");
		Native.putchar(10);
	}
}
