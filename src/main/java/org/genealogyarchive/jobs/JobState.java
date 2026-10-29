package org.genealogyarchive.jobs;

/** Lifecycle of a background job. */
public enum JobState {
    QUEUED, RUNNING, SUCCEEDED, FAILED
}
