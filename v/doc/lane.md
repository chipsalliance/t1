## lane

在这个设计中, lane仅仅只作为执行单元

#### 1.指令分类

##### 1.1逻辑运算

包括arithmetic里面的逻辑计算, mask指令里面的逻辑计算

* and 
* or 
* xor
* seq -> xor
* sne -> nxor?

mask

* nand

* andn

* xor

* xnor

* nor

* orn

  

##### 1.2算数运算

* add
* sub
* (m)adc
* (m)sbc
* slt(u)
* sle(u)
* sgt(u)
* sge(u)
* max(u)
* min(u)

定点

* vsadd/sub: 需要处理溢出
* vaadd/sub: 去掉低位舍入

##### 1.3 移位

* sll
* s(n)rl
* s(n)ra

定点

* ssrl(只右移,需要处理舍入)
* ssra

##### 1.4多周期(m/d/ma)

*  div(u)
* rem(u)
* wmul
* wmulu
* wmulsu
*  乘加 vmacc vnmsac vmadd vnmsub (有widen类的)

##### 1.5 其他指令

* Integer Extension:需要纠结过不过lane
* vmerge: 

reduce: reduce 直接过运算单元, reduce 的控制外面做就好了

* sum
* max
* min
* and
* or
* xor
* vwredsumu (wide的reduce, 初始值和vd都是 2 * sew)

mask

* pop count
* find first one: 4种变体
* vid: 获取index的逻辑

permutation

* gather: 需要与maxvlen 比较的逻辑, 合并

#### 2.结构

![](./rvv-lane.drawio.svg)

* 如图, 进lane把所有数据广播给所有的执行单元, 然后根据控制逻辑决定是否需要计算(执行单元可能需要在不是自己的请求的时候把输入拉0)
* 然后根据控制从多个结果中选一个出来写到寄存器里面, 然后做一些通用的处理(widen, extend...)
* 如果不是单周期的请求result的使能由计算单元给出.

#### 3.计算单元
##### 3.1 logic

位运算计划用单bit查表的方式计算,逻辑运算有8种 and nand andn or nor orn xor xnor, 直接用这三位和两bit的输入算真值表.

逻辑运算的编码下不纠结, 先随便编. 真值表代码里算去. 

##### 3.2 arithmetic

这里面只有一个加法器, 因为需要兼容adc ma, 所以是一个三输入的加, 这样减法就是取反加1(减两是取反加2), 额外续要1bit作为carry.

##### 3.2 shift
正常的移位, 注意不同sew的移动有效位不同, sign决定填充的是0还是符号.

##### 3.3 mul
直接用华莱士树乘法, 得到最终的c&s, 最后过一次加法器, 有乘加就把加的值作为第三个输入.

##### 3.4 div

正常的除法器, 需要纠结的是init是否在里面做.

##### 3.5 popcount

算一串数里面有几个1, 先用chisel自带的, 后面再纠结咋优化.

##### 3.6 ffo

find first one, 也用通用的, 根据控制信号返回不同的结果.

##### 3.7 get index

根据lane自己的标号和控制里面的组号拼接成整体的index.

#### 4. 控制