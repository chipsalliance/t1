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

#include <stdint.h>
#include <stddef.h>

void boring_chacha20(uint8_t *out, const uint8_t *in,
		     size_t in_len, const uint8_t key[32],
		     const uint8_t nonce[12], uint32_t counter);

typedef uint8_t poly1305_state[512];

void boring_poly1305_init(poly1305_state *state,
			  const uint8_t key[32]);

void boring_poly1305_update(poly1305_state *state,
			    const uint8_t *in, size_t in_len);

void boring_poly1305_finish(poly1305_state *state,
			    uint8_t mac[16]);
