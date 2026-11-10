package com.king.esb.admin.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted route definition. Denormalised columns ({@code name}, {@code path}, {@code method},
 * {@code enabled}) support fast lookup and uniqueness; the full {@link com.king.esb.core.route.RouteDefinition}
 * is stored as JSON in {@code config_json} so schema evolution needs no DDL change.
 */
@Entity
@Table(name = "esb_route")
public class RouteRecord {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "name", length = 255, nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "path", length = 512, nullable = false)
    private String path;

    @Column(name = "method", length = 16, nullable = false)
    private String method;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "config_json", nullable = false, columnDefinition = "text")
    private String configJson;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
