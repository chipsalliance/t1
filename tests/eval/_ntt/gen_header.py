import sys
import json
import random


def genRandomPoly(l, p):
    n = 1 << l
    a = [random.randrange(p) for _ in range(n)]
    return a


def genGoldPoly(l, p, g, poly):
    n = 1 << l
    poly_out = []
    for i in range(n):
        tmp = 0
        for j in range(n):
            tmp += poly[j] * pow(g, i * j, p)
            tmp = tmp % p
        poly_out.append(tmp)
    return poly_out


def genScalarTW(l, p, g):
    w = g

    twiddle_list = []
    for _ in range(l):
        twiddle_list.append(w)
        w = (w * w) % p

    return twiddle_list


def genVectorTW(l, p, g):
    n = 1 << l
    m = 2
    layerIndex = 0

    outTW = []
    while m <= n:
        # print(f"// layer #{layerIndex}")
        layerIndex += 1
        wPower = 0

        for j in range(m // 2):
            k = 0
            while k < n:
                currentW = pow(g, wPower, p)
                k += m
                outTW.append(currentW)
                # print(currentW, end =", ")
            wPower += n // m
        m *= 2
        # print("\n")
    return outTW


def main(l, p, g):
    # poly_in = genRandomPoly(l, p)
    # poly_out = genGoldPoly(l, p, g, poly_in)
    scalar_tw = genScalarTW(l, p, g)
    vector_tw = genVectorTW(l, p, g)
    n = 1 << l
    data = {
        "l": l,
        "n": n,
        "p": p,
        # "input": poly_in,
        # "output": poly_out,
        "vector_tw": vector_tw,
        "scalar_tw": scalar_tw,
    }

    json_name = "ntt_" + str(n) + ".json"
    with open(json_name, "r") as json_in:
        json_data = json.load(json_in)

    header_str = "#define macroL " + str(data["l"]) + "\n"
    header_str += "#define macroN " + str(data["n"]) + "\n"
    header_str += "#define macroP " + str(data["p"]) + "\n"
    header_str += (
        "#define macroIn " + ",".join(str(e) for e in json_data["input"]) + "\n"
    )
    header_str += (
        "#define macroOut " + ",".join(str(e) for e in json_data["output"]) + "\n"
    )
    header_str += (
        "#define macroScalarTW " + ",".join(str(e) for e in data["scalar_tw"]) + "\n"
    )
    header_str += (
        "#define macroVectorTW " + ",".join(str(e) for e in data["vector_tw"]) + "\n"
    )

    header_file = "ntt_" + str(n) + ".h"
    with open(header_file, "w") as header_out:
        header_out.write(header_str)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise Exception("No Enough Input Args")

    p = 12289
    if sys.argv[1] in ("ntt_64"):
        main(6, p, 7311)
    elif sys.argv[1] in ("ntt_128"):
        main(7, p, 12149)
    elif sys.argv[1] in ("ntt_256"):
        main(8, p, 8340)
    elif sys.argv[1] in ("ntt_512"):
        main(9, p, 3400)
    elif sys.argv[1] in ("ntt_1024"):
        main(10, p, 10302)
    elif sys.argv[1] in ("ntt_4096"):
        main(12, p, 1331)
    else:
        raise Exception(f"Unknown Args {sys.argv[1]}")
