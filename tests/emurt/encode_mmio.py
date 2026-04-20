import sys
import json


def main():
    if len(sys.argv) != 2:
        print("Usage: python encode_uart.py <file.jsonl>", file=sys.stderr)
        sys.exit(1)

    filename = sys.argv[1]

    try:
        with open(filename, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue

                try:
                    entry = json.loads(line)

                    if entry.get("event") == "uart-write":
                        val = entry.get("value")

                        if isinstance(val, int):
                            sys.stdout.write(chr(val))

                except json.JSONDecodeError:
                    continue

    except FileNotFoundError:
        print(f"Error: File '{filename}' not found.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
