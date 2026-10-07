package com.example.kai.repository;

import java.io.IOException;
import java.util.List;

import com.example.kai.config.KaiProperties.Target;

/**
 * Source-agnostic contract between Kai and a user's knowledge base.
 *
 * Kai's agents never need to know whether a document came from a local folder,
 * SharePoint, S3, OneDrive, Google Drive, or another repository. An adapter turns
 * that source into the small set of operations Kai needs.
 */
public interface KnowledgeSourceAdapter {

    /** Stable adapter key used by kai.source.<id>.type, e.g. "local" or "sharepoint". */
    String type();

    /** Optional startup validation for provider authentication / location / permissions. */
    default void validate(Target source) throws IOException {
        // No-op by default. Cloud adapters can make a lightweight provider call here.
    }

    /** List document ids available under one configured source. */
    List<String> list(Target source) throws IOException;

    /** Return plain text used by the Kai agents for impact analysis. */
    String read(Target source, String id) throws IOException;

    /** Whether Kai is allowed and technically able to rewrite this document. */
    boolean canWrite(Target source, String id);

    /** Write user-approved text back to the connected source. */
    void write(Target source, String id, String content) throws IOException;

    /** Exact source bytes used for backup and rollback. */
    byte[] readBytes(Target source, String id) throws IOException;

    /** Restore exact bytes during rollback. */
    void writeBytes(Target source, String id, byte[] content) throws IOException;
}
