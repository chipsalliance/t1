# https://github.com/NixOS/nixpkgs/blob/nixpkgs-unstable/pkgs/applications/virtualization/sail-riscv/default.nix

{ lib
, stdenv
, fetchFromGitHub
, ocamlPackages
, ocaml
, zlib
, z3
, bear

, targetArch ? "rv32"
}:
assert lib.assertMsg (lib.elem targetArch [ "rv32" "rv64" ]) "targetArch `${targetArch}' invalid";

let
  ocamlPackages' = ocamlPackages.overrideScope (finalScope: prevScope: {
    sail = prevScope.sail.overrideAttrs rec {
      version = "0.18";
      src = fetchFromGitHub {
        owner = "rems-project";
        repo = "sail";
        rev = version;
        hash = "sha256-QvVK7KeAvJ/RfJXXYo6xEGEk5iOmVsZbvzW28MHRFic=";
      };

      buildInputs = [ finalScope.menhirLib ];
    };
  });
in
stdenv.mkDerivation (finalAttr: {
  pname = "sail-riscv-c-model";
  version = "unstable-b90ec78";

  src = fetchFromGitHub {
    owner = "riscv";
    repo = "sail-riscv";
    rev = "b90ec7881eb0a0ecea6521e3d6cc03bf0b057e41";
    hash = "sha256-vWOKYCkFTYP3t3ZFpmiXOjTpqoX06Ubs2GTncCdj/Cg=";
  };

  nativeBuildInputs = with ocamlPackages'; [ ocamlbuild findlib ocaml z3 sail ];
  buildInputs = with ocamlPackages'; [ zlib linksem ];
  strictDeps = true;

  postPatch = ''
    rm -r prover_snapshots
    rm -r handwritten_support
  '';

  sailArgs = [
    "--strict-var"
    "-dno_cast"
    "-c_preserve"
    "_set_Misa_C"
    "-O"
    "-Oconstant_fold"
    "-memo_z3"
    "-c"
    "-c_no_main"
  ];

  sailSrcs = [
    "model/prelude.sail"
  ] ++ (if targetArch == "rv32" then [
    "model/riscv_xlen32.sail"
  ] else [
    "model/riscv_xlen64.sail"
  ])
  ++ [
    "model/riscv_xlen.sail"
    "model/riscv_flen_D.sail"
    "model/riscv_vlen.sail"
    "model/prelude_mem_metadata.sail"
    "model/prelude_mem.sail"
    "model/riscv_types_common.sail"
    "model/riscv_types_ext.sail"
    "model/riscv_types.sail"
    "model/riscv_vmem_types.sail"
    "model/riscv_reg_type.sail"
    "model/riscv_freg_type.sail"
    "model/riscv_regs.sail"
    "model/riscv_pc_access.sail"
    "model/riscv_sys_regs.sail"
    "model/riscv_pmp_regs.sail"
    "model/riscv_pmp_control.sail"
    "model/riscv_ext_regs.sail"
    "model/riscv_addr_checks_common.sail"
    "model/riscv_addr_checks.sail"
    "model/riscv_misa_ext.sail"
    "model/riscv_vreg_type.sail"
    "model/riscv_vext_regs.sail"
    "model/riscv_csr_begin.sail"
    "model/riscv_vext_control.sail"
    "model/riscv_next_regs.sail"
    "model/riscv_sys_exceptions.sail"
    "model/riscv_sync_exception.sail"
    "model/riscv_next_control.sail"
    "model/riscv_softfloat_interface.sail"
    "model/riscv_fdext_regs.sail"
    "model/riscv_fdext_control.sail"
    "model/riscv_sys_control.sail"
    "model/riscv_platform.sail"
    "model/riscv_mem.sail"
    "model/riscv_vmem_common.sail"
    "model/riscv_vmem_pte.sail"
    "model/riscv_vmem_ptw.sail"
    "model/riscv_vmem_tlb.sail"
    "model/riscv_vmem.sail"
    "model/riscv_types_kext.sail"
    "model/riscv_insts_begin.sail"
    "model/riscv_insts_base.sail"
    "model/riscv_insts_aext.sail"
    "model/riscv_insts_zca.sail"
    "model/riscv_insts_mext.sail"
    "model/riscv_insts_zicsr.sail"
    "model/riscv_insts_next.sail"
    "model/riscv_insts_hints.sail"
    "model/riscv_insts_fext.sail"
    "model/riscv_insts_zcf.sail"
    "model/riscv_insts_dext.sail"
    "model/riscv_insts_zcd.sail"
    "model/riscv_insts_svinval.sail"
    "model/riscv_insts_zba.sail"
    "model/riscv_insts_zbb.sail"
    "model/riscv_insts_zbc.sail"
    "model/riscv_insts_zbs.sail"
    "model/riscv_insts_zcb.sail"
    "model/riscv_insts_zfh.sail"
    "model/riscv_insts_zfa.sail"
    "model/riscv_insts_zkn.sail"
    "model/riscv_insts_zks.sail"
    "model/riscv_insts_zbkb.sail"
    "model/riscv_insts_zbkx.sail"
    "model/riscv_insts_zicond.sail"
    "model/riscv_insts_vext_utils.sail"
    "model/riscv_insts_vext_fp_utils.sail"
    "model/riscv_insts_vext_vset.sail"
    "model/riscv_insts_vext_arith.sail"
    "model/riscv_insts_vext_fp.sail"
    "model/riscv_insts_vext_mem.sail"
    "model/riscv_insts_vext_mask.sail"
    "model/riscv_insts_vext_vm.sail"
    "model/riscv_insts_vext_fp_vm.sail"
    "model/riscv_insts_vext_red.sail"
    "model/riscv_insts_vext_fp_red.sail"
    "model/riscv_insts_zicbom.sail"
    "model/riscv_insts_zicboz.sail"
    "model/riscv_jalr_seq.sail"
    "model/riscv_insts_end.sail"
    "model/riscv_csr_end.sail"
    "model/riscv_step_common.sail"
    "model/riscv_step_ext.sail"
    "model/riscv_decode_ext.sail"
    "model/riscv_fetch.sail"
    "model/riscv_step.sail"
    "model/main.sail"
  ];

  buildPhase = ''
    runHook preBuild

    mkdir -p build
    sailPhase="sail $sailArgs $sailSrcs -o build/riscv_model_${targetArch}"
    echo "$sailPhase"
    eval "$sailPhase"

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/share/
    cp -v build/riscv_model_${targetArch}.c $out/share/

    runHook postInstall
  '';

  passthru = {
    inherit (ocamlPackages') sail;
    inherit targetArch;

    dev = finalAttr.finalPackage.overrideAttrs (old: {
      nativeBuildInputs = old.nativeBuildInputs ++ [ bear ];
    });
  };

  meta = {
    description = "Generated C source of the sail RISC-V (${targetArch}) model";
  };
})
