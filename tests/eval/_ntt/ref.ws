#!/usr/bin/env -S wolframscript -file

On[Assert];
x = {
   9997, 6362, 7134, 11711, 5849, 9491, 5972, 4164, 5894, 11069, 7697,
    8319, 2077, 12086, 10239, 5394, 4898, 1370, 1205, 2997, 5274, 
   4625, 11983, 1789, 3645, 7666, 12128, 10883, 7376, 8883, 2321, 
   1889, 2026, 8059, 2741, 865, 1785, 9955, 2395, 9330, 11465, 7383, 
   9649, 11285, 3647, 578, 1158, 9936, 12019, 11114, 7894, 4832, 
   10148, 10363, 11388, 9122, 10758, 2642, 4171, 10586, 1194, 5280, 
   3055, 9220
   };  (* the input array *)
n = 64;  (* the array length *)
p = 12289;  (* a prime number s.t. n | p - 1 *)
g = 7311;  (* an n-th root of p *)

Assert[Length[x] == n];
Assert[PowerMod[g, n, p] == 1 && PowerMod[g, n/2, p] != 1 ]; (* g is an n-th root of p *)

(* note that a wolfram array is indexed from 1, so plus one from the index *)

Table[Mod[
   Sum[
      x[[j + 1]] g^(i j),
      {j, 0, n - 1}
   ], p
], {i, 0, n - 1}] // Print
