package com.kdd.setting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting {

    @Id
    @Column(name = "key_name", length = 100, nullable = false)
    private String key;

    @Column(name = "value", length = 500, nullable = false)
    private String value;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AppSetting(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
    }

    public void updateValue(String value) {
        this.value = value;
    }
}
