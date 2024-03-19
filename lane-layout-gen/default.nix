{ rustPlatform }:

rustPlatform.buildRustPackage {
  name = "lane-layout-gen";
  src = ./.;
  cargoHash = "sha256-XXvxSeNBpnYAWqmXxHIKIh4mHLm0+hYMsVho4dRgOi8=";
}

