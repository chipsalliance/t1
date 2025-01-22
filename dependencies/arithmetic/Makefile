init:
	git submodule update --init

compile:
	mill -i -j 0 arithmetic[5.0.0].compile

run:
	mill -i -j 0 arithmetic[5.0.0].run

test:
	mill -i -j 0 test[5.0.0].test

bsp:
	mill -i mill.bsp.BSP/install

clean:
	git clean -fd

