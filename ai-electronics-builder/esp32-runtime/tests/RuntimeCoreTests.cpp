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

    std::unordered_map<std::string, std::string> outputs;
    std::vector<std::string> tests;
};

std::string goldenManifest() {
    return
        "meta\tversion\t1.0\n"
        "meta\tproject\tgolden\n"
        "meta\tboard\txiao_esp32s3\n"
        "meta\truntime_min\t0.1.0\n"
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

} // namespace

int main() {
    testExpression();
    testManifestAndRuntime();
    testUnsupportedDriverBlocked();
    testProtocol();

    std::cout << "UNIVERSAL_RUNTIME_CORE_OK\n";
    return 0;
}
