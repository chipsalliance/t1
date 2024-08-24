{ mkShellNoCC, jq, ncurses, gettext }:
mkShellNoCC {
  name = "t1-dev-mode";
  nativeBuildInputs = [ jq ncurses gettext ];
  shellHook = ''
    source ${./dev-mode.sh}
  '';
}
