package org.genealogyarchive.preservation;

/** The Phase 2 file-preservation services, assembled once and shared by web, worker and command line. */
public record PreservationServices(
        PreservationContext context,
        IngestionService ingestion,
        OperationProcessor processor,
        DestructionProcessor destruction,
        RecoveryService recovery,
        FileService files,
        IntegrityService integrity,
        RepairService repair,
        OperationAdminService operations,
        VolumePreservationService volumes,
        ThumbnailService thumbnails) {

    public static PreservationServices create(PreservationContext ctx) {
        IngestionService ingestion = new IngestionService(ctx);
        OperationProcessor processor = new OperationProcessor(ctx);
        DestructionProcessor destruction = new DestructionProcessor(ctx);
        return new PreservationServices(ctx, ingestion, processor, destruction,
                new RecoveryService(ctx, processor, destruction), new FileService(ctx), new IntegrityService(ctx),
                new RepairService(ctx, ingestion), new OperationAdminService(ctx), new VolumePreservationService(ctx),
                new ThumbnailService(ctx));
    }
}
