cmake_minimum_required(VERSION 3.22)
project(ThreeRDIAnalogPercussion VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_OSX_ARCHITECTURES "x86_64" CACHE STRING "" FORCE)
set(CMAKE_OSX_DEPLOYMENT_TARGET "10.15" CACHE STRING "" FORCE)

include(FetchContent)
FetchContent_Declare(
  JUCE
  GIT_REPOSITORY https://github.com/juce-framework/JUCE.git
  GIT_TAG 8.0.8
  GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(JUCE)

juce_add_plugin(ThreeRDIAnalogPercussion
  COMPANY_NAME "3RDI Audio Labs"
  BUNDLE_ID "com.threerdi.analogpercussion"
  IS_SYNTH TRUE
  NEEDS_MIDI_INPUT TRUE
  NEEDS_MIDI_OUTPUT FALSE
  IS_MIDI_EFFECT FALSE
  EDITOR_WANTS_KEYBOARD_FOCUS FALSE
  COPY_PLUGIN_AFTER_BUILD FALSE
  PLUGIN_MANUFACTURER_CODE Trdi
  PLUGIN_CODE R3ap
  FORMATS VST3 AU
  PRODUCT_NAME "3RDI Analog Percussion"
)

juce_generate_juce_header(ThreeRDIAnalogPercussion)

target_sources(ThreeRDIAnalogPercussion PRIVATE
  Source/PluginProcessor.cpp
  Source/PluginProcessor.h
  Source/PluginEditor.cpp
  Source/PluginEditor.h
)

target_compile_definitions(ThreeRDIAnalogPercussion PUBLIC
  JUCE_WEB_BROWSER=0
  JUCE_USE_CURL=0
  JUCE_VST3_CAN_REPLACE_VST2=0
)

target_link_libraries(ThreeRDIAnalogPercussion PRIVATE
  juce::juce_audio_utils
  juce::juce_dsp
)
