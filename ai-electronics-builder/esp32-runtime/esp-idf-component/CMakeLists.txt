idf_component_register(
    SRCS
        "../src/Manifest.cpp"
        "../src/Expression.cpp"
        "../src/RuntimeCore.cpp"
        "../src/Protocol.cpp"
        "../src/BlePacket.cpp"
        "../src/BleRuntimeBridge.cpp"
        "src/RuntimeBleGattService.cpp"
    INCLUDE_DIRS
        "../include"
        "include"
    REQUIRES
        bt
)

target_compile_features(${COMPONENT_LIB} PUBLIC cxx_std_17)
