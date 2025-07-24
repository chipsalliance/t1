set -e

JINJA="minijinja-cli --strict --trim-blocks --lstrip-blocks --format toml"
INST_TEMPLATE="template/inst.asl.j2"

RVV_INSTRUCTIONS=(
    "vmax_vv"
    "vmax_vx"
    "vmin_vv"
    "vmin_vx"
    "vmaxu_vv"
    "vmaxu_vx"
    "vminu_vv"
    "vminu_vx"
)

for inst in "${RVV_INSTRUCTIONS[@]}"; do
  echo "processing rv_v/${inst} ..."
  $JINJA $INST_TEMPLATE \
    -o extensions/rv_v/${inst}.asl \
    -D inst=${inst} \
    extensions/rv_v/${inst}.toml
done
