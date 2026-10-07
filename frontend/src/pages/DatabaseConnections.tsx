import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { clearSession, getSession } from "../auth";
import "../styles/global.css";
import "../styles/dashboard.css";

type DatabaseConnection = {
  connectionId: number;
  dbType: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  sslEnabled: boolean;
  connectionStatus: string;
  lastVerified: string | null;
};

type DatabaseSchema = { schemaName: string };

type DatabaseTable = {
  schemaName: string;
  tableName: string;
  tableType: string;
};

type MonitoringDraft = {
  monitorInsert: boolean;
  monitorUpdate: boolean;
  monitorDelete: boolean;
  sensitivityLevel: string;
  monitoringEnabled: boolean;
};

const DEFAULT_MONITORING_DRAFT: MonitoringDraft = {
  monitorInsert: true,
  monitorUpdate: true,
  monitorDelete: true,
  sensitivityLevel: "NORMAL",
  monitoringEnabled: true,
};

function DatabaseConnections() {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();
  const session = getSession();

  const username = session?.username ?? "User";
  const role = session?.role ?? "USER";

  const [connections, setConnections] = useState<
    DatabaseConnection[]
  >([]);

  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testingConnectionId, setTestingConnectionId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [testErrors, setTestErrors] = useState<Record<number, string>>({});
  const [activeDiscoveryConnectionId, setActiveDiscoveryConnectionId] = useState<number | null>(null);
  const [schemas, setSchemas] = useState<DatabaseSchema[]>([]);
  const [selectedSchema, setSelectedSchema] = useState("");
  const [tables, setTables] = useState<DatabaseTable[]>([]);
  const [selectedTables, setSelectedTables] = useState<string[]>([]);
  const [monitoringDrafts, setMonitoringDrafts] = useState<Record<string, MonitoringDraft>>({});
  const [discovering, setDiscovering] = useState(false);
  const [loadingTables, setLoadingTables] = useState(false);
  const [savingMonitoring, setSavingMonitoring] = useState(false);
  const [showMonitoringForm, setShowMonitoringForm] = useState(false);
  const [discoveryError, setDiscoveryError] = useState("");
  const [discoveryNotice, setDiscoveryNotice] = useState("");

  const [dbType, setDbType] = useState("PostgreSQL");
  const [host, setHost] = useState("");
  const [port, setPort] = useState("5432");
  const [databaseName, setDatabaseName] = useState("");
  const [dbUsername, setDbUsername] = useState("");
  const [password, setPassword] = useState("");
  const [sslEnabled, setSslEnabled] = useState(false);

  const loadConnections = async () => {
    if (!session?.accessToken) {
      navigate("/login");
      return;
    }

    if (!projectId) {
      setError("Project ID is missing.");
      setLoading(false);
      return;
    }

    try {
      setError("");

      const response = await fetch(
        `http://localhost:8080/database-connections/project/${projectId}`,
        {
          headers: {
            Authorization: `Bearer ${session.accessToken}`,
          },
        }
      );

      if (!response.ok) {
        if (response.status === 401) {
          clearSession();
          navigate("/login", { replace: true });
          return;
        }
        throw new Error("Failed to load database connections.");
      }

      const data: DatabaseConnection[] = await response.json();

      setConnections(data);
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Failed to load database connections."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConnections();
  }, [projectId]);

  const handleCreateConnection = async (
    event: FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    if (!session?.accessToken) {
      navigate("/login");
      return;
    }

    if (!projectId) {
      setError("Project ID is missing.");
      return;
    }

    setSaving(true);
    setError("");

    try {
      const response = await fetch(
        "http://localhost:8080/database-connections",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${session.accessToken}`,
          },
          body: JSON.stringify({
            projectId: Number(projectId),
            dbType,
            host,
            port: Number(port),
            databaseName,
            username: dbUsername,
            encryptedPassword: password,
            sslEnabled,
            connectionStatus: "CONFIGURED",
          }),
        }
      );

      if (!response.ok) {
        const message = await response.text();

        throw new Error(
          message || "Failed to create database connection."
        );
      }

      setHost("");
      setPort("5432");
      setDatabaseName("");
      setDbUsername("");
      setPassword("");
      setSslEnabled(false);
      setDbType("PostgreSQL");

      setShowForm(false);

      await loadConnections();
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Failed to create database connection."
      );
    } finally {
      setSaving(false);
    }
  };

  const handleApiError = async (response: Response, fallback: string) => {
    if (response.status === 401) {
      clearSession();
      navigate("/login", { replace: true });
      return "Your session has expired. Please log in again.";
    }
    if (response.status === 404) {
      return "Database connection not found.";
    }
    if (response.status >= 500) {
      return "Unable to connect to the target database.";
    }
    try {
      const body: { message?: string } = await response.json();
      return body.message || fallback;
    } catch {
      return fallback;
    }
  };

  const handleDiscoverDatabase = async (connection: DatabaseConnection) => {
    if (!session?.accessToken) {
      clearSession();
      navigate("/login", { replace: true });
      return;
    }
    if (connection.connectionStatus !== "CONNECTED") {
      setDiscoveryError("Test the database connection before discovering tables.");
      return;
    }
    if (!projectId) {
      setDiscoveryError("Project ID is missing.");
      return;
    }

    setActiveDiscoveryConnectionId(connection.connectionId);
    setSelectedSchema("");
    setSchemas([]);
    setTables([]);
    setSelectedTables([]);
    setShowMonitoringForm(false);
    setDiscoveryError("");
    setDiscoveryNotice("");
    setDiscovering(true);
    const requestUrl = `http://localhost:8080/database-connections/${connection.connectionId}/schemas?projectId=${projectId}`;
    console.info("Discover schemas request", {
      projectId,
      connectionId: connection.connectionId,
      requestUrl,
      method: "GET",
      authorizationHeaderPresent: Boolean(session.accessToken),
    });
    try {
      const response = await fetch(requestUrl, {
        headers: { Authorization: `Bearer ${session.accessToken}` },
      });
      if (!response.ok) {
        setDiscoveryError(await handleApiError(response, "Unable to discover database schemas."));
        return;
      }
      const result: DatabaseSchema[] = await response.json();
      setSchemas(result);
      if (result.length === 0) {
        setDiscoveryNotice("No user schemas found.");
      }
    } catch {
      setDiscoveryError("Unable to connect to the target database.");
    } finally {
      setDiscovering(false);
    }
  };

  const handleSelectSchema = async (schemaName: string) => {
    if (!session?.accessToken || !projectId || activeDiscoveryConnectionId === null) {
      return;
    }
    setSelectedSchema(schemaName);
    setTables([]);
    setSelectedTables([]);
    setShowMonitoringForm(false);
    setDiscoveryError("");
    setDiscoveryNotice("");
    setLoadingTables(true);
    const query = new URLSearchParams({ projectId, schemaName });
    const requestUrl = `http://localhost:8080/database-connections/${activeDiscoveryConnectionId}/tables?${query}`;
    console.info("Discover tables request", {
      projectId,
      connectionId: activeDiscoveryConnectionId,
      requestUrl,
      method: "GET",
      authorizationHeaderPresent: Boolean(session.accessToken),
    });
    try {
      const response = await fetch(requestUrl, {
        headers: { Authorization: `Bearer ${session.accessToken}` },
      });
      if (!response.ok) {
        setDiscoveryError(await handleApiError(response, "Unable to load tables in this schema."));
        return;
      }
      const result: DatabaseTable[] = await response.json();
      setTables(result);
      if (result.length === 0) {
        setDiscoveryNotice("No tables found in this schema.");
      }
    } catch {
      setDiscoveryError("Unable to connect to the target database.");
    } finally {
      setLoadingTables(false);
    }
  };

  const toggleTableSelection = (tableName: string) => {
    setSelectedTables((current) => current.includes(tableName)
      ? current.filter((selected) => selected !== tableName)
      : [...current, tableName]
    );
  };

  const updateMonitoringDraft = <K extends keyof MonitoringDraft>(
    tableName: string,
    field: K,
    value: MonitoringDraft[K]
  ) => {
    setMonitoringDrafts((current) => ({
      ...current,
      [tableName]: {
        ...(current[tableName] ?? DEFAULT_MONITORING_DRAFT),
        [field]: value,
      },
    }));
  };

  const openMonitoringConfiguration = () => {
    setMonitoringDrafts((current) => {
      const updated = { ...current };
      selectedTables.forEach((tableName) => {
        updated[tableName] ??= DEFAULT_MONITORING_DRAFT;
      });
      return updated;
    });
    setShowMonitoringForm(true);
    setDiscoveryError("");
  };

  const handleSaveMonitoring = async () => {
    if (!session?.accessToken || !projectId || activeDiscoveryConnectionId === null) {
      clearSession();
      navigate("/login", { replace: true });
      return;
    }
    setSavingMonitoring(true);
    setDiscoveryError("");
    let savedCount = 0;
    try {
      for (const tableName of selectedTables) {
        const draft = monitoringDrafts[tableName] ?? DEFAULT_MONITORING_DRAFT;
        const response = await fetch("http://localhost:8080/monitoring-configurations", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${session.accessToken}`,
          },
          body: JSON.stringify({
            projectId: Number(projectId),
            connectionId: activeDiscoveryConnectionId,
            schemaName: selectedSchema,
            tableName,
            ...draft,
          }),
        });
        if (!response.ok) {
          const message = await handleApiError(response, "Unable to save monitoring configuration.");
          setDiscoveryError(savedCount > 0
            ? `Saved ${savedCount} table configuration(s). ${message}`
            : message);
          return;
        }
        savedCount += 1;
      }
      setDiscoveryNotice(`${savedCount} monitoring configuration(s) saved.`);
      setShowMonitoringForm(false);
      setSelectedTables([]);
    } catch {
      setDiscoveryError(savedCount > 0
        ? `Saved ${savedCount} table configuration(s). Unable to connect to DataTrail.`
        : "Unable to connect to DataTrail. Please try again.");
    } finally {
      setSavingMonitoring(false);
    }
  };

  const handleDeleteConnection = async (connectionId: number) => {
    if (!session?.accessToken) {
      navigate("/login");
      return;
    }

    const confirmed = window.confirm(
      "Are you sure you want to delete this database connection?"
    );

    if (!confirmed) {
      return;
    }

    try {
      const response = await fetch(
        `http://localhost:8080/database-connections/${connectionId}`,
        {
          method: "DELETE",
          headers: {
            Authorization: `Bearer ${session.accessToken}`,
          },
        }
      );

      if (!response.ok) {
        throw new Error(await handleApiError(
          response,
          "Failed to delete database connection."
        ));
      }

      await loadConnections();
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Failed to delete database connection."
      );
    }
  };

  const handleTestConnection = async (connection: DatabaseConnection) => {
    if (!session?.accessToken) {
      navigate("/login");
      return;
    }

    setTestingConnectionId(connection.connectionId);
    setTestErrors((current) => {
      const updated = { ...current };
      delete updated[connection.connectionId];
      return updated;
    });

    try {
      const response = await fetch(
        `http://localhost:8080/database-connections/${connection.connectionId}/test`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${session.accessToken}`,
          },
        }
      );

      if (!response.ok) {
        let message = "Database connection test failed.";
        try {
          const body: { message?: string } = await response.json();
          message = body.message || message;
        } catch {
          // Keep the fallback message when the response is not JSON.
        }

        setConnections((current) => current.map((item) =>
          item.connectionId === connection.connectionId
            ? {
                ...item,
                connectionStatus: item.dbType.toLowerCase() === "postgresql"
                  ? "FAILED"
                  : "UNSUPPORTED",
              }
            : item
        ));
        setTestErrors((current) => ({
          ...current,
          [connection.connectionId]: message,
        }));
        return;
      }

      const result: DatabaseConnection = await response.json();
      setConnections((current) => current.map((item) =>
        item.connectionId === connection.connectionId
          ? {
              ...item,
              connectionStatus: result.connectionStatus,
              lastVerified: result.lastVerified,
            }
          : item
      ));
    } catch (error) {
      setConnections((current) => current.map((item) =>
        item.connectionId === connection.connectionId
          ? { ...item, connectionStatus: "FAILED" }
          : item
      ));
      setTestErrors((current) => ({
        ...current,
        [connection.connectionId]: error instanceof Error
          ? error.message
          : "Database connection test failed.",
      }));
    } finally {
      setTestingConnectionId(null);
    }
  };

  return (
    <main className="dashboard-page">

      {/* SIDEBAR */}

      <aside className="dashboard-sidebar">

        <div className="dashboard-logo">
          <span className="logo-mark">T</span>
          <span className="logo-text">TraceDB</span>
        </div>

        <nav className="dashboard-nav">

          <div
            className="nav-item"
            onClick={() => navigate("/dashboard")}
          >
            <span>⌂</span>
            Dashboard
          </div>

          <div
            className="nav-item active"
            onClick={() => navigate("/projects")}
          >
            <span>▣</span>
            Projects
          </div>

          <div className="nav-item active">
            <span>◉</span>
            Connections
          </div>

          <div className="nav-item">
            <span>◈</span>
            Monitoring
          </div>

          <div className="nav-item">
            <span>⌁</span>
            Audit Timeline
          </div>

          <div className="nav-item">
            <span>◇</span>
            Provenance
          </div>

          <div className="nav-item">
            <span>↶</span>
            Recovery
          </div>

        </nav>

      </aside>

      {/* MAIN */}

      <section className="dashboard-content">

        {/* HEADER */}

        <header className="dashboard-header">

          <div>

            <p className="dashboard-eyebrow">
              PROVENANCE INTELLIGENCE ENGINE
            </p>

            <h1>
              Database Connections
            </h1>

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

        {/* BACK */}

        <button
          className="panel-link"
          onClick={() =>
            navigate(`/projects/${projectId}`)
          }
        >
          ← Back to Project
        </button>

        {/* INTRO */}

        <section className="dashboard-welcome">

          <div>

            <p className="section-label">
              DATABASE CONFIGURATION
            </p>

            <h2>
              Connect an external database.
            </h2>

            <p>
              Connect the database you want TraceDB to monitor.
              After connecting, you can discover schemas and
              configure table monitoring.
            </p>

          </div>

          <button
            className="new-project-button"
            onClick={() => setShowForm(!showForm)}
          >
            {showForm ? "Cancel" : "+ Add Database"}
          </button>

        </section>

        {/* ERROR */}

        {error && (
          <div className="dashboard-panel">
            <div className="activity-item">

              <div className="activity-marker">
                ERROR
              </div>

              <div className="activity-details">
                <strong>
                  Database connection error
                </strong>

                <p>
                  {error}
                </p>
              </div>

            </div>
          </div>
        )}

        {/* ADD CONNECTION FORM */}

        {showForm && (

          <section className="dashboard-panel">

            <div className="panel-header">

              <div>

                <p className="section-label">
                  NEW CONNECTION
                </p>

                <h3>
                  Database details
                </h3>

              </div>

            </div>

            <form
              className="connection-form"
              onSubmit={handleCreateConnection}
            >

              <div className="form-grid">

                <div className="form-group">

                  <label>
                    Database Type
                  </label>

                  <select
                    value={dbType}
                    onChange={(event) =>
                      setDbType(event.target.value)
                    }
                  >
                    <option value="PostgreSQL">
                      PostgreSQL
                    </option>

                    <option value="MySQL">
                      MySQL
                    </option>
                  </select>

                </div>

                <div className="form-group">

                  <label>
                    Host
                  </label>

                  <input
                    type="text"
                    placeholder="localhost"
                    value={host}
                    onChange={(event) =>
                      setHost(event.target.value)
                    }
                    required
                  />

                </div>

                <div className="form-group">

                  <label>
                    Port
                  </label>

                  <input
                    type="number"
                    placeholder="5432"
                    min="1"
                    max="65535"
                    value={port}
                    onChange={(event) =>
                      setPort(event.target.value)
                    }
                    required
                  />

                </div>

                <div className="form-group">

                  <label>
                    Database Name
                  </label>

                  <input
                    type="text"
                    placeholder="my_database"
                    value={databaseName}
                    onChange={(event) =>
                      setDatabaseName(event.target.value)
                    }
                    required
                  />

                </div>

                <div className="form-group">

                  <label>
                    Username
                  </label>

                  <input
                    type="text"
                    placeholder="postgres"
                    value={dbUsername}
                    onChange={(event) =>
                      setDbUsername(event.target.value)
                    }
                    required
                  />

                </div>

                <div className="form-group">

                  <label>
                    Password
                  </label>

                  <input
                    type="password"
                    placeholder="Database password"
                    value={password}
                    onChange={(event) =>
                      setPassword(event.target.value)
                    }
                    required
                  />

                </div>

              </div>

              <label className="ssl-option">

                <input
                  type="checkbox"
                  checked={sslEnabled}
                  onChange={(event) =>
                    setSslEnabled(event.target.checked)
                  }
                />

                <span>
                  Enable SSL
                </span>

              </label>

              <div className="form-actions">

                <button
                  type="button"
                  className="panel-link"
                  onClick={() => setShowForm(false)}
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  className="new-project-button"
                  disabled={saving}
                >
                  {saving
                    ? "Saving..."
                    : "Save Connection"}
                </button>

              </div>

            </form>

          </section>

        )}

        {/* CONNECTIONS */}

        <section className="dashboard-panel">

          <div className="panel-header">

            <div>

              <p className="section-label">
                CONNECTED DATABASES
              </p>

              <h3>
                Your database connections
              </h3>

            </div>

            <span>
              {connections.length} connection
              {connections.length !== 1 ? "s" : ""}
            </span>

          </div>

          {loading ? (

            <div className="activity-item">

              <div className="activity-details">

                <strong>
                  Loading connections...
                </strong>

                <p>
                  Retrieving database configuration.
                </p>

              </div>

            </div>

          ) : connections.length === 0 ? (

            <div className="activity-item">

              <div className="activity-marker">
                EMPTY
              </div>

              <div className="activity-details">

                <strong>
                  No database connections yet
                </strong>

                <p>
                  Add your first database connection to
                  start configuring TraceDB monitoring.
                </p>

              </div>

              <button
                className="panel-link"
                onClick={() => setShowForm(true)}
              >
                Add →
              </button>

            </div>

          ) : (

            connections.map((connection) => (

              <div
                className="activity-item"
                key={connection.connectionId}
              >

                <div className="activity-marker">
                  {connection.dbType
                    .substring(0, 4)
                    .toUpperCase()}
                </div>

                <div className="activity-details">

                  <strong>
                    {connection.databaseName}
                  </strong>

                  <p>
                    {connection.dbType} ·{" "}
                    {connection.host}:
                    {connection.port}
                  </p>

                  <small>
                    User: {connection.username}
                    {" · "}
                    SSL:{" "}
                    {connection.sslEnabled
                      ? "Enabled"
                      : "Disabled"}
                  </small>

                </div>

                <div>

                  <span className="activity-time">
                    {connection.connectionStatus}
                  </span>

                  <br />

                  {testErrors[connection.connectionId] && (
                    <small role="alert">
                      {testErrors[connection.connectionId]}
                    </small>
                  )}

                  <button
                    className="panel-link"
                    disabled={testingConnectionId === connection.connectionId}
                    onClick={() => handleTestConnection(connection)}
                  >
                    {testingConnectionId === connection.connectionId
                      ? "Testing..."
                      : "Test Connection"}
                  </button>

                  <br />

                  <button
                    className="panel-link"
                    disabled={connection.connectionStatus !== "CONNECTED"
                      || discovering && activeDiscoveryConnectionId === connection.connectionId}
                    title={connection.connectionStatus !== "CONNECTED"
                      ? "Test the connection first."
                      : "Discover schemas and tables in this database."}
                    onClick={() => void handleDiscoverDatabase(connection)}
                  >
                    {discovering && activeDiscoveryConnectionId === connection.connectionId
                      ? "Discovering..."
                      : "Discover Tables"}
                  </button>

                  <br />

                  <button
                    className="panel-link"
                    onClick={() => navigate(
                      `/projects/${projectId}/connections/${connection.connectionId}/monitoring`
                    )}
                  >
                    Monitoring
                  </button>

                  <br />

                  <button
                    className="panel-link"
                    onClick={() =>
                      handleDeleteConnection(
                        connection.connectionId
                      )
                    }
                  >
                    Delete
                  </button>

                </div>

              </div>

            ))

          )}

        </section>

        {activeDiscoveryConnectionId !== null && (
          <>
            {discoveryError && (
              <div className="dashboard-panel" role="alert">
                <div className="activity-item">
                  <div className="activity-marker">ERROR</div>
                  <div className="activity-details"><p>{discoveryError}</p></div>
                </div>
              </div>
            )}

            {discoveryNotice && (
              <div className="dashboard-panel" role="status">
                <p>{discoveryNotice}</p>
              </div>
            )}

            <section className="dashboard-panel discovery-panel">
              <div className="panel-header">
                <div>
                  <p className="section-label">DATABASE SCHEMAS</p>
                  <h3>Available schemas</h3>
                </div>
                <button
                  className="panel-link"
                  onClick={() => setActiveDiscoveryConnectionId(null)}
                >
                  Close
                </button>
              </div>

              {discovering ? (
                <div className="activity-item"><div className="activity-details"><strong>Discovering database...</strong></div></div>
              ) : schemas.length === 0 ? (
                <div className="activity-item"><div className="activity-details"><p>No user schemas found.</p></div></div>
              ) : (
                <div className="schema-list">
                  {schemas.map(({ schemaName }) => (
                    <label className="schema-option" key={schemaName}>
                      <input
                        type="radio"
                        name="database-schema"
                        checked={selectedSchema === schemaName}
                        onChange={() => void handleSelectSchema(schemaName)}
                      />
                      <span>{schemaName}</span>
                    </label>
                  ))}
                </div>
              )}
            </section>

            {selectedSchema && (
              <section className="dashboard-panel discovery-panel">
                <div className="panel-header">
                  <div>
                    <p className="section-label">TABLES IN {selectedSchema}</p>
                    <h3>Base tables</h3>
                  </div>
                  <span>{selectedTables.length} selected</span>
                </div>

                {loadingTables ? (
                  <div className="activity-item"><div className="activity-details"><strong>Loading tables...</strong></div></div>
                ) : tables.length === 0 ? (
                  <div className="activity-item"><div className="activity-details"><p>No tables found in this schema.</p></div></div>
                ) : (
                  <div className="table-discovery-list">
                    {tables.map((table) => (
                      <label className="table-discovery-row" key={`${table.schemaName}.${table.tableName}`}>
                        <input
                          type="checkbox"
                          checked={selectedTables.includes(table.tableName)}
                          onChange={() => toggleTableSelection(table.tableName)}
                        />
                        <strong>{table.tableName}</strong>
                        <span>{table.schemaName}</span>
                        <small>{table.tableType}</small>
                      </label>
                    ))}
                  </div>
                )}

                {selectedTables.length > 0 && (
                  <div className="form-actions">
                    <span>{selectedTables.length} tables selected</span>
                    <button
                      className="new-project-button"
                      onClick={openMonitoringConfiguration}
                    >
                      Configure Monitoring
                    </button>
                  </div>
                )}
              </section>
            )}

            {showMonitoringForm && selectedTables.length > 0 && (
              <section className="dashboard-panel discovery-panel">
                <div className="panel-header">
                  <div>
                    <p className="section-label">MONITORING CONFIGURATION</p>
                    <h3>Selected tables</h3>
                  </div>
                </div>

                {selectedTables.map((tableName) => {
                  const draft = monitoringDrafts[tableName] ?? DEFAULT_MONITORING_DRAFT;
                  return (
                    <div className="monitoring-draft-row" key={tableName}>
                      <div>
                        <strong>{selectedSchema}.{tableName}</strong>
                      </div>
                      <label><input type="checkbox" checked={draft.monitorInsert} onChange={(event) => updateMonitoringDraft(tableName, "monitorInsert", event.target.checked)} /> INSERT</label>
                      <label><input type="checkbox" checked={draft.monitorUpdate} onChange={(event) => updateMonitoringDraft(tableName, "monitorUpdate", event.target.checked)} /> UPDATE</label>
                      <label><input type="checkbox" checked={draft.monitorDelete} onChange={(event) => updateMonitoringDraft(tableName, "monitorDelete", event.target.checked)} /> DELETE</label>
                      <label className="sensitivity-control">
                        Sensitivity
                        <select value={draft.sensitivityLevel} onChange={(event) => updateMonitoringDraft(tableName, "sensitivityLevel", event.target.value)}>
                          <option value="LOW">LOW</option>
                          <option value="NORMAL">NORMAL</option>
                          <option value="MEDIUM">MEDIUM</option>
                          <option value="HIGH">HIGH</option>
                        </select>
                      </label>
                      <label><input type="checkbox" checked={draft.monitoringEnabled} onChange={(event) => updateMonitoringDraft(tableName, "monitoringEnabled", event.target.checked)} /> ENABLED</label>
                    </div>
                  );
                })}

                <div className="form-actions">
                  <button className="panel-link" disabled={savingMonitoring} onClick={() => setShowMonitoringForm(false)}>
                    Cancel
                  </button>
                  <button className="new-project-button" disabled={savingMonitoring} onClick={() => void handleSaveMonitoring()}>
                    {savingMonitoring ? "Saving..." : "Save Monitoring Configuration"}
                  </button>
                </div>
              </section>
            )}
          </>
        )}

        {/* NEXT STEP */}

        <section className="dashboard-panel">

          <div className="panel-header">

            <div>

              <p className="section-label">
                NEXT STEP
              </p>

              <h3>
                Table discovery
              </h3>

            </div>

          </div>

          <div className="activity-item">

            <div className="activity-marker">
              NEXT
            </div>

            <div className="activity-details">

              <strong>
                Discover database schemas and tables
              </strong>

              <p>
                Once a database is connected, TraceDB will
                discover available schemas and tables so you
                can choose what to monitor.
              </p>

            </div>

            <span className="activity-time">
              AFTER CONNECTION
            </span>

          </div>

        </section>

        {/* FOOTER */}

        <footer className="dashboard-footer">

          <span>
            TraceDB
          </span>

          <span>
            Database Provenance Platform
          </span>

          <span>
            © 2026
          </span>

        </footer>

      </section>

    </main>
  );
}

export default DatabaseConnections;