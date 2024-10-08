{ lib, newScope }:
lib.makeScope newScope (scope: {
  sail-riscv-sys = scope.callPackage ./sail-riscv-sys { };
})
