{
  lib,
  stdenv,
  makeWrapper,
  writeShellApplication,

  mill,
  mill-ivy-fetcher,
  riscv-opcodes-src,
  ivy-gather,
  ivy-rvdecoderdb,
  add-determinism-hook,
}:
let
  ivyCache = ivy-gather ./mill-lock.nix;
in
stdenv.mkDerivation rec {
  name = "pokedex-codegen-cli-jar";

  src =
    with lib.fileset;
    toSource {
      root = ./.;
      fileset = unions [
        ./build.mill
        ./src
      ];
    };

  nativeBuildInputs = [
    makeWrapper
    add-determinism-hook
    ivy-rvdecoderdb.setupHook
  ];

  propagatedBuildInputs = [
    mill
  ];

  buildInputs = [ ivyCache ];

  passthru = {
    inherit ivyCache;
    bump = writeShellApplication {
      name = "bump-${name}-jar-lock";
      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];
      text = ''
        ivyLocal="${ivy-rvdecoderdb}"
        export JAVA_TOOL_OPTIONS="''${JAVA_TOOL_OPTIONS:-} -Dcoursier.ivy.home=$ivyLocal -Divy.home=$ivyLocal"

        mif run --project-dir ${src} -o ./mill-lock.nix "$@"
      '';
    };
  };

  env = {
    "RISCV_OPCODES_PATH" = "${riscv-opcodes-src}";
    "RISCV_CUSTOM_OPCODES_PATH" = "${../../custom-instructions}";
  };

  buildPhase = ''
    runHook preBuild

    mill -i assembly

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/share/java

    mv out/assembly.dest/out.jar $out/share/java/codegen.jar

    mkdir -p $out/bin
    makeWrapper ${mill.jre}/bin/java $out/bin/codegen \
      --add-flags "-jar $out/share/java/codegen.jar"

    runHook postInstall
  '';

  meta.mainProgram = "codegen";
}
