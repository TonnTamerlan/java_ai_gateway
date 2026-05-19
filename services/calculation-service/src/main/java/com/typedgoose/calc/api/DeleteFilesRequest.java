package com.typedgoose.calc.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record DeleteFilesRequest(
        @NotEmpty(message = "ids must not be empty")
        @Size(max = 100, message = "at most 100 ids per request")
        List<UUID> ids) {
}
