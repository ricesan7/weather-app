import test from "node:test";
import assert from "node:assert/strict";
import {
  buildOpenAIRequest,
  buildComponentResearchRequest,
  extractOutputText,
  refineRevision,
  researchComponent
} from "../core.mjs";

test("request uses structured outputs and does not store response state", () => {
  const request = buildOpenAIRequest(
    {
      current_goal: "30℃以上でファンを回す",
      user_message: "32℃に変更",
      conversation: []
    },
    "gpt-5.6-luna"
  );

  assert.equal(request.model, "gpt-5.6-luna");
  assert.equal(request.store, false);
  assert.equal(request.text.format.type, "json_schema");
  assert.equal(request.text.format.strict, true);
  assert.equal(request.text.format.schema.additionalProperties, false);
});

test("extractOutputText reads the assistant output item", () => {
  assert.equal(
    extractOutputText({
      output: [
        {
          type: "message",
          content: [{ type: "output_text", text: "{\"ok\":true}" }]
        }
      ]
    }),
    "{\"ok\":true}"
  );
});

test("refineRevision parses the structured result", async () => {
  let captured;
  const fakeFetch = async (_url, options) => {
    captured = JSON.parse(options.body);
    return {
      ok: true,
      status: 200,
      async text() {
        return JSON.stringify({
          output: [
            {
              type: "message",
              content: [
                {
                  type: "output_text",
                  text: JSON.stringify({
                    assistant_message: "32℃へ変更します。",
                    updated_goal: "32℃以上でファンを回す",
                    needs_clarification: false,
                    clarification_question: ""
                  })
                }
              ]
            }
          ]
        });
      }
    };
  };

  const result = await refineRevision(
    {
      current_goal: "30℃以上でファンを回す",
      user_message: "32℃に変更",
      conversation: []
    },
    {
      apiKey: "test-key",
      model: "gpt-5.6-luna",
      fetchImpl: fakeFetch
    }
  );

  assert.equal(result.updated_goal, "32℃以上でファンを回す");
  assert.equal(captured.store, false);
  assert.equal(captured.reasoning.effort, "low");
});


test("component research request enables web search with structured output", () => {
  const request = buildComponentResearchRequest(
    {
      requested_name: "DHT11",
      category_hint: "sensor",
      required_capabilities: [
        "measure_temperature",
        "measure_humidity"
      ],
      project_goal: "DHT11で温湿度を測る"
    },
    "gpt-5.6-luna"
  );

  assert.equal(request.tools[0].type, "web_search");
  assert.equal(request.text.format.type, "json_schema");
  assert.equal(request.text.format.strict, true);
  assert.equal(request.store, false);
});

test("component research only keeps URLs returned by actual web search", async () => {
  const officialUrl = "https://example-manufacturer.test/dht11.pdf";
  const inventedUrl = "https://invented.invalid/spec.pdf";

  const fakeFetch = async (_url, options) => {
    const request = JSON.parse(options.body);
    assert.equal(request.tools[0].type, "web_search");

    return {
      ok: true,
      status: 200,
      async text() {
        return JSON.stringify({
          output: [
            {
              type: "web_search_call",
              action: {
                type: "search",
                queries: ["DHT11 datasheet"],
                sources: [
                  { type: "url", url: officialUrl }
                ]
              }
            },
            {
              type: "message",
              content: [
                {
                  type: "output_text",
                  text: JSON.stringify({
                    requested_name: "DHT11",
                    manufacturer: "Example",
                    model: "DHT11",
                    display_name: "DHT11",
                    kind: "SENSOR",
                    primary_interface: "ONE_WIRE",
                    voltage_min_v: 3.0,
                    voltage_typical_v: 5.0,
                    voltage_max_v: 5.5,
                    preferred_supply_v: 3.3,
                    current_max_ma: 2.5,
                    i2c_address: "",
                    requires_external_power: false,
                    driver_id: "",
                    capabilities: [
                      "measure_temperature",
                      "measure_humidity"
                    ],
                    aliases: ["DHT-11"],
                    pins: [
                      { label: "VCC", role: "VCC" },
                      { label: "DATA", role: "DATA" },
                      { label: "GND", role: "GND" }
                    ],
                    sources: [
                      {
                        url: officialUrl,
                        title: "Official datasheet",
                        authority: "MANUFACTURER_DATASHEET"
                      },
                      {
                        url: inventedUrl,
                        title: "Invented",
                        authority: "MANUFACTURER_DATASHEET"
                      }
                    ],
                    confidence: 0.9,
                    notes: []
                  })
                }
              ]
            }
          ]
        });
      }
    };
  };

  const result = await researchComponent(
    {
      requested_name: "DHT11",
      category_hint: "sensor",
      required_capabilities: [],
      project_goal: "DHT11を使う"
    },
    {
      apiKey: "test-key",
      model: "gpt-5.6-luna",
      fetchImpl: fakeFetch
    }
  );

  assert.equal(result.sources.length, 1);
  assert.equal(result.sources[0].url, officialUrl);
  assert.equal(result.evidence_complete, true);
});
