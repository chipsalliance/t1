#!/usr/bin/env -S wolframscript -file
(* ::Package:: *)

genRandomPoly[l_, p_] := RandomInteger[{0, p - 1}, n]

genScalarTW[l_, p_, g_] := NestList[PowerMod[#, 2, p]&, g, l]

genVectorTW[l_, p_, g_] := Module[{n = 2^l, m = 2, outTW = {}, wPower, currentW},
  While[m <= n,
    wPower = 0;
    Do[
      k = 0;
      While[k < n,
        currentW = PowerMod[g, wPower, p];
        k += m;
        AppendTo[outTW, currentW];
      ];
      wPower += n / m,
      {j, 1, m / 2}
    ];
    m *= 2;
  ];
  outTW
];

On[Assert];
l = 11;
n = 2 ^ l;  (* the array length *)
p = 12289;  (* a prime number s.t. n | p - 1 *)
g = 1945;  (* an n-th root of p *)
x = genRandomPoly[l, p];

Assert[Length[x] == n];
Assert[PowerMod[g, n, p] == 1 && PowerMod[g, n/2, p] != 1 ]; (* g is an n-th root of p *)

(* note that a wolfram array is indexed from 1, so plus one from the index *)
xOut = Table[Mod[
   Sum[
      x[[j + 1]] PowerMod[g,i j,p],
      {j, 0, n - 1}
   ], p
], {i, 0, n - 1}];

vectorTW = genVectorTW[l, p, g];

scalarTW = genScalarTW[l, p, g];

Export["ntt_2048.json", {
  "l" -> l,
  "n" -> n,
  "p" -> p,
  "input" -> x,
  "output" -> xOut,
  "vector_tw" -> vectorTW, 
  "scalar_tw" -> scalarTW
}];

