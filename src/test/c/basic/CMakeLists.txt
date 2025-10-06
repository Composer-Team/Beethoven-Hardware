cmake_minimum_required(VERSION 3.15)
project(basic)
set(CMAKE_CXX_STANDARD 20)

find_package(beethoven REQUIRED)

beethoven_build(my_accel SOURCES MyAccelerator.cc)



