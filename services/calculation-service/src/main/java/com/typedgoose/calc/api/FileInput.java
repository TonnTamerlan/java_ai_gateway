package com.typedgoose.calc.api;

import jakarta.validation.constraints.NotBlank;

public record FileInput(
        @NotBlank String instruction,
        @NotBlank String content) {
}
