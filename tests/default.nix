{ lib, runCommand, callPackage, testcase-env }:

let
  /* Return true if the given path contains a file called "default.nix";

     Example:
        isCallableDir ./testDir => true

     Type:
       isCallableDir :: Path -> bool
  */
  isCallableDir = path:
    with builtins;
    let
      files = lib.filesystem.listFilesRecursive path;
    in
    any (f: baseNameOf (toString f) == "default.nix") files;

  /* Search for callable directory (there is a file default.nix in the directory),
     and use callPackage to call it. Return an attr set with key as directory basename, value as derivation.

     Example:
        $ ls testDir
        testDir
          * A
            - default.nix
          * B
            - default.nix
          * C
            - otherStuff

        nix> searchAndCallPackage ./testDir => { A = <derivation>; B = <derivation>; }

     Type:
       searchAndCallPackage :: Path -> AttrSet
  */
  searchAndCallPackage = dir:
    with builtins;
    lib.pipe (readDir dir) [
      # First filter out all non-directory object
      (lib.filterAttrs (_: type: type == "directory"))
      # { "A": "directory"; "B": "directory" } => { "A": "/nix/store/.../"; B: "/nix/store/.../"; }
      (lib.mapAttrs (subDirName: _: (lib.path.append dir subDirName)))
      # Then filter out those directory that have no file named default.nix
      (lib.filterAttrs (_: fullPath: isCallableDir fullPath))
      # { "A": "/nix/store/.../"; B: "/nix/store/.../"; } => { "A": <derivation>; "B": <derivation>; }
      (lib.mapAttrs (_: fullPath: callPackage fullPath { }))
    ];

  self = {
    # nix build .#t1.rvv-testcases.<type>.<name>
    mlir = searchAndCallPackage ./mlir;
    intrinsic = searchAndCallPackage ./intrinsic;
    asm = searchAndCallPackage ./asm;

    # nix build .#t1.rvv-testcases.codegen.vaadd-vv -L
    # codegen case are using xLen=32,vLen=1024 by default
    codegen =
      with lib;
      let
        # batchMkCases convert a list of name to a set of codegen case derivation.
        # Eg. [ { caseName = "vadd.vx"; <mkCodegenCase args...> } ] => { "vadd-vx": <vadd.vx codegen drv>, ... }
        batchMkCases = cases: pipe cases [
          (map (caseSpec: nameValuePair
            (replaceStrings [ "." ] [ "-" ] caseSpec.caseName)
            (testcase-env.mkCodegenCase caseSpec)
          ))
          listToAttrs
        ];
      in
      batchMkCases [
        { caseName = "vaadd.vv"; }
        { caseName = "vaadd.vx"; }
        { caseName = "vaaddu.vv"; }
        { caseName = "vaaddu.vx"; }
        { caseName = "vadc.vim"; }
        { caseName = "vadc.vvm"; }
        { caseName = "vadc.vxm"; }
        { caseName = "vadd.vi"; }
        { caseName = "vadd.vv"; }
        { caseName = "vadd.vx"; }
        { caseName = "vand.vi"; }
        { caseName = "vand.vv"; }
        { caseName = "vand.vx"; }
        { caseName = "vasub.vv"; }
        { caseName = "vasub.vx"; }
        { caseName = "vasubu.vv"; }
        { caseName = "vasubu.vx"; }
        { caseName = "vcompress.vm"; }
        { caseName = "vcpop.m"; }
        { caseName = "vdiv.vv"; }
        { caseName = "vdiv.vx"; }
        { caseName = "vdivu.vv"; }
        { caseName = "vdivu.vx"; }
        { caseName = "vfadd.vf"; fp = true; }
        { caseName = "vfadd.vv"; fp = true; }
        { caseName = "vfclass.v"; fp = true; }
        { caseName = "vfcvt.f.x.v"; fp = true; }
        { caseName = "vfcvt.f.xu.v"; fp = true; }
        { caseName = "vfcvt.rtz.x.f.v"; fp = true; }
        { caseName = "vfcvt.rtz.xu.f.v"; fp = true; }
        { caseName = "vfcvt.x.f.v"; fp = true; }
        { caseName = "vfcvt.xu.f.v"; fp = true; }
        { caseName = "vfdiv.vf"; fp = true; }
        { caseName = "vfdiv.vv"; fp = true; }
        { caseName = "vfmacc.vf"; fp = true; }
        { caseName = "vfmacc.vv"; fp = true; }
        { caseName = "vfmadd.vf"; fp = true; }
        { caseName = "vfmadd.vv"; fp = true; }
        { caseName = "vfmax.vf"; fp = true; }
        { caseName = "vfmax.vv"; fp = true; }
        { caseName = "vfmerge.vfm"; fp = true; }
        { caseName = "vfmin.vf"; fp = true; }
        { caseName = "vfmin.vv"; fp = true; }
        { caseName = "vfmsac.vf"; fp = true; }
        { caseName = "vfmsac.vv"; fp = true; }
        { caseName = "vfmsub.vf"; fp = true; }
        { caseName = "vfmsub.vv"; fp = true; }
        { caseName = "vfmul.vf"; fp = true; }
        { caseName = "vfmul.vv"; fp = true; }
        { caseName = "vfmv.f.s"; fp = true; }
        { caseName = "vfmv.s.f"; fp = true; }
        { caseName = "vfmv.v.f"; fp = true; }
        { caseName = "vfnmacc.vf"; fp = true; }
        { caseName = "vfnmacc.vv"; fp = true; }
        { caseName = "vfnmadd.vf"; fp = true; }
        { caseName = "vfnmadd.vv"; fp = true; }
        { caseName = "vfnmsac.vf"; fp = true; }
        { caseName = "vfnmsac.vv"; fp = true; }
        { caseName = "vfnmsub.vf"; fp = true; }
        { caseName = "vfnmsub.vv"; fp = true; }
        { caseName = "vfrdiv.vf"; fp = true; }
        { caseName = "vfrec7.v"; fp = true; }
        { caseName = "vfrsqrt7.v"; fp = true; }
        { caseName = "vfrsub.vf"; fp = true; }
        { caseName = "vfsgnj.vf"; fp = true; }
        { caseName = "vfsgnj.vv"; fp = true; }
        { caseName = "vfsgnjn.vf"; fp = true; }
        { caseName = "vfsgnjn.vv"; fp = true; }
        { caseName = "vfsgnjx.vf"; fp = true; }
        { caseName = "vfsgnjx.vv"; fp = true; }
        { caseName = "vfsqrt.v"; fp = true; }
        { caseName = "vfsub.vf"; fp = true; }
        { caseName = "vfsub.vv"; fp = true; }
        { caseName = "vid.v"; }
        { caseName = "viota.m"; }
        { caseName = "vl1re8.v"; }
        { caseName = "vl1re16.v"; }
        { caseName = "vl1re32.v"; }
        { caseName = "vl2re8.v"; }
        { caseName = "vl2re16.v"; }
        { caseName = "vl2re32.v"; }
        { caseName = "vl4re8.v"; }
        { caseName = "vl4re16.v"; }
        { caseName = "vl4re32.v"; }
        { caseName = "vl8re8.v"; }
        { caseName = "vl8re16.v"; }
        { caseName = "vl8re32.v"; }
        { caseName = "vle8.v"; }
        { caseName = "vle8ff.v"; }
        { caseName = "vle16.v"; }
        { caseName = "vle16ff.v"; }
        { caseName = "vle32.v"; }
        { caseName = "vle32ff.v"; }
        { caseName = "vlm.v"; }
        { caseName = "vloxei8.v"; }
        { caseName = "vloxei16.v"; }
        { caseName = "vloxei32.v"; }
        { caseName = "vloxseg2ei8.v"; }
        { caseName = "vloxseg2ei16.v"; }
        { caseName = "vloxseg2ei32.v"; }
        { caseName = "vloxseg3ei8.v"; }
        { caseName = "vloxseg3ei16.v"; }
        { caseName = "vloxseg3ei32.v"; }
        { caseName = "vloxseg4ei8.v"; }
        { caseName = "vloxseg4ei16.v"; }
        { caseName = "vloxseg4ei32.v"; }
        { caseName = "vloxseg5ei8.v"; }
        { caseName = "vloxseg5ei16.v"; }
        { caseName = "vloxseg5ei32.v"; }
        { caseName = "vloxseg6ei8.v"; }
        { caseName = "vloxseg6ei16.v"; }
        { caseName = "vloxseg6ei32.v"; }
        { caseName = "vloxseg7ei8.v"; }
        { caseName = "vloxseg7ei16.v"; }
        { caseName = "vloxseg7ei32.v"; }
        { caseName = "vloxseg8ei8.v"; }
        { caseName = "vloxseg8ei16.v"; }
        { caseName = "vloxseg8ei32.v"; }
        { caseName = "vlse8.v"; }
        { caseName = "vlse16.v"; }
        { caseName = "vlse32.v"; }
        { caseName = "vlseg2e8.v"; }
        { caseName = "vlseg2e16.v"; }
        { caseName = "vlseg2e32.v"; }
        { caseName = "vlseg3e8.v"; }
        { caseName = "vlseg3e16.v"; }
        { caseName = "vlseg3e32.v"; }
        { caseName = "vlseg4e8.v"; }
        { caseName = "vlseg4e16.v"; }
        { caseName = "vlseg4e32.v"; }
        { caseName = "vlseg5e8.v"; }
        { caseName = "vlseg5e16.v"; }
        { caseName = "vlseg5e32.v"; }
        { caseName = "vlseg6e8.v"; }
        { caseName = "vlseg6e16.v"; }
        { caseName = "vlseg6e32.v"; }
        { caseName = "vlseg7e8.v"; }
        { caseName = "vlseg7e16.v"; }
        { caseName = "vlseg7e32.v"; }
        { caseName = "vlseg8e8.v"; }
        { caseName = "vlseg8e16.v"; }
        { caseName = "vlseg8e32.v"; }
        { caseName = "vlsseg2e8.v"; }
        { caseName = "vlsseg2e16.v"; }
        { caseName = "vlsseg2e32.v"; }
        { caseName = "vlsseg3e8.v"; }
        { caseName = "vlsseg3e16.v"; }
        { caseName = "vlsseg3e32.v"; }
        { caseName = "vlsseg4e8.v"; }
        { caseName = "vlsseg4e16.v"; }
        { caseName = "vlsseg4e32.v"; }
        { caseName = "vlsseg5e8.v"; }
        { caseName = "vlsseg5e16.v"; }
        { caseName = "vlsseg5e32.v"; }
        { caseName = "vlsseg6e8.v"; }
        { caseName = "vlsseg6e16.v"; }
        { caseName = "vlsseg6e32.v"; }
        { caseName = "vlsseg7e8.v"; }
        { caseName = "vlsseg7e16.v"; }
        { caseName = "vlsseg7e32.v"; }
        { caseName = "vlsseg8e8.v"; }
        { caseName = "vlsseg8e16.v"; }
        { caseName = "vlsseg8e32.v"; }
        { caseName = "vluxei8.v"; }
        { caseName = "vluxei16.v"; }
        { caseName = "vluxei32.v"; }
        { caseName = "vluxseg2ei8.v"; }
        { caseName = "vluxseg2ei16.v"; }
        { caseName = "vluxseg2ei32.v"; }
        { caseName = "vluxseg3ei8.v"; }
        { caseName = "vluxseg3ei16.v"; }
        { caseName = "vluxseg3ei32.v"; }
        { caseName = "vluxseg4ei8.v"; }
        { caseName = "vluxseg4ei16.v"; }
        { caseName = "vluxseg4ei32.v"; }
        { caseName = "vluxseg5ei8.v"; }
        { caseName = "vluxseg5ei16.v"; }
        { caseName = "vluxseg5ei32.v"; }
        { caseName = "vluxseg6ei8.v"; }
        { caseName = "vluxseg6ei16.v"; }
        { caseName = "vluxseg6ei32.v"; }
        { caseName = "vluxseg7ei8.v"; }
        { caseName = "vluxseg7ei16.v"; }
        { caseName = "vluxseg7ei32.v"; }
        { caseName = "vluxseg8ei8.v"; }
        { caseName = "vluxseg8ei16.v"; }
        { caseName = "vluxseg8ei32.v"; }
        { caseName = "vmacc.vv"; }
        { caseName = "vmacc.vx"; }
        { caseName = "vmadc.vi"; }
        { caseName = "vmadc.vim"; }
        { caseName = "vmadc.vv"; }
        { caseName = "vmadc.vvm"; }
        { caseName = "vmadc.vx"; }
        { caseName = "vmadc.vxm"; }
        { caseName = "vmadd.vv"; }
        { caseName = "vmadd.vx"; }
        { caseName = "vmand.mm"; }
        { caseName = "vmandn.mm"; }
        { caseName = "vmax.vv"; }
        { caseName = "vmax.vx"; }
        { caseName = "vmaxu.vv"; }
        { caseName = "vmaxu.vx"; }
        { caseName = "vmerge.vim"; }
        { caseName = "vmerge.vvm"; }
        { caseName = "vmerge.vxm"; }
        { caseName = "vmfeq.vf"; fp = true; }
        { caseName = "vmfeq.vv"; fp = true; }
        { caseName = "vmfge.vf"; fp = true; }
        { caseName = "vmfgt.vf"; fp = true; }
        { caseName = "vmflt.vf"; fp = true; }
        { caseName = "vmflt.vv"; fp = true; }
        { caseName = "vmfne.vf"; fp = true; }
        { caseName = "vmfne.vv"; fp = true; }
        { caseName = "vmin.vv"; }
        { caseName = "vmin.vx"; }
        { caseName = "vminu.vv"; }
        { caseName = "vminu.vx"; }
        { caseName = "vmnand.mm"; }
        { caseName = "vmnor.mm"; }
        { caseName = "vmor.mm"; }
        { caseName = "vmorn.mm"; }
        { caseName = "vmsbc.vv"; }
        { caseName = "vmsbc.vvm"; }
        { caseName = "vmsbc.vx"; }
        { caseName = "vmsbc.vxm"; }
        { caseName = "vmsbf.m"; }
        { caseName = "vmseq.vi"; }
        { caseName = "vmseq.vv"; }
        { caseName = "vmseq.vx"; }
        { caseName = "vmsgt.vi"; }
        { caseName = "vmsgt.vv"; }
        { caseName = "vmsgt.vx"; }
        { caseName = "vmsgtu.vi"; }
        { caseName = "vmsgtu.vv"; }
        { caseName = "vmsgtu.vx"; }
        { caseName = "vmsif.m"; }
        { caseName = "vmsle.vi"; }
        { caseName = "vmsle.vv"; }
        { caseName = "vmsle.vx"; }
        { caseName = "vmsleu.vi"; }
        { caseName = "vmsleu.vv"; }
        { caseName = "vmsleu.vx"; }
        { caseName = "vmslt.vv"; }
        { caseName = "vmslt.vx"; }
        { caseName = "vmsltu.vv"; }
        { caseName = "vmsltu.vx"; }
        { caseName = "vmsne.vi"; }
        { caseName = "vmsne.vv"; }
        { caseName = "vmsne.vx"; }
        { caseName = "vmsof.m"; }
        { caseName = "vmul.vv"; }
        { caseName = "vmul.vx"; }
        { caseName = "vmulh.vv"; }
        { caseName = "vmulh.vx"; }
        { caseName = "vmulhsu.vv"; }
        { caseName = "vmulhsu.vx"; }
        { caseName = "vmulhu.vv"; }
        { caseName = "vmulhu.vx"; }
        { caseName = "vmv.s.x"; }
        { caseName = "vmv.v.i"; }
        { caseName = "vmv.v.v"; }
        { caseName = "vmv.v.x"; }
        { caseName = "vmv.x.s"; }
        { caseName = "vmv1r.v"; }
        { caseName = "vmv2r.v"; }
        { caseName = "vmv4r.v"; }
        { caseName = "vmv8r.v"; }
        { caseName = "vmxnor.mm"; }
        { caseName = "vmxor.mm"; }
        { caseName = "vnclip.wi"; }
        { caseName = "vnclip.wv"; }
        { caseName = "vnclip.wx"; }
        { caseName = "vnclipu.wi"; }
        { caseName = "vnclipu.wv"; }
        { caseName = "vnclipu.wx"; }
        { caseName = "vnmsac.vv"; }
        { caseName = "vnmsac.vx"; }
        { caseName = "vnmsub.vv"; }
        { caseName = "vnmsub.vx"; }
        { caseName = "vnsra.wi"; }
        { caseName = "vnsra.wv"; }
        { caseName = "vnsra.wx"; }
        { caseName = "vnsrl.wi"; }
        { caseName = "vnsrl.wv"; }
        { caseName = "vnsrl.wx"; }
        { caseName = "vor.vi"; }
        { caseName = "vor.vv"; }
        { caseName = "vor.vx"; }
        { caseName = "vredand.vs"; }
        { caseName = "vredmax.vs"; }
        { caseName = "vredmaxu.vs"; }
        { caseName = "vredmin.vs"; }
        { caseName = "vredminu.vs"; }
        { caseName = "vredor.vs"; }
        { caseName = "vredsum.vs"; }
        { caseName = "vredxor.vs"; }
        { caseName = "vrem.vv"; }
        { caseName = "vrem.vx"; }
        { caseName = "vremu.vv"; }
        { caseName = "vremu.vx"; }
        { caseName = "vrgather.vi"; }
        { caseName = "vrgather.vv"; }
        { caseName = "vrgather.vx"; }
        { caseName = "vrgatherei16.vv"; }
        { caseName = "vrsub.vi"; }
        { caseName = "vrsub.vx"; }
        { caseName = "vs1r.v"; }
        { caseName = "vs2r.v"; }
        { caseName = "vs4r.v"; }
        { caseName = "vs8r.v"; }
        { caseName = "vsadd.vi"; }
        { caseName = "vsadd.vv"; }
        { caseName = "vsadd.vx"; }
        { caseName = "vsaddu.vi"; }
        { caseName = "vsaddu.vv"; }
        { caseName = "vsaddu.vx"; }
        { caseName = "vsbc.vvm"; }
        { caseName = "vsbc.vxm"; }
        { caseName = "vse8.v"; }
        { caseName = "vse16.v"; }
        { caseName = "vse32.v"; }
        { caseName = "vsetivli"; }
        { caseName = "vsetvl"; }
        { caseName = "vsetvli"; }
        { caseName = "vsext.vf2"; }
        { caseName = "vsext.vf4"; }
        { caseName = "vslide1down.vx"; }
        { caseName = "vslide1up.vx"; }
        { caseName = "vslidedown.vi"; }
        { caseName = "vslidedown.vx"; }
        { caseName = "vslideup.vi"; }
        { caseName = "vslideup.vx"; }
        { caseName = "vsll.vi"; }
        { caseName = "vsll.vv"; }
        { caseName = "vsll.vx"; }
        { caseName = "vsm.v"; }
        { caseName = "vsmul.vv"; }
        { caseName = "vsmul.vx"; }
        { caseName = "vsoxei8.v"; }
        { caseName = "vsoxei16.v"; }
        { caseName = "vsoxei32.v"; }
        { caseName = "vsoxseg2ei8.v"; }
        { caseName = "vsoxseg2ei16.v"; }
        { caseName = "vsoxseg2ei32.v"; }
        { caseName = "vsoxseg3ei8.v"; }
        { caseName = "vsoxseg3ei16.v"; }
        { caseName = "vsoxseg3ei32.v"; }
        { caseName = "vsoxseg4ei8.v"; }
        { caseName = "vsoxseg4ei16.v"; }
        { caseName = "vsoxseg4ei32.v"; }
        { caseName = "vsoxseg5ei8.v"; }
        { caseName = "vsoxseg5ei16.v"; }
        { caseName = "vsoxseg5ei32.v"; }
        { caseName = "vsoxseg6ei8.v"; }
        { caseName = "vsoxseg6ei16.v"; }
        { caseName = "vsoxseg6ei32.v"; }
        { caseName = "vsoxseg7ei8.v"; }
        { caseName = "vsoxseg7ei16.v"; }
        { caseName = "vsoxseg7ei32.v"; }
        { caseName = "vsoxseg8ei8.v"; }
        { caseName = "vsoxseg8ei16.v"; }
        { caseName = "vsoxseg8ei32.v"; }
        { caseName = "vsra.vi"; }
        { caseName = "vsra.vv"; }
        { caseName = "vsra.vx"; }
        { caseName = "vsrl.vi"; }
        { caseName = "vsrl.vv"; }
        { caseName = "vsrl.vx"; }
        { caseName = "vsse8.v"; }
        { caseName = "vsse16.v"; }
        { caseName = "vsse32.v"; }
        { caseName = "vsseg2e8.v"; }
        { caseName = "vsseg2e16.v"; }
        { caseName = "vsseg2e32.v"; }
        { caseName = "vsseg3e8.v"; }
        { caseName = "vsseg3e16.v"; }
        { caseName = "vsseg3e32.v"; }
        { caseName = "vsseg4e8.v"; }
        { caseName = "vsseg4e16.v"; }
        { caseName = "vsseg4e32.v"; }
        { caseName = "vsseg5e8.v"; }
        { caseName = "vsseg5e16.v"; }
        { caseName = "vsseg5e32.v"; }
        { caseName = "vsseg6e8.v"; }
        { caseName = "vsseg6e16.v"; }
        { caseName = "vsseg6e32.v"; }
        { caseName = "vsseg7e8.v"; }
        { caseName = "vsseg7e16.v"; }
        { caseName = "vsseg7e32.v"; }
        { caseName = "vsseg8e8.v"; }
        { caseName = "vsseg8e16.v"; }
        { caseName = "vsseg8e32.v"; }
        { caseName = "vssra.vi"; }
        { caseName = "vssra.vv"; }
        { caseName = "vssra.vx"; }
        { caseName = "vssrl.vi"; }
        { caseName = "vssrl.vv"; }
        { caseName = "vssrl.vx"; }
        { caseName = "vssseg2e8.v"; }
        { caseName = "vssseg2e16.v"; }
        { caseName = "vssseg2e32.v"; }
        { caseName = "vssseg3e8.v"; }
        { caseName = "vssseg3e16.v"; }
        { caseName = "vssseg3e32.v"; }
        { caseName = "vssseg4e8.v"; }
        { caseName = "vssseg4e16.v"; }
        { caseName = "vssseg4e32.v"; }
        { caseName = "vssseg5e8.v"; }
        { caseName = "vssseg5e16.v"; }
        { caseName = "vssseg5e32.v"; }
        { caseName = "vssseg6e8.v"; }
        { caseName = "vssseg6e16.v"; }
        { caseName = "vssseg6e32.v"; }
        { caseName = "vssseg7e8.v"; }
        { caseName = "vssseg7e16.v"; }
        { caseName = "vssseg7e32.v"; }
        { caseName = "vssseg8e8.v"; }
        { caseName = "vssseg8e16.v"; }
        { caseName = "vssseg8e32.v"; }
        { caseName = "vssub.vv"; }
        { caseName = "vssub.vx"; }
        { caseName = "vssubu.vv"; }
        { caseName = "vssubu.vx"; }
        { caseName = "vsub.vv"; }
        { caseName = "vsub.vx"; }
        { caseName = "vsuxei8.v"; }
        { caseName = "vsuxei16.v"; }
        { caseName = "vsuxei32.v"; }
        { caseName = "vsuxseg2ei8.v"; }
        { caseName = "vsuxseg2ei16.v"; }
        { caseName = "vsuxseg2ei32.v"; }
        { caseName = "vsuxseg3ei8.v"; }
        { caseName = "vsuxseg3ei16.v"; }
        { caseName = "vsuxseg3ei32.v"; }
        { caseName = "vsuxseg4ei8.v"; }
        { caseName = "vsuxseg4ei16.v"; }
        { caseName = "vsuxseg4ei32.v"; }
        { caseName = "vsuxseg5ei8.v"; }
        { caseName = "vsuxseg5ei16.v"; }
        { caseName = "vsuxseg5ei32.v"; }
        { caseName = "vsuxseg6ei8.v"; }
        { caseName = "vsuxseg6ei16.v"; }
        { caseName = "vsuxseg6ei32.v"; }
        { caseName = "vsuxseg7ei8.v"; }
        { caseName = "vsuxseg7ei16.v"; }
        { caseName = "vsuxseg7ei32.v"; }
        { caseName = "vsuxseg8ei8.v"; }
        { caseName = "vsuxseg8ei16.v"; }
        { caseName = "vsuxseg8ei32.v"; }
        { caseName = "vwadd.vv"; }
        { caseName = "vwadd.vx"; }
        { caseName = "vwadd.wv"; }
        { caseName = "vwadd.wx"; }
        { caseName = "vwaddu.vv"; }
        { caseName = "vwaddu.vx"; }
        { caseName = "vwaddu.wv"; }
        { caseName = "vwaddu.wx"; }
        { caseName = "vwmacc.vv"; }
        { caseName = "vwmacc.vx"; }
        { caseName = "vwmaccsu.vv"; }
        { caseName = "vwmaccsu.vx"; }
        { caseName = "vwmaccu.vv"; }
        { caseName = "vwmaccu.vx"; }
        { caseName = "vwmaccus.vx"; }
        { caseName = "vwmul.vv"; }
        { caseName = "vwmul.vx"; }
        { caseName = "vwmulsu.vv"; }
        { caseName = "vwmulsu.vx"; }
        { caseName = "vwmulu.vv"; }
        { caseName = "vwmulu.vx"; }
        { caseName = "vwredsum.vs"; }
        { caseName = "vwredsumu.vs"; }
        { caseName = "vwsub.vv"; }
        { caseName = "vwsub.vx"; }
        { caseName = "vwsub.wv"; }
        { caseName = "vwsub.wx"; }
        { caseName = "vwsubu.vv"; }
        { caseName = "vwsubu.vx"; }
        { caseName = "vwsubu.wv"; }
        { caseName = "vwsubu.wx"; }
        { caseName = "vxor.vi"; }
        { caseName = "vxor.vv"; }
        { caseName = "vxor.vx"; }
        { caseName = "vzext.vf2"; }
        { caseName = "vzext.vf4"; }
      ];
  };

  all = runCommand "all-testcases"
    {
      mlirCases = lib.attrValues self.mlir;
      asmCases = lib.attrValues self.asm;
      intrinsicCases = lib.attrValues self.intrinsic;
      codegenCases = lib.attrValues self.codegen;
    }
    ''
      mkdir -p $out/cases/{mlir,asm,intrinsic,codegen}
      mkdir -p $out/configs

      linkCases() {
        local -a caseArray
        caseArray=( $1 )
        for case in ''${caseArray[@]}; do
          cp $case/bin/*.elf $out/cases/$2/
          cp $case/*.json $out/configs/
        done
      }

      linkCases "$mlirCases" mlir
      linkCases "$asmCases" asm
      linkCases "$intrinsicCases" intrinsic
      linkCases "$codegenCases" codegen
    '';
in
self
