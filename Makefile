.PHONY: init
init:
	git submodule update --init

.PHONY: patch
patch:
	find patches -type f | awk -F/ '{print("(echo "$$0" && cd dependencies/" $$2 " && git apply -3 --ignore-space-change --ignore-whitespace ../../" $$0 ")")}' | sh

.PHONY: depatch
depatch:
	git submodule update
	git submodule foreach git restore -S -W .
	git submodule foreach git clean -xdf

.PHONY: compile
compile:
	mill -i vector.compile

.PHONY: bump
bump:
	git submodule foreach git stash
	git submodule update --remote
	git add dependencies

.PHONY: bsp
bsp:
	mill -i mill.bsp.BSP/install

.PHONY: update-patches
update-patches:
	rm -rf patches
	sed '/BEGIN-PATCH/,/END-PATCH/!d;//d' readme.md | awk '{print("mkdir -p patches/" $$1 " && wget " $$2 " -P patches/" $$1 )}' | parallel
	git add patches

.PHONY: clean
clean:
	git clean -fd

.PHONY: reformat
reformat:
	mill -i __.reformat

.PHONY: checkformat
checkformat:
	mill -i __.checkFormat

.PHONY: list-configs
list-configs:
	@nix eval ".#t1.allConfigs" --json | jq -r 'to_entries| map({key: .key, value: .value|keys|map(split(".")[4])}) | map( .key as $$key | reduce .value[] as $$item ([]; ["\($$key).\($$item)"]+. )) | flatten'
