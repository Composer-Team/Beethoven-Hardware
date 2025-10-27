#include <beethoven/fpga_handle.h>
#include <beethoven_hardware.h>
#include <random>
using namespace beethoven;

// convert from sign-magnitude fixed-point to floating point
double fixp_to_fp(int16_t a) {
  auto f = double(a & 0x7FFF) / (1 << FRAC_BITS);
  return (a & 0x8000) ? -f : f;
}

// inverse of the previous function
double fp_to_fixp(double a) {
  int16_t f = abs(a) * (1 << FRAC_BITS);
  if (a < 0) {
    f |= 0x8000;
  }
  return f;
}

int main() {
  fpga_handle_t handle;
  int inner_dimension = 1;

  // this testbench relies on uint16_t...
  assert(DATA_WIDTH_BYTES == 2);

  // allocate memory for the accelerator
  auto activations =
      handle.malloc(sizeof(int16_t) * SYSTOLIC_ARRAY_DIM * inner_dimension);
  auto weights =
      handle.malloc(sizeof(int16_t) * SYSTOLIC_ARRAY_DIM * inner_dimension);
  auto outputs =
      handle.malloc(sizeof(int16_t) * SYSTOLIC_ARRAY_DIM * SYSTOLIC_ARRAY_DIM);

  // random number generation
  std::random_device rd;
  std::uniform_real_distribution<double> dist(-2, 2);
  std::default_random_engine eng(rd());

  // get host pointers out of the memory handles
  int16_t *host_act = (int16_t *)activations.getHostAddr(),
          *host_wgt = (int16_t *)weights.getHostAddr(),
          *host_out = (int16_t *)outputs.getHostAddr();

  // allocate arrays for golden model
  float *gold_act = new float[inner_dimension * SYSTOLIC_ARRAY_DIM];
  float *gold_wgt = new float[inner_dimension * SYSTOLIC_ARRAY_DIM];
  float *gold_out = new float[SYSTOLIC_ARRAY_DIM * SYSTOLIC_ARRAY_DIM];

  // sanity checks for our fixed-point <-> floating-point conversions
  assert(fixp_to_fp(fp_to_fixp(3)) == 3);
  assert(fixp_to_fp(fp_to_fixp(0.5)) == 0.5);
  assert(fixp_to_fp(fp_to_fixp(-8)) == -8);

  // initialize arrays like usual
  // MINUTIAE: You _might_ notice that host_act is **INCORRECTLY** indexed.
  // Since the inputs are streamed sideways from the array (column-major), it
  // should indexed by (i + SYSTOLIC_ARRAY_DIM * j). Alternatively we could
  // store in the typical (row-major) fashion and transpose the matrix in
  // memory. This would make this explanatory example more complicated than it
  // needs to be so we're transposing in-place for the C++ golden-model.
  for (int i = 0; i < inner_dimension; ++i) {
    for (int j = 0; j < SYSTOLIC_ARRAY_DIM; ++j) {
      host_act[i * SYSTOLIC_ARRAY_DIM + j] =
          fp_to_fixp(gold_act[i * SYSTOLIC_ARRAY_DIM + j] = dist(eng));
      host_wgt[i * SYSTOLIC_ARRAY_DIM + j] =
          fp_to_fixp(gold_wgt[i * SYSTOLIC_ARRAY_DIM + j] = dist(eng));
    }
  }

  // perform golden-model matrix multiply
  memset(gold_out, 0, sizeof(float) * SYSTOLIC_ARRAY_DIM * SYSTOLIC_ARRAY_DIM);
  for (int i = 0; i < SYSTOLIC_ARRAY_DIM; ++i) {
    for (int j = 0; j < SYSTOLIC_ARRAY_DIM; ++j) {
      for (int k = 0; k < inner_dimension; ++k) {
        gold_out[i * SYSTOLIC_ARRAY_DIM + j] +=
            gold_act[k * SYSTOLIC_ARRAY_DIM + i] *
            gold_wgt[k * SYSTOLIC_ARRAY_DIM + j];
      }
    }
  }

  // move the data over to the accelerator - in simulation/embedded platforms,
  // this is a NOOP, for PCIE-mounted FPGAs, this triggers DMA
  handle.copy_to_fpga(activations);
  handle.copy_to_fpga(weights);

  // execute the command on the accelerator
  SystolicArrayCore::matmul(0, activations.getFpgaAddr(), inner_dimension,
                            outputs.getFpgaAddr(), weights.getFpgaAddr())
      .get();

  // move the data back from the accelerator
  handle.copy_from_fpga(outputs);

  // print out the outputs from the accelerator
  for (int i = 0; i < SYSTOLIC_ARRAY_DIM; ++i) {
    for (int j = 0; j < SYSTOLIC_ARRAY_DIM; ++j) {
      printf("%0.2f ", fixp_to_fp(host_out[i * SYSTOLIC_ARRAY_DIM + j]));
    }
    printf("\n");
  }

  // print out the golden model (transpose)
  // because the accelerator outputs the matrix transpose (useful for re-using
  // the output in subsequent matrix multiplies), we output the transpose here
  // for comparison
  printf("\nGOLDEN:\n");
  for (int i = 0; i < SYSTOLIC_ARRAY_DIM; ++i) {
    for (int j = 0; j < SYSTOLIC_ARRAY_DIM; ++j) {
      // print transpose
      printf("%0.2f ", gold_out[j * SYSTOLIC_ARRAY_DIM + i]);
    }
    printf("\n");
  }
}
