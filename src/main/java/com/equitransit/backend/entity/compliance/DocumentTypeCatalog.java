package com.equitransit.backend.entity.compliance;





/** Explicit row mapping for the existing DocumentTypeCatalog table. */
public class DocumentTypeCatalog {
    private String typeCode;
    private String displayName;
    private String description;
    private boolean active;

    /** @return the persisted typeCode */
    public String getTypeCode() {
        return typeCode;
    }

    /** @param value the persisted typeCode */
    public void setTypeCode(String value) {
        this.typeCode = value;
    }

    /** @return the persisted displayName */
    public String getDisplayName() {
        return displayName;
    }

    /** @param value the persisted displayName */
    public void setDisplayName(String value) {
        this.displayName = value;
    }

    /** @return the persisted description */
    public String getDescription() {
        return description;
    }

    /** @param value the persisted description */
    public void setDescription(String value) {
        this.description = value;
    }

    /** @return the persisted active */
    public boolean isActive() {
        return active;
    }

    /** @param value the persisted active */
    public void setActive(boolean value) {
        this.active = value;
    }
}
