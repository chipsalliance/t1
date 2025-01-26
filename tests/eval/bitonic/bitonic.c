// n must be no more than VLEN * 8 / 32bit
extern void bitonic(int *in, int n);

#define SIZE 32

int in_[SIZE] = {
  31, 24, 5, 13, 14, 7, 20, 16, 21, 18, 4, 30, 9, 10, 27, 25, 
  19, 28, 29, 15, 8, 22, 3, 17, 11, 12, 23, 2, 1, 6, 32, 26
};

void test() {
  bitonic(in_, SIZE);
}
