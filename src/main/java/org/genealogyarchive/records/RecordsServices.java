package org.genealogyarchive.records;

/** The Phase 3 records services, assembled once and shared by web and command line. */
public record RecordsServices(
        RecordsContext context,
        VocabularyService vocabularies,
        CatalogService catalog,
        CaptureSessionService sessions,
        RecordService records,
        CitationService citations,
        GroupingService grouping,
        ConsolidationService consolidation) {

    public static RecordsServices create(RecordsContext ctx) {
        VocabularyService vocabularies = new VocabularyService(ctx);
        RecordService records = new RecordService(ctx, vocabularies);
        CaptureSessionService sessions = new CaptureSessionService(ctx, vocabularies);
        return new RecordsServices(ctx, vocabularies, new CatalogService(ctx), sessions, records, new CitationService(ctx),
                new GroupingService(ctx, records, sessions), new ConsolidationService(ctx, records));
    }
}
