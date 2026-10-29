package org.genealogyarchive.storage;

/**
 * A volume's availability on this installation. A required volume that is not available is an
 * operational problem to report prominently; it does not by itself make the instance unready,
 * because safe database-only work can continue (owner decision D, F-010). An unavailable optional
 * volume (e.g. a disconnected backup drive) is normal.
 */
public record VolumeStatus(StorageVolume volume, VolumeAvailability availability) {

    public boolean problem() {
        return volume.active() && volume.required() && availability != VolumeAvailability.AVAILABLE;
    }
}
