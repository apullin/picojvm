public class Linker {
    static void writeOut() {
        C.outLen = 0;
        int userClassCount = C.cCount - C.uClsStart;

        // Count non-interface user classes
        int pjvmClassCount = 0;
        for (int ci = C.uClsStart; ci < C.cCount; ci++) {
            if (!C.cIsIface[ci]) pjvmClassCount++;
        }

        // Header (10 bytes)
        wB(0x85); // magic
        wB(0x4A);
        wB(C.mCount); // n_methods
        wB(C.mainMi); // main_mi
        wB(C.sfCount); // n_static
        wB(C.intCC); // n_integers
        wB(pjvmClassCount); // n_classes
        wB(C.strCC); // n_strings
        wSLE(C.cdLen); // bytecodes_size

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
            C.mExcIdx[mi] = excRunning;
            excRunning += C.mExcC[mi];
        }

        // Method table (12 bytes per method)
        for (int mi = 0; mi < C.mCount; mi++) {
            wB(C.mMaxLoc[mi]);
            wB(C.mMaxStk[mi]);
            wB(C.mArgC[mi]);
            wB(C.mFlags[mi]);
            wSLE(C.mCodeOff[mi]);
            wSLE(C.mCpBase[mi]);
            wB(C.mVtSlot[mi]);
            wB(C.mVmid[mi]);
            wB(C.mExcC[mi]);
            wB(C.mExcIdx[mi]);
        }

        // CP resolution table
        wSLE(C.cpSz);
        Native.writeBytes(C.cpEnt, 0, C.cpSz);
        C.outLen += C.cpSz;

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
        for (int i = 0; i < C.cdLen; i++) {
            wB(Native.peek(C.cdBase + i) & 0xFF);
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
}
