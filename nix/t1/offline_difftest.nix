{ libspike
, libspike_interfaces

, rtl
}:
# todo
# let
#   myRustPlatform = makeRustPlatform {
#     cargo = myRustToolchain;
#     rustc = myRustToolchain;
#   };

#   self = myRustPlatform.buildRustPackage {
#     pname = "offline_difftest";
#     version = "0.1.0";
#     src = ../../difftest/offline_difftest;
#     buildInputs = [ libspike libspike_interfaces ];
#     cargoLock = {
#       lockFile = ../../difftest/offline_difftest/Cargo.lock;
#     };
#   };
# in
# self
