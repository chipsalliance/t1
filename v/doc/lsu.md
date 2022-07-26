## LSU

#### 1. 记录

* unit-stride 是连续的访存,也就是间隔是eew
* constant-strided 间隔是记录在 rs2 里面的定值.
* indexed 是直接基于基地址的offset存在vs2里面
* offset 在加base之前做0扩展,如果超过了就直接切去非有效位.
* unit-stride 和 stride都是无序的, index两种都有.
* index的eew作用于offset, data依然沿用原来的, 剩下的两种eew是编码数据的(待确认)
* constant-strided 不用x0作为rs2,但是x[rs2] = 0的时候虽然地址不变,但是每个有效的element都需要有访存操作.
* ls whole register 有自己的evl = nf * vlen / eew, evl 会代替vl作为控制.

