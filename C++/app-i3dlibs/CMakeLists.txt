#------------------------------------------------------------------------
#
# Cross platform CMake configure script
# for 'CBIA's i3dlibs with DAIS-wp13' image transferring example code
#
# author: Vladimir Ulman
#         CSBD & MPI-CBG (c) 2018
#
#------------------------------------------------------------------------

project(i3dlibs-DAIS-wp13)

cmake_minimum_required(VERSION 3.1)
include(GNUInstallDirs)
set (CMAKE_CXX_STANDARD 11)

#------------------------------------------------------------------------
find_path(INC_DAISwp13 "i3d/TransferImage.h")
find_library(LIB_DAISwp13 "DAIS-wp13")

find_path(INC_ZMQ "zmq.hpp")
find_library(LIB_ZMQ "zmq")

find_path(INC_I3D "i3d/image3d.h")
find_library(LIB_I3DCORE "i3dcore")
find_library(LIB_I3DALGO "i3dalgo")

if (UNIX AND CMAKE_COMPILER_IS_GNUCXX)
	set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Xlinker -defsym -Xlinker MAIN__=main")
endif (UNIX AND CMAKE_COMPILER_IS_GNUCXX)

include_directories(${INC_DAISwp13} ${INC_ZMQ} ${INC_I3D} ${INC_I3D}/i3d)
set(LIBS ${LIBS} ${LIB_DAISwp13} ${LIB_ZMQ} ${LIB_I3DALGO} ${LIB_I3DCORE})

#------------------------------------------------------------------------
add_executable(recvAndSave src/recvAndSave.cpp)
target_link_libraries(recvAndSave ${LIBS})
