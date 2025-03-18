let
  loweringOptions = [
    "emittedLineLength=160"
    "verifLabels"
    "disallowLocalVariables"
    "explicitBitcast"
    "locationInfoStyle=wrapInAtSquareBracket"
    "wireSpillingHeuristic=spillLargeTermsWithNamehints"
    "disallowMuxInlining"
    "wireSpillingNamehintTermLimit=8"
    "maximumNumberOfTermsPerExpression=8"
    "disallowExpressionInliningInPorts"
    "caseInsensitiveKeywords"
  ];
in
[
  "-O=release"
  "--disable-all-randomization"
  "--split-verilog"
  "--preserve-values=all"
  "--strip-debug-info"
  "--strip-fir-debug-info"
  "--verification-flavor=sva"
  "--lowering-options=${builtins.concatStringsSep "," loweringOptions}"
]
