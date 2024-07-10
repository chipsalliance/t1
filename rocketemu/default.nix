{ lib
, newScope
}:
lib.makeScope newScope (scope: {
  c-dpi-lib = scope.callPackage ./dpi { };
  driver = scope.callPackage ./driver { };
})
