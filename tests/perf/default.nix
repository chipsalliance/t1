{ findAndBuild }:

let
  build = throw "no default builder for perf cases";
in
findAndBuild ./. build
