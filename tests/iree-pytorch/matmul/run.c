#include <stddef.h>

#include "iree/base/api.h"
#include "iree/hal/api.h"
#include "iree/modules/hal/module.h"
#include "iree/vm/api.h"
#include "iree/vm/bytecode/module.h"

// A function to create the HAL device from the different backend targets.
// The HAL device is returned based on the implementation, and it must be
// released by the caller.
extern iree_status_t create_sample_device(iree_allocator_t host_allocator,
    iree_hal_device_t **out_device);

// A function to load the vm bytecode module from the different backend targets.
// The bytecode module is generated for the specific backend and platform.
extern const iree_const_byte_span_t load_bytecode_module_data();

iree_status_t Run(void) {
    iree_vm_instance_t* instance = NULL;
    IREE_RETURN_IF_ERROR(iree_vm_instance_create(
        IREE_VM_TYPE_CAPACITY_DEFAULT, iree_allocator_system(), &instance));
    IREE_RETURN_IF_ERROR(iree_hal_module_register_all_types(instance));
  
    iree_hal_device_t* device = NULL;
    IREE_RETURN_IF_ERROR(create_sample_device(iree_allocator_system(), &device),
                         "create device");
    iree_vm_module_t* hal_module = NULL;
    IREE_RETURN_IF_ERROR(iree_hal_module_create(
        instance, /*device_count=*/1, &device, IREE_HAL_MODULE_FLAG_SYNCHRONOUS,
        iree_hal_module_debug_sink_stdio(stderr), iree_allocator_system(),
        &hal_module));
  
    // Load bytecode module from the embedded data.
    const iree_const_byte_span_t module_data = load_bytecode_module_data();
  
    iree_vm_module_t* bytecode_module = NULL;
    IREE_RETURN_IF_ERROR(iree_vm_bytecode_module_create(
        instance, module_data, iree_allocator_null(), iree_allocator_system(),
        &bytecode_module));
  
    // Allocate a context that will hold the module state across invocations.
    iree_vm_context_t* context = NULL;
    iree_vm_module_t* modules[] = {hal_module, bytecode_module};
    IREE_RETURN_IF_ERROR(iree_vm_context_create_with_modules(
        instance, IREE_VM_CONTEXT_FLAG_NONE, IREE_ARRAYSIZE(modules), &modules[0],
        iree_allocator_system(), &context));
    iree_vm_module_release(hal_module);
    iree_vm_module_release(bytecode_module);
  
    // Lookup the entry point function.
    // Note that we use the synchronous variant which operates on pure type/shape
    // erased buffers.
    const char kMainFunctionName[] = "module.main";
    iree_vm_function_t main_function;
    IREE_RETURN_IF_ERROR(iree_vm_context_resolve_function(
        context, iree_make_cstring_view(kMainFunctionName), &main_function));
  
    const int32_t arg0[] = {1, 1, 1, 1};
    const int32_t arg1[] = {2, 2, 2, 2};
  
    // Allocate buffers in device-local memory so that if the device has an
    // independent address space they live on the fast side of the fence.
    iree_hal_dim_t shape[] = {2, 2};
    iree_hal_buffer_view_t* arg0_buffer_view = NULL;
    iree_hal_buffer_view_t* arg1_buffer_view = NULL;
    IREE_RETURN_IF_ERROR(iree_hal_buffer_view_allocate_buffer_copy(
        device, iree_hal_device_allocator(device), IREE_ARRAYSIZE(shape), shape,
        IREE_HAL_ELEMENT_TYPE_SINT_32, IREE_HAL_ENCODING_TYPE_DENSE_ROW_MAJOR,
        (iree_hal_buffer_params_t){
            .type = IREE_HAL_MEMORY_TYPE_DEVICE_LOCAL,
            .usage = IREE_HAL_BUFFER_USAGE_DEFAULT,
        },
        iree_make_const_byte_span(arg0, sizeof(arg0)), &arg0_buffer_view));
    IREE_RETURN_IF_ERROR(iree_hal_buffer_view_allocate_buffer_copy(
        device, iree_hal_device_allocator(device), IREE_ARRAYSIZE(shape), shape,
        IREE_HAL_ELEMENT_TYPE_SINT_32, IREE_HAL_ENCODING_TYPE_DENSE_ROW_MAJOR,
        (iree_hal_buffer_params_t){
            .type = IREE_HAL_MEMORY_TYPE_DEVICE_LOCAL,
            .usage = IREE_HAL_BUFFER_USAGE_DEFAULT,
        },
        iree_make_const_byte_span(arg1, sizeof(arg1)), &arg1_buffer_view));
  
    // Setup call inputs with our buffers.
    iree_vm_list_t* inputs = NULL;
    IREE_RETURN_IF_ERROR(
        iree_vm_list_create(iree_vm_make_undefined_type_def(),
                            /*capacity=*/2, iree_allocator_system(), &inputs),
        "can't allocate input vm list");
  
    iree_vm_ref_t arg0_buffer_view_ref =
        iree_hal_buffer_view_move_ref(arg0_buffer_view);
    iree_vm_ref_t arg1_buffer_view_ref =
        iree_hal_buffer_view_move_ref(arg1_buffer_view);
    IREE_RETURN_IF_ERROR(
        iree_vm_list_push_ref_move(inputs, &arg0_buffer_view_ref));
    IREE_RETURN_IF_ERROR(
        iree_vm_list_push_ref_move(inputs, &arg1_buffer_view_ref));
  
    // Prepare outputs list to accept the results from the invocation.
    // The output vm list is allocated statically.
    iree_vm_list_t* outputs = NULL;
    IREE_RETURN_IF_ERROR(
        iree_vm_list_create(iree_vm_make_undefined_type_def(),
                            /*capacity=*/1, iree_allocator_system(), &outputs),
        "can't allocate output vm list");
  
    // Synchronously invoke the function.
    IREE_RETURN_IF_ERROR(iree_vm_invoke(
        context, main_function, IREE_VM_INVOCATION_FLAG_NONE,
        /*policy=*/NULL, inputs, outputs, iree_allocator_system()));
  
    // Get the result buffers from the invocation.
    iree_hal_buffer_view_t* ret_buffer_view =
        iree_vm_list_get_buffer_view_assign(outputs, 0);
    if (ret_buffer_view == NULL) {
      return iree_make_status(IREE_STATUS_NOT_FOUND,
                              "can't find return buffer view");
    }
  
    // Read back the results and ensure we got the right values.
    int32_t results[] = {0, 0, 0, 0};
    IREE_RETURN_IF_ERROR(iree_hal_device_transfer_d2h(
        device, iree_hal_buffer_view_buffer(ret_buffer_view), 0, results,
        sizeof(results), IREE_HAL_TRANSFER_BUFFER_FLAG_DEFAULT,
        iree_infinite_timeout()));
    for (iree_host_size_t i = 0; i < IREE_ARRAYSIZE(results); ++i) {
      if (results[i] != 4) {
        return iree_make_status(IREE_STATUS_UNKNOWN, "result mismatches");
      }
    }
  
    // Print statistics (no-op if statistics are not enabled).
    iree_hal_allocator_statistics_fprint(stdout,
                                         iree_hal_device_allocator(device));
  
    iree_vm_list_release(inputs);
    iree_vm_list_release(outputs);
    iree_hal_device_release(device);
    iree_vm_context_release(context);
    iree_vm_instance_release(instance);
    return iree_ok_status();
  }
