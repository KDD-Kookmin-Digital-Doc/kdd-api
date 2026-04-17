package com.kdd.document.repository;

import java.time.LocalDateTime;

public interface SearchByPopularityProjection {
    Long getId();
    String getTitle();
    String getCategoryName();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
