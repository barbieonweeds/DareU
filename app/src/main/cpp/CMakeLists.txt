cmake_minimum_required(VERSION 3.22.1)
project("dareu")

add_library(dareu SHARED word_trap.cpp)

find_library(log-lib log)
target_link_libraries(dareu ${log-lib})