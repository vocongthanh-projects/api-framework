package vn.ots.qc.api.core.context;

import vn.ots.qc.api.core.auth.Actor;
import vn.ots.qc.api.core.trace.ExchangeTrace;
import vn.ots.qc.api.core.trace.TraceSink;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TestContext implements TraceSink {
    private final String testId;
    private final Instant startedAt;
    private final Map<String, Actor> actors = new ConcurrentHashMap<>();
    private final List<ExchangeTrace> traces = Collections.synchronizedList(new ArrayList<>());

    public TestContext(String testId) {
        this.testId = require(testId, "testId");
        this.startedAt = Instant.now();
    }

    private String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value;
    }

    public String testId() { return testId; }
    public Instant startedAt() { return startedAt; }
    public void actor(Actor actor) { actors.put(actor.alias(), actor); }
    public Actor actor(String alias) {
        Actor actor = actors.get(alias);
        if (actor == null) throw new IllegalArgumentException("Unknown actor: " + alias);
        return actor;
    }
    public Map<String, Actor> actors() { return Map.copyOf(actors); }
    @Override public void record(ExchangeTrace trace) { traces.add(trace); }
    public List<ExchangeTrace> traces() { synchronized (traces) { return List.copyOf(traces); } }
}
