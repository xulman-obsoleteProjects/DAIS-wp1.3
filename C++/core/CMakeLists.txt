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

cmake_minimum_required(VERSION 3.1)
include(GNUInstallDirs)
set (CMAKE_CXX_STANDARD 11)

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
  src/TransferImage_Utils.h
  src/TransferImage.h
  )

set(LIBSOURCES
  src/TransferImage.cpp
  )

add_library(DAIS-wp13 ${LIBSOURCES})
#target_link_libraries(DAIS-wp13 STATIC libzmq-static) -- STATIC does not work on Apple
target_link_libraries(DAIS-wp13 libzmq-static)
set_target_properties(DAIS-wp13 PROPERTIES PUBLIC_HEADER "${LIBHEADERS}")

#------------------------------------------------------------------------
option (BUILD_TEST_BINARY "Build also src/testLibCalls.cpp to have some testing binary?" OFF)
if (BUILD_TEST_BINARY)
	add_executable(testLibCalls src/testLibCalls.cpp)
	target_link_libraries(testLibCalls DAIS-wp13)
endif (BUILD_TEST_BINARY)

#------------------------------------------------------------------------
install (TARGETS DAIS-wp13 DESTINATION lib PUBLIC_HEADER DESTINATION include)

option (INSTALL_COPYOF_ZEROMQ "Install also ZeroMQ header and library along with this library?" OFF)
# should also install from  variables: ZeroMQ_INCLUDE_DIR and ZeroMQ_STATIC_LIBRARY
if (INSTALL_COPYOF_ZEROMQ)
	install (FILES      ${ZeroMQ_STATIC_LIBRARY} DESTINATION lib)
	install (DIRECTORY "${ZeroMQ_INCLUDE_DIR}/"  DESTINATION include FILES_MATCHING PATTERN zmq*)
endif (INSTALL_COPYOF_ZEROMQ)
