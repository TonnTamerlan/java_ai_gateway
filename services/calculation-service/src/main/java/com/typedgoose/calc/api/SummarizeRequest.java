package com.typedgoose.calc.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SummarizeRequest(
        @NotEmpty @Valid List<FileInput> files) {
}
