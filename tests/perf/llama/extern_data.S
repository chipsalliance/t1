  .section .rodata
  .global checkpoint_data
  .type   checkpoint_data, @object
  .align  4
checkpoint_data:
  .incbin "{{checkpoint_bin}}"  # will be replaced on nix build
checkpoint_end:
  .global checkpoint_size
  .type   checkpoint_size, @object
  .align  4
checkpoint_size:
  .int    checkpoint_end - checkpoint_data

  .section .rodata
  .global tokenizer_data
  .type   tokenizer_data, @object
  .align  4
tokenizer_data:
  .incbin "{{tokenizer_bin}}"  # will be replaced on nix build
tokenizer_end:
  .global tokenizer_size
  .type   tokenizer_size, @object
  .align  4
tokenizer_size:
  .int    tokenizer_end - tokenizer_data
