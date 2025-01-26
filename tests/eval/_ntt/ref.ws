#!/usr/bin/env -S wolframscript -file

genRandomPoly[l_, p_] := Module[
  {n = 2^l, a},
  (* Generate random list a *)
  a = RandomInteger[{0, p - 1}, n];
  Print[a];
]


genScalarTW[l_, p_, g_] := Module[{w = g, twiddleList = {}},
  Do[
    AppendTo[twiddleList, w];
    w = Mod[w * w, p],
    {l}
  ];
  twiddleList
]

genVectorTW[l_, p_, g_] := Module[{n = 2^l, m = 2, layerIndex = 0, outTW = {}, wPower, currentW},
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
]

On[Assert];
l = 6;
n = 2 ^ l;  (* the array length *)
p = 12289;  (* a prime number s.t. n | p - 1 *)
g = 7311;  (* an n-th root of p *)
x = genRandomPoly[l, p]

Assert[Length[x] == n];
Assert[PowerMod[g, n, p] == 1 && PowerMod[g, n/2, p] != 1 ]; (* g is an n-th root of p *)

(* note that a wolfram array is indexed from 1, so plus one from the index *)

x_out = Table[Mod[
   Sum[
      x[[j + 1]] g^(i j),
      {j, 0, n - 1}
   ], p
], {i, 0, n - 1}]

vectorTW = genVectorTW[l, p, g]

scalarTW = genScalarTW[l, p, g]

Export["ntt_64.json", {
  "input" -> x,
  "output" -> x_out,
  "vector_tw" -> vectorTW, 
  "scalar_tw" -> scalarTW
}]
