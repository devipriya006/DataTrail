import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { clearSession, getSession } from "../auth";
import "../styles/global.css";
import "../styles/dashboard.css";

const API_BASE_URL = "http://localhost:8080";

type Project = {
  projectId: number;
  projectName: string;
  description?: string;
  createdAt?: string;
};

type DatabaseConnection = {
  connectionId: number;
  connectionStatus?: string | null;
  projectId: number;
};

type MonitoringConfiguration = {
  configId: number;
  connectionId: number;
  schemaName?: string | null;
  tableName?: string | null;
  monitoringEnabled?: boolean | null;
};

type AuditEvent = {
  auditId: number;
};

async function fetchArray<T>(path: string, accessToken: string): Promise<T[]> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`);
  }

  const data: unknown = await response.json();
  if (!Array.isArray(data)) {
    throw new Error("The backend returned an unexpected response format.");
  }

  return data as T[];
}

function Dashboard() {
  const navigate = useNavigate();
  const session = getSession();
  const accessToken = session?.accessToken;
  const username = session?.username ?? "User";
  const role = session?.role ?? "USER";

  const [projectCount, setProjectCount] = useState<number | null>(null);
  const [connectionCount, setConnectionCount] = useState<number | null>(null);
  const [monitoredTableCount, setMonitoredTableCount] = useState<number | null>(null);
  const [auditEventCount, setAuditEventCount] = useState<number | null>(null);
  const [loadingCounts, setLoadingCounts] = useState(true);
  const [countError, setCountError] = useState("");

  useEffect(() => {
    if (!accessToken) {
      navigate("/login");
      return;
    }

    let cancelled = false;

    const loadCounts = async () => {
      setLoadingCounts(true);
      setCountError("");
      const unavailableCounts: string[] = [];

      // These two endpoints return the signed-in user's records directly.
      // This avoids failures from querying monitoring/audit data separately
      // through every project or connection.
      const [projectsResult, monitoringResult, auditResult] =
        await Promise.allSettled([
          fetchArray<Project>("/projects", accessToken),
          fetchArray<MonitoringConfiguration>("/monitoring-configurations", accessToken),
          fetchArray<AuditEvent>("/audit-logs", accessToken),
        ]);

      if (cancelled) return;

      let projects: Project[] = [];
      if (projectsResult.status === "fulfilled") {
        projects = projectsResult.value;
        setProjectCount(projects.length);
      } else {
        setProjectCount(null);
        unavailableCounts.push("projects");
      }

      if (projectsResult.status === "fulfilled") {
        const connectionResults = await Promise.all(
          projects.map(async (project) => {
            try {
              const connections = await fetchArray<Omit<DatabaseConnection, "projectId">>(
                `/database-connections/project/${project.projectId}`,
                accessToken,
              );
              return {
                ok: true,
                connections: connections.map((connection) => ({
                  ...connection,
                  projectId: project.projectId,
                })),
              };
            } catch {
              return { ok: false, connections: [] as DatabaseConnection[] };
            }
          }),
        );

        if (cancelled) return;

        const allConnections = connectionResults.flatMap((result) => result.connections);
        const connectionsLoaded = connectionResults.every((result) => result.ok);
        setConnectionCount(connectionsLoaded ? allConnections.length : null);
        if (!connectionsLoaded) unavailableCounts.push("database connections");
      } else {
        setConnectionCount(null);
      }

      if (monitoringResult.status === "fulfilled") {
        const enabledTableKeys = new Set(
          monitoringResult.value
            .filter((configuration) => configuration.monitoringEnabled === true)
            .map((configuration) =>
              `${configuration.connectionId}:${configuration.schemaName ?? ""}.${configuration.tableName ?? `config-${configuration.configId}`}`,
            ),
        );
        setMonitoredTableCount(enabledTableKeys.size);
      } else {
        setMonitoredTableCount(null);
        unavailableCounts.push("monitored tables");
      }

      if (auditResult.status === "fulfilled") {
        setAuditEventCount(auditResult.value.length);
      } else {
        setAuditEventCount(null);
        unavailableCounts.push("audit events");
      }

      if (!cancelled && unavailableCounts.length > 0) {
        setCountError(
          `Could not load ${unavailableCounts.join(", ")} completely. A dash means the count is unavailable, not zero.`,
        );
      }

      if (!cancelled) setLoadingCounts(false);
    };

    void loadCounts().catch(() => {
      if (!cancelled) {
        setProjectCount(null);
        setConnectionCount(null);
        setMonitoredTableCount(null);
        setAuditEventCount(null);
        setCountError(
          "Dashboard counts could not be loaded. Check that the backend is running and you are signed in.",
        );
        setLoadingCounts(false);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [accessToken, navigate]);

  const handleLogout = () => {
    clearSession();
    navigate("/login");
  };

  const displayCount = (count: number | null) => {
    if (loadingCounts) return "…";
    return count === null ? "—" : count;
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
          <div className="nav-item active">
            <span>⌂</span>
            Dashboard
          </div>
          <div className="nav-item" onClick={() => navigate("/projects")}>
            <span>▣</span>
            Projects
          </div>
          <div className="nav-item">
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
        <button className="logout-button" onClick={handleLogout}>
          <span>↪</span>
          Sign out
        </button>
      </aside>

      {/* MAIN */}
      <section className="dashboard-content">
        {/* HEADER */}
        <header className="dashboard-header">
          <div>
            <p className="dashboard-eyebrow">PROVENANCE INTELLIGENCE ENGINE</p>
            <h1>Dashboard</h1>
          </div>
          <div className="dashboard-user">
            <div>
              <strong>{username}</strong>
              <small>{role}</small>
            </div>
            <div className="user-avatar">{username.charAt(0).toUpperCase()}</div>
          </div>
        </header>

        {/* WELCOME */}
        <section className="dashboard-welcome">
          <div>
            <p className="section-label">TRACE YOUR DATA</p>
            <h2>Welcome to your workspace.</h2>
            <p>
              Monitor database changes, investigate provenance,
              and safely recover previous states.
            </p>
          </div>
          <button className="new-project-button" onClick={() => navigate("/projects")}>
            + New Project
          </button>
        </section>

        {/* DYNAMIC STATISTICS */}
        <section className="dashboard-stats">
          <div className="stat-card">
            <span>PROJECTS</span>
            <strong>{displayCount(projectCount)}</strong>
            <small>Projects in workspace</small>
          </div>
          <div className="stat-card">
            <span>DATABASE CONNECTIONS</span>
            <strong>{displayCount(connectionCount)}</strong>
            <small>Saved connections</small>
          </div>
          <div className="stat-card">
            <span>MONITORED TABLES</span>
            <strong>{displayCount(monitoredTableCount)}</strong>
            <small>Tables with monitoring enabled</small>
          </div>
          <div className="stat-card">
            <span>AUDIT EVENTS</span>
            <strong>{displayCount(auditEventCount)}</strong>
            <small>Tracked changes</small>
          </div>
        </section>

        {countError && (
          <p className="auth-error" role="status" aria-live="polite">
            {countError}
          </p>
        )}

        {/* LOWER CONTENT — preserved for this counts-only update */}
        <section className="dashboard-grid">
          {/* RECENT ACTIVITY */}
          <div className="dashboard-panel">
            <div className="panel-header">
              <div>
                <p className="section-label">RECENT ACTIVITY</p>
                <h3>Latest database changes</h3>
              </div>
              <button className="panel-link">View timeline →</button>
            </div>
            <div className="activity-item">
              <div className="activity-marker">UPDATE</div>
              <div className="activity-details">
                <strong>users record modified</strong>
                <p>
                  Email value changed from old@example.com
                  to new@example.com
                </p>
                <small>TXN-001 · changed by alice</small>
              </div>
              <span className="activity-time">Recent</span>
            </div>
          </div>

          {/* SYSTEM STATUS — preserved for this counts-only update */}
          <div className="dashboard-panel">
            <div className="panel-header">
              <div>
                <p className="section-label">SYSTEM STATUS</p>
                <h3>TraceDB health</h3>
              </div>
            </div>
            <div className="health-row">
              <span>Backend API</span>
              <strong>ONLINE</strong>
            </div>
            <div className="health-row">
              <span>PostgreSQL</span>
              <strong>CONNECTED</strong>
            </div>
            <div className="health-row">
              <span>Monitoring Engine</span>
              <strong>ACTIVE</strong>
            </div>
            <div className="health-row">
              <span>Provenance Engine</span>
              <strong>READY</strong>
            </div>
          </div>
        </section>

        {/* FOOTER */}
        <footer className="dashboard-footer">
          <span>TraceDB</span>
          <span>Database Provenance Platform</span>
          <span>© 2026</span>
        </footer>
      </section>
    </main>
  );
}

export default Dashboard;
