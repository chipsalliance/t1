#!/usr/bin/env -S wolframscript -file

genRandomPoly[l_, p_] := Module[
  {n = 2^l, a},
  (* Generate random list a *)
  a = RandomInteger[{0, p - 1}, n];
  Print[a];
]

genScalarTW[l_, p_, g_] := Module[
  {w = g, twiddleList},

  (* Assert conditions *)
  If[Mod[p - 1, n] != 0, Return["Assertion failed: (p - 1) mod n != 0"]];

  (* Generate twiddle list *)
  twiddleList = {};
  Do[
    AppendTo[twiddleList, w];
    w = Mod[w^2, p],
    {l}
  ];
  Print[twiddleList];
]

genVectorTW[l_, p_, w_] := Module[
  {n = 2^l, m = 2, layerIndex = 0},

  While[m <= n,
    Print["// layer #", layerIndex];
    layerIndex++;
    
    Module[{wPower = 0, currentW},
      For[j = 0, j < m/2, j++,
        For[k = 0, k < n, k += m,
          currentW = PowerMod[w, wPower, p];
          Print[currentW, ", "];
        ];
        wPower += n/m;
      ];
    ];
    m *= 2;
    Print["\n"];
  ];
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
], {i, 0, n - 1}] // Print

Export["ntt_64.json", {"input" -> x, "output" -> x_out}]
