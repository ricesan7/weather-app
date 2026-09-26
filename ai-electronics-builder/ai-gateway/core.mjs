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
