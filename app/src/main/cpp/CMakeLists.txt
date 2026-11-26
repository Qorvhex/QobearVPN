cmake_minimum_required(VERSION 3.20)
project(qobearopenvpn LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

set(DEPS "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/openvpn3")
if(NOT EXISTS "${DEPS}/source-lock.json")
    message(FATAL_ERROR "Missing third_party/openvpn3/source-lock.json")
endif()

file(READ "${DEPS}/source-lock.json" SOURCE_LOCK)
string(JSON CORE_COMMIT GET "${SOURCE_LOCK}" coreCommit)
set(CORE "${DEPS}/openvpn3-${CORE_COMMIT}" CACHE PATH "Pinned OpenVPN 3 core directory")

if(NOT EXISTS "${CORE}/client/ovpncli.cpp")
    message(FATAL_ERROR "OpenVPN 3 sources missing. Run: python3 scripts/fetch_openvpn_deps.py")
endif()

set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(GEN_FILES OFF CACHE BOOL "" FORCE)
set(MBEDTLS_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
add_subdirectory("${DEPS}/mbedtls-mbedtls-3.6.7" mbedtls EXCLUDE_FROM_ALL)

set(FMT_TEST OFF CACHE BOOL "" FORCE)
set(FMT_DOC OFF CACHE BOOL "" FORCE)
add_subdirectory("${DEPS}/fmt-11.1.4" fmt EXCLUDE_FROM_ALL)

set(OVPN_SOURCES
    "${CORE}/client/ovpncli.cpp"
    "${CORE}/openvpn/crypto/data_epoch.cpp"
    "${DEPS}/lz4-1.10.0/lib/lz4.c"
)

if(ANDROID)
    add_library(qobearopenvpn SHARED bridge.cpp ${OVPN_SOURCES})
    target_include_directories(qobearopenvpn PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}"
        "${CORE}"
        "${DEPS}/asio-asio-1-30-2/asio/include"
        "${DEPS}/lz4-1.10.0/lib"
    )
    target_compile_definitions(qobearopenvpn PRIVATE
        USE_ASIO
        ASIO_STANDALONE
        USE_MBEDTLS
        HAVE_LZ4
        OPENVPN_CORE_API_VISIBILITY_HIDDEN
    )
    target_compile_options(qobearopenvpn PRIVATE -O2 -ffunction-sections -fdata-sections)
    target_link_options(qobearopenvpn PRIVATE -Wl,--gc-sections -Wl,-z,max-page-size=16384)
    target_link_libraries(qobearopenvpn PRIVATE mbedtls mbedx509 mbedcrypto fmt::fmt log)
else()
    add_executable(qobear_profile_check profile_check.cpp ${OVPN_SOURCES})
    target_include_directories(qobear_profile_check PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}"
        "${CORE}"
        "${DEPS}/asio-asio-1-30-2/asio/include"
        "${DEPS}/lz4-1.10.0/lib"
    )
    target_compile_definitions(qobear_profile_check PRIVATE
        USE_ASIO
        ASIO_STANDALONE
        USE_MBEDTLS
        HAVE_LZ4
    )
    find_package(Threads REQUIRED)
    target_link_libraries(qobear_profile_check PRIVATE mbedtls mbedx509 mbedcrypto fmt::fmt Threads::Threads)
endif()
