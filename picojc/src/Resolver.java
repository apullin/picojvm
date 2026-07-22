public class Resolver {
	static byte[] classOrder = new byte[C.MAX_CLASSES];
	static byte[] classRemap = new byte[C.MAX_CLASSES];
	static byte[] classAt = new byte[C.MAX_CLASSES];

	static void resolve() {
		// Resolve parent class references (name → class index)
		// Save cCount: synthExcCls (called via fClsByNm)
		// may add new classes whose cParent is ALREADY a class index.
		int origClassCount = C.cCount;
		for (int ci = 0; ci < origClassCount; ci++) {
			if (C.cParent[ci] != -1) {
				int parentNm = C.cParent[ci]; // currently a name index
				int pid = fClsByNm(parentNm);
				if (pid < 0 && parentNm != C.N_OBJECT) Lexer.error(202);
				C.cParent[ci] = (short)pid; // now a class index, -1 if not found (Object)
			}
		}

		// Resolve interface references
		for (int ci = 0; ci < C.cCount; ci++) {
			int start = C.cIfaceS[ci] & 0xFF;
			for (int j = 0; j < (C.cIfaceC[ci] & 0xFF); j++) {
				int ifNm = C.ifList[start + j]; // name index
				int ifId = fClsByNm(ifNm);
				if (ifId < 0 || !C.cIsIface[ifId]) Lexer.error(202);
				C.ifList[start + j] = (short)ifId; // now class index
			}
		}

		// Every declared reference type must resolve after the full catalog pass.
		for (int fi = 0; fi < C.fCount; fi++) {
			int nm = C.fRefNm[fi];
			if (nm >= 0 && !Catalog.isBuiltinType(nm) && fClsByNm(nm) < 0) Lexer.error(202);
		}
		for (int mi = 0; mi < C.mCount; mi++) {
			int nm = C.mRetRefNm[mi];
			if (nm >= 0 && !Catalog.isBuiltinType(nm) && fClsByNm(nm) < 0) Lexer.error(202);
			int ss = C.mSigS[mi] & 0xFFFF;
			for (int i = 0; i < (C.mSigC[mi] & 0xFF); i++) {
				int sig = C.sigParam[ss + i];
				if (sig >= C.SIG_OBJ_ARRAY_BASE) sig = sig - C.SIG_OBJ_ARRAY_BASE;
				if (sig >= 0 && !Catalog.isBuiltinType(sig) && fClsByNm(sig) < 0) Lexer.error(202);
			}
		}

		// Stable topological order: preserve declaration order among unrelated
		// classes while ensuring every parent precedes its children.
		for (int ci = 0; ci < C.cCount; ci++) {
			classRemap[ci] = (byte)(ci < C.uClsStart ? ci : -1);
			classOrder[ci] = (byte)ci;
			classAt[ci] = (byte)ci;
		}
		int ordered = C.uClsStart;
		while (ordered < C.cCount) {
			boolean progress = false;
			for (int ci = C.uClsStart; ci < C.cCount; ci++) {
				if (classRemap[ci] >= 0) continue;
				int parent = C.cParent[ci];
				if (parent < 0 || classRemap[parent] >= 0) {
					classOrder[ordered] = (byte)ci;
					classRemap[ci] = (byte)ordered++;
					progress = true;
				}
			}
			if (!progress) Lexer.error(271); // cyclic inheritance
		}
		for (int dst = C.uClsStart; dst < C.cCount; dst++) {
			int wanted = classOrder[dst] & 0xFF;
			int src = dst;
			while ((classAt[src] & 0xFF) != wanted) src++;
			if (src != dst) {
				short sv; byte bv; int iv; boolean zv;
				sv = C.cName[dst]; C.cName[dst] = C.cName[src]; C.cName[src] = sv;
				sv = C.cSimple[dst]; C.cSimple[dst] = C.cSimple[src]; C.cSimple[src] = sv;
				sv = C.cParent[dst]; C.cParent[dst] = C.cParent[src]; C.cParent[src] = sv;
				bv = C.cFieldC[dst]; C.cFieldC[dst] = C.cFieldC[src]; C.cFieldC[src] = bv;
				bv = C.cOwnF[dst]; C.cOwnF[dst] = C.cOwnF[src]; C.cOwnF[src] = bv;
				bv = C.cVtSize[dst]; C.cVtSize[dst] = C.cVtSize[src]; C.cVtSize[src] = bv;
				sv = C.cClinit[dst]; C.cClinit[dst] = C.cClinit[src]; C.cClinit[src] = sv;
				bv = C.cIfaceS[dst]; C.cIfaceS[dst] = C.cIfaceS[src]; C.cIfaceS[src] = bv;
				bv = C.cIfaceC[dst]; C.cIfaceC[dst] = C.cIfaceC[src]; C.cIfaceC[src] = bv;
				zv = C.cIsIface[dst]; C.cIsIface[dst] = C.cIsIface[src]; C.cIsIface[src] = zv;
				zv = C.cIsEnum[dst]; C.cIsEnum[dst] = C.cIsEnum[src]; C.cIsEnum[src] = zv;
				zv = C.cAbstract[dst]; C.cAbstract[dst] = C.cAbstract[src]; C.cAbstract[src] = zv;
				iv = C.cBodyS[dst]; C.cBodyS[dst] = C.cBodyS[src]; C.cBodyS[src] = iv;
				iv = C.cBodyE[dst]; C.cBodyE[dst] = C.cBodyE[src]; C.cBodyE[src] = iv;
				bv = C.vtBase[dst]; C.vtBase[dst] = C.vtBase[src]; C.vtBase[src] = bv;
				byte old = classAt[dst];
				classAt[dst] = classAt[src];
				classAt[src] = old;
			}
		}
		for (int ci = C.uClsStart; ci < C.cCount; ci++) {
			if (C.cParent[ci] >= 0) C.cParent[ci] = (short)(classRemap[C.cParent[ci]] & 0xFF);
		}
		for (int i = 0; i < C.ifListLen; i++) C.ifList[i] = (short)(classRemap[C.ifList[i]] & 0xFF);
		for (int fi = 0; fi < C.fCount; fi++) C.fClass[fi] = classRemap[C.fClass[fi] & 0xFF];
		for (int mi = 0; mi < C.mCount; mi++) C.mClass[mi] = classRemap[C.mClass[mi] & 0xFF];

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

		// Default constructors are materialized lazily by fCtor() so that
		// never-instantiated classes do not consume method-table slots.

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
					if (sameSig(existingMi, mi)) {
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

		// A concrete class must close every abstract vtable slot and every
		// interface signature that its declaration hierarchy promises.
		for (int ci = 0; ci < C.cCount; ci++) {
			if (C.cIsIface[ci] || C.cAbstract[ci]) continue;
			int base = C.vtBase[ci] & 0xFF;
			for (int slot = 0; slot < (C.cVtSize[ci] & 0xFF); slot++) {
				if (C.mAbstract[C.vtable[base + slot] & 0xFFFF]) Lexer.error(274);
			}
			for (int imi = 0; imi < C.mCount; imi++) {
				int ii = C.mClass[imi] & 0xFF;
				if (!C.cIsIface[ii] || C.mStatic[imi] || C.mIsCtor[imi] ||
					!Linker.hasInterface(ci, ii)) continue;
				boolean found = false;
				for (int slot = 0; slot < (C.cVtSize[ci] & 0xFF); slot++) {
					int cmi = C.vtable[base + slot] & 0xFFFF;
					if (!C.mAbstract[cmi] && sameSig(cmi, imi)) { found = true; break; }
				}
				if (!found) Lexer.error(274);
			}
		}

		// A signature has one global vmid, even when inherited or declared by
		// multiple interfaces. One implementation can therefore satisfy all of it.
		int nextVmid = 0;
		for (int ci = 0; ci < C.cCount; ci++) {
			if (!C.cIsIface[ci]) continue;
			for (int mi = 0; mi < C.mCount; mi++) {
				if (C.mClass[mi] != ci) continue;
				int vmid = -1;
				for (int prior = 0; prior < mi; prior++) {
					int pci = C.mClass[prior] & 0xFF;
					if (C.cIsIface[pci] && sameSig(prior, mi)) {
						vmid = C.mVmid[prior] & 0xFF;
						break;
					}
				}
				if (vmid < 0) {
					C.chk(nextVmid, 255, 275);
					vmid = nextVmid++;
				}
				C.mVmid[mi] = (byte)vmid;
			}
		}
		// Any matching class method may appear in a subclass's vtable as an
		// inherited implementation, so annotate by signature rather than owner.
		for (int cmi = 0; cmi < C.mCount; cmi++) {
			int ci = C.mClass[cmi] & 0xFF;
			if (C.cIsIface[ci] || C.mStatic[cmi] || C.mIsCtor[cmi]) continue;
			for (int imi = 0; imi < C.mCount; imi++) {
				int ii = C.mClass[imi] & 0xFF;
				if (C.cIsIface[ii] && sameSig(cmi, imi)) {
					C.mVmid[cmi] = C.mVmid[imi];
					break;
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

		// Entrypoint selection uses the same declaration-shape matching as other exact lookups.
		C.mainMi = fMain(true);
		if (C.mainMi < 0) C.mainMi = fMain(false);
		if (C.mainMi < 0) {
			Lexer.error(200); // No main method found
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
		int fi = fField(ci, nm);
		return fi >= 0 && C.fStatic[fi] ? fi : -1;
	}

	static int fInstField(int ci, int nm) {
		int fi = fField(ci, nm);
		return fi >= 0 && !C.fStatic[fi] ? fi : -1;
	}

	// Declaration-shape matching: used for exact body lookup, ctors, and entrypoints.
	static boolean declShapeFits(int mi, int ci, int nm, boolean isStatic, int argc,
								 boolean allowNative, boolean allowCtor) {
		return (ci < 0 || C.mClass[mi] == ci) &&
			   C.mName[mi] == nm &&
			   C.mStatic[mi] == isStatic &&
			   C.mArgC[mi] == argc &&
			   (allowNative || !C.mNative[mi]) &&
			   (allowCtor || !C.mIsCtor[mi]);
	}

	static boolean sigFits(int mi, short[] sig, int sigC, boolean exact) {
		if (sigC < 0 || C.mSigC[mi] == 0 || (!exact && C.mVarargs[mi])) return true;
		if ((C.mSigC[mi] & 0xFF) != sigC) return false;
		int s = C.mSigS[mi] & 0xFFFF;
		for (int i = 0; i < sigC; i++) {
			short expect = C.sigParam[s + i];
			short actual = sig[i];
			if (expect == actual) continue;
			if (exact) return false;
			if (!sigAssignable(actual, expect)) return false;
		}
		return true;
	}

	static boolean sigAssignable(short actual, short expect) {
		if (actual == expect) return true;
		boolean actualClass = actual >= 0 && actual < C.SIG_OBJ_ARRAY_BASE;
		boolean expectClass = expect >= 0 && expect < C.SIG_OBJ_ARRAY_BASE;
		boolean actualObjArray = actual >= C.SIG_OBJ_ARRAY_BASE;
		boolean expectObjArray = expect >= C.SIG_OBJ_ARRAY_BASE;
		boolean actualArray = actualObjArray || actual == C.SIG_INT_ARR || actual == C.SIG_BYTE_ARR ||
			actual == C.SIG_CHAR_ARR || actual == C.SIG_SHORT_ARR || actual == C.SIG_BOOL_ARR;
		boolean expectArray = expectObjArray || expect == C.SIG_INT_ARR || expect == C.SIG_BYTE_ARR ||
			expect == C.SIG_CHAR_ARR || expect == C.SIG_SHORT_ARR || expect == C.SIG_BOOL_ARR;
		if (actual == C.SIG_NULL && (expectClass || expectArray)) return true;
		if (expect == C.N_OBJECT && (actualClass || actualArray)) return true;
		if ((actualClass && expectClass) || (actualObjArray && expectObjArray)) {
			int actualNm = actualObjArray ? actual - C.SIG_OBJ_ARRAY_BASE : actual;
			int expectNm = expectObjArray ? expect - C.SIG_OBJ_ARRAY_BASE : expect;
			int srcCi = fClsByNm(actualNm);
			int dstCi = fClsByNm(expectNm);
			if (srcCi >= 0 && dstCi >= 0) {
				for (int ci = srcCi; ci >= 0; ci = C.cParent[ci]) {
					if (ci == dstCi) return true;
				}
				if (C.cIsIface[dstCi] && Linker.hasInterface(srcCi, dstCi)) return true;
			}
		}
		boolean actualScalar = actual == C.SIG_INT || actual == C.SIG_BYTE || actual == C.SIG_CHAR ||
			actual == C.SIG_SHORT || actual == C.SIG_BOOL;
		return expect == C.SIG_INT && actualScalar && actual != C.SIG_BOOL;
	}

	// Shared call matching: keep staticness, arity, and varargs rules in one place.
	static boolean sameSig(int mi, int mj) {
		if (C.mName[mi] != C.mName[mj] || C.mArgC[mi] != C.mArgC[mj]) return false;
		int ci = C.mSigC[mi] & 0xFF;
		int cj = C.mSigC[mj] & 0xFF;
		if (ci != cj) return false;
		int si = C.mSigS[mi] & 0xFFFF;
		int sj = C.mSigS[mj] & 0xFFFF;
		for (int i = 0; i < ci; i++) {
			if (C.sigParam[si + i] != C.sigParam[sj + i]) return false;
		}
		return true;
	}

	static boolean argcFits(int mi, int argc) {
		if (argc < 0) return true;
		if (!C.mVarargs[mi]) return C.mArgC[mi] == argc;
		int minArgc = (C.mStatic[mi] ? 0 : 1) + C.mFixedArgs[mi];
		return argc >= minArgc && argc <= minArgc + C.MAX_VA_SLOTS;
	}

	static boolean callShapeFits(int mi, int nm, boolean isStatic, int argc) {
		return C.mName[mi] == nm &&
			   C.mStatic[mi] == isStatic &&
			   !C.mIsCtor[mi] &&
			   argcFits(mi, argc);
	}

	static int fMethodExact(int ci, int nm, boolean isStatic, int argc) {
		while (ci >= 0) {
			int varargsMi = -1;
			for (int mi = 0; mi < C.mCount; mi++) {
				if (C.mClass[mi] != ci || !callShapeFits(mi, nm, isStatic, argc)) {
					continue;
				}
				if (!C.mVarargs[mi]) return mi;
				if (varargsMi < 0) varargsMi = mi;
			}
			if (varargsMi >= 0) return varargsMi;
			ci = C.cParent[ci];
		}
		return -1;
	}

	// Exact declaration lookup for emit-time body matching; calls use fMethodExact/fCallTarget.
	static int fDeclaredMethod(int ci, int nm, boolean isStatic, int argc) {
		for (int mi = 0; mi < C.mCount; mi++) {
			if (declShapeFits(mi, ci, nm, isStatic, argc, false, false) &&
				sigFits(mi, Catalog.sigTmp, Catalog.sigTmpC, true)) {
				return mi;
			}
		}
		return -1;
	}

	static boolean sigFromCatalog;

	static int fCtor(int ci, int argc) {
		short[] sig = sigFromCatalog ? Catalog.sigTmp : Expr.lastArgSig;
		int sigC = sigFromCatalog ? Catalog.sigTmpC : Expr.lastArgSigC;
		int best = -1;
		for (int mi = 0; mi < C.mCount; mi++) {
			if (declShapeFits(mi, ci, C.N_INIT, false, argc, false, true) &&
				sigFits(mi, sig, sigC, true)) {
				return mi;
			}
		}
		if (!sigFromCatalog) {
			for (int mi = 0; mi < C.mCount; mi++) {
				if (!declShapeFits(mi, ci, C.N_INIT, false, argc, false, true) ||
					!sigFits(mi, sig, sigC, false)) continue;
				if (best < 0) { best = mi; continue; }
				int count = C.mSigC[mi] & 0xFF;
				int cs = C.mSigS[mi] & 0xFFFF;
				int bs = C.mSigS[best] & 0xFFFF;
				boolean candidateBetter = count == (C.mSigC[best] & 0xFF);
				boolean bestBetter = candidateBetter;
				for (int ai = 0; ai < count; ai++) {
					short candidateArg = C.sigParam[cs + ai];
					short bestArg = C.sigParam[bs + ai];
					if (candidateArg == bestArg) continue;
					if (!sigAssignable(candidateArg, bestArg)) candidateBetter = false;
					if (!sigAssignable(bestArg, candidateArg)) bestBetter = false;
				}
				if (candidateBetter && !bestBetter) best = mi;
			}
			if (best >= 0) {
				for (int mi = 0; mi < C.mCount; mi++) {
					if (mi == best ||
						!declShapeFits(mi, ci, C.N_INIT, false, argc, false, true) ||
						!sigFits(mi, sig, sigC, false)) continue;
					int count = C.mSigC[best] & 0xFF;
					int bs = C.mSigS[best] & 0xFFFF;
					int cs = C.mSigS[mi] & 0xFFFF;
					boolean bestBetter = count == (C.mSigC[mi] & 0xFF);
					for (int ai = 0; ai < count; ai++) {
						if (!sigAssignable(C.sigParam[bs + ai], C.sigParam[cs + ai]))
							bestBetter = false;
					}
					if (!bestBetter) return -1;
				}
				return best;
			}
		}
		// Lazily materialize the default constructor: only classes that are
		// actually instantiated (or super()-chained) pay a method-table slot.
		if (argc == 1 && ci >= C.uClsStart && !C.cIsIface[ci]) {
			boolean declared = false;
			for (int mi = 0; mi < C.mCount; mi++) {
				if (C.mClass[mi] == ci && C.mIsCtor[mi]) { declared = true; break; }
			}
			if (!declared) {
				int mi = C.initMethod(ci, C.N_INIT, 1, false, true, false, 0);
				C.mBodyS[mi] = -2; C.mBodyE[mi] = -2;
				return mi;
			}
		}
		return -1;
	}

	static int fMain(boolean wantStringArgs) {
		int argc = wantStringArgs ? 1 : 0;
		for (int mi = 0; mi < C.mCount; mi++) {
			if (declShapeFits(mi, -1, C.N_MAIN, true, argc, false, false) &&
				C.mMainStrArgs[mi] == wantStringArgs) {
				return mi;
			}
		}
		return -1;
	}

	// Resolve a direct call against natives first, then user classes.
	static int fCallTarget(int ownerNm, int methodNm, boolean isStatic, int argc) {
		int mi = C.ensNat(ownerNm, methodNm);
		if (mi >= 0 && callShapeFits(mi, methodNm, isStatic, argc)) return mi;
		int ci = fClsByNm(ownerNm);
		if (ci < 0) return -1;
		for (int pass = 0; pass < 2; pass++) {
			int walkCi = ci;
			boolean exact = pass == 0;
			int best = -1;
			int varargsMi = -1;
			while (walkCi >= 0) {
				for (mi = 0; mi < C.mCount; mi++) {
					if (C.mClass[mi] != walkCi || !callShapeFits(mi, methodNm, isStatic, argc) ||
						!sigFits(mi, Expr.lastArgSig, Expr.lastArgSigC, exact)) {
						continue;
					}
					if (exact && !C.mVarargs[mi]) return mi;
					if (C.mVarargs[mi]) {
						if (varargsMi < 0) varargsMi = mi;
						continue;
					}
					if (best < 0) {
						best = mi;
						continue;
					}
					if (sameSig(best, mi)) continue; // overridden declaration
					int count = C.mSigC[mi] & 0xFF;
					int cs = C.mSigS[mi] & 0xFFFF;
					int bs = C.mSigS[best] & 0xFFFF;
					boolean candidateBetter = count == (C.mSigC[best] & 0xFF);
					boolean bestBetter = candidateBetter;
					for (int ai = 0; ai < count; ai++) {
						short candidateArg = C.sigParam[cs + ai];
						short bestArg = C.sigParam[bs + ai];
						if (candidateArg == bestArg) continue;
						if (!sigAssignable(candidateArg, bestArg)) candidateBetter = false;
						if (!sigAssignable(bestArg, candidateArg)) bestBetter = false;
					}
					if (candidateBetter && !bestBetter) {
						best = mi;
					}
				}
				walkCi = C.cParent[walkCi];
			}
			if (best >= 0) {
				walkCi = ci;
				while (walkCi >= 0) {
					for (mi = 0; mi < C.mCount; mi++) {
						if (mi == best || C.mClass[mi] != walkCi || C.mVarargs[mi] ||
							!callShapeFits(mi, methodNm, isStatic, argc) ||
							!sigFits(mi, Expr.lastArgSig, Expr.lastArgSigC, exact) ||
							sameSig(best, mi)) continue;
						int count = C.mSigC[best] & 0xFF;
						int bs = C.mSigS[best] & 0xFFFF;
						int cs = C.mSigS[mi] & 0xFFFF;
						boolean bestBetter = count == (C.mSigC[mi] & 0xFF);
						for (int ai = 0; ai < count; ai++) {
							if (!sigAssignable(C.sigParam[bs + ai], C.sigParam[cs + ai]))
								bestBetter = false;
						}
						if (!bestBetter) return -1;
					}
					walkCi = C.cParent[walkCi];
				}
				return best;
			}
			if (varargsMi >= 0) return varargsMi;
		}
		return -1;
	}
}
