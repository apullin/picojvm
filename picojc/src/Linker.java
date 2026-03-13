public class Linker {
    static void writeOutput() {
        Compiler.outLen = 0;
        int userClassCount = Compiler.classCount - Compiler.userClassStart;

        // Count non-interface user classes
        int pjvmClassCount = 0;
        for (int ci = Compiler.userClassStart; ci < Compiler.classCount; ci++) {
            if (!Compiler.classIsInterface[ci]) pjvmClassCount++;
        }

        // Header (10 bytes)
        writeByte(0x85); // magic
        writeByte(0x4A);
        writeByte(Compiler.methodCount); // n_methods
        writeByte(Compiler.mainMi); // main_mi
        writeByte(Compiler.staticFieldCount); // n_static
        writeByte(Compiler.intConstCount); // n_integers
        writeByte(pjvmClassCount); // n_classes
        writeByte(Compiler.strConstCount); // n_strings
        writeShortLE(Compiler.codeLen); // bytecodes_size

        // Class table
        for (int ci = Compiler.userClassStart; ci < Compiler.classCount; ci++) {
            if (Compiler.classIsInterface[ci]) continue;
            int parentId = 0xFF;
            if (Compiler.classParent[ci] >= 0) {
                parentId = Compiler.classParent[ci] - Compiler.userClassStart;
                if (parentId < 0) parentId = 0xFF;
            }
            writeByte(parentId);
            writeByte(Compiler.classFieldCount[ci]);
            writeByte(Compiler.classVtableSize[ci]);
            int clinitIdx = 0xFF;
            if (Compiler.classClinitMi[ci] != 0xFF) clinitIdx = Compiler.classClinitMi[ci];
            writeByte(clinitIdx);
            // Vtable entries
            for (int j = 0; j < Compiler.classVtableSize[ci]; j++) {
                writeByte(Compiler.vtable[Compiler.vtableBase[ci] + j]);
            }
        }

        // Compute exc_off_idx: cumulative exception entry count per method
        int excRunning = 0;
        for (int mi = 0; mi < Compiler.methodCount; mi++) {
            Compiler.methodExcIdx[mi] = excRunning;
            excRunning += Compiler.methodExcCount[mi];
        }

        // Method table (12 bytes per method)
        for (int mi = 0; mi < Compiler.methodCount; mi++) {
            writeByte(Compiler.methodMaxLocals[mi]);
            writeByte(Compiler.methodMaxStack[mi]);
            writeByte(Compiler.methodArgCount[mi]);
            writeByte(Compiler.methodFlags[mi]);
            writeShortLE(Compiler.methodCodeOff[mi]);
            writeShortLE(Compiler.methodCpBase[mi]);
            writeByte(Compiler.methodVtableSlot[mi]);
            writeByte(Compiler.methodVmid[mi]);
            writeByte(Compiler.methodExcCount[mi]);
            writeByte(Compiler.methodExcIdx[mi]);
        }

        // CP resolution table
        writeShortLE(Compiler.cpSize);
        Native.writeBytes(Compiler.cpEntries, 0, Compiler.cpSize);
        Compiler.outLen += Compiler.cpSize;

        // Integer constants (4 bytes each, LE)
        for (int i = 0; i < Compiler.intConstCount; i++) {
            int v = Compiler.intConsts[i];
            writeByte(v & 0xFF);
            writeByte((v >> 8) & 0xFF);
            writeByte((v >> 16) & 0xFF);
            writeByte((v >> 24) & 0xFF);
        }

        // String constants
        for (int i = 0; i < Compiler.strConstCount; i++) {
            int len = Compiler.strConstLen[i];
            writeShortLE(len);
            Native.writeBytes(Compiler.strConsts[i], 0, len);
            Compiler.outLen += len;
        }

        // Bytecodes
        for (int i = 0; i < Compiler.codeLen; i++) {
            writeByte(Native.peek(Compiler.codeBase + i) & 0xFF);
        }

        // Exception table (7 bytes per entry)
        for (int i = 0; i < Compiler.excCount; i++) {
            writeShortLE(Compiler.excStartPc[i]);
            writeShortLE(Compiler.excEndPc[i]);
            writeShortLE(Compiler.excHandlerPc[i]);
            writeByte(Compiler.excCatchClass[i]);
        }
    }

    static void writeByte(int b) {
        Native.putchar(b & 0xFF);
        Compiler.outLen++;
    }

    static void writeShortLE(int s) {
        writeByte(s & 0xFF);
        writeByte((s >> 8) & 0xFF);
    }
}
