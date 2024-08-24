export TMPDIR=/tmp

# prefer terminal safe colored and bold text when tput is supported
if tput setaf 0 &>/dev/null; then
  ALL_OFF="$(tput sgr0)"
  BOLD="$(tput bold)"
  BLUE="${BOLD}$(tput setaf 4)"
  GREEN="${BOLD}$(tput setaf 2)"
  RED="${BOLD}$(tput setaf 1)"
  YELLOW="${BOLD}$(tput setaf 3)"
else
  ALL_OFF="\e[0m"
  BOLD="\e[1m"
  BLUE="${BOLD}\e[34m"
  GREEN="${BOLD}\e[32m"
  RED="${BOLD}\e[31m"
  YELLOW="${BOLD}\e[33m"
fi
readonly ALL_OFF BOLD BLUE GREEN RED YELLOW

info() {
  local mesg=$1; shift
  printf "${GREEN}[T1]${ALL_OFF}${BOLD} ${mesg}${ALL_OFF}\n" "$@"
}

info2() {
  local mesg=$1; shift
  printf "${BLUE}  ->${ALL_OFF}${BOLD} ${mesg}${ALL_OFF}\n" "$@"
}

error() {
  local mesg=$1; shift
  printf "${RED}[T1] $(gettext "ERROR:")${ALL_OFF}${BOLD} ${mesg}${ALL_OFF}\n" "$@" >&2
}

_ROOT_PATTERN="flake.nix"
if [ ! -r "$_ROOT_PATTERN" ]; then
  error "Not in T1 root directory" "Please run nix develop at the root of the T1 project directory"
  exit 1
fi

mkdir -p .cache/dev-mode
_CTX=.cache/dev-mode/state.json
if [ ! -r "$_CTX" ]; then
  printf "{}" > "$_CTX"
fi

_CFG=""
get_config() {
  local cfg=$(jq -r ".config" "$_CTX")
  _CFG="$cfg"
  echo "$_CFG"
}

_config_is_set() {
  _CFG=$(get_config)
  if [[ -z "$_CFG" || "$_CFG" = "null" ]]; then
    info "Config not set yet, use 'set_config <config>' to set config"
    info2 "Available configs: ${BLUE}$(list_config | xargs)${ALL_OFF}"
    return 1
  else
    return 0
  fi
}

list_config() {
  nix --no-warn-dirty eval ".#t1.allConfigs" --json | jq -r 'keys[]'
}

set_config() {
  if [[ -z "$@" ]]; then
    error "No argument was given, use 'set_config <config>' to set config"
    return 1
  fi

  local config=$1; shift
  local configArray=( $(list_config) )
  # TODO: this is not an elegant way
  configArray+=("t1rocket")

  local verifiedConfig=""
  for c in ${configArray[@]}; do
    if [[ "$c" == "$config" ]]; then
      verifiedConfig="$c"
    fi
  done
  if [[ -z "$verifiedConfig" ]]; then
    error "Config '$config' is not supported, available configs: ${configArray[*]}"
    return 1
  fi

  local newCtx=$(jq --arg config "$config" '.config = $config' $_CTX)
  printf "$newCtx" > "$_CTX"

  _CFG=$(get_config)

  info "Config is set to ${BLUE}${_CFG}${ALL_OFF}"
}

_get_ip_attrs() {
  nix --no-warn-dirty eval --json ".#legacyPackages.x86_64-linux" \
    --apply "pkgs: pkgs.lib.filterAttrs (_: v: pkgs.lib.isDerivation v) pkgs.t1.$_CFG.ip" \
    | jq -r 'keys[]'
}

list_emulator() {
  _get_ip_attrs | grep -E '(-emu$|-emu-trace$)'
}

_EMU=""
get_emulator() {
  local emu=$(jq -r ".emulator" "$_CTX")
  if [[ -z "$emu" || "$emu" = "null" ]]; then
    error "Emulator not set yet, please use 'set_emulator <emulator>'"
    return 1
  fi

  _EMU="$emu"
  echo "$_EMU"
}

_emulator_is_set() {
  _EMU=$(get_emulator)
  if [[ -z "$_EMU" || "$_EMU" = "null" ]]; then
    return 1
  else
    return 0
  fi
}

set_emulator() {
  if [[ -z "$@" ]]; then
    error "No argument was given, use 'set_emulator <emulator>' to set main testing emulator"
    return 1
  fi

  local emulator=$1; shift
  local allEmuArray=( $(list_emulator) )

  local verifiedEmulator=""
  for e in ${allEmuArray[@]}; do
    if [[ "$e" == "$emulator" ]]; then
      verifiedEmulator="$e"
    fi
  done
  if [[ -z "$verifiedEmulator" ]]; then
    error "Emulator '$emulator' is not supported, available emulators: ${allEmuArray[*]}"
    return 1
  fi

  local newCtx=$(jq --arg emulator "$verifiedEmulator" '.emulator = $emulator' $_CTX)
  printf "$newCtx" > "$_CTX"

  _EMU=$(get_emulator)

  info "Emulator is set to ${BLUE}${_EMU}${ALL_OFF}"
}

build() {
  if [[ -z "$@" || "$@" = "help" ]]; then
    error "Use ${BLUE}'build <attr>'${ALL_OFF} to build"
    info2 "Available attrs"
    _get_ip_attrs
    return 1
  fi

  local attr=$1; shift
  local availableAttrsArray=( $(_get_ip_attrs) )
  local verifiedAttr=""
  for a in ${availableAttrsArray[@]}; do
    if [ "$a" = "$attr" ]; then
      verifiedAttr="$a"
    fi
  done
  if [ -z "$verifiedAttr" ]; then
    error "Invalid attr '$attr', available attrs:"
    info2 "${availableAttrsArray[*]}"
    return 1
  fi

  if ! _config_is_set; then
    return 1
  fi

  local fullAttr=".#t1.$_CFG.ip.$attr"
  info "Building $fullAttr to result dir ./result with flag '$@'"
  nix build --no-warn-dirty --print-build-logs "$fullAttr" $@
}

run_test() {
  if [[ -z "$@" || "$@" = "help" ]]; then
    error "Use 'test <case-name>' to run a test"
    return 1
  fi

  local case=$1; shift

  if [[ -z "$_EMU" ]]; then
    error "Emulator is not set yet"
    info2 "Use 'set_emulator <emu>' to set the main testing emulator"
    return 1
  fi

  if ! printf "$case" | grep -Eq '\S\.\S'; then
    error "Invalid case form '$case', expect '<type>.<name>'"
    return 1
  fi

  local hasAttr=$(nix eval --json --no-warn-dirty ".#t1.$_CFG.ip.$_EMU.cases" --apply "cases: cases ? $case")
  if [[ "x$hasAttr" = "xfalse" ]]; then
    error "Unknown cases $case"
    return 1
  fi

  DEBUG=${DEBUG:-0}
  caseAttr=".#t1.$_CFG.ip.$_EMU.cases.$case.emu-result"
  if (( DEBUG )); then
    caseAttr="$caseAttr.debug"
  fi

  local args="$@"
  if [[ "$_EMU" =~ "vcs-" ]]; then
    args="$args --impure"

    if [[ -z "$SNPSLMD_LICENSE_FILE" ]]; then
      error "SNPSLMD_LICENSE_FILE not set"
      return 1
    fi

    if [[ -z "$VC_STATIC_HOME" ]]; then
      error "VC_STATIC_HOME not set"
      return 1
    fi
  fi

  info "Running test case $case with emulator $_EMU, output result to ./result"
  nix build --no-warn-dirty "$caseAttr" $args
  ls -l ./result/
}

main() {
  info "Welcome to T1 dev mode, here are some convenient commands"
  info2 "set_config: set current configs"
  info2 "list_config: get all available configs"
  info2 "get_config: print current config"
  info2 "set_emulator: set current emulators"
  info2 "list_emulator: get all available emulators"
  info2 "get_emulator: print the main testing emulator"
  info2 "build: build T1 artifacts with config $_CFG"
  info2 "run_test: run test case with specific emulator"

  echo

  if _config_is_set; then
    info "Config is set to ${BLUE}$_CFG${ALL_OFF}, use ${GREEN}'set_config <config>'${ALL_OFF} to modify config"
  fi

  if _emulator_is_set; then
    info "Emulator is set to ${BLUE}${_EMU}${ALL_OFF}, use ${GREEN}'set_emulator <emulator>'${ALL_OFF} to modify emulator"
  fi
}

main
