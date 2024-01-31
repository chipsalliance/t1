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
    ] // { recurseForDerivations = true; };

  self = {
    recurseForDerivations = true;
    # nix build .#t1.cases.<type>.<name>
    mlir = searchAndCallPackage ./mlir;
    intrinsic = searchAndCallPackage ./intrinsic;
    asm = searchAndCallPackage ./asm;

    # nix build .#t1.cases.codegen.vaadd-vv -L
    # codegen case are using xLen=32,vLen=1024 by default
    codegen =
      let
        /* Return an attr set based on given file. File is expected to be a list of codegen case name, separated by "\n".
           It will turn this file into a set of derivation, with each key as the canonicalized test name, and value as derivation.

           Example:
             $ cat test.txt
             v.a
             v.b

             getTestsFromFile ./test.txt { } => { "v-a": <derivation>; "v-b": <derivation>; }
             getTestsFromFile ./test.txt { fp = true } => { "v-a": <derivation>; "v-b": <derivation>; }

           Type:
             getTestsFromFile :: Path -> AttrSet -> AttrSet
        */
        getTestsFromFile = file: extra:
          with lib;
          let
            textNames = lib.splitString "\n" (lib.fileContents file);
            buildSpec = map (caseName: { inherit caseName; } // extra) textNames;
          in
          (map
            (spec: nameValuePair
              # If we using `.` as key, nix command line will fail to parse our input
              (replaceStrings [ "." ] [ "-" ] spec.caseName)
              (testcase-env.mkCodegenCase spec)
            )
            buildSpec);

        commonTests = getTestsFromFile ./codegen/common.txt { };
        fpTests = getTestsFromFile ./codegen/fp.txt { fp = true; };
      in
      { recurseForDerivations = true; } // builtins.listToAttrs (commonTests ++ fpTests);

    all =
      let
        /*
          Transform the current attribute set into bash script.

          Example:
            { mlir: { hello: <derivation> } } => ''
              mkdir -p $out/configs
              mkdir -p $out/cases/mlir
              cp /nix/store/xxx/bin/*.elf $out/case/mlir/
              cp /nix/store/xxx/*.json $out/configs/
            '';

          Types:
            annoynomous :: set -> string
        */
        script = lib.pipe self [
          # filter the `all` attr set
          (lib.filterAttrs (k: _: k != "all"))
          # filter all the `recurseForDerivation` name value pair
          (lib.filterAttrsRecursive (k: _: k != "recurseForDerivations"))
          # turn kkv to k-list, example: { k: { a: v, b: x } } -> { k: [ v, x ] }
          (lib.mapAttrs (_: drvs: lib.attrValues drvs))
          # turn the k-list to final bash script
          # for each k, the value list will be transformed into bash glob path
          #
          #   { k: [ /a /b /c ] } -> cp /a/bin/*.elf $out/case/$k/
          (lib.foldlAttrs
            (acc: name: drvs:
              let
                makeGlobPaths = segment: paths: lib.concatStringsSep " " (map (x: x + segment) paths);
              in
              acc + ''
                mkdir -p $out/cases/${name}
                cp ${makeGlobPaths "/bin/*.elf" drvs} $out/cases/${name}/
                cp ${makeGlobPaths "/*.json" drvs} $out/configs/
              '') "mkdir -p $out/configs \n")
        ];
      in
      runCommand "build-all-testcases" { } script;
  };
in
self
