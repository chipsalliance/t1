// A function to create the HAL device from the different backend targets.
// The HAL device is returned based on the implementation, and it must be
// released by the caller.
iree_status_t create_sample_device(iree_allocator_t host_allocator,
    iree_hal_device_t** out_device);

// A function to load the vm bytecode module from the different backend targets.
// The bytecode module is generated for the specific backend and platform.
const iree_const_byte_span_t load_bytecode_module_data();
