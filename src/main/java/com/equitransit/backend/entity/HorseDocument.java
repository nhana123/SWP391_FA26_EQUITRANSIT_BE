package com.equitransit.backend.entity;


import java.time.OffsetDateTime;


/** Explicit row mapping for the existing HorseDocument table. */
public class HorseDocument {
    private long documentId;
    private long horseId;
    private String documentType;
    private OffsetDateTime createdAt;

    /** @return the persisted documentId */
    public long getDocumentId() {
        return documentId;
    }

    /** @param value the persisted documentId */
    public void setDocumentId(long value) {
        this.documentId = value;
    }

    /** @return the persisted horseId */
    public long getHorseId() {
        return horseId;
    }

    /** @param value the persisted horseId */
    public void setHorseId(long value) {
        this.horseId = value;
    }

    /** @return the persisted documentType */
    public String getDocumentType() {
        return documentType;
    }

    /** @param value the persisted documentType */
    public void setDocumentType(String value) {
        this.documentType = value;
    }

    /** @return the persisted createdAt */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param value the persisted createdAt */
    public void setCreatedAt(OffsetDateTime value) {
        this.createdAt = value;
    }
}
