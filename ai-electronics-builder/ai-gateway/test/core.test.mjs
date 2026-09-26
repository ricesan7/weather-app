import test from "node:test";
import assert from "node:assert/strict";
import {
  buildOpenAIRequest,
  extractOutputText,
  refineRevision
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
