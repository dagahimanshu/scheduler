package com.smartscheduler.scheduler.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartscheduler.scheduler.model.TaskPriority;
import com.smartscheduler.scheduler.model.TaskRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class PriorityService {

    private final WebClient priorityWebClient;
    private final String huggingFaceApiKey;

    public PriorityService(
            @Value("${huggingface.model}") String priorityModel,
            @Value("${huggingface.api-key:}") String huggingFaceApiKey
    ) {
        this.huggingFaceApiKey = huggingFaceApiKey == null ? "" : huggingFaceApiKey.trim();
        this.priorityWebClient = buildClient(priorityModel);
    }

    public String inferPriority(TaskRequest task) {
        requireApiKey();

        if (task == null) {
            throw new IllegalArgumentException("Task details are required to infer priority.");
        }

        return priorityWebClient.post()
                .bodyValue(Map.of(
                        "inputs", buildPriorityPrompt(task),
                        "parameters", Map.of("candidate_labels", List.of("urgent", "high", "medium", "low"))
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void populateMissingPriority(TaskRequest task) {
        if (task == null || task.getPriority() != null) {
            return;
        }

        String topPriority = extractTopPriority(inferPriority(task));
        if (topPriority != null) {
            task.setPriority(TaskPriority.valueOf(topPriority.toUpperCase(Locale.ROOT)));
        }
    }

    public String extractTopPriority(String response) {
        if (!hasText(response)) {
            return null;
        }

        JsonElement root = JsonParser.parseString(response);
        if (!root.isJsonArray()) {
            return null;
        }

        JsonArray items = root.getAsJsonArray();
        JsonObject bestMatch = null;

        for (JsonElement item : items) {
            if (!item.isJsonObject()) {
                continue;
            }

            JsonObject candidate = item.getAsJsonObject();
            if (!candidate.has("label") || !candidate.has("score")) {
                continue;
            }

            if (bestMatch == null || candidate.get("score").getAsDouble() > bestMatch.get("score").getAsDouble()) {
                bestMatch = candidate;
            }
        }

        return bestMatch == null ? null : bestMatch.get("label").getAsString();
    }

    private String buildPriorityPrompt(TaskRequest task) {
        StringJoiner prompt = new StringJoiner("\n");
        prompt.add("Classify this task as urgent, high, medium, or low.");
        prompt.add("Use the available task details only.");
        prompt.add("If a description is provided, treat it as the strongest signal for urgency.");
        prompt.add(buildTaskContext(task));
        return prompt.toString();
    }


    private String buildTaskContext(TaskRequest task) {
        StringJoiner details = new StringJoiner("\n");

        addDetail(details, "Title", task.getTitle());
        addDetail(details, "Description", task.getDescription());
        addDetail(details, "Date", task.getDate());
        addDetail(details, "Time", task.getTime());
        addDetail(details, "Create Meet Link", task.getCreateMeetLink() == null ? null : task.getCreateMeetLink().toString());

        if (task.getAttachmentUrls() != null && !task.getAttachmentUrls().isEmpty()) {
            addDetail(details, "Attachment URLs", String.join(", ", task.getAttachmentUrls()));
        }

        return details.length() == 0 ? "Task details are missing." : details.toString();
    }

    private void addDetail(StringJoiner details, String label, String value) {
        if (hasText(value)) {
            details.add(label + ": " + value.trim());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void requireApiKey() {
        if (huggingFaceApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Hugging Face API key is missing. Set huggingface.api-key in application-local.properties."
            );
        }
    }

    private WebClient buildClient(String model) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://router.huggingface.co/hf-inference/models/" + model);

        if (!huggingFaceApiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + huggingFaceApiKey);
        }

        return builder.build();
    }
}
