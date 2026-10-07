package com.example.kai.repository;

import java.io.IOException;

import com.example.kai.config.KaiProperties.Target;

/** Convenience base class for connectors that Kai can analyse but must not modify. */
public abstract class ReadOnlyKnowledgeSourceAdapter implements KnowledgeSourceAdapter {

    @Override
    public boolean canWrite(Target source, String id) {
        return false;
    }

    @Override
    public void write(Target source, String id, String content) throws IOException {
        throw new IOException(type() + " source is read-only in Kai");
    }

    @Override
    public void writeBytes(Target source, String id, byte[] content) throws IOException {
        throw new IOException(type() + " source is read-only in Kai");
    }
}
