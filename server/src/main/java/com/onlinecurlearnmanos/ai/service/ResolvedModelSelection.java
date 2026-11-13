package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.entity.LlmModel;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;

public record ResolvedModelSelection(
        LlmProviderConfig provider,
        LlmModel model,
        int pointCost
) {
}
