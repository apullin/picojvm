public class Linker {
	static void writeOut() {
		C.outLen = 0;
		int userClassCount = C.cCount - C.uClsStart;

		// Count non-interface user classes
		int pjvmClassCount = 0;
		for (int ci = C.uClsStart; ci < C.cCount; ci++) {
			if (!C.cIsIface[ci]) pjvmClassCount++;
		}

		// v3 Header (16 bytes)
		wB(0x85); // magic
		wB(0x4C); // version = v3
		wB(C.mCount); // n_methods
		wB(C.mainMi); // main_mi
		wSLE(C.sfCount); // n_static_fields (16-bit LE)
		wB(C.intCC); // n_int_constants
		wB(pjvmClassCount); // n_classes
		wB(C.strCC); // n_string_constants
		wB(0); // region_flags (reserved)
		wILE(C.cdLen); // bytecodes_size (32-bit LE)
		wSLE(0); // reserved

		// Class table
		for (int ci = C.uClsStart; ci < C.cCount; ci++) {
			if (C.cIsIface[ci]) continue;
			int parentId = 0xFF;
			if (C.cParent[ci] >= 0) {
				parentId = C.cParent[ci] - C.uClsStart;
				if (parentId < 0) parentId = 0xFF;
			}
			wB(parentId);
			wB(C.cFieldC[ci]);
			wB(C.cVtSize[ci]);
			int clinitIdx = 0xFF;
			if (C.cClinit[ci] != 0xFF) clinitIdx = C.cClinit[ci];
			wB(clinitIdx);
			// Vtable entries
			for (int j = 0; j < C.cVtSize[ci]; j++) {
				wB(C.vtable[C.vtBase[ci] + j]);
			}
		}

		// Compute exc_off_idx: cumulative exception entry count per method
		int excRunning = 0;
		for (int mi = 0; mi < C.mCount; mi++) {
			C.mExcIdx[mi] = (byte)excRunning;
			excRunning += C.mExcC[mi];
		}

		// Method table (14 bytes per method, v2/v3 format)
		for (int mi = 0; mi < C.mCount; mi++) {
			wB(C.mMaxLoc[mi]);
			wB(C.mMaxStk[mi]);
			wB(C.mArgC[mi]);
			wB(C.mFlags[mi]);
			wILE(C.mCodeOff[mi]); // 32-bit code_offset
			wSLE(C.mCpBase[mi] * 2); // byte offset (2 bytes per entry)
			wB(C.mVtSlot[mi]);
			wB(C.mVmid[mi]);
			wB(C.mExcC[mi]);
			wB(C.mExcIdx[mi]);
		}

		// CP resolution table (16-bit entries)
		wSLE(C.cpSz * 2); // byte count = entries × 2
		for (int i = 0; i < C.cpSz; i++) {
			wB(C.cpEnt[i] & 0xFF);
			wB(C.cpEntH[i] & 0xFF);
		}

		// Integer constants (4 bytes each, LE)
		for (int i = 0; i < C.intCC; i++) {
			int v = C.intC[i];
			wB(v & 0xFF);
			wB((v >> 8) & 0xFF);
			wB((v >> 16) & 0xFF);
			wB((v >> 24) & 0xFF);
		}

		// String constants
		for (int i = 0; i < C.strCC; i++) {
			int len = C.strCLen[i];
			wSLE(len);
			Native.writeBytes(C.strC[i], 0, len);
			C.outLen += len;
		}

		// Bytecodes
		if (C.diskSpill) {
			// Read bytecodes back from spill file
			for (int i = 0; i < C.cdLen; i++) {
				wB(Native.fileReadByte());
			}
		} else {
			for (int i = 0; i < C.cdLen; i++) {
				wB(Native.peek(C.cdBase + i) & 0xFF);
			}
		}

		// Exception table (7 bytes per entry)
		for (int i = 0; i < C.excC; i++) {
			wSLE(C.excSPc[i]);
			wSLE(C.excEPc[i]);
			wSLE(C.excHPc[i]);
			wB(C.excCCls[i]);
		}
	}

	static void wB(int b) {
		Native.putchar(b & 0xFF);
		C.outLen++;
	}

	static void wSLE(int s) {
		wB(s & 0xFF);
		wB((s >> 8) & 0xFF);
	}

	static void wILE(int v) {
		wB(v & 0xFF);
		wB((v >> 8) & 0xFF);
		wB((v >> 16) & 0xFF);
		wB((v >> 24) & 0xFF);
	}
}
