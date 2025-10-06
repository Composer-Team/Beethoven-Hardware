#include <beethoven_hardware.h>
#include <beethoven/fpga_handle.h>
using namespace beethoven;
int main() {
  fpga_handle_t handle;
  using dtype = int;
  auto vec_len = 32;
  auto vec = handle.malloc(32 * sizeof(dtype));
  auto host = (dtype*)vec.getHostAddr();
  for(int i = 0; i < vec_len; ++i) {
    host[i] = i;
  }
  // on core 0, add 7 to 32 elements starting at 'vec'
  MyAcceleratorSystem2::my_accel(0, 7, 32, vec).get();

  for(int i = 0; i < vec_len; ++i) {
    printf("EXPECT(%d), GOT(%d)\n", i + 7, host[i]);
  } 
}
