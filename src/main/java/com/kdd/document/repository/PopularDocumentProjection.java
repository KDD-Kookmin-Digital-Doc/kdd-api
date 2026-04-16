package com.kdd.document.repository;

import java.time.LocalDateTime;

public interface PopularDocumentProjection {
    Long getId();
    String getTitle();
    String getCategoryName();
    long getViewCount();
    long getReferenceCount();
    long getPopularityScore();
    LocalDateTime getUpdatedAt();
}
