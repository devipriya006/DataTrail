import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getSession } from "../auth";
import "../styles/global.css";
import "../styles/dashboard.css";

type Project = {
  projectId: number;
  projectName: string;
  description: string;
  createdAt: string;
};

function Projects() {
  const navigate = useNavigate();
  const session = getSession();

  const [projects, setProjects] = useState<Project[]>([]);
  const [showForm, setShowForm] = useState(false);

  const [projectName, setProjectName] = useState("");
  const [description, setDescription] = useState("");

  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");

  const loadProjects = async () => {
    if (!session?.accessToken) {
      navigate("/login");
      return;
    }

    try {
      const response = await fetch(
        "http://localhost:8080/projects",
        {
          headers: {
            Authorization: `Bearer ${session.accessToken}`,
          },
        }
      );

      if (!response.ok) {
        throw new Error("Failed to load projects");
      }

      const data = await response.json();

      setProjects(data);
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Failed to load projects"
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProjects();
  }, []);

  const handleCreateProject = async (
    event: React.FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    if (!session?.accessToken) {
      navigate("/login");
      return;
    }

    setCreating(true);
    setError("");

    try {
      const response = await fetch(
        "http://localhost:8080/projects",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${session.accessToken}`,
          },
          body: JSON.stringify({
            projectName,
            description,
            ownerId: session.userId,
          }),
        }
      );

      if (!response.ok) {
        const message = await response.text();

        throw new Error(
          message || "Failed to create project"
        );
      }

      setProjectName("");
      setDescription("");
      setShowForm(false);

      await loadProjects();

    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Failed to create project"
      );
    } finally {
      setCreating(false);
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

          <div className="nav-item active">
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

        <header className="dashboard-header">

          <div>
            <p className="dashboard-eyebrow">
              PROVENANCE INTELLIGENCE ENGINE
            </p>

            <h1>
              Projects
            </h1>
          </div>

          <div className="dashboard-user">

            <div>
              <strong>{session?.username}</strong>
              <small>{session?.role}</small>
            </div>

            <div className="user-avatar">
              {session?.username
                ?.charAt(0)
                .toUpperCase()}
            </div>

          </div>

        </header>

        <section className="dashboard-welcome">

          <div>
            <p className="section-label">
              WORKSPACES
            </p>

            <h2>
              Your projects
            </h2>

            <p>
              Create and manage database provenance
              monitoring projects.
            </p>
          </div>

          <button
            className="new-project-button"
            onClick={() => setShowForm(true)}
          >
            + New Project
          </button>

        </section>

        {/* CREATE PROJECT FORM */}

        {showForm && (

          <section className="dashboard-panel">

            <div className="panel-header">

              <div>
                <p className="section-label">
                  NEW WORKSPACE
                </p>

                <h3>
                  Create Project
                </h3>
              </div>

              <button
                className="panel-link"
                onClick={() => setShowForm(false)}
              >
                Cancel
              </button>

            </div>

            <form onSubmit={handleCreateProject}>

              <div className="input-group">

                <label>
                  Project name
                </label>

                <input
                  type="text"
                  value={projectName}
                  onChange={(event) =>
                    setProjectName(event.target.value)
                  }
                  placeholder="e.g. TraceDB Production"
                  required
                />

              </div>

              <div className="input-group">

                <label>
                  Description
                </label>

                <textarea
                  value={description}
                  onChange={(event) =>
                    setDescription(event.target.value)
                  }
                  placeholder="Describe what this project monitors..."
                  rows={4}
                />

              </div>

              {error && (
                <p className="auth-error">
                  {error}
                </p>
              )}

              <button
                type="submit"
                className="new-project-button"
                disabled={creating}
              >
                {creating
                  ? "Creating..."
                  : "Create Project"}
              </button>

            </form>

          </section>

        )}

        {/* PROJECT LIST */}

        <section className="dashboard-grid">

          <div className="dashboard-panel">

            <div className="panel-header">

              <div>
                <p className="section-label">
                  PROJECTS
                </p>

                <h3>
                  All projects
                </h3>
              </div>

            </div>

            {loading && (
              <div className="activity-item">
                Loading projects...
              </div>
            )}

            {!loading &&
              projects.length === 0 && (

                <div className="activity-item">

                  <div className="activity-marker">
                    EMPTY
                  </div>

                  <div className="activity-details">

                    <strong>
                      No projects yet
                    </strong>

                    <p>
                      Create your first project
                      to begin monitoring a database.
                    </p>

                  </div>

                </div>

              )}

            {projects.map((project) => (

              <div
                className="activity-item"
                key={project.projectId}
              >

                <div className="activity-marker">
                  PROJECT
                </div>

                <div className="activity-details">

                  <strong>
                    {project.projectName}
                  </strong>

                  <p>
                    {project.description ||
                      "No description provided."}
                  </p>

                  <small>
                    Project #{project.projectId}
                  </small>

                </div>

                <button
                  className="panel-link"
                  onClick={() =>
                    navigate(
                      `/projects/${project.projectId}`
                    )
                  }
                >
                  Open →
                </button>

              </div>

            ))}

          </div>

        </section>

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

export default Projects;