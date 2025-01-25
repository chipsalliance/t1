import random

def main():
    vlen = 4096
    l = 12
    n = 1 << l
    # assert n <= vlen // 4
    p = 12289  # p is prime and n | p - 1
    g = 11  # primitive root of p
    assert (p - 1) % n == 0
    w = (g ** ((p - 1) // n)) % p  # now w^n == 1 mod p by Fermat's little theorem
    print(w)

    twindle_list = []
    for _ in range(l):
        twindle_list.append(w)
        w = (w * w) % p
    print(twindle_list)

    a = [random.randrange(p) for _ in range(n)]
    print(a)

if __name__ == '__main__':
    main()
