#include "runtime.h"
#ifdef MULTICORE
#ifdef RAW
#include "math.h"
#endif
#else
#include "math.h"
#endif
#include "structdefs.h"
#if 0
double CALL11(___Math______cos____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return cos(___a___);
#endif
#else
  return cos(___a___);
#endif
}

double CALL11(___Math______sin____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return sin(___a___);
#endif
#else
  return sin(___a___);
#endif
}

double CALL11(___Math______tan____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return tan(___a___);
#endif
#else
  return tan(___a___);
#endif
}

double CALL11(___Math______acos____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return acos(___a___);
#endif
#else
  return acos(___a___);
#endif
}

double CALL11(___Math______asin____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return asin(___a___);
#endif
#else
  return asin(___a___);
#endif
}

double CALL11(___Math______atan____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return atan(___a___);
#endif
#else
  return atan(___a___);
#endif
}

double CALL22(___Math______atan2____D_D, double ___a___, double ___b___, double ___a___, double ___b___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return atan2(___a___,___b___);
#endif
#else
  return atan2(___a___,___b___);
#endif
}

double CALL11(___Math______log____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return log(___a___);
#endif
#else
  return log(___a___);
#endif
}

double CALL11(___Math______exp____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return exp(___a___);
#endif
#else
  return exp(___a___);
#endif
}

double CALL11(___Math______sqrt____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return -1;
#ifdef RAW
  return sqrt(___a___);
#endif
#else
  return sqrt(___a___);
#endif
}

double CALL22(___Math______pow____D_D, double ___a___, double ___b___, double ___a___, double ___b___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return pow(___a___);
#endif
#else
  return pow(___a___,___b___);
#endif
}

double CALL11(___Math______ceil____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return ceil(___a___);
#endif
#else
  return ceil(___a___);
#endif
}

double CALL11(___Math______floor____D, double ___a___, double ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return floor(___a___);
#endif
#else
  return floor(___a___);
#endif
}

float CALL11(___Math______cosf____F, float ___a___, float ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return cosf(___a___);
#endif
#else
  return cosf(___a___);
#endif
}

float CALL11(___Math______sinf____F, float ___a___, float ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return sinf(___a___);
#endif
#else
  return sinf(___a___);
#endif
}

float CALL11(___Math______expf____F, float ___a___, float ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return expf(___a___);
#endif
#else
  return expf(___a___);
#endif
}

float CALL11(___Math______sqrtf____F, float ___a___, float ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return sqrtf(___a___);
#endif
#else
  return sqrtf(___a___);
#endif
}

float CALL11(___Math______logf____F, float ___a___, float ___a___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return logf(___a___);
#endif
#else
  return logf(___a___);
#endif
}

float CALL22(___Math______powf____F_F, float ___a___, float ___b___, float ___a___, float ___b___) {
#ifdef MULTICORE
  return 1;
#ifdef RAW
  return powf(___a___,___b___);
#endif
#else
  return powf(___a___,___b___);
#endif
}
#endif
