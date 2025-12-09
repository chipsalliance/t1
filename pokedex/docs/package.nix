{
  lib,
  newScope,
}:
lib.makeScope newScope (scope: {
  mkTypstEnv = scope.callPackage ./makeTypstEnv.nix { };

  guidance = scope.callPackage ./guidance/package.nix { };
})
