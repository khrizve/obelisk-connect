cmake_minimum_required(VERSION 3.21)
project(obelisk VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

find_package(Qt6 REQUIRED COMPONENTS Core Gui Widgets Qml Quick QuickControls2 WebSockets Network)

# --- libqrencode -------------------------------------------------------
# Obelisk's Flutter build used qr_flutter; Qt has no built-in QR
# generator, so we lean on the small, ubiquitous libqrencode C library
# instead of hand-rolling a QR encoder.
#   Debian/Ubuntu: sudo apt-get install libqrencode-dev
#   macOS (brew):  brew install qrencode
#   Windows (vcpkg): vcpkg install libqrencode
find_package(PkgConfig QUIET)
if(PkgConfig_FOUND)
    pkg_check_modules(QRENCODE QUIET libqrencode)
endif()
if(NOT QRENCODE_FOUND)
    find_path(QRENCODE_INCLUDE_DIR qrencode.h)
    find_library(QRENCODE_LIBRARY NAMES qrencode)
    if(QRENCODE_INCLUDE_DIR AND QRENCODE_LIBRARY)
        set(QRENCODE_FOUND TRUE)
        set(QRENCODE_INCLUDE_DIRS ${QRENCODE_INCLUDE_DIR})
        set(QRENCODE_LIBRARIES ${QRENCODE_LIBRARY})
    endif()
endif()
if(NOT QRENCODE_FOUND)
    message(FATAL_ERROR "libqrencode not found. Install it (see comment above this line in CMakeLists.txt) and re-run CMake.")
endif()

qt_standard_project_setup(REQUIRES 6.4)

# Theme.qml uses `pragma Singleton`; on some Qt 6.4 packaging this needs an
# explicit nudge for qt_add_qml_module to register it as `singleton Theme`
# in qmldir rather than a plain type (which left Theme.* unresolved at
# runtime — undefined color/font values all through the QML tree).
set_source_files_properties(qml/Theme.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)

qt_add_executable(obelisk
    src/main.cpp
    src/towerserver.h
    src/towerserver.cpp
    src/qrcodeitem.h
    src/qrcodeitem.cpp
    src/frameimageprovider.h
    src/frameimageprovider.cpp
    src/desktopnotifier.h
    src/desktopnotifier.cpp
    src/fileentrymodel.h
    src/fileentrymodel.cpp
    src/filemanagercontroller.h
    src/filemanagercontroller.cpp
)

qt_add_qml_module(obelisk
    URI Obelisk
    VERSION 1.0
    QML_FILES
        qml/main.qml
        qml/Theme.qml
        qml/QrPanel.qml
        qml/ScreenTab.qml
        qml/CallingTab.qml
        qml/NotificationsTab.qml
        qml/FileManagerTab.qml
)

# Cinzel/Manrope (the same pairing app_theme.dart used via google_fonts)
# aren't redistributed in this repo — drop the .ttf files into
# assets/fonts/ yourself (see assets/fonts/README.md) and they'll be
# picked up automatically; the app falls back to the system font if
# they're absent, so this step is optional, not build-breaking.
file(GLOB OBELISK_FONT_FILES "${CMAKE_CURRENT_SOURCE_DIR}/assets/fonts/*.ttf")
if(OBELISK_FONT_FILES)
    qt_add_resources(obelisk "fonts"
        PREFIX "/Obelisk/assets/fonts"
        BASE "${CMAKE_CURRENT_SOURCE_DIR}/assets/fonts"
        FILES ${OBELISK_FONT_FILES}
    )
endif()

target_include_directories(obelisk PRIVATE ${QRENCODE_INCLUDE_DIRS} "${CMAKE_CURRENT_SOURCE_DIR}/src")

target_link_libraries(obelisk PRIVATE
    Qt6::Core
    Qt6::Gui
    Qt6::Widgets
    Qt6::Qml
    Qt6::Quick
    Qt6::QuickControls2
    Qt6::WebSockets
    Qt6::Network
    ${QRENCODE_LIBRARIES}
)

set_target_properties(obelisk PROPERTIES
    WIN32_EXECUTABLE ON
    MACOSX_BUNDLE ON
)
