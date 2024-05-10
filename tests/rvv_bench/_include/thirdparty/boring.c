/* Copyright (c) 2014, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

// Adapted from the public domain, estream code by D. Bernstein.

#include "boring.h"


extern void *memcpy(void *restrict dest, void const *restrict src, size_t n);

#define U8TO32_LITTLE(p)                              \
  (((uint32_t)((p)[0])) | ((uint32_t)((p)[1]) << 8) | \
   ((uint32_t)((p)[2]) << 16) | ((uint32_t)((p)[3]) << 24))

// sigma contains the ChaCha constants, which happen to be an ASCII string.
static const uint8_t sigma[16] = { 'e', 'x', 'p', 'a', 'n', 'd', ' ', '3',
                                   '2', '-', 'b', 'y', 't', 'e', ' ', 'k' };

#define ROTATE(v, n) (((v) << (n)) | ((v) >> (32 - (n))))

// QUARTERROUND updates a, b, c, d with a ChaCha "quarter" round.
#define QUARTERROUND(a, b, c, d)                \
  x[a] += x[b]; x[d] = ROTATE(x[d] ^ x[a], 16); \
  x[c] += x[d]; x[b] = ROTATE(x[b] ^ x[c], 12); \
  x[a] += x[b]; x[d] = ROTATE(x[d] ^ x[a],  8); \
  x[c] += x[d]; x[b] = ROTATE(x[b] ^ x[c],  7);

#define U32TO8_LITTLE(p, v)    \
  {                            \
    (p)[0] = (v >> 0) & 0xff;  \
    (p)[1] = (v >> 8) & 0xff;  \
    (p)[2] = (v >> 16) & 0xff; \
    (p)[3] = (v >> 24) & 0xff; \
  }

// chacha_core performs 20 rounds of ChaCha on the input words in
// |input| and writes the 64 output bytes to |output|.
static void chacha_core(uint8_t output[64], const uint32_t input[16]) {
  uint32_t x[16];
  int i;

  memcpy(x, input, sizeof(uint32_t) * 16);
  for (i = 20; i > 0; i -= 2) {
    QUARTERROUND(0, 4, 8, 12)
    QUARTERROUND(1, 5, 9, 13)
    QUARTERROUND(2, 6, 10, 14)
    QUARTERROUND(3, 7, 11, 15)
    QUARTERROUND(0, 5, 10, 15)
    QUARTERROUND(1, 6, 11, 12)
    QUARTERROUND(2, 7, 8, 13)
    QUARTERROUND(3, 4, 9, 14)
  }

  for (i = 0; i < 16; ++i) {
    x[i] += input[i];
  }
  for (i = 0; i < 16; ++i) {
    U32TO8_LITTLE(output + 4 * i, x[i]);
  }
}

void boring_chacha20(uint8_t *out, const uint8_t *in, size_t in_len,
		      const uint8_t key[32], const uint8_t nonce[12],
		      uint32_t counter) {

  uint32_t input[16];
  uint8_t buf[64];
  size_t todo, i;

  input[0] = U8TO32_LITTLE(sigma + 0);
  input[1] = U8TO32_LITTLE(sigma + 4);
  input[2] = U8TO32_LITTLE(sigma + 8);
  input[3] = U8TO32_LITTLE(sigma + 12);

  input[4] = U8TO32_LITTLE(key + 0);
  input[5] = U8TO32_LITTLE(key + 4);
  input[6] = U8TO32_LITTLE(key + 8);
  input[7] = U8TO32_LITTLE(key + 12);

  input[8] = U8TO32_LITTLE(key + 16);
  input[9] = U8TO32_LITTLE(key + 20);
  input[10] = U8TO32_LITTLE(key + 24);
  input[11] = U8TO32_LITTLE(key + 28);

  input[12] = counter;
  input[13] = U8TO32_LITTLE(nonce + 0);
  input[14] = U8TO32_LITTLE(nonce + 4);
  input[15] = U8TO32_LITTLE(nonce + 8);

  while (in_len > 0) {
    todo = sizeof(buf);
    if (in_len < todo) {
      todo = in_len;
    }

    chacha_core(buf, input);
    for (i = 0; i < todo; i++) {
      out[i] = in[i] ^ buf[i];
    }

    out += todo;
    in += todo;
    in_len -= todo;

    input[12]++;
  }
}

///// poly1305

static uint32_t U8TO32_LE(const uint8_t *m) {
  uint32_t r;
  memcpy(&r, m, sizeof(r));
  return r;
}

static void U32TO8_LE(uint8_t *m, uint32_t v) {
  memcpy(m, &v, sizeof(v));
}


static uint64_t mul32x32_64(uint32_t a, uint32_t b) { return (uint64_t)a * b; }

struct poly1305_state_st {
  uint32_t r0, r1, r2, r3, r4;
  uint32_t s1, s2, s3, s4;
  uint32_t h0, h1, h2, h3, h4;
  uint8_t buf[16];
  unsigned int buf_used;
  uint8_t key[16];
};

static inline struct poly1305_state_st *poly1305_aligned_state(
							       poly1305_state *state) {
  return (struct poly1305_state_st *)(((uintptr_t)state + 63) & ~63);
}

static void poly1305_update(struct poly1305_state_st *state, const uint8_t *in,
			    size_t len) {
  uint32_t t0, t1, t2, t3;
  uint64_t t[5];
  uint32_t b;
  uint64_t c;
  size_t j;
  uint8_t mp[16];

  if (len < 16) {
    goto poly1305_donna_atmost15bytes;
  }

 poly1305_donna_16bytes:
  t0 = U8TO32_LE(in);
  t1 = U8TO32_LE(in + 4);
  t2 = U8TO32_LE(in + 8);
  t3 = U8TO32_LE(in + 12);

  in += 16;
  len -= 16;

  state->h0 += t0 & 0x3ffffff;
  state->h1 += ((((uint64_t)t1 << 32) | t0) >> 26) & 0x3ffffff;
  state->h2 += ((((uint64_t)t2 << 32) | t1) >> 20) & 0x3ffffff;
  state->h3 += ((((uint64_t)t3 << 32) | t2) >> 14) & 0x3ffffff;
  state->h4 += (t3 >> 8) | (1 << 24);

 poly1305_donna_mul:
  t[0] = mul32x32_64(state->h0, state->r0) + mul32x32_64(state->h1, state->s4) +
    mul32x32_64(state->h2, state->s3) + mul32x32_64(state->h3, state->s2) +
    mul32x32_64(state->h4, state->s1);
  t[1] = mul32x32_64(state->h0, state->r1) + mul32x32_64(state->h1, state->r0) +
    mul32x32_64(state->h2, state->s4) + mul32x32_64(state->h3, state->s3) +
    mul32x32_64(state->h4, state->s2);
  t[2] = mul32x32_64(state->h0, state->r2) + mul32x32_64(state->h1, state->r1) +
    mul32x32_64(state->h2, state->r0) + mul32x32_64(state->h3, state->s4) +
    mul32x32_64(state->h4, state->s3);
  t[3] = mul32x32_64(state->h0, state->r3) + mul32x32_64(state->h1, state->r2) +
    mul32x32_64(state->h2, state->r1) + mul32x32_64(state->h3, state->r0) +
    mul32x32_64(state->h4, state->s4);
  t[4] = mul32x32_64(state->h0, state->r4) + mul32x32_64(state->h1, state->r3) +
    mul32x32_64(state->h2, state->r2) + mul32x32_64(state->h3, state->r1) +
    mul32x32_64(state->h4, state->r0);

  state->h0 = (uint32_t)t[0] & 0x3ffffff;
  c = (t[0] >> 26);
  t[1] += c;
  state->h1 = (uint32_t)t[1] & 0x3ffffff;
  b = (uint32_t)(t[1] >> 26);
  t[2] += b;
  state->h2 = (uint32_t)t[2] & 0x3ffffff;
  b = (uint32_t)(t[2] >> 26);
  t[3] += b;
  state->h3 = (uint32_t)t[3] & 0x3ffffff;
  b = (uint32_t)(t[3] >> 26);
  t[4] += b;
  state->h4 = (uint32_t)t[4] & 0x3ffffff;
  b = (uint32_t)(t[4] >> 26);
  state->h0 += b * 5;

  if (len >= 16) {
    goto poly1305_donna_16bytes;
  }

  // final bytes
 poly1305_donna_atmost15bytes:
  if (!len) {
    return;
  }

  for (j = 0; j < len; j++) {
    mp[j] = in[j];
  }
  mp[j++] = 1;
  for (; j < 16; j++) {
    mp[j] = 0;
  }
  len = 0;

  t0 = U8TO32_LE(mp + 0);
  t1 = U8TO32_LE(mp + 4);
  t2 = U8TO32_LE(mp + 8);
  t3 = U8TO32_LE(mp + 12);

  state->h0 += t0 & 0x3ffffff;
  state->h1 += ((((uint64_t)t1 << 32) | t0) >> 26) & 0x3ffffff;
  state->h2 += ((((uint64_t)t2 << 32) | t1) >> 20) & 0x3ffffff;
  state->h3 += ((((uint64_t)t3 << 32) | t2) >> 14) & 0x3ffffff;
  state->h4 += (t3 >> 8);

  goto poly1305_donna_mul;
}

void boring_poly1305_init(poly1305_state *statep, const uint8_t key[32]) {
  struct poly1305_state_st *state = poly1305_aligned_state(statep);
  uint32_t t0, t1, t2, t3;

  t0 = U8TO32_LE(key + 0);
  t1 = U8TO32_LE(key + 4);
  t2 = U8TO32_LE(key + 8);
  t3 = U8TO32_LE(key + 12);

  // precompute multipliers
  state->r0 = t0 & 0x3ffffff;
  t0 >>= 26;
  t0 |= t1 << 6;
  state->r1 = t0 & 0x3ffff03;
  t1 >>= 20;
  t1 |= t2 << 12;
  state->r2 = t1 & 0x3ffc0ff;
  t2 >>= 14;
  t2 |= t3 << 18;
  state->r3 = t2 & 0x3f03fff;
  t3 >>= 8;
  state->r4 = t3 & 0x00fffff;

  state->s1 = state->r1 * 5;
  state->s2 = state->r2 * 5;
  state->s3 = state->r3 * 5;
  state->s4 = state->r4 * 5;

  // init state
  state->h0 = 0;
  state->h1 = 0;
  state->h2 = 0;
  state->h3 = 0;
  state->h4 = 0;

  state->buf_used = 0;
  memcpy(state->key, key + 16, sizeof(state->key));
}

void boring_poly1305_update(poly1305_state *statep, const uint8_t *in,
			    size_t in_len) {
  unsigned int i;
  struct poly1305_state_st *state = poly1305_aligned_state(statep);

  if (state->buf_used) {
    unsigned todo = 16 - state->buf_used;
    if (todo > in_len) {
      todo = (unsigned)in_len;
    }
    for (i = 0; i < todo; i++) {
      state->buf[state->buf_used + i] = in[i];
    }
    state->buf_used += todo;
    in_len -= todo;
    in += todo;

    if (state->buf_used == 16) {
      poly1305_update(state, state->buf, 16);
      state->buf_used = 0;
    }
  }

  if (in_len >= 16) {
    size_t todo = in_len & ~0xf;
    poly1305_update(state, in, todo);
    in += todo;
    in_len &= 0xf;
  }

  if (in_len) {
    for (i = 0; i < in_len; i++) {
      state->buf[i] = in[i];
    }
    state->buf_used = (unsigned)in_len;
  }
}

void boring_poly1305_finish(poly1305_state *statep, uint8_t mac[16]) {
  struct poly1305_state_st *state = poly1305_aligned_state(statep);
  uint64_t f0, f1, f2, f3;
  uint32_t g0, g1, g2, g3, g4;
  uint32_t b, nb;

  if (state->buf_used) {
    poly1305_update(state, state->buf, state->buf_used);
  }

  b = state->h0 >> 26;
  state->h0 = state->h0 & 0x3ffffff;
  state->h1 += b;
  b = state->h1 >> 26;
  state->h1 = state->h1 & 0x3ffffff;
  state->h2 += b;
  b = state->h2 >> 26;
  state->h2 = state->h2 & 0x3ffffff;
  state->h3 += b;
  b = state->h3 >> 26;
  state->h3 = state->h3 & 0x3ffffff;
  state->h4 += b;
  b = state->h4 >> 26;
  state->h4 = state->h4 & 0x3ffffff;
  state->h0 += b * 5;

  g0 = state->h0 + 5;
  b = g0 >> 26;
  g0 &= 0x3ffffff;
  g1 = state->h1 + b;
  b = g1 >> 26;
  g1 &= 0x3ffffff;
  g2 = state->h2 + b;
  b = g2 >> 26;
  g2 &= 0x3ffffff;
  g3 = state->h3 + b;
  b = g3 >> 26;
  g3 &= 0x3ffffff;
  g4 = state->h4 + b - (1 << 26);

  b = (g4 >> 31) - 1;
  nb = ~b;
  state->h0 = (state->h0 & nb) | (g0 & b);
  state->h1 = (state->h1 & nb) | (g1 & b);
  state->h2 = (state->h2 & nb) | (g2 & b);
  state->h3 = (state->h3 & nb) | (g3 & b);
  state->h4 = (state->h4 & nb) | (g4 & b);

  f0 = ((state->h0) | (state->h1 << 26)) + (uint64_t)U8TO32_LE(&state->key[0]);
  f1 = ((state->h1 >> 6) | (state->h2 << 20)) +
    (uint64_t)U8TO32_LE(&state->key[4]);
  f2 = ((state->h2 >> 12) | (state->h3 << 14)) +
    (uint64_t)U8TO32_LE(&state->key[8]);
  f3 = ((state->h3 >> 18) | (state->h4 << 8)) +
    (uint64_t)U8TO32_LE(&state->key[12]);

  U32TO8_LE(&mac[0], f0);
  f1 += (f0 >> 32);
  U32TO8_LE(&mac[4], f1);
  f2 += (f1 >> 32);
  U32TO8_LE(&mac[8], f2);
  f3 += (f2 >> 32);
  U32TO8_LE(&mac[12], f3);
}
