#include <beethoven/fpga_handle.h>
#include <beethoven_hardware.h>

using namespace beethoven;


// build with 
// g++ -rpath /usr/local/lib -I$BEETHOVEN_PATH/build -lbeethoven --std=c++17 test.cc $BEETHOVEN_PATH/build/beethoven_hardware.cc
// discard rpath for non-mac
int main() {
    fpga_handle_t handle;
    int vec_len = 32;
    auto vec_a =   handle.malloc(vec_len * sizeof(int));
    auto vec_b =   handle.malloc(vec_len * sizeof(int));
    auto vec_out = handle.malloc(vec_len * sizeof(int));
    VecAddAccel::dispatch_vector_add(0, 0, vec_a, vec_b, vec_out, vec_len).get();
    VecAddAccel::dispatch_vector_add(0, 1, vec_a, vec_b, vec_out, vec_len).get();


}