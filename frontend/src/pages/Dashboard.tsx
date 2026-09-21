import { useNavigate } from "react-router-dom";
import { clearSession, getSession } from "../auth";
import "../styles/global.css";
import "../styles/dashboard.css";

function Dashboard() {
  const navigate = useNavigate();
  
    const session = getSession();

  const username = session?.username ?? "User";
  const role = session?.role ?? "USER";

  const handleLogout = () => {
    clearSession();
    navigate("/login");
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

          <div className="nav-item active">
            <span>⌂</span>
            Dashboard
          </div>

          <div
            className="nav-item"
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

        <button
          className="logout-button"
          onClick={handleLogout}
        >
          <span>↪</span>
          Sign out
        </button>
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
              Dashboard
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

        {/* WELCOME */}

        <section className="dashboard-welcome">

          <div>

            <p className="section-label">
              TRACE YOUR DATA
            </p>

            <h2>
              Welcome to your workspace.
            </h2>

            <p>
              Monitor database changes, investigate provenance,
              and safely recover previous states.
            </p>

          </div>

          <button
            className="new-project-button"
            onClick={() => navigate("/projects")}
          >
            + New Project
          </button>

        </section>

        {/* STATISTICS */}

        <section className="dashboard-stats">

          <div className="stat-card">
            <span>PROJECTS</span>
            <strong>1</strong>
            <small>Active projects</small>
          </div>

          <div className="stat-card">
            <span>DATABASE CONNECTIONS</span>
            <strong>1</strong>
            <small>Connected databases</small>
          </div>

          <div className="stat-card">
            <span>MONITORED TABLES</span>
            <strong>1</strong>
            <small>Currently monitored</small>
          </div>

          <div className="stat-card">
            <span>AUDIT EVENTS</span>
            <strong>1</strong>
            <small>Tracked changes</small>
          </div>

        </section>

        {/* LOWER CONTENT */}

        <section className="dashboard-grid">

          {/* RECENT ACTIVITY */}

          <div className="dashboard-panel">

            <div className="panel-header">

              <div>

                <p className="section-label">
                  RECENT ACTIVITY
                </p>

                <h3>
                  Latest database changes
                </h3>

              </div>

              <button className="panel-link">
                View timeline →
              </button>

            </div>

            <div className="activity-item">

              <div className="activity-marker">
                UPDATE
              </div>

              <div className="activity-details">

                <strong>
                  users record modified
                </strong>

                <p>
                  Email value changed from old@example.com
                  to new@example.com
                </p>

                <small>
                  TXN-001 · changed by alice
                </small>

              </div>

              <span className="activity-time">
                Recent
              </span>

            </div>

          </div>

          {/* SYSTEM STATUS */}

          <div className="dashboard-panel">

            <div className="panel-header">

              <div>

                <p className="section-label">
                  SYSTEM STATUS
                </p>

                <h3>
                  TraceDB health
                </h3>

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

export default Dashboard;