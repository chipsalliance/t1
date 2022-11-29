import copy
import json
import math

fun3 = {
    "line0": {
        "V": "000",
        "X": "100",
        "I": "011",
    },
    "line1": {
        "V": "010",
        "X": "110",
    }
}

extend_encode = {
    "vs1": {
        "VWXUNARY0": {
            "00000": "vmv.x.s",
            "10000": "vpopc",
            "10001": "vfirst",
        },
        "VXUNARY0": {
            "00010": "vzext.vf8",
            "00011": "vsext.vf8",
            "00100": "vzext.vf4",
            "00101": "vsext.vf4",
            "00110": "vzext.vf2",
            "00111": "vsext.vf2",
        },
        "VMUNARY0": {
            "00001": "vmsbf",
            "00010": "vmsof",
            "00011": "vmsif",
            "10000": "viota",
            "10001": "vid",
        },
    },
    "vs2": {
        "VRXUNARY0": {
            "00000": "vmv.s.x"
        }
    }
}
sp_inst = ["100111", "", "", "I", "vmv<nr>r"]
opList = ["and", "or", "xor", "xnor", "add", "sub", "adc", "sbc", "slt", "sle", "sgt", "sge", "seq", "sne", "max",
          "min", "sl", "sr", "mul", "div",
          "rem", "ma", "ms", "slide", "rgather", "merge", "mv", "clip", "compress", "sum"]
mul_list = ["mul", "ma", "ms"]
div_list = ["div", "rem"]
add_list = ["add", "sub", "slt", "sle", "sgt", "sge", "max", "min", "seq", "sne", "adc", "sbc", "sum"]
logic_list = ["and", "or", "xor", "xnor"]
shift_list = ["sr", "sl"]
other_list = ["slide", "rgather", "merge", "mv", "clip", "compress"]
ffo_list = ["vfirst", "vmsbf", "vmsof", "vmsif"]


def res_gen():
    with open("inst.txt", "r") as fd:
        res = {
            "line0": [sp_inst],
            "line1": []
        }
        for i in fd.readlines():
            if "|===" in i:
                break
            sp = [s.strip(" ").strip("\n") for s in i.split("|")]
            line0 = sp[1:6]
            line1 = sp[6:10]
            if line0[-1] == "":
                ...
            elif line0[0] == "":
                print("code miss: ", sp)
            else:
                res["line0"].append(line0)

            if line1[-1] == "":
                ...
            elif line1[0] == "":
                print("code miss: ", line1)
            else:
                res["line1"].append(line1)
    return res


def dump_inst():
    with open("inst_list.json", "w") as fd:
        json.dump(res_gen(), fd, indent=2)


def load_res():
    with open("inst_list.json", "r") as fd:
        res = json.load(fd)
        return res


def b2s(b):
    return "1" if b else "0"


def inst_parse():
    fd = open("decode_res.txt", 'w')
    res = load_res()
    count = 0
    end_count = 0
    for fun_3 in ["line0", "line1"]:
        for i in res[fun_3]:
            count += 1
            c_op = False
            j_list = []
            for j in opList:
                if j in i[-1]:
                    j_list.append(j)
                    c_op = True
            if not c_op:
                v_src = "V" in i[1:3]
                fun_3_st = fun3["line1"]["V"] if v_src else fun3["line1"]["X"]
                v_type = b2s(v_src)
                x_type = b2s(not v_src)
                placeholder = i[-1]
                for k, v in extend_encode.items():
                    if v.get(placeholder):
                        uop_head = "1"
                        for rsx, inst_name in v.get(placeholder).items():
                            res_uop = "???"
                            inst_st_p = "BitPat(\"b%s??????%s%s\")" if k == "vs1" else "BitPat(\"b%s?%s?????%s\")"
                            inst_st = inst_st_p % (i[0], rsx, fun_3_st)
                            t_rd = placeholder == "VWXUNARY0"
                            v_extend = placeholder == "VXUNARY0"
                            mv = inst_name.startswith("vmv")
                            ffo = inst_name in ffo_list
                            pop_c = inst_name == "vpopc"
                            viota = inst_name == "viota"
                            vid = inst_name == "vid"
                            control_list = [t_rd, v_extend, mv, ffo, pop_c, viota, vid, v_src]
                            if v_extend:
                                res_uop = b2s(inst_name.startswith("vs")) + "{0:02b}".format(
                                    int(math.log2(int(inst_name[-1]))))
                            if ffo:
                                res_uop = "?" + "{0:02b}".format(ffo_list.index(inst_name))
                            con_st = "".join([b2s(ll) for ll in control_list])
                            res_st = "BitPat(\"b000001????%s?%s\")" % (con_st + v_type + x_type, uop_head + res_uop)
                            w_st = "\t%s ->\t%s,\t//%s\n" % (inst_st, res_st, inst_name)
                            fd.write(w_st)

            else:
                inst_str = copy.deepcopy(i[-1])
                op_st = ""
                # 确认opcode
                if len(j_list) == 1:
                    op_st = j_list[0]
                else:
                    #  不是乘加/减
                    if "ma" in j_list:
                        if "add" in j_list:
                            j_list.remove("add")
                        else:
                            j_list.remove("ma")
                    if "ms" in j_list:
                        if "sub" in j_list:
                            j_list.remove("sub")
                        else:
                            j_list.remove("ms")
                    # 找最长的
                    max_len = 0
                    max_op = ""
                    for op_ in j_list:
                        if len(op_) > max_len:
                            max_len = len(op_)
                            max_op = op_
                    if all(max_op.__contains__(ss) for ss in j_list):
                        op_st += max_op
                        j_list = [max_op]

                    if "merge" in j_list:
                        j_list.remove("mv")

                    if len(j_list) != 1:
                        print(j_list, "???")
                    else:
                        op_st += j_list[0]
                remainder = str(inst_str).replace(j_list[0], "")[1:]
                opcode = j_list[0]
                shift = any(uop == j_list[0] for uop in ["sr", "sl"])
                ma_u = any(uop == j_list[0] for uop in ["ma", "ms"])
                slide = j_list[0] == "slide"
                mul = j_list[0] == "mul"
                mul_unit = any(uop == j_list[0] for uop in mul_list)
                div_unit = any(uop == j_list[0] for uop in div_list)
                add_unit = any(uop == j_list[0] for uop in add_list)
                logic_unit = any(uop == j_list[0] for uop in logic_list)
                shift_unit = any(uop == j_list[0] for uop in shift_list)
                other_unit = any(uop == j_list[0] for uop in other_list)
                unit_list = [logic_unit, add_unit, shift_unit, mul_unit, div_unit, other_unit]
                # 处理decode的项
                # 特殊的有eew的指令
                eew16 = remainder.endswith("16")
                remainder = remainder.replace("ei16", "")
                # 特殊的有nr的指令
                nr = "<nr>r" in remainder
                remainder = remainder.replace("<nr>r", "")
                remainder = remainder.replace("/vmv", "")

                # reduce的
                red = "red" in remainder
                remainder = remainder.replace("red", "")

                # mask_b有多种:
                # 1. 代表元操作数以mask的形式的logic运算
                # 2. 代表adc的结果只需要进位
                # 3. 代表比较的结果以mask的形式写进寄存器
                mask_b = remainder.startswith("m")
                nx, m_not = False, False
                if mask_b:
                    remainder = remainder[1:]
                    nx = remainder == "n"
                    m_not = remainder == "not"
                    remainder = remainder.replace("not", "").replace("n", "")

                # 区分移位的算数与逻辑
                logic, arithmetic = False, False
                if shift:
                    logic = remainder.endswith("l")
                    arithmetic = remainder.endswith("a")
                    remainder = remainder.replace("a", "").replace("l", "")

                # 会有颠倒操作数的
                reverse = remainder == "r"
                if reverse:
                    remainder = remainder.replace("r", "")

                # 这里需要先把n剪出来, 要不然会和narrow搞混
                negative = False
                # vd 是不是作为乘加里的加数
                as_addend = False
                if ma_u:
                    negative = remainder.startswith("n")
                    if negative:
                        remainder = remainder[1:]
                    for ma_tail in ["dd", "cc", "ac", "ub"]:
                        if ma_tail in remainder:
                            as_addend = ma_tail.endswith("c")
                            remainder = remainder.replace(ma_tail, "")

                #  区分 n w s a
                narrow = remainder.startswith("n")
                widen = remainder.startswith("w")
                saturate = remainder.startswith("s")
                average = remainder.startswith("a")
                if any([narrow, widen, saturate, average]):
                    remainder = remainder[1:]

                first_widen = remainder.endswith(".w")
                if first_widen:
                    remainder = remainder.replace(".w", "")

                # 处理slide
                slide_up = False
                slide_1 = False
                if slide:
                    if "1" in remainder:
                        slide_1 = True
                    if "up" in remainder:
                        slide_up = True
                    remainder = ""

                # 处理mul
                high = False
                if mul:
                    # 乘法结果是不是取高位
                    high = remainder.startswith("h")
                    if high:
                        remainder = remainder[1:]
                # 处理符号
                first_unsigned = remainder in ["u", "us"]
                second_unsigned = remainder in ["u", "su"]
                if remainder != "":
                    if remainder not in ["u", "us", "su"]:
                        print("remainder:", remainder)
                    remainder = ""

                # 写decode的表

                shift_type = [logic, arithmetic]
                slide_type = [slide_up, slide_1]
                mul_type = [high, negative, as_addend]

                control_list = [first_widen, eew16, nr, red, mask_b, reverse, narrow, widen, saturate,
                                average, first_unsigned, second_unsigned]
                uop_len = 4
                end_index = 4 if fun_3 == "line0" else 3
                for inst_type in i[1:end_index]:
                    if inst_type == "":
                        continue
                    inst_st = "BitPat(\"b%s%s%s\")" % (i[0], "?" * 11, fun3[fun_3][inst_type])
                    # 处理 uop
                    uop = ""
                    if mul_unit:
                        n = mul_list.index(opcode)
                        if high:
                            n = 3
                        uop = b2s(negative) + b2s(as_addend) + "{0:02b}".format(n)
                    if div_unit:
                        uop = "?" * 3 + "{0:01b}".format(div_list.index(opcode))
                    if add_unit:
                        n = add_list.index(opcode)
                        if opcode == "sum":
                            n = 0
                        uop = "{0:04b}".format(n)
                    if logic_unit:
                        n = logic_list.index(opcode)
                        if opcode == "xnor":
                            n = 2
                            m_not = True
                        uop = b2s(nx) + b2s(m_not) + "{0:02b}".format(n)
                    if shift_unit:
                        n = shift_list.index(opcode)
                        m = shift_type.index(True)
                        uop = "?" * 2 + "{0:01b}".format(m) + "{0:01b}".format(n)
                    if other_unit:
                        # 这里的1需要用来编特殊指令
                        uop = "0" + "{0:03b}".format(other_list.index(opcode))

                    unit_list_st = "".join([b2s(k) for k in unit_list])
                    control_st = "".join([b2s(k) for k in control_list])
                    v_type = b2s(inst_type == "V")
                    x_type = b2s(inst_type == "X")
                    i_type = b2s(inst_type == "I")
                    res_st = "BitPat(\"b%s%s%s\")" % (unit_list_st, control_st + v_type + x_type + i_type, uop)
                    w_st = "\t%s ->\t%s,\t//%s.%s\n" % (inst_st, res_st, i[-1], inst_type.lower())
                    fd.write(w_st)

                end_count += 1
    print(count, end_count)


dump_inst()
inst_parse()
