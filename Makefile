init:
	git submodule update --init

patch:
	find patches -type f | awk -F/ '{print("(echo "$$0" && cd dependencies/" $$2 " && git apply -3 --ignore-space-change --ignore-whitespace ../../" $$0 ")")}' | sh

depatch:
	git submodule update
	git submodule foreach git restore -S -W .
	git submodule foreach git clean -xdf

compile:
	mill -i vector.compile

bump:
	git submodule foreach git stash
	git submodule update --remote
	git add dependencies

bsp:
	mill -i mill.bsp.BSP/install

update-patches:
	rm -rf patches
	sed '/BEGIN-PATCH/,/END-PATCH/!d;//d' readme.md | awk '{print("mkdir -p patches/" $$1 " && wget " $$2 " -P patches/" $$1 )}' | parallel
	git add patches

clean:
	git clean -fd

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

ci:
	awk '{ print("nix develop -c mill -i -k -j $$(expr $$(nproc) / 8) tests.run[" $$0 "] GLOG_logtostderr=0") }' tests/passed.txt | sh

testall:
	nix develop -c mill -k -i -j $$(expr $$(nproc) / 8) 'tests.run[__].run' GLOG_logtostderr=0
