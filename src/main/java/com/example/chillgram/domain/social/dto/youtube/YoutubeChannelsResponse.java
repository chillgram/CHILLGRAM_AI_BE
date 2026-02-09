package com.example.chillgram.domain.social.dto.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeChannelsResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, Snippet snippet, Statistics statistics) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(Long subscriberCount) {}
}