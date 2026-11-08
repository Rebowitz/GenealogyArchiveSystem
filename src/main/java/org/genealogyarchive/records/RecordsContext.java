package org.genealogyarchive.records;

import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.data.ArchivalRecordRepository;
import org.genealogyarchive.data.ArchiveInstanceRepository;
import org.genealogyarchive.data.CaptureSessionRepository;
import org.genealogyarchive.data.CatalogRepository;
import org.genealogyarchive.data.FriendlyIdSeriesRepository;
import org.genealogyarchive.data.GroupingRunRepository;
import org.genealogyarchive.data.OperationLocks;
import org.genealogyarchive.data.VocabularyRepository;
import org.genealogyarchive.history.ChangeRecorder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** What the Phase 3 records services share. */
public record RecordsContext(CatalogRepository catalog, CaptureSessionRepository sessions,
                             ArchivalRecordRepository records, VocabularyRepository vocabularies,
                             GroupingRunRepository runs, ArchivalFileRepository files,
                             ArchiveInstanceRepository archive, FriendlyIdSeriesRepository friendlyIds,
                             TransactionTemplate tx, ChangeRecorder recorder, OperationLocks locks, Clock clock) {
}
