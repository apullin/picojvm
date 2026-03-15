public class Resolver {
	static void resolve() {
		// Resolve parent class references (name → class index)
		// Save cCount: synthExcCls (called via fClsByNm)
		// may add new classes whose cParent is ALREADY a class index.
		int origClassCount = C.cCount;
		for (int ci = 0; ci < origClassCount; ci++) {
			if (C.cParent[ci] != -1) {
				int parentNm = C.cParent[ci]; // currently a name index
				int pid = fClsByNm(parentNm);
				C.cParent[ci] = (short)pid; // now a class index, -1 if not found (Object)
			}
		}

		// Resolve interface references
		for (int ci = 0; ci < C.cCount; ci++) {
			int start = C.cIfaceS[ci];
			for (int j = 0; j < C.cIfaceC[ci]; j++) {
				int ifNm = C.ifList[start + j]; // name index
				int ifId = fClsByNm(ifNm);
				C.ifList[start + j] = (byte)ifId; // now class index
			}
		}

		// Compute instance field counts (including inherited)
		for (int ci = 0; ci < C.cCount; ci++) {
			int inherited = 0;
			if (C.cParent[ci] >= 0) {
				inherited = C.cFieldC[C.cParent[ci]];
			}
			C.cFieldC[ci] = (byte)(inherited + C.cOwnF[ci]);
		}

		// Assign field slots
		C.sfCount = 0;
		for (int fi = 0; fi < C.fCount; fi++) {
			if (C.fStatic[fi]) {
				if (C.fFinal[fi] && C.fHasConst[fi]) continue; // inlined, no slot needed
				C.fSlot[fi] = (short)C.sfCount++;
			} else {
				// Instance field: slot = parent field count + own offset
				int ci = C.fClass[fi];
				int inherited = 0;
				if (C.cParent[ci] >= 0) {
					inherited = C.cFieldC[C.cParent[ci]];
				}
				int ownIdx = 0;
				for (int fj = 0; fj < fi; fj++) {
					if (!C.fStatic[fj] && C.fClass[fj] == ci) {
						ownIdx++;
					}
				}
				C.fSlot[fi] = (short)(inherited + ownIdx);
			}
		}

		// Ensure all user classes have a default constructor if none declared
		for (int ci = C.uClsStart; ci < C.cCount; ci++) {
			if (C.cIsIface[ci]) continue;
			boolean hasCtor = false;
			for (int mi = 0; mi < C.mCount; mi++) {
				if (C.mClass[mi] == ci && C.mIsCtor[mi]) {
					hasCtor = true;
					break;
				}
			}
			if (!hasCtor) {
				int mi = C.initMethod(ci, C.N_INIT, 1, false, true, false, 0);
				C.mBodyS[mi] = -2; C.mBodyE[mi] = -2;
			}
		}

		// Ensure Object.<init> exists
		C.ensNat(C.N_OBJECT, C.N_INIT);

		// Build vtables
		for (int ci = 0; ci < C.cCount; ci++) {
			if (C.cIsIface[ci]) continue;
			C.vtBase[ci] = (byte)vtableLen();
			int parentVtSize = 0;
			if (C.cParent[ci] >= 0) {
				// Copy parent vtable
				int pid = C.cParent[ci];
				parentVtSize = C.cVtSize[pid];
				int pBase = C.vtBase[pid];
				for (int j = 0; j < parentVtSize; j++) {
					C.vtable[C.vtBase[ci] + j] = C.vtable[pBase + j];
				}
			}
			C.cVtSize[ci] = (byte)parentVtSize;

			// Add/override methods
			for (int mi = 0; mi < C.mCount; mi++) {
				if (C.mClass[mi] != ci) continue;
				if (C.mStatic[mi] || C.mNative[mi]) continue;
				if (C.mIsCtor[mi]) continue;

				// Check if this overrides a parent method
				int slot = -1;
				for (int j = 0; j < C.cVtSize[ci]; j++) {
					int existingMi = C.vtable[C.vtBase[ci] + j];
					if (C.mName[existingMi] == C.mName[mi]) {
						slot = j;
						break;
					}
				}
				if (slot >= 0) {
					C.vtable[C.vtBase[ci] + slot] = (short)mi;
					C.mVtSlot[mi] = (byte)slot;
				} else {
					slot = C.cVtSize[ci]++;
					C.vtable[C.vtBase[ci] + slot] = (short)mi;
					C.mVtSlot[mi] = (byte)slot;
				}
			}
		}

		// Assign vmids for interface methods
		int nextVmid = 0;
		for (int ci = 0; ci < C.cCount; ci++) {
			if (!C.cIsIface[ci]) continue;
			for (int mi = 0; mi < C.mCount; mi++) {
				if (C.mClass[mi] != ci) continue;
				C.mVmid[mi] = (byte)nextVmid++;
			}
		}
		// Copy vmids to implementing class methods
		for (int ci = 0; ci < C.cCount; ci++) {
			if (C.cIsIface[ci]) continue;
			int start = C.cIfaceS[ci];
			for (int j = 0; j < C.cIfaceC[ci]; j++) {
				int ifId = C.ifList[start + j];
				if (ifId < 0) continue;
				for (int imi = 0; imi < C.mCount; imi++) {
					if (C.mClass[imi] != ifId) continue;
					// Find matching method in implementing class
					for (int cmi = 0; cmi < C.mCount; cmi++) {
						if (C.mClass[cmi] != ci) continue;
						if (C.mName[cmi] == C.mName[imi]) {
							C.mVmid[cmi] = C.mVmid[imi];
						}
					}
				}
			}
		}

		// Ensure all cataloged native methods have their flags set
		for (int mi = 0; mi < C.mCount; mi++) {
			if (C.mNative[mi] && C.mFlags[mi] == 0) {
				int info = C.natInfo(C.mName[mi]);
				if (info >= 0) C.mFlags[mi] = (byte)(((info >> 8) << 1) | 1);
			}
		}

		// Find main method
		C.mainMi = -1;
		for (int mi = 0; mi < C.mCount; mi++) {
			if (C.mName[mi] == C.N_MAIN && C.mStatic[mi] && !C.mNative[mi]) {
				C.mainMi = mi;
				break;
			}
		}
		if (C.mainMi < 0) {
			Lexer.error(200); // No main method found
		}
		// picoJVM calls main without pushing arguments, so arg_count must be 0
		if (C.mainMi >= 0) {
			C.mArgC[C.mainMi] = 0;
		}
	}

	static int vtableLen() {
		// Sum of all vtable sizes so far
		int total = 0;
		for (int ci = 0; ci < C.cCount; ci++) {
			total += C.cVtSize[ci];
		}
		return total;
	}

	static int fClsByNm(int nm) {
		for (int ci = 0; ci < C.cCount; ci++) {
			if (C.cName[ci] == nm) return ci;
		}
		// Check well-known names
		if (nm == C.N_THROWABLE || nm == C.N_EXCEPTION || nm == C.N_RUNTIME_EX) {
			// Synthesize exception class
			return synthExcCls(nm);
		}
		return -1;
	}

	static int synthExcCls(int nm) {
		// Ensure parent hierarchy exists
		int parentNm;
		if (nm == C.N_THROWABLE) parentNm = -1; // Object
		else if (nm == C.N_EXCEPTION) {
			parentNm = C.N_THROWABLE;
			synthExcCls(C.N_THROWABLE); // ensure parent exists
		}
		else { // RuntimeException
			parentNm = C.N_EXCEPTION;
			synthExcCls(C.N_EXCEPTION); // ensure parent exists
		}

		// Check if already exists
		for (int ci = 0; ci < C.cCount; ci++) {
			if (C.cName[ci] == nm) return ci;
		}

		int ci = C.initClass(nm);
		C.cParent[ci] = (short)(parentNm == -1 ? -1 : fClsByNm(parentNm));
		C.vtBase[ci] = (byte)vtableLen();
		C.cBodyS[ci] = -1; C.cBodyE[ci] = -1;
		return ci;
	}


	static int fField(int ci, int nm) {
		// Search this class and parents
		while (ci >= 0) {
			for (int fi = 0; fi < C.fCount; fi++) {
				if (C.fClass[fi] == ci && C.fName[fi] == nm) return fi;
			}
			ci = C.cParent[ci];
		}
		return -1;
	}

	static int fStatField(int ci, int nm) {
		int best = -1;
		for (int fi = 0; fi < C.fCount; fi++) {
			if (C.fName[fi] == nm && C.fStatic[fi]) {
				if (C.fClass[fi] == ci) return fi;
				if (best < 0) best = fi;
			}
		}
		return best;
	}

	static int fInstField(int nm) {
		for (int fi = 0; fi < C.fCount; fi++) {
			if (C.fName[fi] == nm && !C.fStatic[fi]) return fi;
		}
		return -1;
	}
}
