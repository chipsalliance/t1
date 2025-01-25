def gen_tw_for_vector_ntt(l, w_one, prime_p):
    n = pow(2, l)
    w_power_list = []
    m = 2
    while m <= n:
        w_power = 0
        w = 1
        w_power_dict = {}
        for j in range(m // 2):
            k = 0
            while k < n:
                i_u = k + j
                i_t = k + j + m //2
                k += m 
                w_power_dict[i_u] = (i_t, w_power)
            w_power += n//m
        m = 2 * m
        w_power_list.append(w_power_dict)

    # print(w_power_list)
    perm_each = { }
    for i in range(n//2):
        perm_each[i] = i
        perm_each[i+n//2] = i + n//2
    # print("(coe 0, 1), w_power, (permu 0, 1)\n")
    print(f"\nfor ntt {n}")
    layer_index = 0
    for w_power_dict in w_power_list:
        print(f"// layer #{layer_index}")
        layer_index += 1

        # sort_keys = sorted(w_power_dict.keys())
        sort_keys = w_power_dict.keys()
        index = 0
        for w_key in sort_keys:
            # print(f"({w_key}, {w_power_dict[w_key][0]}), {w_power_dict[w_key][1]}, ", end = "") 
            # print(f"({perm_each[w_key]}, {perm_each[w_power_dict[w_key][0]]})")
            current_w = pow(w_one, w_power_dict[w_key][1], prime_p) 
            print(current_w, end = ", ")
            perm_each[w_key] = index 
            perm_each[w_power_dict[w_key][0]] = index + n//2 
            index += 1

        print("\n")

if __name__ == '__main__':
    gen_tw_for_vector_ntt(6, 7311, 12289)
    gen_tw_for_vector_ntt(7, 12149, 12289)
    gen_tw_for_vector_ntt(8, 8340, 12289)
    gen_tw_for_vector_ntt(9, 3400, 12289)
    gen_tw_for_vector_ntt(10, 10302, 12289)
    gen_tw_for_vector_ntt(12, 1331, 12289)

