cmake_minimum_required(VERSION 3.22)
project(eduke32_android LANGUAGES C CXX)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# Keep debug symbols while optimizing native gameplay code for usable FPS measurements.
add_compile_options($<$<CONFIG:Debug>:-O2>)
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../../SDL" SDL2)
# Polymer uses the desktop OpenGL compatibility API. GL4ES translates that API
# to the SDL-created Android GLES context, keeping the actual Polymer renderer active.
set(NOEGL ON CACHE BOOL "" FORCE)
set(NOX11 ON CACHE BOOL "" FORCE)
set(STATICLIB ON CACHE BOOL "" FORCE)
set(NO_LOADER ON CACHE BOOL "" FORCE)
set(NO_INIT_CONSTRUCTOR ON CACHE BOOL "" FORCE)
set(DEFAULT_ES 2 CACHE STRING "" FORCE)
set(USE_ANDROID_LOG ON CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../../third_party/gl4es" gl4es)
get_filename_component(EDUKE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../.." ABSOLUTE)
file(GLOB BUILD_CPP CONFIGURE_DEPENDS "${EDUKE_ROOT}/source/build/src/*.cpp")
list(FILTER BUILD_CPP EXCLUDE REGEX "/(build|config|rawinput|winlayer|sdlayer12|sdlkeytrans|startgtk.editor|startwin.editor|winbits|wiibits|gtkbits|dynamicgtk)\\.cpp$")
file(GLOB BUILD_C CONFIGURE_DEPENDS "${EDUKE_ROOT}/source/build/src/*.c")
file(GLOB DUKE_CPP CONFIGURE_DEPENDS "${EDUKE_ROOT}/source/duke3d/src/*.cpp")
list(FILTER DUKE_CPP EXCLUDE REGEX "/(astub|m32common|m32def|m32exec|m32vars|m32structures|mdump|sounds_mapster32|startgtk.game|startwin.game)\\.cpp$")
file(GLOB MACT_CPP CONFIGURE_DEPENDS "${EDUKE_ROOT}/source/mact/src/*.cpp")
file(GLOB AUDIOLIB_CPP CONFIGURE_DEPENDS "${EDUKE_ROOT}/source/audiolib/src/*.cpp")
list(FILTER AUDIOLIB_CPP EXCLUDE REGEX "/(music_external|driver_alsa|driver_directsound|driver_winmm)\\.cpp$")
file(GLOB IMGUI_CPP CONFIGURE_DEPENDS "${EDUKE_ROOT}/source/imgui/src/*.cpp")
list(FILTER IMGUI_CPP EXCLUDE REGEX "/imgui_impl_win32\\.cpp$")
set(POLYMOST_SHADER_SOURCES)
foreach(shader polymost1Frag polymost1Vert)
    file(READ "${EDUKE_ROOT}/source/build/src/${shader}.glsl" shader_source)
    set(shader_cpp "${CMAKE_CURRENT_BINARY_DIR}/${shader}.glsl.cpp")
    file(WRITE "${shader_cpp}" "extern char const *${shader};\nchar const *${shader} = R\"shader(\n${shader_source})shader\";\n")
    list(APPEND POLYMOST_SHADER_SOURCES "${shader_cpp}")
endforeach()
add_library(main SHARED ${BUILD_CPP} ${BUILD_C} ${DUKE_CPP} ${MACT_CPP} ${AUDIOLIB_CPP} ${IMGUI_CPP} ${POLYMOST_SHADER_SOURCES} "${EDUKE_ROOT}/source/glad/src/glad.c" main.cpp)
target_include_directories(main PRIVATE
    "${EDUKE_ROOT}/source/build/include"
    "${EDUKE_ROOT}/source/duke3d/src"
    "${EDUKE_ROOT}/source/audiolib/include"
    "${EDUKE_ROOT}/source/mact/include"
    "${EDUKE_ROOT}/source/imgui/include"
    "${EDUKE_ROOT}/source/glad/include"
    "${EDUKE_ROOT}/source/voidwrap/include"
    "${CMAKE_CURRENT_LIST_DIR}/../../third_party/gl4es/include")
target_compile_definitions(main PRIVATE RENDERTYPESDL=1 SDL_TARGET=2 USE_OPENGL=1 POLYMER=1 IMGUI_IMPL_OPENGL_ES2=1 NOASM=1 NETCODE_DISABLE DEBUGGINGAIDS=0 EDUKE32_ANDROID_MENU=1 EDUKE32_TOUCH_DEVICES=1)
target_compile_options(main PRIVATE -funsigned-char -fno-strict-aliasing -fexceptions)
target_link_libraries(main PRIVATE SDL2 GL log android dl EGL GLESv2 m)
