cmake_minimum_required(VERSION 3.15)
project(systolic_array_test)

set(CMAKE_CXX_STANDARD 20)

find_package(beethoven REQUIRED)

beethoven_build(systolic_array_test
  SOURCES systolic_array_test.cc)