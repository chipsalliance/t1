{ mkShellNoCC, jq, ncurses }:
mkShellNoCC {
  name = "t1-dev-mode";
  nativeBuildInputs = [ jq ncurses ];
  shellHook = ''
    source ${./dev-mode.sh}
  '';
}
