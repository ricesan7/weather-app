#include "aie/BlePacket.hpp"
#include "aie/BleRuntimeBridge.hpp"
#include "aie/Expression.hpp"
#include "aie/Manifest.hpp"
#include "aie/Protocol.hpp"
#include "aie/RuntimeCore.hpp"

#include <cassert>
#include <iostream>
#include <set>
#include <string>
#include <unordered_map>
#include <vector>

namespace {

class FakeHardware : public aie::RuntimeHardware {
public:
    bool setOutput(const std::string& outputId, const std::string& value) override {
        outputs[outputId] = value;
        return true;
    }

    bool runTest(const std::string& command) override {
        tests.push_back(command);
        return command != "force_fail";
    }

    std::optional<std::string> loadSetting(
        const std::string& projectId,
        const std::string& settingId
    ) override {
        const auto key = projectId + ":" + settingId;
        const auto it = persistedSettings.find(key);
        if (it == persistedSettings.end()) return std::nullopt;
        return it->second;
    }

    bool storeSetting(
        const std::string& projectId,
        const std::string& settingId,
        const std::string& value
    ) override {
        persistedSettings[projectId + ":" + settingId] = value;
        return true;
    }

    std::optional<std::string> loadManifest() override {
        return persistedManifest;
    }

    bool storeManifest(const std::string& encodedManifest) override {
        if (failManifestStore) return false;
        persistedManifest = encodedManifest;
        return true;
    }

    std::unordered_map<std::string, std::string> outputs;
    std::unordered_map<std::string, std::string> persistedSettings;
    std::optional<std::string> persistedManifest;
    bool failManifestStore = false;
    std::vector<std::string> tests;
};

std::string goldenManifest() {
    return
        "meta\tversion\t1.1\n"
        "meta\tproject\tgolden\n"
        "meta\tboard\txiao_esp32s3\n"
        "meta\truntime_min\t0.2.0\n"
        "autonomy\tAUTONOMOUS_MCU\ttrue\ttrue\ttrue\t\n"
        "driver\tdrv_sht31\n"
        "driver\tdrv_gpio_sink\n"
        "setting\tmode\tENUM\tAUTO\ttrue\t\t\t\tAUTO,MANUAL\t\n"
        "setting\ttemp_on\tNUMBER\t30.0\ttrue\t0.0\t60.0\t0.5\t\t\n"
        "setting\ttemp_off\tNUMBER\t28.0\ttrue\t0.0\t59.5\t0.5\t\ttemp_off < temp_on\n"
        "setting\tmanual_fan\tBOOLEAN\tfalse\ttrue\t\t\t\t\t\n"
        "rule\tmanual_on\t100\tmode == MANUAL && manual_fan == true\tset:fan:ON\n"
        "rule\tmanual_off\t100\tmode == MANUAL && manual_fan == false\tset:fan:OFF\n"
        "rule\tauto_on\t50\tmode == AUTO && temperature >= temp_on\tset:fan:ON\n"
        "rule\tauto_off\t50\tmode == AUTO && temperature <= temp_off\tset:fan:OFF\n"
        "failsafe\tsensor_timeout\trequired_sensor_invalid_for >= 5s\tset:fan:OFF;event:sensor_fault\n"
        "telemetry\ttemperature\n"
        "telemetry\tfan_state\n"
        "test\tsensor_probe\tprobe_required_sensors\ttrue\n"
        "test\tfan_output_test\tfan_on_1s_then_off\ttrue\n";
}

void testExpression() {
    aie::ExpressionEvaluator evaluator;
    std::unordered_map<std::string, aie::Value> context = {
        {"mode", aie::Value("AUTO")},
        {"temperature", aie::Value(31.0)},
        {"temp_on", aie::Value(30.0)},
        {"humidity", aie::Value(60.0)},
        {"rh_on", aie::Value(75.0)},
    };

    assert(evaluator.evaluate(
        "mode == AUTO && (temperature >= temp_on || humidity >= rh_on)",
        context
    ));
}

void testManifestAndRuntime() {
    aie::ManifestParser parser;
    const auto manifest = parser.parse(goldenManifest());

    assert(manifest.projectId == "golden");
    assert(manifest.rules.size() == 4);
    assert(manifest.settings.size() == 4);
    assert(manifest.failsafe.size() == 1);
    assert(manifest.autonomy.coreOperationMode == "AUTONOMOUS_MCU");
    assert(manifest.autonomy.localBehaviorExecutionRequired);
    assert(manifest.autonomy.localSafetyExecutionRequired);
    assert(manifest.autonomy.persistRuntimeSettings);

    FakeHardware hardware;
    aie::RuntimeCore runtime(
        hardware,
        std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
    );

    std::string error;
    assert(runtime.deploy(manifest, error));
    assert(runtime.verifyProject("golden"));

    runtime.updateInput("temperature", aie::Value(31.0));
    runtime.updateInput("required_sensor_invalid_for", aie::Value(0.0));
    runtime.tick();
    assert(hardware.outputs["fan"] == "ON");

    runtime.updateInput("temperature", aie::Value(27.0));
    runtime.tick();
    assert(hardware.outputs["fan"] == "OFF");

    assert(runtime.setSetting("mode", "MANUAL", error));
    assert(runtime.setSetting("manual_fan", "true", error));
    runtime.tick();
    assert(hardware.outputs["fan"] == "ON");

    runtime.updateInput("required_sensor_invalid_for", aie::Value(5.0));
    runtime.tick();
    assert(hardware.outputs["fan"] == "OFF");
    assert(!runtime.events().empty());
    assert(runtime.events().back().id == "sensor_fault");

    assert(!runtime.setSetting("temp_off", "35.0", error));
    assert(runtime.getSetting("temp_off").value() == "28.0");

    const auto telemetry = runtime.telemetry();
    assert(telemetry.at("temperature").asString() == "27");
    assert(telemetry.at("fan_state").asString() == "OFF");

    assert(runtime.runTest("sensor_probe"));
    assert(runtime.runTest("fan_output_test"));
    assert(hardware.tests.size() == 2);
}

void testAutonomousControlWithoutPhoneBridge() {
    const auto manifest = aie::ManifestParser().parse(goldenManifest());

    FakeHardware hardware;
    aie::RuntimeCore runtime(
        hardware,
        std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
    );

    std::string error;
    assert(runtime.deploy(manifest, error));

    // No BLE bridge, Android client, or cloud connection is involved here.
    runtime.updateInput("temperature", aie::Value(33.0));
    runtime.updateInput("required_sensor_invalid_for", aie::Value(0.0));
    runtime.tick();

    assert(hardware.outputs["fan"] == "ON");
}

void testRuntimeSettingsSurviveRuntimeRecreation() {
    const auto manifest = aie::ManifestParser().parse(goldenManifest());

    FakeHardware hardware;
    std::string error;

    {
        aie::RuntimeCore runtime(
            hardware,
            std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
        );
        assert(runtime.deploy(manifest, error));
        assert(runtime.setSetting("temp_on", "32.0", error));
        assert(runtime.getSetting("temp_on").value() == "32.0");
    }

    {
        aie::RuntimeCore rebooted(
            hardware,
            std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
        );
        assert(rebooted.deploy(manifest, error));
        assert(rebooted.getSetting("temp_on").value() == "32.0");
    }
}

void testPersistedManifestRestoresWithoutPhone() {
    FakeHardware hardware;
    std::string error;

    {
        aie::RuntimeCore runtime(
            hardware,
            std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
        );
        const auto manifest = aie::ManifestParser().parse(goldenManifest());
        assert(runtime.deploy(manifest, error));
        assert(runtime.persistManifest(goldenManifest(), error));
        assert(runtime.setSetting("temp_on", "32.0", error));
    }

    {
        aie::RuntimeCore rebooted(
            hardware,
            std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
        );
        assert(rebooted.restorePersistedManifest(error));
        assert(rebooted.verifyProject("golden"));
        assert(rebooted.getSetting("temp_on").value() == "32.0");

        rebooted.updateInput("temperature", aie::Value(33.0));
        rebooted.updateInput(
            "required_sensor_invalid_for",
            aie::Value(0.0)
        );
        rebooted.tick();

        assert(hardware.outputs["fan"] == "ON");
    }
}

void testDeploymentFailsClosedWhenManifestCannotPersist() {
    FakeHardware hardware;
    hardware.failManifestStore = true;

    aie::RuntimeCore runtime(
        hardware,
        std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
    );
    aie::ProtocolDispatcher dispatcher(runtime);

    aie::RuntimeFrame deploy;
    deploy.type = aie::MessageType::DEPLOY_MANIFEST;
    deploy.requestId = "persist-fail";
    deploy.fields["payload"] = goldenManifest();

    const auto response = dispatcher.handle(deploy);

    assert(response.type == aie::MessageType::DEPLOY_RESULT);
    assert(response.fields.at("ok") == "false");
    assert(!runtime.verifyProject("golden"));
}

void testUnsupportedDriverBlocked() {
    auto manifest = aie::ManifestParser().parse(goldenManifest());
    manifest.drivers.push_back("drv_missing");

    FakeHardware hardware;
    aie::RuntimeCore runtime(
        hardware,
        std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
    );

    std::string error;
    assert(!runtime.deploy(manifest, error));
    assert(error.find("Unsupported driver") != std::string::npos);
}

void testProtocol() {
    FakeHardware hardware;
    aie::RuntimeCore runtime(
        hardware,
        std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
    );
    aie::ProtocolDispatcher dispatcher(runtime);
    aie::FrameCodec codec;

    aie::RuntimeFrame deploy;
    deploy.type = aie::MessageType::DEPLOY_MANIFEST;
    deploy.requestId = "req|1";
    deploy.fields["payload"] = goldenManifest();

    const auto wire = codec.encode(deploy);
    const auto decoded = codec.decode(wire);
    assert(decoded.requestId == "req|1");
    assert(decoded.fields.at("payload") == goldenManifest());

    const auto deployResult = dispatcher.handle(decoded);
    assert(deployResult.type == aie::MessageType::DEPLOY_RESULT);
    assert(deployResult.fields.at("ok") == "true");

    aie::RuntimeFrame set;
    set.type = aie::MessageType::SET_VALUE;
    set.requestId = "set1";
    set.fields = {{"setting_id", "temp_on"}, {"value", "32.0"}};

    const auto setResult = dispatcher.handle(set);
    assert(setResult.type == aie::MessageType::VALUE);
    assert(setResult.fields.at("value") == "32.0");
}


void testBlePacketContract() {
    aie::BlePacketCodec codec;

    const auto encoded = codec.encode(aie::BlePacket{
        0x1234,
        0,
        true,
        true,
        std::vector<std::uint8_t>{0x41}
    });

    const std::vector<std::uint8_t> expected = {
        0xA1, 0x01, 0x03, 0x12, 0x34, 0x00, 0x00, 0x41
    };
    assert(encoded == expected);

    aie::BlePacketizer packetizer;
    std::vector<std::uint8_t> payload(400, static_cast<std::uint8_t>('x'));
    const auto packets = packetizer.split(42, payload, 20);

    assert(packets.size() > 20);
    assert(packetizer.join(packets) == payload);
}

void testBleRuntimeBridge() {
    FakeHardware hardware;
    aie::RuntimeCore runtime(
        hardware,
        std::set<std::string>{"drv_sht31", "drv_gpio_sink"}
    );
    aie::ProtocolDispatcher dispatcher(runtime);
    aie::BleRuntimeBridge bridge(dispatcher, 20);
    aie::FrameCodec frameCodec;
    aie::BlePacketizer packetizer;

    aie::RuntimeFrame hello;
    hello.type = aie::MessageType::HELLO;
    hello.requestId = "hello-1";

    const auto helloText = frameCodec.encode(hello);
    const std::vector<std::uint8_t> helloBytes(
        helloText.begin(),
        helloText.end()
    );
    const auto requestPackets = packetizer.split(7, helloBytes, 20);

    std::vector<std::vector<std::uint8_t>> responsePackets;
    for (const auto& packet : requestPackets) {
        const auto response = bridge.onPacket(packet, 20);
        if (!response.empty()) {
            responsePackets = response;
        }
    }

    assert(!responsePackets.empty());

    const auto responseBytes = packetizer.join(responsePackets);
    const std::string responseText(responseBytes.begin(), responseBytes.end());
    const auto responseFrame = frameCodec.decode(responseText);

    assert(responseFrame.type == aie::MessageType::CAPABILITIES);
    assert(responseFrame.requestId == "hello-1");
    assert(responseFrame.fields.at("protocol_version") == "1");
    assert(responseFrame.fields.at("offline_autonomy") == "true");
    assert(responseFrame.fields.at("persistent_manifest") == "true");
    assert(responseFrame.fields.at("persistent_settings") == "true");

    aie::RuntimeFrame deploy;
    deploy.type = aie::MessageType::DEPLOY_MANIFEST;
    deploy.requestId = "deploy-long";
    deploy.fields["payload"] = goldenManifest();

    const auto deployText = frameCodec.encode(deploy);
    const std::vector<std::uint8_t> deployBytes(
        deployText.begin(),
        deployText.end()
    );
    const auto deployPackets = packetizer.split(8, deployBytes, 20);

    responsePackets.clear();
    for (const auto& packet : deployPackets) {
        const auto response = bridge.onPacket(packet, 20);
        if (!response.empty()) {
            responsePackets = response;
        }
    }

    const auto deployResponseBytes = packetizer.join(responsePackets);
    const std::string deployResponseText(
        deployResponseBytes.begin(),
        deployResponseBytes.end()
    );
    const auto deployResponse = frameCodec.decode(deployResponseText);

    assert(deployResponse.type == aie::MessageType::DEPLOY_RESULT);
    assert(deployResponse.requestId == "deploy-long");
    assert(deployResponse.fields.at("ok") == "true");
    assert(runtime.verifyProject("golden"));
}

} // namespace

int main() {
    testExpression();
    testManifestAndRuntime();
    testAutonomousControlWithoutPhoneBridge();
    testRuntimeSettingsSurviveRuntimeRecreation();
    testPersistedManifestRestoresWithoutPhone();
    testDeploymentFailsClosedWhenManifestCannotPersist();
    testUnsupportedDriverBlocked();
    testProtocol();
    testBlePacketContract();
    testBleRuntimeBridge();

    std::cout << "UNIVERSAL_RUNTIME_CORE_OK\n";
    return 0;
}
