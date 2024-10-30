{ lib, stdenvNoCC, vcs-fhs-env }:
vcs-emu-trace:

let
  caseName = vcs-emu-trace.caseName;
in
stdenvNoCC.mkDerivation (finalAttr: {
  name = "${caseName}-vcs-prof-vcd";

  __noChroot = true;

  dontUnpack = true;

  buildCommand = ''
    mkdir -p "$out"

    fhsEnv="${vcs-fhs-env}/bin/vcs-fhs-env"
    "$fhsEnv" -c "fsdb2vcd ${vcs-emu-trace}/*.fsdb -o ${caseName}.prof.vcd -s /TestBench/verification/profData"

    cp -t "$out" "${caseName}.prof.vcd"
  '';
})
