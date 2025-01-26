import sys
import json
import random

def main(n):
    json_name = "ntt_" + str(n) + ".json"
    with open(json_name, "r") as json_in:
        json_data = json.load(json_in)

    header_str = "#define macroL " + str(json_data["l"]) + "\n"
    header_str += "#define macroN " + str(json_data["n"]) + "\n"
    header_str += "#define macroP " + str(json_data["p"]) + "\n"
    header_str += (
        "#define macroIn " + ",".join(str(e) for e in json_data["input"]) + "\n"
    )
    header_str += (
        "#define macroOut " + ",".join(str(e) for e in json_data["output"]) + "\n"
    )
    header_str += (
        "#define macroScalarTW " + ",".join(str(e) for e in json_data["scalar_tw"]) + "\n"
    )
    header_str += (
        "#define macroVectorTW " + ",".join(str(e) for e in json_data["vector_tw"]) + "\n"
    )

    header_file = "ntt_" + str(n) + ".h"
    with open(header_file, "w") as header_out:
        header_out.write(header_str)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise Exception("No Enough Input Args")

    if sys.argv[1] in ("ntt_64"):
        main(64)
    elif sys.argv[1] in ("ntt_128"):
        main(128)
    elif sys.argv[1] in ("ntt_256"):
        main(256)
    elif sys.argv[1] in ("ntt_512"):
        main(512)
    elif sys.argv[1] in ("ntt_1024"):
        main(1024)
    elif sys.argv[1] in ("ntt_4096"):
        main(4096)
    else:
        raise Exception(f"Unknown Args {sys.argv[1]}")
