package com.ghosted907.clusterdiag;

/** The live binder descriptor is not the CiService interface. No transact was made. */
final class DescriptorMismatch extends Exception {
    final String remote;

    DescriptorMismatch(String remote) {
        super(remote == null ? "" : remote);
        this.remote = remote;
    }
}
