package com.personalhub.sync.application;

public class SyncAlreadyRunningException extends RuntimeException {
    public SyncAlreadyRunningException() { super("A GitHub synchronization is already running"); }
}
