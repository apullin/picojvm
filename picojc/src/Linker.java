public class Linker {
	static final int RF_REF_BITMAPS = 0x02;
	static final int REF_BITMAP_STRIDE = (C.MAX_FIELDS + 7) >> 3;
	static byte[] refBm = new byte[C.MAX_CLASSES * REF_BITMAP_STRIDE];

	// @Const ROM array accumulation (filled by Emit, written by writeOut)
	static final int MAX_CONST = 16;
	static int constC;
	static short[] constSlot = new short[MAX_CONST];
	static byte[] constET = new byte[MAX_CONST];     // elem type (0=byte,1=char,2=short,3=int)
	static short[] constEC = new short[MAX_CONST];    // elem count
	static byte[] constBuf = new byte[1024];          // flat binary data
	static int constBL;                               // buf write pos
	static int[] constOff = new int[MAX_CONST];       // per-entry offset into constBuf
	static int[] constFO = new int[MAX_CONST];        // per-entry file offset (computed during link)

	static boolean isRefField(int fi) {
		return C.fGcRef[fi];
	}

	static void buildRefBitmaps() {
		for (int i = 0; i < refBm.length; i++) refBm[i] = 0;

		for (int ci = C.uClsStart; ci < C.cCount; ci++) {
			int outCi = ci - C.uClsStart;
			int dst = outCi * REF_BITMAP_STRIDE;
			int parent = C.cParent[ci];

			if (parent >= C.uClsStart) {
				int src = (parent - C.uClsStart) * REF_BITMAP_STRIDE;
				int parentBytes = (C.cFieldC[parent] + 7) >> 3;
				for (int j = 0; j < parentBytes; j++) refBm[dst + j] = refBm[src + j];
			}

			for (int fi = 0; fi < C.fCount; fi++) {
				if (C.fClass[fi] != ci || C.fStatic[fi] || !isRefField(fi)) continue;
				int slot = C.fSlot[fi] & 0xFFFF;
				int byteIdx = dst + (slot >> 3);
				int mask = 1 << (slot & 7);
				refBm[byteIdx] = (byte)((refBm[byteIdx] & 0xFF) | mask);
			}
		}
	}

	static void writeOut() {
		C.outLen = 0;
		int userClassCount = C.cCount - C.uClsStart;

		// All user classes (including interfaces as stubs for class ID consistency)
		int pjvmClassCount = C.cCount - C.uClsStart;

		// v3 Header (16 bytes)
		wB(0x85); // magic
		wB(0x4C); // version = v3
		wB(C.mCount); // n_methods
		wB(C.mainMi); // main_mi
		wSLE(C.sfCount); // n_static_fields (16-bit LE)
		wB(C.intCC); // n_int_constants
		wB(pjvmClassCount); // n_classes
		wB(C.strCC); // n_string_constants
		buildRefBitmaps();
		wB(RF_REF_BITMAPS | (constC > 0 ? 0x04 : 0)); // region_flags
		wILE(C.cdLen); // bytecodes_size (32-bit LE)
		wSLE(0); // reserved

		// Class table (interfaces included as stubs for class ID mapping)
		for (int ci = C.uClsStart; ci < C.cCount; ci++) {
			int outCi = ci - C.uClsStart;
			int bitmapOff = outCi * REF_BITMAP_STRIDE;
			int bitmapLen = (C.cFieldC[ci] + 7) >> 3;
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
			for (int j = 0; j < bitmapLen; j++) {
				wB(refBm[bitmapOff + j] & 0xFF);
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

		// const_data section (@Const ROM arrays)
		if (constC > 0) {
			wSLE(constC); // n_const_arrays
			// Write entries and record file offsets
			for (int i = 0; i < constC; i++) {
				constFO[i] = C.outLen;
				wSLE(constEC[i]);        // n_elements
				wB(constET[i] & 0xFF);   // elem_type
				wB(0);                   // reserved
				int dStart = constOff[i];
				int dEnd = (i + 1 < constC) ? constOff[i + 1] : constBL;
				for (int j = dStart; j < dEnd; j++) {
					wB(constBuf[j] & 0xFF);
				}
			}
			// Init table: (slot, lo, hi) triples
			wSLE(constC); // n_init
			for (int i = 0; i < constC; i++) {
				wSLE(constSlot[i]);
				int off = constFO[i];
				wSLE(off & 0xFFFF);          // lo
				wSLE((off >> 16) + 1);       // hi
			}
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
