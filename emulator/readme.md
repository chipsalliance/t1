## How to use clangd or other LSP for emulator

```bash
nix develop .#emulator
# Generate CMakefile and ninja file
mill -i emulator[v1024l8b2-test].cmake
ln -s $PWD/out/emulator/v1024l8b2-test/buildDir.dest $PWD/build

# Then everything should just work
vim/code/nvim emulator/src
```
