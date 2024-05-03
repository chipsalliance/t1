{ lib
, emurt
, fetchurl
, _caseBuilders
}:

let
  checkpoint_bin = fetchurl {
    url = "https://huggingface.co/karpathy/tinyllamas/resolve/main/stories15M.bin";
    sha256 = "sha256-zVkGRNljhnorbloRB/UfrWY8QdecFJ++y7sflfqB9Jo=";
  };

  tokenizer_bin = fetchurl {
    url = "https://github.com/karpathy/llama2.c/raw/b3c4b6c3c4bbff42e5211293280307019368ccb5/tokenizer.bin";
    sha256 = "sha256-UKUu+CLunoPeXOnQvgoCWnc9AZQ39Ytf+dyvsGPs42E=";
  };
in

_caseBuilders.mkIntrinsicCase {
  casePrefix = "perf";
  caseName = "llama";

  buildInputs = [ emurt ];

  src = with lib.fileset; toSource {
    root = ./.;
    fileset = fileFilter (file: file.name != "default.nix") ./.;
  };

  unpackPhase = ''
    cp $src -rT .
    chmod -R +w .
  '';

  postPatch = ''
    substituteInPlace extern_data.S \
      --replace-fail '{{checkpoint_bin}}' ${checkpoint_bin} \
      --replace-fail '{{tokenizer_bin}}' ${tokenizer_bin}
  '';

  srcs = [
    "run.c"
    "trap.c"
    "extern_data.S"
    ../../t1_main.S
  ];
}
