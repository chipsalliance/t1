{
  callPackage,
}:

let
  nttCases = callPackage ./_ntt { };
in
{
  softmax = callPackage ./softmax { };
}
// nttCases
