package com.equitransit.backend.entity.compliance;


import java.time.LocalDate;
import java.time.OffsetDateTime;


/** Explicit row mapping for the existing DocumentVersion table. */
public class DocumentVersion {
    private long documentVersionId;
    private long documentId;
    private int versionNo;
    private String documentNumber;
    private String fileKey;
    private String fileHash;
    private LocalDate issuedDate;
    private LocalDate expiryDate;
    private OffsetDateTime uploadedAt;
    private Long uploadedBy;
    private String originalFileName;
    private String contentType;
    private Long fileSizeBytes;

    /** @return the persisted documentVersionId */
    public long getDocumentVersionId() {
        return documentVersionId;
    }

    /** @param value the persisted documentVersionId */
    public void setDocumentVersionId(long value) {
        this.documentVersionId = value;
    }

    /** @return the persisted documentId */
    public long getDocumentId() {
        return documentId;
    }

    /** @param value the persisted documentId */
    public void setDocumentId(long value) {
        this.documentId = value;
    }

    /** @return the persisted versionNo */
    public int getVersionNo() {
        return versionNo;
    }

    /** @param value the persisted versionNo */
    public void setVersionNo(int value) {
        this.versionNo = value;
    }

    /** @return the persisted documentNumber */
    public String getDocumentNumber() {
        return documentNumber;
    }

    /** @param value the persisted documentNumber */
    public void setDocumentNumber(String value) {
        this.documentNumber = value;
    }

    /** @return the persisted fileKey */
    public String getFileKey() {
        return fileKey;
    }

    /** @param value the persisted fileKey */
    public void setFileKey(String value) {
        this.fileKey = value;
    }

    /** @return the persisted fileHash */
    public String getFileHash() {
        return fileHash;
    }

    /** @param value the persisted fileHash */
    public void setFileHash(String value) {
        this.fileHash = value;
    }

    /** @return the persisted issuedDate */
    public LocalDate getIssuedDate() {
        return issuedDate;
    }

    /** @param value the persisted issuedDate */
    public void setIssuedDate(LocalDate value) {
        this.issuedDate = value;
    }

    /** @return the persisted expiryDate */
    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    /** @param value the persisted expiryDate */
    public void setExpiryDate(LocalDate value) {
        this.expiryDate = value;
    }

    /** @return the persisted uploadedAt */
    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    /** @param value the persisted uploadedAt */
    public void setUploadedAt(OffsetDateTime value) {
        this.uploadedAt = value;
    }

    /** @return the persisted uploadedBy */
    public Long getUploadedBy() {
        return uploadedBy;
    }

    /** @param value the persisted uploadedBy */
    public void setUploadedBy(Long value) {
        this.uploadedBy = value;
    }

    /** @return the persisted originalFileName */
    public String getOriginalFileName() {
        return originalFileName;
    }

    /** @param value the persisted originalFileName */
    public void setOriginalFileName(String value) {
        this.originalFileName = value;
    }

    /** @return the persisted contentType */
    public String getContentType() {
        return contentType;
    }

    /** @param value the persisted contentType */
    public void setContentType(String value) {
        this.contentType = value;
    }

    /** @return the persisted fileSizeBytes */
    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    /** @param value the persisted fileSizeBytes */
    public void setFileSizeBytes(Long value) {
        this.fileSizeBytes = value;
    }
}
