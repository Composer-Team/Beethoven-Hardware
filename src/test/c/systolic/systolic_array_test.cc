#include <beethoven/fpga_handle.h>
#include <beethoven_hardware.h>
#include <random>
using namespace beethoven;

// convert from sign-magnitude fixed-point to floating point
double fixp_to_fp(int16_t a) {
  auto f = double(a & 0x7FFF) / (1 << 8);
  return (a & 0x8000) ? -f : f;
}

// inverse of the previous function
double fp_to_fixp(double a) {
  int16_t f = abs(a) * (1 << 8);
  if (a < 0) {
    f |= 0x8000;
  }
  return f;
}

int main() {
  fpga_handle_t handle;
  // 8 x 8 systolic array
  // problem size
  int inner_dimension = 2;

  // allocate memory for the accelerator
  auto activations = handle.malloc(sizeof(int16_t) * 8 * inner_dimension);
  auto weights = handle.malloc(sizeof(int16_t) * 8 * inner_dimension);
  auto outputs = handle.malloc(sizeof(int16_t) * 8 * 8);

  // random number generation
  std::random_device rd;
  std::uniform_real_distribution<double> dist(-2, 2);
  std::default_random_engine eng(rd());

  // get host pointers out of the memory handles
  int16_t *host_act = (int16_t *)activations.getHostAddr(),
          *host_wgt = (int16_t *)weights.getHostAddr(),
          *host_out = (int16_t *)outputs.getHostAddr();

  // allocate arrays for golden model
  float *gold_act = new float[inner_dimension * 8];
  float *gold_wgt = new float[inner_dimension * 8];
  float *gold_out = new float[8 * 8];

  // sanity checks for our fixed-point <-> floating-point conversions
  assert(fixp_to_fp(fp_to_fixp(3)) == 3);
  assert(fixp_to_fp(fp_to_fixp(0.5)) == 0.5);
  assert(fixp_to_fp(fp_to_fixp(-8)) == -8);

  // initialize arrays
  for (int i = 0; i < inner_dimension; ++i) {
    for (int j = 0; j < 8; ++j) {
      host_act[i * 8 + j] = fp_to_fixp(gold_act[i * 8 + j] = dist(eng));
      host_wgt[i * 8 + j] = fp_to_fixp(gold_wgt[i * 8 + j] = dist(eng));
    }
  }

  // perform golden-model matrix multiply
  memset(gold_out, 0, sizeof(float) * 8 * 8);
  for (int i = 0; i < 8; ++i) {
    for (int j = 0; j < 8; ++j) {
      for (int k = 0; k < inner_dimension; ++k) {
        gold_out[i * 8 + j] += gold_act[k * 8 + i] * gold_wgt[k * 8 + j];
      }
    }
  }

  // move the data over to the accelerator
  handle.copy_to_fpga(activations);
  handle.copy_to_fpga(weights);

  // execute the command on the accelerator
  SystolicArrayCore::matmul(0, activations.getFpgaAddr(), inner_dimension,
                            outputs.getFpgaAddr(), weights.getFpgaAddr())
      .get();
  
  // move the data back from the accelerator
  handle.copy_from_fpga(outputs);

  // print out the outpus from the accelerator
  for (int i = 0; i < 8; ++i) {
    for (int j = 0; j < 8; ++j) {
      printf("%0.2f ", fixp_to_fp(host_out[i * 8 + j]));
    }
    printf("\n");
  }

  // print out the golden model (transpose)
  // because the accelerator outputs the matrix transpose (useful for re-using the
  // output in subsequent matrix multiplies), we output the transpose here for
  // comparison
  printf("\nGOLDEN:\n");
  for (int i = 0; i < 8; ++i) {
    for (int j = 0; j < 8; ++j) {
      // print transpose
      printf("%0.2f ", gold_out[j * 8 + i]);
    }
    printf("\n");
  }
}
