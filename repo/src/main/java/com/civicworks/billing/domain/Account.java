package com.civicworks.billing.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    // Resident identifier — sensitive PII. The plaintext is never stored:
    // we keep an AES-GCM ciphertext for authorized retrieval and a SHA-256
    // hash for deterministic lookup/dedupe. Mirrors users.encrypted_resident_id.
    @Column(name = "encrypted_resident_id")
    private String encryptedResidentId;

    @Column(name = "resident_id_hash", length = 64)
    private String residentIdHash;

    @Column(name = "address_line1", length = 200) private String addressLine1;
    @Column(name = "address_line2", length = 200) private String addressLine2;
    @Column(length = 120) private String city;
    @Column(length = 60)  private String state;
    @Column(name = "postal_code", length = 20) private String postalCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getEncryptedResidentId() { return encryptedResidentId; }
    public void setEncryptedResidentId(String encryptedResidentId) { this.encryptedResidentId = encryptedResidentId; }
    public String getResidentIdHash() { return residentIdHash; }
    public void setResidentIdHash(String residentIdHash) { this.residentIdHash = residentIdHash; }
}
