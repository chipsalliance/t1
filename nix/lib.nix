{ lib }:

{
  sourceFilesByPrefixes = src: prefixes: rec {
    name = "source";
    origSrc = src;
    filter = (p: t: lib.any
      (
        prefix: lib.hasPrefix prefix (lib.removePrefix (toString origSrc) p)
      )
      prefixes);
    outPath = builtins.path {
      inherit filter name;
      path = origSrc;
    };
  };
}
