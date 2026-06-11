package com.familyagent.module.photo.dto;

import java.io.InputStream;

public record PhotoContentResource(InputStream stream, String contentType) {
}
