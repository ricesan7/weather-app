const RESPONSE_SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    assistant_message: { type: "string" },
    updated_goal: { type: "string" },
    needs_clarification: { type: "boolean" },
    clarification_question: { type: "string" }
  },
  required: [
    "assistant_message",
    "updated_goal",
    "needs_clarification",
    "clarification_question"
  ]
};

const SYSTEM_INSTRUCTIONS = `
You are the requirements conversation layer for an electronics-builder application.

Your job is limited to understanding the user's intended behavior and consolidating it into clear natural-language requirements.
Preserve every unchanged requirement from current_goal. Apply the user's newest instruction as the latest semantic change.
If the user's request is ambiguous in a way that changes intended behavior, ask one short non-technical clarification question.

Never claim that a design is electrically safe or validated.
Never invent or finalize GPIO numbers, pin assignments, wire routing, voltage/current ratings, power topology, protection components, libraries, firmware framework, or other electrical safety decisions.
Do not ask beginners to choose GPIO, buses, libraries, or build settings.
The Android application will run a deterministic requirement resolver, component selection, power planning, pin allocation, CircuitGraph generation, and electrical validator after your response.

assistant_message must be concise Japanese.
updated_goal must be a standalone, consolidated Japanese requirement description usable without the chat history.
When clarification is required, set needs_clarification=true and place exactly one question in clarification_question.
Otherwise set needs_clarification=false and clarification_question="".
`.trim();

export function buildOpenAIRequest(payload, model = "gpt-5.6-luna") {
  const conversation = Array.isArray(payload.conversation)
    ? payload.conversation.slice(-20)
    : [];

  return {
    model,
    store: false,
    reasoning: { effort: "low" },
    max_output_tokens: 1400,
    instructions: SYSTEM_INSTRUCTIONS,
    input: [
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: JSON.stringify({
              current_goal: String(payload.current_goal || ""),
              latest_user_message: String(payload.user_message || ""),
              conversation
            })
          }
        ]
      }
    ],
    text: {
      format: {
        type: "json_schema",
        name: "electronics_revision_dialogue",
        description: "Consolidated electronics requirements and at most one semantic clarification.",
        strict: true,
        schema: RESPONSE_SCHEMA
      }
    }
  };
}

export function extractOutputText(response) {
  for (const item of response?.output || []) {
    if (item?.type !== "message") continue;
    for (const content of item.content || []) {
      if (content?.type === "output_text" && typeof content.text === "string") {
        return content.text;
      }
    }
  }
  throw new Error("OpenAI response contained no output_text");
}

function validateResult(value) {
  if (
    !value ||
    typeof value.assistant_message !== "string" ||
    typeof value.updated_goal !== "string" ||
    typeof value.needs_clarification !== "boolean" ||
    typeof value.clarification_question !== "string"
  ) {
    throw new Error("OpenAI structured output did not match the gateway contract");
  }
  if (!value.updated_goal.trim()) {
    throw new Error("OpenAI returned an empty updated_goal");
  }
  return value;
}

export async function refineRevision(
  payload,
  {
    apiKey = process.env.OPENAI_API_KEY,
    model = process.env.OPENAI_MODEL || "gpt-5.6-luna",
    fetchImpl = fetch
  } = {}
) {
  if (!apiKey) throw new Error("OPENAI_API_KEY is not configured");

  const request = buildOpenAIRequest(payload, model);
  const response = await fetchImpl("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  const raw = await response.text();
  if (!response.ok) {
    throw new Error(`OpenAI request failed with HTTP ${response.status}: ${raw.slice(0, 500)}`);
  }

  const responseJson = JSON.parse(raw);
  const structured = JSON.parse(extractOutputText(responseJson));
  return validateResult(structured);
}


const COMPONENT_RESEARCH_SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    requested_name: { type: "string" },
    manufacturer: { type: "string" },
    model: { type: "string" },
    display_name: { type: "string" },
    kind: {
      type: "string",
      enum: [
        "SENSOR",
        "ACTUATOR",
        "DRIVER",
        "DISPLAY",
        "POWER_SUPPLY",
        "LEVEL_SHIFTER",
        "STORAGE",
        "OTHER"
      ]
    },
    primary_interface: {
      type: "string",
      enum: [
        "GPIO",
        "I2C",
        "SPI",
        "UART",
        "PWM",
        "ADC",
        "USB",
        "ONE_WIRE",
        "RS485",
        "UNKNOWN"
      ]
    },
    voltage_min_v: { type: ["number", "null"] },
    voltage_typical_v: { type: ["number", "null"] },
    voltage_max_v: { type: ["number", "null"] },
    preferred_supply_v: { type: ["number", "null"] },
    current_max_ma: { type: ["number", "null"] },
    requires_external_power: { type: "boolean" },
    driver_id: { type: "string" },
    capabilities: {
      type: "array",
      items: { type: "string" }
    },
    aliases: {
      type: "array",
      items: { type: "string" }
    },
    pins: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          label: { type: "string" },
          role: {
            type: "string",
            enum: [
              "VCC",
              "GND",
              "I2C_SDA",
              "I2C_SCL",
              "ADDRESS",
              "CONTROL_INPUT",
              "LOAD_OUTPUT",
              "CLAMP_COMMON",
              "POSITIVE",
              "NEGATIVE",
              "DATA",
              "SIGNAL_INPUT",
              "SIGNAL_OUTPUT"
            ]
          }
        },
        required: ["label", "role"]
      }
    },
    sources: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          url: { type: "string" },
          title: { type: "string" },
          authority: {
            type: "string",
            enum: [
              "MANUFACTURER_DATASHEET",
              "MANUFACTURER_PRODUCT_PAGE",
              "AUTHORIZED_DISTRIBUTOR",
              "OTHER"
            ]
          }
        },
        required: ["url", "title", "authority"]
      }
    },
    confidence: { type: "number", minimum: 0, maximum: 1 },
    notes: {
      type: "array",
      items: { type: "string" }
    }
  },
  required: [
    "requested_name",
    "manufacturer",
    "model",
    "display_name",
    "kind",
    "primary_interface",
    "voltage_min_v",
    "voltage_typical_v",
    "voltage_max_v",
    "preferred_supply_v",
    "current_max_ma",
    "requires_external_power",
    "driver_id",
    "capabilities",
    "aliases",
    "pins",
    "sources",
    "confidence",
    "notes"
  ]
};

const COMPONENT_RESEARCH_INSTRUCTIONS = `
You research electronic components for an engineering compiler.

Search the web before answering.
Prioritize evidence in this order:
1. manufacturer datasheet;
2. manufacturer product/documentation page;
3. authorized distributor;
4. other sources only for discovery, never as sole evidence for electrical ratings.

Identify the exact manufacturer/model or module variant. Do not merge a bare IC/sensor
with a breakout/module unless the evidence clearly matches the requested product.

Never claim a part is electrically verified merely because a search result exists.
Extract only values supported by the sources you actually used.
If a value cannot be confirmed, return null or an empty string/list as appropriate.

For mains-voltage, inverter, VFD, contactor, SSR, relay, industrial-drive, or other
hazardous-energy components, extract facts but do not imply they are automatically safe
for deployment.

Capabilities are semantic strings such as measure_temperature, measure_humidity,
display_visual, sense_button, actuate_fan, switch_load, communicate_rs485.

driver_id must be empty unless a concrete compatible runtime driver is known from the
provided project context. Do not invent driver names.

Return Japanese-friendly display names where practical, but preserve exact model numbers.
`.trim();

export function buildComponentResearchRequest(
  payload,
  model = "gpt-5.6-luna"
) {
  return {
    model,
    store: false,
    reasoning: { effort: "medium" },
    max_output_tokens: 3000,
    tools: [
      {
        type: "web_search",
        search_context_size: "medium"
      }
    ],
    instructions: COMPONENT_RESEARCH_INSTRUCTIONS,
    input: [
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: JSON.stringify({
              requested_name: String(payload.requested_name || ""),
              category_hint: String(payload.category_hint || ""),
              required_capabilities: Array.isArray(payload.required_capabilities)
                ? payload.required_capabilities
                : [],
              project_goal: String(payload.project_goal || "")
            })
          }
        ]
      }
    ],
    text: {
      format: {
        type: "json_schema",
        name: "electronics_component_research",
        description:
          "Structured component facts researched from current web sources.",
        strict: true,
        schema: COMPONENT_RESEARCH_SCHEMA
      }
    }
  };
}

export function extractWebSourceUrls(response) {
  const urls = new Set();
  for (const item of response?.output || []) {
    if (item?.type !== "web_search_call") continue;
    const action = item.action || {};
    for (const source of action.sources || []) {
      if (source?.type === "url" && typeof source.url === "string") {
        urls.add(source.url);
      }
    }
    if (typeof action.url === "string") urls.add(action.url);
  }
  return urls;
}

function validateComponentResearchResult(value, actualSourceUrls) {
  if (
    !value ||
    typeof value.requested_name !== "string" ||
    typeof value.display_name !== "string" ||
    !Array.isArray(value.sources) ||
    !Array.isArray(value.pins) ||
    !Array.isArray(value.capabilities)
  ) {
    throw new Error(
      "OpenAI component research output did not match the gateway contract"
    );
  }

  const allowedSources = value.sources.filter(
    (source) =>
      typeof source?.url === "string" &&
      actualSourceUrls.has(source.url)
  );

  return {
    ...value,
    sources: allowedSources,
    evidence_complete:
      allowedSources.some((source) =>
        source.authority === "MANUFACTURER_DATASHEET" ||
        source.authority === "MANUFACTURER_PRODUCT_PAGE"
      )
  };
}

export async function researchComponent(
  payload,
  {
    apiKey = process.env.OPENAI_API_KEY,
    model = process.env.OPENAI_MODEL || "gpt-5.6-luna",
    fetchImpl = fetch
  } = {}
) {
  if (!apiKey) throw new Error("OPENAI_API_KEY is not configured");

  const request = buildComponentResearchRequest(payload, model);
  const response = await fetchImpl(
    "https://api.openai.com/v1/responses",
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(request)
    }
  );

  const raw = await response.text();
  if (!response.ok) {
    throw new Error(
      `OpenAI component research failed with HTTP ${response.status}: ${raw.slice(0, 500)}`
    );
  }

  const responseJson = JSON.parse(raw);
  const structured = JSON.parse(extractOutputText(responseJson));
  const actualSourceUrls = extractWebSourceUrls(responseJson);
  return validateComponentResearchResult(
    structured,
    actualSourceUrls
  );
}
