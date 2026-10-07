import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { clearSession, getSession } from "../auth";
import "../styles/global.css";
import "../styles/dashboard.css";

type MonitoringConfiguration = {
  configId: number;
  connectionId: number;
  schemaName: string;
  tableName: string;
  monitorInsert: boolean;
  monitorUpdate: boolean;
  monitorDelete: boolean;
  sensitivityLevel: string;
  monitoringEnabled: boolean;
  effectiveFrom: string;
  effectiveTo: string | null;
};

function Monitoring() {
  const navigate = useNavigate();
  const { projectId, connectionId } = useParams<{
    projectId: string;
    connectionId: string;
  }>();
  const session = getSession();
  const [configurations, setConfigurations] = useState<MonitoringConfiguration[]>([]);
  const [loading, setLoading] = useState(true);
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [error, setError] = useState("");

  const handleUnauthorized = () => {
    clearSession();
    navigate("/login", { replace: true });
  };

  const responseMessage = async (response: Response, fallback: string) => {
    if (response.status === 401) {
      handleUnauthorized();
      return "Your session has expired. Please log in again.";
    }
    if (response.status >= 500) {
      return "Unable to complete the monitoring request.";
    }
    try {
      const body: { message?: string } = await response.json();
      return body.message || fallback;
    } catch {
      return fallback;
    }
  };

  const loadConfigurations = async () => {
    setLoading(true);
    if (!session?.accessToken) {
      handleUnauthorized();
      return;
    }
    if (!projectId || !connectionId) {
      setError("Project or database connection was not found.");
      setLoading(false);
      return;
    }

    try {
      const response = await fetch(
        `http://localhost:8080/monitoring-configurations/connection/${connectionId}?projectId=${projectId}`,
        { headers: { Authorization: `Bearer ${session.accessToken}` } }
      );
      if (!response.ok) {
        setError(await responseMessage(response, "Unable to load monitoring configurations."));
        return;
      }
      setConfigurations(await response.json());
      setError("");
    } catch {
      setError("Unable to connect to DataTrail. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadConfigurations();
  }, [projectId, connectionId]);

  const updateConfiguration = async (configuration: MonitoringConfiguration) => {
    if (!session?.accessToken || !projectId || !connectionId) {
      handleUnauthorized();
      return;
    }
    setUpdatingId(configuration.configId);
    setError("");
    try {
      const response = await fetch(
        `http://localhost:8080/monitoring-configurations/${configuration.configId}`,
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${session.accessToken}`,
          },
          body: JSON.stringify({
            projectId: Number(projectId),
            connectionId: Number(connectionId),
            schemaName: configuration.schemaName,
            tableName: configuration.tableName,
            monitorInsert: configuration.monitorInsert,
            monitorUpdate: configuration.monitorUpdate,
            monitorDelete: configuration.monitorDelete,
            sensitivityLevel: configuration.sensitivityLevel,
            monitoringEnabled: !configuration.monitoringEnabled,
          }),
        }
      );
      if (!response.ok) {
        setError(await responseMessage(response, "Unable to update monitoring configuration."));
        return;
      }
      const updated: MonitoringConfiguration = await response.json();
      setConfigurations((current) => current.map((item) =>
        item.configId === updated.configId ? updated : item
      ));
    } catch {
      setError("Unable to connect to DataTrail. Please try again.");
    } finally {
      setUpdatingId(null);
    }
  };

  const deleteConfiguration = async (configuration: MonitoringConfiguration) => {
    if (!session?.accessToken || !projectId) {
      handleUnauthorized();
      return;
    }
    if (!window.confirm(`Delete monitoring for ${configuration.schemaName}.${configuration.tableName}?`)) {
      return;
    }
    setDeletingId(configuration.configId);
    setError("");
    try {
      const response = await fetch(
        `http://localhost:8080/monitoring-configurations/${configuration.configId}?projectId=${projectId}`,
        {
          method: "DELETE",
          headers: { Authorization: `Bearer ${session.accessToken}` },
        }
      );
      if (!response.ok) {
        setError(await responseMessage(response, "Unable to delete monitoring configuration."));
        return;
      }
      setConfigurations((current) => current.filter(
        (item) => item.configId !== configuration.configId
      ));
    } catch {
      setError("Unable to connect to DataTrail. Please try again.");
    } finally {
      setDeletingId(null);
    }
  };

  const username = session?.username ?? "User";
  const role = session?.role ?? "USER";

  return (
    <main className="dashboard-page">
      <aside className="dashboard-sidebar">
        <div className="dashboard-logo">
          <span className="logo-mark">T</span>
          <span className="logo-text">TraceDB</span>
        </div>
        <nav className="dashboard-nav">
          <button className="nav-item" onClick={() => navigate("/dashboard")}>
            <span>⌂</span>Dashboard
          </button>
          <button className="nav-item active" onClick={() => navigate("/projects")}>
            <span>▣</span>Projects
          </button>
          <button
            className="nav-item"
            onClick={() => navigate(`/projects/${projectId}/connections`)}
          >
            <span>◉</span>Connections
          </button>
          <div className="nav-item active"><span>◈</span>Monitoring</div>
        </nav>
      </aside>

      <section className="dashboard-content">
        <header className="dashboard-header">
          <div>
            <p className="dashboard-eyebrow">PROVENANCE INTELLIGENCE ENGINE</p>
            <h1>Monitoring</h1>
          </div>
          <div className="dashboard-user">
            <div><strong>{username}</strong><small>{role}</small></div>
            <div className="user-avatar">{username.charAt(0).toUpperCase()}</div>
          </div>
        </header>

        <button
          className="panel-link"
          onClick={() => navigate(`/projects/${projectId}/connections`)}
        >
          ← Back to Database Connections
        </button>

        {error && <div className="dashboard-panel" role="alert">{error}</div>}

        <section className="dashboard-panel monitoring-list">
          <div className="panel-header">
            <div>
              <p className="section-label">TABLE MONITORING</p>
              <h3>Connection #{connectionId}</h3>
            </div>
            <span>{configurations.length} table{configurations.length === 1 ? "" : "s"}</span>
          </div>

          {loading ? (
            <div className="activity-item"><div className="activity-details"><strong>Loading monitoring configurations...</strong></div></div>
          ) : configurations.length === 0 ? (
            <div className="activity-item">
              <div className="activity-marker">EMPTY</div>
              <div className="activity-details">
                <strong>No tables are currently configured for monitoring.</strong>
              </div>
            </div>
          ) : configurations.map((configuration) => (
            <article className="activity-item monitoring-config-row" key={configuration.configId}>
              <div className="activity-marker">TABLE</div>
              <div className="activity-details">
                <strong>{configuration.schemaName}.{configuration.tableName}</strong>
                <p>
                  INSERT {configuration.monitorInsert ? "✓" : "—"} · UPDATE {configuration.monitorUpdate ? "✓" : "—"} · DELETE {configuration.monitorDelete ? "✓" : "—"}
                </p>
                <small>Sensitivity: {configuration.sensitivityLevel} · {configuration.monitoringEnabled ? "ACTIVE" : "DISABLED"}</small>
              </div>
              <div className="monitoring-actions">
                <button
                  className="panel-link"
                  disabled={updatingId === configuration.configId || deletingId === configuration.configId}
                  onClick={() => void updateConfiguration(configuration)}
                >
                  {updatingId === configuration.configId
                    ? "Updating..."
                    : configuration.monitoringEnabled ? "Disable" : "Enable"}
                </button>
                <button
                  className="panel-link"
                  disabled={updatingId === configuration.configId || deletingId === configuration.configId}
                  onClick={() => void deleteConfiguration(configuration)}
                >
                  {deletingId === configuration.configId ? "Deleting..." : "Delete"}
                </button>
              </div>
            </article>
          ))}
        </section>

        <footer className="dashboard-footer">
          <span>TraceDB</span>
          <span>Database Provenance Platform</span>
          <span>© 2026</span>
        </footer>
      </section>
    </main>
  );
}

export default Monitoring;
