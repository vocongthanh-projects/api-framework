package vn.ots.qc.api.fixture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks created resources and cleans them in reverse creation order. */
public final class ResourceLedger implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ResourceLedger.class);
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public synchronized void register(String type, String id, CheckedRunnable cleanup) {
        if (closed.get()) throw new IllegalStateException("ResourceLedger is already closed");
        if (cleanup == null) throw new IllegalArgumentException("cleanup action is required");
        entries.push(new Entry(type + ":" + id, cleanup));
    }

    public synchronized int size() { return entries.size(); }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        List<String> failed = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        while (!entries.isEmpty()) {
            Entry entry = entries.pop();
            try { entry.cleanup.run(); }
            catch (Exception error) {
                failed.add(entry.label);
                errors.add(error);
                log.error("Cleanup failed for {}", entry.label, error);
            }
        }
        if (!failed.isEmpty()) throw new CleanupException(failed, errors);
    }

    private record Entry(String label, CheckedRunnable cleanup) {}
}

