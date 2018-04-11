#------------------------------------------------------------------------
#
# Cross platform CMake configure script
# for 'DAIS-wp13 C++' image transferring core library
#
# author: Vladimir Ulman
#         CSBD & MPI-CBG (c) 2018
#
#------------------------------------------------------------------------

project(DAIS-wp13)

cmake_minimum_required(VERSION 3.0)
include(GNUInstallDirs)

#------------------------------------------------------------------------
# attempts to find the complete ZeroMQ Cmake package because...
# a) default installation provides the Cmake module files
# b) and the module knows how to link ZeroMQ statically to my libraries/binaries
list (APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_BINARY_DIR}/cmake/ZeroMQ")
find_package(ZeroMQ)

# libzmq autotools install: fallback to pkg-config
if(NOT ZeroMQ_FOUND)
    # try again with pkg-config (normal install of zeromq)
    list (APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_LIST_DIR}/libzmq-pkg-config)
    find_package(ZeroMQ REQUIRED)
endif()

#------------------------------------------------------------------------
set(LIBHEADERS
  src/SendImage.h)

set(LIBSOURCES
  src/SendImage.cpp)

add_library(DAIS-wp13 ${LIBSOURCES})
target_link_libraries(DAIS-wp13 libzmq-static)

#------------------------------------------------------------------------
option (TEST_BINARY "Build also src/testLibCalls.cpp to have some testing binary?" OFF)
if (TEST_BINARY)
	add_executable(testLibCalls src/testLibCalls.cpp)
	target_link_libraries(testLibCalls DAIS-wp13)
endif (TEST_BINARY)
