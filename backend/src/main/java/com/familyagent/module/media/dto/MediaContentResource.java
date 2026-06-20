package com.familyagent.module.media.dto;

import java.io.InputStream;

public record MediaContentResource(InputStream stream, String contentType) {
}
