import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { clearSession, getSession } from "../auth";
import "../styles/global.css";
import "../styles/dashboard.css";

type Project = {
  projectId: number;
  projectName: string;
  description: string;
  createdAt: string;
  owner?: {
    userId: number;
    username: string;
    email: string;
    role?: {
      roleName: string;
    };
  };
};

type DatabaseConnection = {
  connectionId: number;
  dbType: string;
  host: string;
  port: number;
  databaseName: string;
  connectionStatus: string;
};

function ProjectDetails() {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();
  const session = getSession();

  const [project, setProject] = useState<Project | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [connections, setConnections] = useState<DatabaseConnection[]>([]);
  const [loadingConnections, setLoadingConnections] = useState(true);
  const [connectionsError, setConnectionsError] = useState("");

  const username = session?.username ?? "User";
  const role = session?.role ?? "USER";

  useEffect(() => {
    const loadProject = async () => {
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
        const response = await fetch(
          `http://localhost:8080/projects/${projectId}`,
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
          throw new Error("Project not found.");
        }

        const data: Project = await response.json();
        setProject(data);
      } catch (error) {
        setError(
          error instanceof Error
            ? error.message
            : "Failed to load project."
        );
      } finally {
        setLoading(false);
      }
    };

    const loadConnections = async () => {
      if (!session?.accessToken) {
        return;
      }

      if (!projectId) {
        setConnectionsError("Project ID is missing.");
        setLoadingConnections(false);
        return;
      }

      try {
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
        setConnectionsError(
          error instanceof Error
            ? error.message
            : "Failed to load database connections."
        );
      } finally {
        setLoadingConnections(false);
      }
    };

    void loadProject();
    void loadConnections();
  }, [projectId, session?.accessToken, navigate]);

  if (loading) {
    return (
      <main className="dashboard-page">
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
          </nav>
        </aside>

        <section className="dashboard-content">
          <header className="dashboard-header">
            <div>
              <p className="dashboard-eyebrow">
                PROVENANCE INTELLIGENCE ENGINE
              </p>

              <h1>Project Workspace</h1>
            </div>
          </header>

          <section className="dashboard-welcome">
            <div>
              <p className="section-label">LOADING PROJECT</p>
              <h2>Loading workspace...</h2>
              <p>
                Retrieving project information from TraceDB.
              </p>
            </div>
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
              Project Workspace
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

        {/* ERROR */}

        {error ? (

          <section className="dashboard-welcome">

            <div>

              <p className="section-label">
                PROJECT ERROR
              </p>

              <h2>
                Unable to load project.
              </h2>

              <p>
                {error}
              </p>

            </div>

            <button
              className="new-project-button"
              onClick={() => navigate("/projects")}
            >
              ← Back to Projects
            </button>

          </section>

        ) : project ? (

          <>

            {/* PROJECT HEADER */}

            <section className="dashboard-welcome">

              <div>

                <p className="section-label">
                  PROJECT WORKSPACE
                </p>

                <h2>
                  {project.projectName}
                </h2>

                <p>
                  {project.description ||
                    "No project description provided."}
                </p>

              </div>

              <button
                className="new-project-button"
                onClick={() => navigate("/projects")}
              >
                ← Projects
              </button>

            </section>

            {/* PROJECT STATS */}

            <section className="dashboard-stats">

              <div className="stat-card">
                <span>PROJECT ID</span>
                <strong>
                  #{project.projectId}
                </strong>
                <small>
                  TraceDB project
                </small>
              </div>

              <div className="stat-card">
                <span>OWNER</span>
                <strong>
                  {project.owner?.username || username}
                </strong>
                <small>
                  Project owner
                </small>
              </div>

              <div className="stat-card">
                <span>DATABASE CONNECTIONS</span>
                <strong>{connections.length}</strong>
                <small>
                  {connections.length === 1 ? "Saved connection" : "Saved connections"}
                </small>
              </div>

              <div className="stat-card">
                <span>AUDIT EVENTS</span>
                <strong>0</strong>
                <small>
                  No events captured
                </small>
              </div>

            </section>

            {/* WORKSPACE */}

            <section className="dashboard-grid">

              {/* DATABASE CONNECTION */}

              <div className="dashboard-panel">

                <div className="panel-header">

                  <div>

                    <p className="section-label">
                      DATABASE
                    </p>

                    <h3>
                      Database Connections
                    </h3>

                  </div>

                  <button
                    className="panel-link"
                    onClick={() =>
                      navigate(
                        `/projects/${project.projectId}/connections`
                      )
                    }
                  >
                    Manage →
                  </button>

                </div>

                {loadingConnections ? (
                  <div className="activity-item">
                    <div className="activity-details"><strong>Loading database connections...</strong></div>
                  </div>
                ) : connectionsError ? (
                  <div className="activity-item" role="alert">
                    <div className="activity-details">
                      <strong>Unable to load database connections.</strong>
                      <p>{connectionsError}</p>
                    </div>
                  </div>
                ) : connections.length === 0 ? (
                  <div className="activity-item">
                    <div className="activity-marker">SETUP</div>
                    <div className="activity-details">
                      <strong>No database connected</strong>
                      <p>Connect an external database to discover tables and begin monitoring.</p>
                    </div>
                    <button
                      className="panel-link"
                      onClick={() => navigate(`/projects/${project.projectId}/connections`)}
                    >
                      Add →
                    </button>
                  </div>
                ) : (
                  connections.map((connection) => (
                    <div className="activity-item" key={connection.connectionId}>
                      <div className="activity-marker">DB</div>
                      <div className="activity-details">
                        <strong>{connection.databaseName}</strong>
                        <p>{connection.dbType} · {connection.host}:{connection.port}</p>
                        <small>{connection.connectionStatus}</small>
                      </div>
                      <button
                        className="panel-link"
                        onClick={() => navigate(`/projects/${project.projectId}/connections`)}
                      >
                        Manage →
                      </button>
                    </div>
                  ))
                )}

              </div>

              {/* PROJECT STATUS */}

              <div className="dashboard-panel">

                <div className="panel-header">

                  <div>

                    <p className="section-label">
                      PROJECT STATUS
                    </p>

                    <h3>
                      Provenance readiness
                    </h3>

                  </div>

                </div>

                <div className="health-row">
                  <span>Database Connection</span>
                  <strong>NOT SET</strong>
                </div>

                <div className="health-row">
                  <span>Table Monitoring</span>
                  <strong>WAITING</strong>
                </div>

                <div className="health-row">
                  <span>Audit Collection</span>
                  <strong>READY</strong>
                </div>

                <div className="health-row">
                  <span>Provenance Engine</span>
                  <strong>READY</strong>
                </div>

              </div>

            </section>

            {/* WORKSPACE MODULES */}

            <section className="dashboard-panel">

              <div className="panel-header">

                <div>

                  <p className="section-label">
                    TRACE YOUR DATA
                  </p>

                  <h3>
                    Project Modules
                  </h3>

                </div>

              </div>

              <div className="activity-item">

                <div className="activity-marker">
                  01
                </div>

                <div className="activity-details">

                  <strong>
                    Database Connections
                  </strong>

                  <p>
                    Connect PostgreSQL or another supported
                    database to this TraceDB project.
                  </p>

                </div>

                <button
                  className="panel-link"
                  onClick={() =>
                    navigate(
                      `/projects/${project.projectId}/connections`
                    )
                  }
                >
                  Open →
                </button>

              </div>

              <div className="activity-item">

                <div className="activity-marker">
                  02
                </div>

                <div className="activity-details">

                  <strong>
                    Monitoring
                  </strong>

                  <p>
                    Select schemas and tables and configure
                    INSERT, UPDATE and DELETE tracking.
                  </p>

                </div>

                <span className="activity-time">
                  NEXT
                </span>

              </div>

              <div className="activity-item">

                <div className="activity-marker">
                  03
                </div>

                <div className="activity-details">

                  <strong>
                    Audit Timeline
                  </strong>

                  <p>
                    Investigate database changes with
                    chronological before and after states.
                  </p>

                </div>

                <span className="activity-time">
                  READY
                </span>

              </div>

              <div className="activity-item">

                <div className="activity-marker">
                  04
                </div>

                <div className="activity-details">

                  <strong>
                    Provenance
                  </strong>

                  <p>
                    Explore relationships and dependencies
                    between database events.
                  </p>

                </div>

                <span className="activity-time">
                  READY
                </span>

              </div>

              <div className="activity-item">

                <div className="activity-marker">
                  05
                </div>

                <div className="activity-details">

                  <strong>
                    Recovery
                  </strong>

                  <p>
                    Plan controlled recovery actions while
                    preserving the complete history.
                  </p>

                </div>

                <span className="activity-time">
                  READY
                </span>

              </div>

            </section>

            {/* PROJECT INFORMATION */}

            <section className="dashboard-panel">

              <div className="panel-header">

                <div>

                  <p className="section-label">
                    PROJECT INFORMATION
                  </p>

                  <h3>
                    Details
                  </h3>

                </div>

              </div>

              <div className="health-row">
                <span>Project name</span>
                <strong>
                  {project.projectName}
                </strong>
              </div>

              <div className="health-row">
                <span>Project ID</span>
                <strong>
                  #{project.projectId}
                </strong>
              </div>

              <div className="health-row">
                <span>Owner</span>
                <strong>
                  {project.owner?.username || username}
                </strong>
              </div>

              <div className="health-row">
                <span>Created</span>
                <strong>
                  {new Date(
                    project.createdAt
                  ).toLocaleDateString("en-IN", {
                    day: "2-digit",
                    month: "short",
                    year: "numeric",
                  })}
                </strong>
              </div>

            </section>

          </>

        ) : null}

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

export default ProjectDetails;