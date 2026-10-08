import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { clearSession, getSession } from "../auth";
import "../styles/global.css";
import "../styles/dashboard.css";

type AuditLogItem = {
  auditId: number;
  configId: number;
  schemaName: string;
  tableName: string;
  operation: string;
  transactionId?: string | null;
  beforeState: string | null;
  afterState: string | null;
  changedBy?: string | null;
  eventTimestamp: string;
  logHash: string;
  prevHash?: string | null;
  chainSequence: number;
};

type JsonState = Record<string, unknown>;

type ComparisonRow = {
  keyName: string;
  beforeValue: unknown;
  afterValue: unknown;
  changed: boolean;
};

const SENSITIVE_FIELDS = new Set([
  "password",
  "password_hash",
  "encrypted_password",
  "token",
  "access_token",
  "refresh_token",
  "secret",
]);

const parseJsonState = (
  raw: string | null | undefined
): JsonState | null => {
  if (!raw) {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(raw);

    if (
      parsed !== null &&
      typeof parsed === "object" &&
      !Array.isArray(parsed)
    ) {
      return parsed as JsonState;
    }

    return null;
  } catch {
    return null;
  }
};

const sanitizeState = (
  state: JsonState | null
): JsonState | null => {
  if (!state) {
    return null;
  }

  return Object.fromEntries(
    Object.entries(state).filter(
      ([key]) => !SENSITIVE_FIELDS.has(key.toLowerCase())
    )
  );
};

const formatTimestamp = (value: string) => {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
};

const formatValue = (value: unknown) => {
  if (value === null || value === undefined) {
    return "—";
  }

  if (typeof value === "string") {
    return value;
  }

  if (
    typeof value === "number" ||
    typeof value === "boolean"
  ) {
    return String(value);
  }

  return JSON.stringify(value, null, 2);
};

const getRecordId = (state: JsonState | null) => {
  if (!state) {
    return null;
  }

  const preferredKeys = [
    "user_id",
    "id",
    "record_id",
    "project_id",
    "connection_id",
    "config_id",
  ];

  for (const key of preferredKeys) {
    if (key in state && state[key] != null) {
      return String(state[key]);
    }
  }

  const fallbackKey = Object.keys(state).find(
    (key) =>
      key.toLowerCase().endsWith("_id") ||
      key.toLowerCase() === "id"
  );

  if (!fallbackKey || state[fallbackKey] == null) {
    return null;
  }

  return String(state[fallbackKey]);
};

const buildComparisonRows = (
  beforeState: JsonState | null,
  afterState: JsonState | null
): ComparisonRow[] => {
  const keys = Array.from(
    new Set([
      ...(beforeState ? Object.keys(beforeState) : []),
      ...(afterState ? Object.keys(afterState) : []),
    ])
  );

  return keys.map((key) => {
    const beforeValue = beforeState?.[key];
    const afterValue = afterState?.[key];

    return {
      keyName: key,
      beforeValue,
      afterValue,
      changed:
        JSON.stringify(beforeValue) !==
        JSON.stringify(afterValue),
    };
  });
};

const getOperationLabel = (operation: string) => {
  switch (operation) {
    case "INSERT":
      return "Created";
    case "UPDATE":
      return "Updated";
    case "DELETE":
      return "Deleted";
    default:
      return operation;
  }
};

function AuditLogs() {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();
  const session = getSession();

  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [operation, setOperation] = useState("ALL");
  const [tableFilter, setTableFilter] = useState("ALL");
  const [userFilter, setUserFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [searchText, setSearchText] = useState("");
  const [refreshing, setRefreshing] = useState(false);

  const username = session?.username ?? "User";
  const role = session?.role ?? "USER";

  const handleUnauthorized = useCallback(() => {
    clearSession();
    navigate("/login", { replace: true });
  }, [navigate]);

  const loadLogs = useCallback(
    async (showRefreshState = false) => {
      if (!session?.accessToken) {
        handleUnauthorized();
        return;
      }

      if (!projectId) {
        setError("Project ID is missing.");
        setLoading(false);
        return;
      }

      if (showRefreshState) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }

      try {
        const params = new URLSearchParams();

        if (operation !== "ALL") {
          params.set("operation", operation);
        }

        if (tableFilter !== "ALL") {
          params.set("table", tableFilter);
        }

        if (userFilter.trim()) {
          params.set("changedBy", userFilter.trim());
        }

        if (dateFrom) {
          params.set("dateFrom", dateFrom);
        }

        if (dateTo) {
          params.set("dateTo", dateTo);
        }

        const queryString = params.toString();

        const response = await fetch(
          `http://localhost:8080/audit-logs/project/${projectId}${
            queryString ? `?${queryString}` : ""
          }`,
          {
            headers: {
              Authorization: `Bearer ${session.accessToken}`,
            },
          }
        );

        if (!response.ok) {
          if (response.status === 401) {
            handleUnauthorized();
            return;
          }

          if (response.status === 403) {
            setError(
              "You are not allowed to view these audit logs."
            );
          } else if (response.status === 404) {
            setError(
              "No audit log data was found for this project."
            );
          } else if (response.status >= 500) {
            setError(
              "The audit log service is unavailable right now."
            );
          } else {
            setError("Unable to load audit logs.");
          }

          setLogs([]);
          return;
        }

        const data: AuditLogItem[] = await response.json();

        setLogs(data);
        setError("");
      } catch {
        setError(
          "Unable to connect to DataTrail. Please try again."
        );
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [
      session?.accessToken,
      projectId,
      operation,
      tableFilter,
      userFilter,
      dateFrom,
      dateTo,
      handleUnauthorized,
    ]
  );

  useEffect(() => {
    void loadLogs();
  }, [loadLogs]);

  const tableOptions = useMemo(() => {
    const values = new Set(
      logs.map((log) => log.tableName)
    );

    return Array.from(values).sort();
  }, [logs]);

  const filteredLogs = useMemo(() => {
    const query = searchText.trim().toLowerCase();

    if (!query) {
      return logs;
    }

    return logs.filter((log) => {
      const beforeState = sanitizeState(
        parseJsonState(log.beforeState)
      );

      const afterState = sanitizeState(
        parseJsonState(log.afterState)
      );

      const recordId = getRecordId(
        beforeState ?? afterState
      );

      const haystack = [
        log.operation,
        log.tableName,
        log.schemaName,
        log.changedBy ?? "",
        log.transactionId ?? "",
        String(log.auditId),
        String(recordId ?? ""),
        JSON.stringify(beforeState ?? {}),
        JSON.stringify(afterState ?? {}),
      ]
        .join(" ")
        .toLowerCase();

      return haystack.includes(query);
    });
  }, [logs, searchText]);

  const clearFilters = () => {
    setOperation("ALL");
    setTableFilter("ALL");
    setUserFilter("");
    setDateFrom("");
    setDateTo("");
    setSearchText("");
  };

  if (!projectId) {
    return (
      <main className="dashboard-page">
        <aside className="dashboard-sidebar">
          <div className="dashboard-logo">
            <span className="logo-mark">T</span>
            <span className="logo-text">TraceDB</span>
          </div>
        </aside>

        <section className="dashboard-content">
          <header className="dashboard-header">
            <div>
              <p className="dashboard-eyebrow">
                PROVENANCE INTELLIGENCE ENGINE
              </p>

              <h1>Audit Logs</h1>
            </div>
          </header>

          <section className="dashboard-panel audit-empty">
            <p className="section-label">
              PROJECT REQUIRED
            </p>

            <h3>
              Select a project to view audit logs.
            </h3>

            <button
              className="new-project-button"
              onClick={() => navigate("/projects")}
            >
              Open projects
            </button>
          </section>
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard-page">
      {/* SIDEBAR */}
      <aside className="dashboard-sidebar">
        <div className="dashboard-logo">
          <span className="logo-mark">T</span>
          <span className="logo-text">TraceDB</span>
        </div>

        <nav className="dashboard-nav">
          <button
            className="nav-item"
            onClick={() => navigate("/dashboard")}
          >
            <span>⌂</span>
            Dashboard
          </button>

          <button
            className="nav-item"
            onClick={() => navigate("/projects")}
          >
            <span>▣</span>
            Projects
          </button>

          <button
            className="nav-item"
            onClick={() =>
              navigate(`/projects/${projectId}`)
            }
          >
            <span>⌂</span>
            Overview
          </button>

          <button
            className="nav-item"
            onClick={() =>
              navigate(
                `/projects/${projectId}/connections`
              )
            }
          >
            <span>◉</span>
            Database
          </button>

          <button
            className="nav-item active"
            onClick={() =>
              navigate(
                `/projects/${projectId}/audit-logs`
              )
            }
          >
            <span>⌁</span>
            Audit Logs
          </button>
        </nav>
      </aside>

      {/* MAIN */}
      <section className="dashboard-content">
        <header className="dashboard-header">
          <div>
            <p className="dashboard-eyebrow">
              PROVENANCE INTELLIGENCE ENGINE
            </p>

            <h1>Audit Logs</h1>
          </div>

          <div className="dashboard-user">
            <div>
              <strong>{username}</strong>
              <small>{role}</small>
            </div>

            <div className="user-avatar">
              {username.charAt(0).toUpperCase()}
            </div>
          </div>
        </header>

        {/* INTRO */}
        <section className="dashboard-welcome">
          <div>
            <p className="section-label">
              DATABASE PROVENANCE
            </p>

            <h2>
              Track who changed what, when, and how the
              data changed.
            </h2>

            <p>
              Every captured database event remains part
              of the project history.
            </p>
          </div>

          <button
            className="new-project-button"
            onClick={() =>
              navigate(`/projects/${projectId}`)
            }
          >
            ← Back to project
          </button>
        </section>

        {/* FILTERS */}
        <section className="dashboard-panel audit-toolbar">
          <div className="audit-toolbar-header">
            <div>
              <p className="section-label">
                INVESTIGATION
              </p>

              <h3>
                Search and filter changes
              </h3>
            </div>

            <div className="audit-toolbar-actions">
              <button
                className="panel-link"
                onClick={clearFilters}
              >
                Clear filters
              </button>

              <button
                className="new-project-button"
                onClick={() => void loadLogs(true)}
                disabled={refreshing}
              >
                {refreshing ? "Refreshing..." : "Refresh"}
              </button>
            </div>
          </div>

          <div className="audit-filters-grid">
            <label>
              Operation

              <select
                value={operation}
                onChange={(event) =>
                  setOperation(event.target.value)
                }
              >
                <option value="ALL">
                  All operations
                </option>
                <option value="INSERT">INSERT</option>
                <option value="UPDATE">UPDATE</option>
                <option value="DELETE">DELETE</option>
              </select>
            </label>

            <label>
              Table

              <select
                value={tableFilter}
                onChange={(event) =>
                  setTableFilter(event.target.value)
                }
              >
                <option value="ALL">All tables</option>

                {tableOptions.map((tableName) => (
                  <option
                    key={tableName}
                    value={tableName}
                  >
                    {tableName}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Changed by

              <input
                type="text"
                value={userFilter}
                onChange={(event) =>
                  setUserFilter(event.target.value)
                }
                placeholder="postgres"
              />
            </label>

            <label>
              From

              <input
                type="datetime-local"
                value={dateFrom}
                onChange={(event) =>
                  setDateFrom(event.target.value)
                }
              />
            </label>

            <label>
              To

              <input
                type="datetime-local"
                value={dateTo}
                onChange={(event) =>
                  setDateTo(event.target.value)
                }
              />
            </label>

            <label>
              Search

              <input
                type="search"
                value={searchText}
                onChange={(event) =>
                  setSearchText(event.target.value)
                }
                placeholder="table, record, email..."
              />
            </label>
          </div>

          <div className="audit-result-summary">
            <span>
              Showing{" "}
              <strong>{filteredLogs.length}</strong>{" "}
              {filteredLogs.length === 1
                ? "change"
                : "changes"}
            </span>

            <span>
              Project #{projectId}
            </span>
          </div>
        </section>

        {/* ERROR */}
        {error && (
          <section
            className="dashboard-panel audit-empty"
            role="alert"
          >
            <p className="section-label">
              AUDIT ERROR
            </p>

            <h3>{error}</h3>
          </section>
        )}

        {/* LOADING / EMPTY / RESULTS */}
        {loading ? (
          <section className="dashboard-panel audit-empty">
            <p className="section-label">
              LOADING
            </p>

            <h3>Loading audit events...</h3>
          </section>
        ) : filteredLogs.length === 0 ? (
          <section className="dashboard-panel audit-empty">
            <p className="section-label">
              NO MATCHING EVENTS
            </p>

            <h3>
              No database changes match the current
              filters.
            </h3>

            <p>
              Configure a monitored table and perform an
              INSERT, UPDATE, or DELETE to generate an
              audit event.
            </p>

            <div className="audit-empty-actions">
              <button
                className="new-project-button"
                onClick={clearFilters}
              >
                Clear filters
              </button>

              <button
                className="panel-link"
                onClick={() =>
                  navigate(
                    `/projects/${projectId}/connections`
                  )
                }
              >
                Open Database →
              </button>
            </div>
          </section>
        ) : (
          <section className="audit-timeline">
            {filteredLogs.map((log) => {
              const beforeState = sanitizeState(
                parseJsonState(log.beforeState)
              );

              const afterState = sanitizeState(
                parseJsonState(log.afterState)
              );

              const recordId = getRecordId(
                beforeState ?? afterState
              );

              const comparisonRows =
                log.operation === "UPDATE"
                  ? buildComparisonRows(
                      beforeState,
                      afterState
                    )
                  : [];

              const changedRows =
                comparisonRows.filter(
                  (row) => row.changed
                );

              const unchangedRows =
                comparisonRows.filter(
                  (row) => !row.changed
                );

              return (
                <article
                  className="audit-card dashboard-panel"
                  key={log.auditId}
                >
                  {/* HEADER */}
                  <div className="audit-card-header">
                    <div className="audit-event-main">
                      <span
                        className={`audit-badge audit-badge-${log.operation.toLowerCase()}`}
                      >
                        {log.operation}
                      </span>

                      <div>
                        <h3>
                          {log.schemaName}.
                          {log.tableName}
                        </h3>

                        <p>
                          {getOperationLabel(
                            log.operation
                          )}{" "}
                          database record
                        </p>
                      </div>
                    </div>

                    <div className="audit-card-meta">
                      <span>
                        Record ID:{" "}
                        <strong>
                          {recordId ?? "n/a"}
                        </strong>
                      </span>

                      <span>
                        Changed by:{" "}
                        <strong>
                          {log.changedBy || "system"}
                        </strong>
                      </span>

                      <span>
                        {formatTimestamp(
                          log.eventTimestamp
                        )}
                      </span>
                    </div>
                  </div>

                  {/* UPDATE */}
                  {log.operation === "UPDATE" && (
                    <>
                      <div className="audit-compare">
                        <div className="audit-compare-panel">
                          <div className="audit-panel-heading">
                            <p>Before</p>
                            <span>Previous state</span>
                          </div>

                          {changedRows.length > 0 ? (
                            <div className="audit-compare-list">
                              {changedRows.map(
                                (row) => (
                                  <div
                                    key={`${log.auditId}-before-${row.keyName}`}
                                    className="compare-row changed"
                                  >
                                    <span>
                                      {row.keyName}
                                    </span>

                                    <strong>
                                      {formatValue(
                                        row.beforeValue
                                      )}
                                    </strong>
                                  </div>
                                )
                              )}
                            </div>
                          ) : (
                            <p className="muted">
                              No changed fields detected.
                            </p>
                          )}
                        </div>

                        <div className="audit-compare-panel">
                          <div className="audit-panel-heading">
                            <p>After</p>
                            <span>New state</span>
                          </div>

                          {changedRows.length > 0 ? (
                            <div className="audit-compare-list">
                              {changedRows.map(
                                (row) => (
                                  <div
                                    key={`${log.auditId}-after-${row.keyName}`}
                                    className="compare-row changed"
                                  >
                                    <span>
                                      {row.keyName}
                                    </span>

                                    <strong>
                                      {formatValue(
                                        row.afterValue
                                      )}
                                    </strong>
                                  </div>
                                )
                              )}
                            </div>
                          ) : (
                            <p className="muted">
                              No changed fields detected.
                            </p>
                          )}
                        </div>
                      </div>

                      {changedRows.length > 0 && (
                        <div className="audit-change-list">
                          <div className="audit-panel-heading">
                            <p>Changed fields</p>

                            <span>
                              {changedRows.length}{" "}
                              {changedRows.length === 1
                                ? "field"
                                : "fields"}
                            </span>
                          </div>

                          <div className="audit-change-items">
                            {changedRows.map(
                              (row) => (
                                <span
                                  key={`${log.auditId}-${row.keyName}`}
                                  className="audit-change-tag"
                                >
                                  {row.keyName}
                                </span>
                              )
                            )}
                          </div>
                        </div>
                      )}

                      {unchangedRows.length > 0 && (
                        <details className="audit-unchanged-details">
                          <summary>
                            Show unchanged fields (
                            {unchangedRows.length})
                          </summary>

                          <div className="audit-compare-list">
                            {unchangedRows.map(
                              (row) => (
                                <div
                                  key={`${log.auditId}-unchanged-${row.keyName}`}
                                  className="compare-row unchanged"
                                >
                                  <span>
                                    {row.keyName}
                                  </span>

                                  <strong>
                                    {formatValue(
                                      row.afterValue
                                    )}
                                  </strong>
                                </div>
                              )
                            )}
                          </div>
                        </details>
                      )}
                    </>
                  )}

                  {/* INSERT */}
                  {log.operation === "INSERT" && (
                    <div className="audit-compare single-state">
                      <div className="audit-compare-panel full-width">
                        <div className="audit-panel-heading">
                          <p>New record</p>
                          <span>Inserted state</span>
                        </div>

                        {afterState &&
                        Object.keys(afterState).length >
                          0 ? (
                          <div className="audit-compare-list">
                            {Object.entries(
                              afterState
                            ).map(
                              ([key, value]) => (
                                <div
                                  key={`${log.auditId}-${key}`}
                                  className="compare-row"
                                >
                                  <span>{key}</span>

                                  <strong>
                                    {formatValue(value)}
                                  </strong>
                                </div>
                              )
                            )}
                          </div>
                        ) : (
                          <p className="muted">
                            No inserted values were
                            captured.
                          </p>
                        )}
                      </div>
                    </div>
                  )}

                  {/* DELETE */}
                  {log.operation === "DELETE" && (
                    <div className="audit-compare single-state">
                      <div className="audit-compare-panel full-width">
                        <div className="audit-panel-heading">
                          <p>Deleted record</p>
                          <span>Previous state</span>
                        </div>

                        {beforeState &&
                        Object.keys(beforeState).length >
                          0 ? (
                          <div className="audit-compare-list">
                            {Object.entries(
                              beforeState
                            ).map(
                              ([key, value]) => (
                                <div
                                  key={`${log.auditId}-${key}-delete`}
                                  className="compare-row"
                                >
                                  <span>{key}</span>

                                  <strong>
                                    {formatValue(value)}
                                  </strong>
                                </div>
                              )
                            )}
                          </div>
                        ) : (
                          <p className="muted">
                            No deleted values were
                            captured.
                          </p>
                        )}
                      </div>
                    </div>
                  )}

                  {/* TECHNICAL DETAILS */}
                  <details className="audit-technical-details">
                    <summary>
                      Technical details
                    </summary>

                    <div className="audit-technical-grid">
                      <div>
                        <span>Transaction ID</span>
                        <code>
                          {log.transactionId || "n/a"}
                        </code>
                      </div>

                      <div>
                        <span>Chain sequence</span>
                        <strong>
                          #{log.chainSequence}
                        </strong>
                      </div>

                      <div>
                        <span>Previous hash</span>
                        <code>
                          {log.prevHash
                            ? `${log.prevHash.slice(
                                0,
                                16
                              )}...`
                            : "Genesis"}
                        </code>
                      </div>

                      <div>
                        <span>Event hash</span>
                        <code>
                          {log.logHash
                            ? `${log.logHash.slice(
                                0,
                                16
                              )}...`
                            : "Unavailable"}
                        </code>
                      </div>

                      <div>
                        <span>Audit ID</span>
                        <strong>
                          #{log.auditId}
                        </strong>
                      </div>

                      <div>
                        <span>Configuration ID</span>
                        <strong>
                          #{log.configId}
                        </strong>
                      </div>
                    </div>
                  </details>
                </article>
              );
            })}
          </section>
        )}
      </section>
    </main>
  );
}

export default AuditLogs;