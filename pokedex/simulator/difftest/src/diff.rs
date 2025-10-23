use crate::replay::*;

#[derive(Debug, thiserror::Error)]
pub enum DiffError {
    #[error("One of the CPU has extra {ty} write at PC={pc}")]
    StateWrite { ty: &'static str, pc: u32 },
    #[error(
        "Register {ty} has mismatch write at index={rd}, PC={pc}, with left value={left}, right value={right}"
    )]
    Register {
        ty: &'static str,
        rd: usize,
        pc: u32,
        left: u32,
        right: u32,
    },
    #[error(
        "CSR has mismatch write at [{id}]({name}), PC={pc}, with left value={left}, right value={right}"
    )]
    Csr {
        id: u16,
        pc: u32,
        name: String,
        left: u32,
        right: u32,
    },
    #[error(
        "CSR has mismatch check type at PC={pc}, with left value={left:?}, right value={right:?}"
    )]
    CsrMask {
        pc: u32,
        left: CsrCheckType,
        right: CsrCheckType,
    },
}

/// Use the first state as golden model to check the second state.
///
/// Panic if:
///     * PC is different between CpuStates
pub fn compare(
    st1: &CpuState,
    ct1: &StateCheckType,
    st2: &CpuState,
    ct2: &StateCheckType,
) -> Result<(), Vec<DiffError>> {
    assert_eq!(st1.pc, st2.pc);

    let mut diag_log: Vec<Result<(), DiffError>> = Vec::new();

    if ct1.gpr_rd.is_some() || ct2.gpr_rd.is_some() {
        if !(ct1.gpr_rd.is_some() && ct2.gpr_rd.is_some()) {
            diag_log.push(Err(DiffError::StateWrite {
                ty: "gpr",
                pc: st1.pc,
            }));
        } else {
            diag_log.push(compare_gpr(st1, ct1, st2, ct2));
        }
    }

    if ct1.fpr_rd.is_some() || ct2.fpr_rd.is_some() {
        if !(ct1.fpr_rd.is_some() && ct2.fpr_rd.is_some()) {
            diag_log.push(Err(DiffError::StateWrite {
                ty: "fpr",
                pc: st1.pc,
            }));
        } else {
            diag_log.push(compare_fpr(st1, ct1, st2, ct2));
        }
    }

    if ct1.csr_mask.has_write() || ct2.csr_mask.has_write() {
        if ct1.csr_mask != ct2.csr_mask {
            diag_log.push(Err(DiffError::CsrMask {
                pc: st1.pc,
                left: ct1.csr_mask,
                right: ct2.csr_mask,
            }));
        } else {
            diag_log.push(compare_csr(st1, ct1, st2, ct2))
        }
    }

    if diag_log.iter().all(|diag| diag.is_ok()) {
        return Ok(());
    }

    Err(diag_log.into_iter().filter_map(|diag| diag.err()).collect())
}

/// Compare specific GPR value between two CPU state, return Ok if value is the same.
///
/// Panic if:
///     * State has different PC
///     * Any of the register index in CheckType is None
///     * Register index is not valid
pub fn compare_gpr(
    st1: &CpuState,
    ct1: &StateCheckType,
    st2: &CpuState,
    ct2: &StateCheckType,
) -> Result<(), DiffError> {
    assert_eq!(st1.pc, st2.pc);
    assert!(ct1.gpr_rd.is_some() && ct2.gpr_rd.is_some());

    let rd1 = ct1.gpr_rd.unwrap();
    let rd2 = ct2.gpr_rd.unwrap();
    assert!(rd1 > 0 && rd1 < 32 && rd2 > 0 && rd2 < 32);

    if rd1 != rd2 {
        return Err(DiffError::StateWrite {
            ty: "gpr",
            pc: st1.pc,
        });
    }

    if st1.gpr[rd1] != st2.gpr[rd2] {
        return Err(DiffError::Register {
            ty: "gpr",
            rd: rd1,
            pc: st1.pc,
            left: st1.gpr[rd1],
            right: st2.gpr[rd2],
        });
    }

    Ok(())
}

/// Compare specific FPR value between two CPU state, return Ok if value is the same.
///
/// Panic if:
///     * State has different PC
///     * Any of the register index in CheckType is None
///     * Register index is not valid
pub fn compare_fpr(
    st1: &CpuState,
    ct1: &StateCheckType,
    st2: &CpuState,
    ct2: &StateCheckType,
) -> Result<(), DiffError> {
    assert_eq!(st1.pc, st2.pc);
    assert!(ct1.fpr_rd.is_some() && ct2.fpr_rd.is_some());

    let rd1 = ct1.fpr_rd.unwrap();
    let rd2 = ct2.fpr_rd.unwrap();
    assert!(rd1 < 32 && rd2 < 32);

    if rd1 != rd2 {
        return Err(DiffError::StateWrite {
            ty: "fpr",
            pc: st1.pc,
        });
    }

    if st1.fpr[rd1] != st2.fpr[rd2] {
        return Err(DiffError::Register {
            ty: "fpr",
            rd: rd1,
            pc: st1.pc,
            left: st1.fpr[rd1],
            right: st2.fpr[rd2],
        });
    }

    Ok(())
}

/// Use first state as golden model to check second states CSR
pub fn compare_csr(
    st1: &CpuState,
    ct1: &StateCheckType,
    st2: &CpuState,
    ct2: &StateCheckType,
) -> Result<(), DiffError> {
    assert_eq!(st1.pc, st2.pc);
    assert_eq!(ct1.csr_mask, ct2.csr_mask);

    match ct1.csr_mask {
        CsrCheckType::AllCsr => {
            for (id1, (name, v1)) in st1.csr.iter() {
                if let Some((_, v2)) = st2.csr.get(id1) {
                    if *v1 != *v2 {
                        return Err(DiffError::Csr {
                            id: *id1,
                            pc: st1.pc,
                            name: name.to_string(),
                            left: *v1,
                            right: *v2,
                        });
                    }
                } else {
                    return Err(DiffError::Csr {
                        id: *id1,
                        pc: st1.pc,
                        name: name.to_string(),
                        left: *v1,
                        right: 0,
                    });
                }
            }
        }
        CsrCheckType::FpCsrOnly => {
            const FP_CSRS: [(&str, u16); 3] = [("fflags", 0x001), ("frm", 0x002), ("fcsr", 0x003)];

            for (name, id) in FP_CSRS {
                let csr1 = st1.csr.get(&id);
                let csr2 = st2.csr.get(&id);

                if csr1.is_none() {
                    continue;
                }

                let (_, v1) = csr1.unwrap();
                let (_, v2) = csr2.unwrap();
                if v1 != v2 {
                    return Err(DiffError::Csr {
                        id,
                        pc: st1.pc,
                        name: name.to_string(),
                        left: *v1,
                        right: *v2,
                    });
                }
            }
        }
        CsrCheckType::VecCsrOnly => unimplemented!(),
        CsrCheckType::NoWrite => panic!("internal error: invalid csr mask argument"),
    }
    Ok(())
}
