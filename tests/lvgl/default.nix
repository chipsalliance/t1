{
  lib,
  linkerScript,
  makeBuilder,
  findAndBuild,
  t1main,
  getTestRequiredFeatures,
  rtlDesignMetadata,
  callPackage,
  fetchFromGitHub,
  cmake,
  ninja,
  stdenv,
}:

let
  builder = makeBuilder { casePrefix = "lvgl"; };
  lvgl = stdenv.mkDerivation rec {
    NIX_CFLAGS_COMPILE =
      let
        march = lib.pipe rtlDesignMetadata.march [
          (lib.splitString "_")
          (map (
            ext:
            # g impls d
            if ext == "rv32gc" then
              "rv32imafc"
            # zvbb has experimental compiler support and required version info
            else if ext == "zvbb" then
              "zvbb1"
            else
              ext
          ))
          (lib.concatStringsSep "_")
        ];
      in
      [
        "-mabi=ilp32f"
        "-march=${march}"
        "-mno-relax"
        "-static"
        "-mcmodel=medany"
        "-fvisibility=hidden"
        "-fno-PIC"
        # disable the support for the Run-Time Type Information, for example, `dynamic_cast`
        "-fno-rtti"
        # disable the support for C++ exceptions
        "-fno-exceptions"
        # disables thread-safe initialization of static variables within functions
        "-fno-threadsafe-statics"
      ]
      ++ lib.optionals (lib.elem "zvbb" (lib.splitString "_" rtlDesignMetadata.march)) [
        "-menable-experimental-extensions"
      ];

    pname = "lvgl";
    version = "9.2.1";

    src = fetchFromGitHub {
      owner = "lvgl";
      repo = "lvgl";
      rev = "v${version}";
      hash = "sha256-+k2ID3nzwrxuQC/1lR/RrEUNoyHfnjVQd3NpyqakD3g=";
    };

    nativeBuildInputs = [
      cmake
      ninja
    ];

    preConfigure = ''
      mkdir -p $out/include/
      cp ${./lv_conf.h} $out/include/lv_conf.h
    '';

    cmakeFlags = [
      "-DLV_CONF_PATH=${placeholder "out"}/include/lv_conf.h"
      (lib.cmakeBool "LV_CONF_BUILD_DISABLE_DEMOS" true)
      (lib.cmakeBool "LV_CONF_BUILD_DISABLE_EXAMPLES" true)
      (lib.cmakeBool "LV_CONF_BUILD_DISABLE_THORVG_INTERNAL" true)
    ];

    installPhase = ''
      runHook preInstall
      ninjaInstallPhase
      cp ${./lv_conf.h} $out/include
      runHook postInstall
    '';
  };
in
{
  simple = builder {
    caseName = "simple";

    buildInputs = [ lvgl ];

    src =
      with lib.fileset;
      (toSource {
        root = ./.;
        fileset = fileFilter (file: file.name != "default.nix") ./.;
      }).outPath;

    passthru = { inherit lvgl; };

    buildPhase = ''
      runHook preBuild

      $CC -T${linkerScript} \
        main.c \
        lv_port_disp.c \
        ${t1main} \
        -llvgl \
        -o $pname.elf

      runHook postBuild
    '';
  };
}
