public class Resolver {
    static void resolve() {
        // Resolve parent class references (name → class index)
        // Save classCount: synthesizeExceptionClass (called via findClassByName)
        // may add new classes whose classParent is ALREADY a class index.
        int origClassCount = Compiler.classCount;
        for (int ci = 0; ci < origClassCount; ci++) {
            if (Compiler.classParent[ci] != -1) {
                int parentNm = Compiler.classParent[ci]; // currently a name index
                int pid = findClassByName(parentNm);
                Compiler.classParent[ci] = pid; // now a class index, -1 if not found (Object)
            }
        }

        // Resolve interface references
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            int start = Compiler.classIfaceStart[ci];
            for (int j = 0; j < Compiler.classIfaceCount[ci]; j++) {
                int ifNm = Compiler.ifaceList[start + j]; // name index
                int ifId = findClassByName(ifNm);
                Compiler.ifaceList[start + j] = ifId; // now class index
            }
        }

        // Compute instance field counts (including inherited)
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            int inherited = 0;
            if (Compiler.classParent[ci] >= 0) {
                inherited = Compiler.classFieldCount[Compiler.classParent[ci]];
            }
            Compiler.classFieldCount[ci] = inherited + Compiler.classOwnFields[ci];
        }

        // Assign field slots
        Compiler.staticFieldCount = 0;
        for (int fi = 0; fi < Compiler.fieldCount; fi++) {
            if (Compiler.fieldIsStatic[fi]) {
                Compiler.fieldSlot[fi] = Compiler.staticFieldCount++;
            } else {
                // Instance field: slot = parent field count + own offset
                int ci = Compiler.fieldClass[fi];
                int inherited = 0;
                if (Compiler.classParent[ci] >= 0) {
                    inherited = Compiler.classFieldCount[Compiler.classParent[ci]];
                }
                int ownIdx = 0;
                for (int fj = 0; fj < fi; fj++) {
                    if (!Compiler.fieldIsStatic[fj] && Compiler.fieldClass[fj] == ci) {
                        ownIdx++;
                    }
                }
                Compiler.fieldSlot[fi] = inherited + ownIdx;
            }
        }

        // Ensure all user classes have a default constructor if none declared
        for (int ci = Compiler.userClassStart; ci < Compiler.classCount; ci++) {
            if (Compiler.classIsInterface[ci]) continue;
            boolean hasCtor = false;
            for (int mi = 0; mi < Compiler.methodCount; mi++) {
                if (Compiler.methodClass[mi] == ci && Compiler.methodIsConstructor[mi]) {
                    hasCtor = true;
                    break;
                }
            }
            if (!hasCtor) {
                // Add default constructor
                int mi = Compiler.methodCount++;
                Compiler.methodClass[mi] = ci;
                Compiler.methodName[mi] = Compiler.N_INIT;
                Compiler.methodArgCount[mi] = 1; // 'this'
                Compiler.methodIsStatic[mi] = false;
                Compiler.methodIsConstructor[mi] = true;
                Compiler.methodIsNative[mi] = false;
                Compiler.methodRetType[mi] = 0;
                Compiler.methodVtableSlot[mi] = 0xFF;
                Compiler.methodVmid[mi] = 0xFF;
                Compiler.methodExcCount[mi] = 0;
                Compiler.methodBodyStart[mi] = -2; // marker for auto-generated
                Compiler.methodBodyEnd[mi] = -2;
            }
        }

        // Ensure Object.<init> exists
        Compiler.ensureNative(Compiler.N_OBJECT, Compiler.N_INIT);

        // Build vtables
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            if (Compiler.classIsInterface[ci]) continue;
            Compiler.vtableBase[ci] = vtableLen();
            int parentVtSize = 0;
            if (Compiler.classParent[ci] >= 0) {
                // Copy parent vtable
                int pid = Compiler.classParent[ci];
                parentVtSize = Compiler.classVtableSize[pid];
                int pBase = Compiler.vtableBase[pid];
                for (int j = 0; j < parentVtSize; j++) {
                    Compiler.vtable[Compiler.vtableBase[ci] + j] = Compiler.vtable[pBase + j];
                }
            }
            Compiler.classVtableSize[ci] = parentVtSize;

            // Add/override methods
            for (int mi = 0; mi < Compiler.methodCount; mi++) {
                if (Compiler.methodClass[mi] != ci) continue;
                if (Compiler.methodIsStatic[mi] || Compiler.methodIsNative[mi]) continue;
                if (Compiler.methodIsConstructor[mi]) continue;

                // Check if this overrides a parent method
                int slot = -1;
                for (int j = 0; j < Compiler.classVtableSize[ci]; j++) {
                    int existingMi = Compiler.vtable[Compiler.vtableBase[ci] + j];
                    if (Compiler.methodName[existingMi] == Compiler.methodName[mi]) {
                        slot = j;
                        break;
                    }
                }
                if (slot >= 0) {
                    Compiler.vtable[Compiler.vtableBase[ci] + slot] = mi;
                    Compiler.methodVtableSlot[mi] = slot;
                } else {
                    slot = Compiler.classVtableSize[ci]++;
                    Compiler.vtable[Compiler.vtableBase[ci] + slot] = mi;
                    Compiler.methodVtableSlot[mi] = slot;
                }
            }
        }

        // Assign vmids for interface methods
        int nextVmid = 0;
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            if (!Compiler.classIsInterface[ci]) continue;
            for (int mi = 0; mi < Compiler.methodCount; mi++) {
                if (Compiler.methodClass[mi] != ci) continue;
                Compiler.methodVmid[mi] = nextVmid++;
            }
        }
        // Copy vmids to implementing class methods
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            if (Compiler.classIsInterface[ci]) continue;
            int start = Compiler.classIfaceStart[ci];
            for (int j = 0; j < Compiler.classIfaceCount[ci]; j++) {
                int ifId = Compiler.ifaceList[start + j];
                if (ifId < 0) continue;
                for (int imi = 0; imi < Compiler.methodCount; imi++) {
                    if (Compiler.methodClass[imi] != ifId) continue;
                    // Find matching method in implementing class
                    for (int cmi = 0; cmi < Compiler.methodCount; cmi++) {
                        if (Compiler.methodClass[cmi] != ci) continue;
                        if (Compiler.methodName[cmi] == Compiler.methodName[imi]) {
                            Compiler.methodVmid[cmi] = Compiler.methodVmid[imi];
                        }
                    }
                }
            }
        }

        // Update clinit method indices
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            int clinitMi = Compiler.classClinitMi[ci];
            if (clinitMi != 0xFF && clinitMi < Compiler.methodCount) {
                // clinitMi is already the method index
            }
        }

        // Ensure all cataloged native methods have their flags set
        for (int mi = 0; mi < Compiler.methodCount; mi++) {
            if (Compiler.methodIsNative[mi] && Compiler.methodFlags[mi] == 0) {
                int nm = Compiler.methodName[mi];
                int nid = -1;
                if (nm == Compiler.N_PUTCHAR)      nid = 0;
                else if (nm == Compiler.N_IN)      nid = 1;
                else if (nm == Compiler.N_OUT)     nid = 2;
                else if (nm == Compiler.N_PEEK)    nid = 3;
                else if (nm == Compiler.N_POKE)    nid = 4;
                else if (nm == Compiler.N_HALT)    nid = 5;
                else if (nm == Compiler.N_INIT)    nid = 6;
                else if (nm == Compiler.N_LENGTH)  nid = 7;
                else if (nm == Compiler.N_CHARAT)  nid = 8;
                else if (nm == Compiler.N_EQUALS)  nid = 9;
                else if (nm == Compiler.N_TOSTRING) nid = 10;
                else if (nm == Compiler.N_PRINT)   nid = 11;
                else if (nm == Compiler.N_HASHCODE) nid = 12;
                else if (nm == Compiler.N_ARRAYCOPY)       nid = 13;
                else if (nm == Compiler.N_MEMCMP)          nid = 14;
                else if (nm == Compiler.N_WRITE_BYTES)     nid = 15;
                else if (nm == Compiler.N_STRING_FROM_BYTES) nid = 16;
                if (nid >= 0) Compiler.methodFlags[mi] = (nid << 1) | 1;
            }
        }

        // Find main method
        Compiler.mainMi = -1;
        for (int mi = 0; mi < Compiler.methodCount; mi++) {
            if (Compiler.methodName[mi] == Compiler.N_MAIN && Compiler.methodIsStatic[mi] && !Compiler.methodIsNative[mi]) {
                Compiler.mainMi = mi;
                break;
            }
        }
        if (Compiler.mainMi < 0) {
            Lexer.error(200); // No main method found
        }
        // picoJVM calls main without pushing arguments, so arg_count must be 0
        if (Compiler.mainMi >= 0) {
            Compiler.methodArgCount[Compiler.mainMi] = 0;
        }
    }

    static int vtableLen() {
        // Sum of all vtable sizes so far
        int total = 0;
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            total += Compiler.classVtableSize[ci];
        }
        return total;
    }

    static int findClassByName(int nm) {
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            if (Compiler.className[ci] == nm) return ci;
        }
        // Check well-known names
        if (nm == Compiler.N_THROWABLE || nm == Compiler.N_EXCEPTION || nm == Compiler.N_RUNTIME_EX) {
            // Synthesize exception class
            return synthesizeExceptionClass(nm);
        }
        return -1;
    }

    static int synthesizeExceptionClass(int nm) {
        // Ensure parent hierarchy exists
        int parentNm;
        if (nm == Compiler.N_THROWABLE) parentNm = -1; // Object
        else if (nm == Compiler.N_EXCEPTION) {
            parentNm = Compiler.N_THROWABLE;
            synthesizeExceptionClass(Compiler.N_THROWABLE); // ensure parent exists
        }
        else { // RuntimeException
            parentNm = Compiler.N_EXCEPTION;
            synthesizeExceptionClass(Compiler.N_EXCEPTION); // ensure parent exists
        }

        // Check if already exists
        for (int ci = 0; ci < Compiler.classCount; ci++) {
            if (Compiler.className[ci] == nm) return ci;
        }

        int ci = Compiler.classCount++;
        Compiler.className[ci] = nm;
        Compiler.classParent[ci] = parentNm == -1 ? -1 : findClassByName(parentNm);
        Compiler.classIsInterface[ci] = false;
        Compiler.classClinitMi[ci] = 0xFF;
        Compiler.classFieldCount[ci] = 0;
        Compiler.classOwnFields[ci] = 0;
        Compiler.classIfaceStart[ci] = Compiler.ifaceListLen;
        Compiler.classIfaceCount[ci] = 0;
        Compiler.vtableBase[ci] = vtableLen();
        Compiler.classVtableSize[ci] = 0;
        Compiler.classBodyStart[ci] = -1;
        Compiler.classBodyEnd[ci] = -1;
        return ci;
    }


    static int findField(int ci, int nm) {
        // Search this class and parents
        while (ci >= 0) {
            for (int fi = 0; fi < Compiler.fieldCount; fi++) {
                if (Compiler.fieldClass[fi] == ci && Compiler.fieldName[fi] == nm) return fi;
            }
            ci = Compiler.classParent[ci];
        }
        return -1;
    }
}
